package com.example.driverlogger;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.ActivityOptions;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.hardware.HardwareBuffer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * Driverade LoggerService with Standby Mode support, unified screenshot coordination, and OCR retry.
 */
public class LoggerService extends AccessibilityService {
    static final int DEBOUNCE_MS = 350;
    static final int TIMED_MS = 1200;
    static final int MIN_SHOT_GAP_MS = 1050;
    static final int SHOT_WIDTH = 720;
    static final int JPEG_QUALITY = 55;
    static final int MAX_NODES = 1500;

    static final long PRE_ROLL_MS = 8000;
    static final long POST_ROLL_MS = 10000;
    static final double DUPLICATE_DIFF = 2.0;
    static final double BIG_CHANGE_DIFF = 22.0;
    static final long VISUAL_COOLDOWN_MS = 8000;

    static volatile boolean running = false;
    static volatile LoggerService instance = null;

    private static final Pattern ACCEPT = Pattern.compile("(text|desc)=\"(Accept|Match)( \u2022[^"]*)?\"");
    private static final Pattern HAS_DOLLAR = Pattern.compile("(text|desc)=\"[^"]*\\$\\d+");
    private static final Pattern BOUNDS_STRIP = Pattern.compile(" bounds=-?\\d+,-?\\d+,-?\\d+,-?\\d+");
    private static final String[] MARKERS = {
            "text=\"Decline\"", "Reject ride", "est. rate for this ride", "eservation request",
            "request matching", "Matched to trip", "xclusive", "Scheduled ride", "Trip Radar",
            "Picking up", "Waiting for rider", "Dropping off", "Finding trips",
            "Waiting for requests", "You're offline", "You're online", "You're Online",
            "GO OFFLINE", "Go Offline",
            "desc=\"ACCEPT\"", "Schedule Bookings", "SUGGESTED SCHEDULED RIDE"
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    static final String UBER = "com.ubercab.driver";
    static final String LYFT = "com.lyft.android.driver";
    static final String HICH = "us.mi.hich.driver";
    private static final long HICH_TAP_GAP_MS = 8000;
    private static final long HICH_REPEAT_MS = 600000;
    private static final long HICH_ONLINE_SETTLE_MS = 1500;
    private static final long HICH_ONLINE_REPEAT_MS = 600000;
    private static final long HICH_OPEN_GAP_MS = 3000;
    private static final long HICH_ACCEPT_RETRY_MS = 2500;
    private static final Pattern OFFLINE = Pattern.compile("You're offline", Pattern.CASE_INSENSITIVE);
    private static final long PRIORITY_GAP_MS = 6000;
    private static final long PRIORITY_NOTE_GAP_MS = 30000;
    private static final long OFFER_REPEAT_MS = 90000;
    private static final long OCR_GAP_MS = 1100;

    private Flasher flasher;
    private TextRecognizer recognizer;
    private final Map<String, Long> offerSeen = new HashMap<>();
    private Rect blankUber = null;
    private volatile Rect pendingOcrBounds = null;
    private int unreadOcrRetries = 0;
    private long lastOcr = 0;
    private long flashUntil = 0;
    private long lastPriorityTap = 0;
    private long lastHichTap = 0;
    private long hichOfflineSince = 0;
    private long lastHichOnlineTap = 0;
    private int lastHichCount = 0;
    private long lastPriorityNote = 0;
    private long lastHichOpen = 0;
    private long fastUntil = 0;
    private final Map<String, HichRide> hichRides = new HashMap<>();
    private volatile boolean ocrBusy = false;
    private boolean ocrRetryScheduled = false;

    private boolean scheduled = false;
    private String lastBodySig = "";
    private long lastTxtWriteMs = 0;
    private long lastShot = 0;
    private List<String> watched = null;
    private long watchedLoadedAt = 0;
    private String curWinSig = "";
    private String prevWinSig = null;
    private int lastState = -1;
    private volatile boolean watchedPresent = false;

    private int writes = 0;
    private int[] lastSig = null;
    private int[] prevSig = null;
    private long keepUntil = 0;
    private long lastVisualTrigger = 0;
    private long lastKeepLog = 0;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            try {
                if (!Store.isStandby(LoggerService.this)) {
                    PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
                    if (pm != null && pm.isInteractive()) capture("", false, true);
                }
            } catch (Throwable t) {
                logLine("tick error: " + t);
            }
            handler.postDelayed(this, TIMED_MS);
        }
    };

    private final Runnable ocrRetryRunnable = new Runnable() {
        @Override
        public void run() {
            ocrRetryScheduled = false;
            Rect r = pendingOcrBounds;
            if (r != null && !Store.isStandby(LoggerService.this)) {
                String base = "cap_" + new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)
                        .format(new Date(System.currentTimeMillis()));
                shoot(base, true, r);
            }
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.eventTypes |= AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                    | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                    | AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED
                    | AccessibilityEvent.TYPE_WINDOWS_CHANGED;
            info.flags |= AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
                    | AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                    | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            info.notificationTimeout = 50;
            setServiceInfo(info);
        }
        running = true;
        instance = this;
        flasher = new Flasher(this);
        try {
            recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        } catch (Throwable t) {
            recognizer = null;
            logLine("OCR unavailable: " + t);
        }
        io.execute(new Runnable() {
            @Override
            public void run() {
                Store.clearPending(LoggerService.this);
            }
        });
        logLine("SERVICE CONNECTED, watching " + Store.getApps(this));
        handler.removeCallbacks(tick);
        handler.postDelayed(tick, TIMED_MS);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        running = false;
        instance = null;
        if (flasher != null) flasher.remove();
        handler.removeCallbacksAndMessages(null);
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        running = false;
        instance = null;
        if (flasher != null) flasher.remove();
        if (recognizer != null) recognizer.close();
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    public void onInterrupt() {
    }

    static boolean triggerTestAlert(Offers.Offer sample) {
        final LoggerService svc = instance;
        if (svc == null || svc.flasher == null) return false;
        svc.handler.post(new Runnable() {
            @Override
            public void run() {
                String label = Flasher.formatPillText(sample, Store.getPillFormat(svc));
                long holdMs = Math.max(Flasher.TOTAL_MS, Store.getPillDuration(svc) * 1000L);
                svc.flashUntil = SystemClock.elapsedRealtime() + holdMs + 500;
                svc.flasher.flash(label, Color.rgb(0, 200, 83), Store.getOpacity(svc),
                        Store.getVibrate(svc), Store.getSound(svc));
            }
        });
        return true;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e == null || Store.isStandby(this)) return;
        int type = e.getEventType();
        CharSequence p = e.getPackageName();
        String pkg = p == null ? "" : p.toString();

        if (type == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            if (isWatched(pkg)) {
                if (HICH.equals(pkg)) {
                    fastUntil = SystemClock.elapsedRealtime() + 8000;
                    openHichNotification(e);
                }
                final String note = "notification from " + pkg + ": " + e.getText();
                logLine(note);
                capture(note, true, false);
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (!Store.isStandby(LoggerService.this)) {
                            capture("follow-up to " + note, true, false);
                        }
                    }
                }, 1500);
            }
            return;
        }
        if (type == AccessibilityEvent.TYPE_WINDOWS_CHANGED || isWatched(pkg)) {
            scheduleCapture();
        }
    }

    private boolean isWatched(String pkg) {
        long n = SystemClock.elapsedRealtime();
        if (watched == null || n - watchedLoadedAt > 3000) {
            watched = Store.getApps(this);
            watchedLoadedAt = n;
        }
        return watched.contains(pkg);
    }

    private void scheduleCapture() {
        if (scheduled || Store.isStandby(this)) return;
        scheduled = true;
        long delay = SystemClock.elapsedRealtime() < fastUntil ? 120 : DEBOUNCE_MS;
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                scheduled = false;
                if (!Store.isStandby(LoggerService.this)) {
                    capture("", false, false);
                }
            }
        }, delay);
    }

    private void capture(String note, boolean force, boolean timed) {
        if (Store.isStandby(this)) return;
        try {
            long now = System.currentTimeMillis();
            final String base = "cap_"
                    + new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(new Date(now));

            StringBuilder body = new StringBuilder();
            boolean any = dumpWindows(body);
            watchedPresent = any;
            String b = body.toString();
            boolean treeOfferFound = false;
            if (any) {
                treeOfferFound = scanOffers(b);
                tryPriorityMode(b);
                tryHichScheduled(b);
                tryHichOnline(b);
                tryHichAccept(b);
                if (blankUber != null && !treeOfferFound) {
                    pendingOcrBounds = new Rect(blankUber);
                    trigger("uber offer card with no readable text");
                } else if (blankUber == null) {
                    pendingOcrBounds = null;
                    unreadOcrRetries = 0;
                }
            } else {
                pendingOcrBounds = null;
            }

            String bodySig = BOUNDS_STRIP.matcher(b).replaceAll("");
            boolean changed = !bodySig.equals(lastBodySig);

            if (force) trigger("notification");
            if (any) {
                if (prevWinSig != null && !curWinSig.equals(prevWinSig)) {
                    trigger("window set changed: " + curWinSig.trim());
                }
                prevWinSig = curWinSig;
                int st = stateOf(b);
                if (lastState != -1 && st != lastState) trigger("screen state changed");
                lastState = st;
            }

            if (force || (any && (changed || now - lastTxtWriteMs >= 15000))) {
                lastBodySig = bodySig;
                lastTxtWriteMs = now;
                final String text = "time="
                        + new SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy", Locale.US).format(new Date(now))
                        + "\nnote=" + note + "\n\n" + b;
                io.execute(new Runnable() {
                    @Override
                    public void run() {
                        writeFile(base + ".txt", text.getBytes(StandardCharsets.UTF_8));
                    }
                });
            }

            Rect ocrRect = pendingOcrBounds;
            boolean wantOcr = ocrRect != null && recognizer != null && !ocrBusy
                    && (SystemClock.elapsedRealtime() - lastOcr >= OCR_GAP_MS)
                    && (SystemClock.elapsedRealtime() >= flashUntil);
            boolean shot = force || wantOcr
                    || (timed && (Store.isAlways(this) || any))
                    || (!timed && any && changed);
            if (shot) {
                shoot(base, force || wantOcr, wantOcr ? ocrRect : null);
            }
        } catch (Throwable t) {
            logLine("capture error: " + t);
        }
    }

    private static int stateOf(String body) {
        int st = ACCEPT.matcher(body).find() ? 1 : 0;
        for (int i = 0; i < MARKERS.length; i++) {
            if (body.contains(MARKERS[i])) st |= 1 << (i + 1);
        }
        return st;
    }

    private boolean dumpWindows(StringBuilder sb) {
        boolean any = false;
        boolean realWatchedWindow = false;
        StringBuilder others = new StringBuilder();
        StringBuilder wsig = new StringBuilder();
        int noroot = 0;
        blankUber = null;
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int screenW = dm.widthPixels;
        int screenH = dm.heightPixels;

        List<AccessibilityWindowInfo> wins = null;
        try {
            wins = getWindows();
        } catch (Throwable t) {
            logLine("getWindows error: " + t);
        }

        if (wins != null) {
            for (AccessibilityWindowInfo w : wins) {
                AccessibilityNodeInfo root = null;
                try {
                    root = w.getRoot();
                } catch (Throwable ignored) {
                }
                if (root == null) {
                    noroot++;
                    others.append("noroot:L").append(w.getLayer())
                            .append(":t").append(w.getType()).append("  ");
                    continue;
                }
                CharSequence cs = root.getPackageName();
                String pkg = cs == null ? "?" : cs.toString();
                if (isWatched(pkg)) {
                    any = true;
                    sb.append("=== WINDOW ").append(pkg)
                            .append(" layer=").append(w.getLayer()).append(" ===\n");
                    int[] count = new int[]{0};
                    StringBuilder seg = new StringBuilder();
                    Rect[] blankSubtree = new Rect[]{null};
                    dumpNode(root, 0, seg, count, pkg.equals(UBER), screenW, screenH, blankSubtree);
                    String segStr = seg.toString();
                    sb.append(segStr);

                    if (pkg.equals(UBER)) {
                        detectBlankUber(root, w.getLayer(), count[0], segStr, blankSubtree[0], screenW, screenH);
                    }
                    if (count[0] > 3) {
                        realWatchedWindow = true;
                        wsig.append(pkg).append(":L").append(w.getLayer()).append(' ');
                    }
                } else {
                    others.append(pkg).append(":L").append(w.getLayer())
                            .append(":t").append(w.getType()).append("  ");
                }
            }
        }

        if (!realWatchedWindow) {
            try {
                AccessibilityNodeInfo r = getRootInActiveWindow();
                if (r != null) {
                    CharSequence cs = r.getPackageName();
                    String pkg = cs == null ? "?" : cs.toString();
                    if (isWatched(pkg)) {
                        any = true;
                        sb.append("=== WINDOW ").append(pkg).append(" layer=-1 ===\n");
                        int[] count = new int[]{0};
                        StringBuilder seg = new StringBuilder();
                        Rect[] blankSubtree = new Rect[]{null};
                        dumpNode(r, 0, seg, count, pkg.equals(UBER), screenW, screenH, blankSubtree);
                        String segStr = seg.toString();
                        sb.append(segStr);
                        if (pkg.equals(UBER)) {
                            detectBlankUber(r, -1, count[0], segStr, blankSubtree[0], screenW, screenH);
                        }
                        if (count[0] > 3) wsig.append(pkg).append(":L-1 ");
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        if (noroot > 0) wsig.append("noroot=").append(noroot);
        curWinSig = wsig.toString();

        if (!any) sb.append("(no watched app window was visible)\n");
        if (others.length() > 0) sb.append("others=").append(others.toString().trim()).append('\n');
        return any;
    }

    private void detectBlankUber(AccessibilityNodeInfo root, int layer, int nodeCount,
                                 String segStr, Rect blankSubtree, int screenW, int screenH) {
        if (blankUber != null) return;
        boolean hasDollar = HAS_DOLLAR.matcher(segStr).find();

        if (layer >= 1 && nodeCount > 1 && !hasDollar
                && (segStr.contains("RecyclerView") || segStr.contains("ViewGroup") || segStr.contains("FrameLayout"))) {
            Rect rb = new Rect();
            root.getBoundsInScreen(rb);
            if (rb.height() > 320) {
                blankUber = rb;
                return;
            }
        }

        if (blankSubtree != null && !hasDollar) {
            blankUber = blankSubtree;
            return;
        }

        boolean hasOfferMarker = segStr.contains("text=\"Decline\"")
                || segStr.contains("request matching")
                || segStr.contains("xclusive")
                || segStr.contains("Trip Radar")
                || segStr.contains("text=\"Match\"");
        if (hasOfferMarker && !segStr.contains(" trip\"")) {
            blankUber = new Rect(0, Math.round(screenH * 0.22f), screenW, screenH);
        }
    }

    private int[] dumpNode(AccessibilityNodeInfo n, int depth, StringBuilder sb, int[] count,
                           boolean isUber, int screenW, int screenH, Rect[] blankSubtree) {
        if (n == null || count[0] >= MAX_NODES) return new int[]{0, 0};
        count[0]++;
        for (int i = 0; i < depth; i++) sb.append("  ");

        CharSequence cn = n.getClassName();
        String cls = cn == null ? "?" : cn.toString();
        int dot = cls.lastIndexOf('.');
        if (dot >= 0) cls = cls.substring(dot + 1);
        sb.append(cls);

        String id = n.getViewIdResourceName();
        if (id != null) sb.append(" id=").append(id);
        CharSequence d = n.getContentDescription();
        boolean hasTextOrDesc = false;
        if (d != null && d.length() > 0) {
            sb.append(" desc=\"").append(oneLine(d)).append('"');
            hasTextOrDesc = true;
        }
        CharSequence t = n.getText();
        if (t != null && t.length() > 0) {
            sb.append(" text=\"").append(oneLine(t)).append('"');
            hasTextOrDesc = true;
        }

        Rect r = new Rect();
        n.getBoundsInScreen(r);
        sb.append(" bounds=").append(r.left).append(',').append(r.top)
                .append(',').append(r.right).append(',').append(r.bottom);

        if (n.isClickable()) sb.append(" CLICKABLE");
        if (!n.isEnabled()) sb.append(" disabled");
        if (!n.isVisibleToUser()) sb.append(" hidden");
        if (!n.isImportantForAccessibility()) sb.append(" notimportant");
        sb.append('\n');

        int subNodes = 1;
        int subTextNodes = hasTextOrDesc ? 1 : 0;
        int c = n.getChildCount();
        for (int i = 0; i < c; i++) {
            AccessibilityNodeInfo ch = n.getChild(i);
            if (ch != null) {
                int[] childStats = dumpNode(ch, depth + 1, sb, count, isUber, screenW, screenH, blankSubtree);
                subNodes += childStats[0];
                subTextNodes += childStats[1];
            }
        }

        if (isUber && blankSubtree[0] == null && "RecyclerView".equals(cls)
                && n.isVisibleToUser() && subNodes >= 2 && subTextNodes == 0
                && r.height() > 320 && r.width() > screenW * 0.70 && r.bottom > screenH * 0.45) {
            blankSubtree[0] = new Rect(r);
        }
        return new int[]{subNodes, subTextNodes};
    }

    private static String oneLine(CharSequence s) {
        return s.toString().replace('\n', ' ').replace('\r', ' ');
    }

    private boolean scanOffers(String body) {
        if (!body.contains("est. rate for this ride") && !body.contains("desc=\"ACCEPT\"")
                && !body.contains("Accept •") && !body.contains("eservation request")) {
            return false;
        }
        boolean completeUberFound = false;
        List<Offers.Offer> list = Offers.fromTree(body, Store.getMinRate(this),
                Store.getHichMiles(this), Store.getHichScheduled(this));
        for (Offers.Offer o : list) {
            if ("uber".equals(o.app) && !"UNREAD".equals(o.verdict)) {
                completeUberFound = true;
            }
            handleOffer(o);
        }
        return completeUberFound;
    }

    private Bitmap cropBitmapForOcr(Bitmap sw, Rect bounds) {
        if (sw == null || bounds == null) return null;
        int top = Math.max(0, bounds.top - 40);
        int bottom = Math.min(sw.getHeight(), bounds.bottom + 40);
        if (bottom - top < 200) return null;
        return Bitmap.createBitmap(sw, 0, top, sw.getWidth(), bottom - top);
    }

    private void readCrop(final Bitmap crop) {
        Store.incDiag(this, Store.KEY_DIAG_OCR_RUNS);
        recognizer.process(InputImage.fromBitmap(crop, 0))
                .addOnSuccessListener(text -> {
                    crop.recycle();
                    ocrBusy = false;
                    Offers.Offer o = Offers.fromUberOcr(text.getText(), Store.getMinRate(LoggerService.this));
                    if ("UNREAD".equals(o.verdict) && unreadOcrRetries < 2 && pendingOcrBounds != null) {
                        unreadOcrRetries++;
                        Store.incDiag(LoggerService.this, Store.KEY_DIAG_OCR_RETRIES);
                        scheduleOcrRetry(500);
                        return;
                    }
                    if ("UNREAD".equals(o.verdict)) {
                        Store.incDiag(LoggerService.this, Store.KEY_DIAG_OCR_UNREAD);
                    }
                    unreadOcrRetries = 0;
                    pendingOcrBounds = null;
                    handleOffer(o);
                })
                .addOnFailureListener(e -> {
                    crop.recycle();
                    ocrBusy = false;
                    logLine("ocr failed: " + e);
                });
    }

    private void scheduleOcrRetry(long delayMs) {
        if (ocrRetryScheduled) return;
        ocrRetryScheduled = true;
        handler.postDelayed(ocrRetryRunnable, Math.max(150, delayMs));
    }

    private void handleOffer(Offers.Offer o) {
        long now = SystemClock.elapsedRealtime();
        String key = o.key();
        Long last = offerSeen.get(key);
        long repeatWindow = "UNREAD".equals(o.verdict) ? 4000 : OFFER_REPEAT_MS;
        if (last != null && now - last < repeatWindow) return;
        offerSeen.put(key, now);
        if (offerSeen.size() > 200) offerSeen.clear();

        if (!"scheduled".equals(o.kind) && Store.quietNow(this)) {
            o.verdict = "QUIET";
            o.detail = o.detail + " (quiet hours)";
        }
        final int mode = Store.getMode(this);
        String logLabel = labelFor(o);
        logLine("OFFER " + logLabel + " " + o.kind + " " + o.verdict + " (" + o.source + ") " + o.detail);

        try {
            final JSONObject j = new JSONObject();
            j.put("t", System.currentTimeMillis());
            j.put("app", o.app);
            j.put("kind", o.kind);
            j.put("price", o.price);
            j.put("rate", o.rate);
            j.put("verdict", o.verdict);
            j.put("detail", o.detail);
            j.put("source", o.source);
            j.put("raw", o.raw);
            j.put("mode", mode);
            io.execute(new Runnable() {
                @Override
                public void run() {
                    Store.addOffer(LoggerService.this, j);
                }
            });
        } catch (Throwable t) {
            logLine("offer save error: " + t);
        }

        if (mode == Store.MODE_ALERT && "GOOD".equals(o.verdict) && flasher != null) {
            String pillText = Flasher.formatPillText(o, Store.getPillFormat(this));
            long holdMs = Math.max(Flasher.TOTAL_MS, Store.getPillDuration(this) * 1000L);
            flashUntil = now + holdMs + 1200;
            flasher.flash(pillText, Color.rgb(0, 200, 83), Store.getOpacity(this),
                    Store.getVibrate(this), Store.getSound(this));
        }
    }

    static String labelFor(Offers.Offer o) {
        return Flasher.formatPillText(o, Store.FORMAT_STANDARD);
    }

    private void tryPriorityMode(String body) {
        if (!Store.getAutoPriority(this)) return;
        if (!body.contains("Turn on")) return;
        long now = SystemClock.elapsedRealtime();
        if (now - lastPriorityTap < PRIORITY_GAP_MS) return;
        if (ACCEPT.matcher(body).find() || body.contains("est. rate for this ride")
                || body.contains("Reject ride")) return;

        List<AccessibilityWindowInfo> wins;
        try {
            wins = getWindows();
        } catch (Throwable t) {
            return;
        }
        if (wins == null) return;

        for (AccessibilityWindowInfo w : wins) {
            AccessibilityNodeInfo root = null;
            try {
                root = w.getRoot();
            } catch (Throwable ignored) {
            }
            if (root == null) continue;
            CharSequence cs = root.getPackageName();
            if (cs == null || !LYFT.equals(cs.toString())) continue;

            List<AccessibilityNodeInfo> hits = root.findAccessibilityNodeInfosByText("Turn on");
            if (hits == null) continue;
            for (AccessibilityNodeInfo n : hits) {
                CharSequence t = n.getText();
                CharSequence d = n.getContentDescription();
                boolean exact = (t != null && "Turn on".equals(t.toString().trim()))
                        || (d != null && "Turn on".equals(d.toString().trim()));
                if (!exact) continue;
                if (!n.isEnabled() || !n.isVisibleToUser()) continue;
                if (!inPriorityRow(n)) {
                    priorityNote(now, "found \"Turn on\" but not next to Priority Mode, left alone");
                    continue;
                }
                AccessibilityNodeInfo target = clickableOf(n);
                if (target == null) {
                    priorityNote(now, "found the Priority Mode pill but nothing clickable around it");
                    continue;
                }
                lastPriorityTap = now;
                boolean ok = target.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                logLine("PRIORITY MODE: tapped \"Turn on\", click result=" + ok);
                trigger("priority mode tap");
                return;
            }
        }
    }

    private static boolean inPriorityRow(AccessibilityNodeInfo n) {
        AccessibilityNodeInfo p = n.getParent();
        for (int i = 0; i < 3 && p != null; i++) {
            List<AccessibilityNodeInfo> l = p.findAccessibilityNodeInfosByText("Priority Mode");
            if (l != null && !l.isEmpty()) return true;
            p = p.getParent();
        }
        return false;
    }

    private static AccessibilityNodeInfo clickableOf(AccessibilityNodeInfo n) {
        AccessibilityNodeInfo c = n;
        for (int i = 0; i < 5 && c != null; i++) {
            if (c.isClickable() && c.isEnabled()) return c;
            c = c.getParent();
        }
        return null;
    }

    private void priorityNote(long now, String msg) {
        if (now - lastPriorityNote < PRIORITY_NOTE_GAP_MS) return;
        lastPriorityNote = now;
        logLine("PRIORITY MODE: " + msg);
    }

    private void tryHichScheduled(String body) {
        if (!Store.getAutoSched(this)) return;
        if (!body.contains(HICH)) return;
        if (body.contains("Schedule Bookings")) return;
        long now = SystemClock.elapsedRealtime();
        if (now - lastHichTap < HICH_TAP_GAP_MS) return;
        if (ACCEPT.matcher(body).find() || body.contains("desc=\"ACCEPT\"")) return;

        List<AccessibilityWindowInfo> wins;
        try {
            wins = getWindows();
        } catch (Throwable t) {
            return;
        }
        if (wins == null) return;
        DisplayMetrics dm = getResources().getDisplayMetrics();

        for (AccessibilityWindowInfo w : wins) {
            AccessibilityNodeInfo root = null;
            try {
                root = w.getRoot();
            } catch (Throwable ignored) {
            }
            if (root == null) continue;
            CharSequence cs = root.getPackageName();
            if (cs == null || !HICH.equals(cs.toString())) continue;

            List<AccessibilityNodeInfo> badges = new ArrayList<>();
            collectBadges(root, dm.widthPixels, dm.heightPixels, badges, new int[]{0});
            for (AccessibilityNodeInfo n : badges) {
                int count;
                try {
                    count = Integer.parseInt(n.getText().toString().trim());
                } catch (Throwable t) {
                    continue;
                }
                if (count == lastHichCount && now - lastHichTap < HICH_REPEAT_MS) continue;
                AccessibilityNodeInfo target = clickableOf(n);
                if (target == null) {
                    priorityNote(now, "hich badge " + count + " found but nothing clickable around it");
                    continue;
                }
                lastHichTap = now;
                lastHichCount = count;
                boolean ok = target.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                logLine("HICH SCHEDULED: badge shows " + count + ", tapped the icon, click result=" + ok);
                trigger("hich scheduled badge tap");
                return;
            }
        }
    }

    private static void collectBadges(AccessibilityNodeInfo n, int w, int h,
                                      List<AccessibilityNodeInfo> out, int[] budget) {
        if (n == null || budget[0]++ > 800) return;
        CharSequence t = n.getText();
        if (t != null && n.isVisibleToUser()) {
            String s = t.toString().trim();
            if (s.matches("[1-9][0-9]?")) {
                Rect r = new Rect();
                n.getBoundsInScreen(r);
                if (r.centerY() < h * 0.12 && r.centerX() > w * 0.70) out.add(n);
            }
        }
        int c = n.getChildCount();
        for (int i = 0; i < c; i++) collectBadges(n.getChild(i), w, h, out, budget);
    }

    private void tryHichOnline(String body) {
        if (!Store.getAutoOnline(this) || !body.contains(HICH) || !OFFLINE.matcher(body).find()
                || ACCEPT.matcher(body).find() || body.contains("desc=\"ACCEPT\"")
                || Store.quietNow(this)) {
            hichOfflineSince = 0;
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (hichOfflineSince == 0) {
            hichOfflineSince = now;
            return;
        }
        if (now - hichOfflineSince < HICH_ONLINE_SETTLE_MS) return;
        if (lastHichOnlineTap != 0 && now - lastHichOnlineTap < HICH_ONLINE_REPEAT_MS) return;

        List<AccessibilityWindowInfo> wins;
        try {
            wins = getWindows();
        } catch (Throwable t) {
            return;
        }
        if (wins == null) return;

        for (AccessibilityWindowInfo w : wins) {
            AccessibilityNodeInfo root = null;
            try {
                root = w.getRoot();
            } catch (Throwable ignored) {
            }
            if (root == null) continue;
            CharSequence cs = root.getPackageName();
            if (cs == null || !HICH.equals(cs.toString())) continue;

            List<AccessibilityNodeInfo> hits = root.findAccessibilityNodeInfosByText("GO");
            if (hits == null) continue;
            for (AccessibilityNodeInfo n : hits) {
                CharSequence t = n.getText();
                CharSequence d = n.getContentDescription();
                boolean exact = (t != null && "GO".equals(t.toString().trim()))
                        || (d != null && "GO".equals(d.toString().trim()));
                if (!exact || !n.isEnabled() || !n.isVisibleToUser()) continue;
                AccessibilityNodeInfo target = clickableOf(n);
                if (target == null) {
                    priorityNote(now, "hich GO button found but nothing clickable around it");
                    continue;
                }
                lastHichOnlineTap = now;
                hichOfflineSince = 0;
                boolean ok = target.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                logLine("HICH ONLINE: tapped GO, click result=" + ok);
                trigger("hich go online tap");
                return;
            }
        }
    }

    private static final class HichRide {
        final long firstSeen;
        final Rules.Scheduled data = new Rules.Scheduled();
        boolean decided = false;
        boolean accept = false;
        boolean gaveUp = false;
        int taps = 0;
        long lastTap = 0;

        HichRide(long firstSeen) {
            this.firstSeen = firstSeen;
        }
    }

    private void openHichNotification(AccessibilityEvent e) {
        if (!Store.getAutoOpen(this)) return;
        long now = SystemClock.elapsedRealtime();
        if (now - lastHichOpen < HICH_OPEN_GAP_MS) return;
        Parcelable data = e.getParcelableData();
        if (!(data instanceof Notification)) {
            logLine("HICH OPEN: the notification event carried no notification, nothing to tap");
            return;
        }
        PendingIntent pi = ((Notification) data).contentIntent;
        if (pi == null) {
            logLine("HICH OPEN: the notification has no tap action");
            return;
        }
        lastHichOpen = now;
        try {
            Bundle opts = null;
            if (Build.VERSION.SDK_INT >= 34) {
                opts = ActivityOptions.makeBasic()
                        .setPendingIntentBackgroundActivityStartMode(
                                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                        .toBundle();
            }
            pi.send(this, 0, null, null, null, null, opts);
            logLine("HICH OPEN: fired the notification's tap action");
            trigger("hich notification opened");
        } catch (Throwable t) {
            logLine("HICH OPEN: could not fire the tap action: " + t);
        }
    }

    private void tryHichAccept(String body) {
        if (!Store.getAutoAccept(this) || !body.contains(HICH)) return;
        for (String seg : Rules.segmentsFor(body, HICH)) {
            Rules.Scheduled s = Rules.parseHichTripDetails(seg);
            if (s == null || s.rideId.isEmpty()) continue;
            long now = SystemClock.elapsedRealtime();
            if (hichRides.size() > 40) hichRides.clear();
            HichRide r = hichRides.get(s.rideId);
            if (r == null) {
                r = new HichRide(now);
                hichRides.put(s.rideId, r);
            }
            r.data.merge(s);
            if (!"Scheduled".equalsIgnoreCase(r.data.status)) continue;
            if (!s.hasAccept) continue;

            if (r.decided) {
                if (r.accept && r.taps < 2 && now - r.lastTap >= HICH_ACCEPT_RETRY_MS) tapHichAccept(r);
                continue;
            }
            Rules.Decision d = Rules.decide(r.data, Store.getHichScheduled(this), Store.getMinRate(this),
                    Store.getQuietOn(this), Store.getQuietStart(this), Store.getQuietEnd(this),
                    now - r.firstSeen);
            if ("WAIT".equals(d.verdict)) continue;
            if ("UNREAD".equals(d.verdict)) {
                if (!r.gaveUp && now - r.firstSeen > 4000) {
                    r.gaveUp = true;
                    logLine("HICH ACCEPT: ride #" + r.data.rideId + " left alone, " + d.reason);
                }
                continue;
            }
            r.decided = true;
            r.accept = d.accept;
            logLine("HICH ACCEPT: ride #" + r.data.rideId + " $" + String.format(Locale.US, "%.2f", r.data.price)
                    + ", pickup " + r.data.pickupClock + " -> " + (d.accept ? "ACCEPT" : "SKIP")
                    + " (" + d.reason + ")");
            recordScheduled(r.data, d);
            if (d.accept) tapHichAccept(r);
        }
    }

    private void recordScheduled(Rules.Scheduled s, Rules.Decision d) {
        Offers.Offer o = new Offers.Offer();
        o.app = "hich";
        o.kind = "scheduled";
        o.price = s.price;
        o.rate = d.rate;
        o.pickupMin = s.pickupMin;
        o.tripMin = s.tripMin;
        o.verdict = d.verdict;
        o.detail = "ride #" + s.rideId + ", pickup " + s.pickupClock
                + (s.dateText.isEmpty() ? "" : " " + s.dateText)
                + ", drive to pickup " + (s.pickupMin < 0 ? "?" : String.valueOf((long) s.pickupMin))
                + " min, trip " + (s.tripMin < 0 ? "?" : String.valueOf((long) s.tripMin))
                + " min: " + d.reason;
        handleOffer(o);
    }

    private void tapHichAccept(HichRide r) {
        List<AccessibilityWindowInfo> wins;
        try {
            wins = getWindows();
        } catch (Throwable t) {
            return;
        }
        if (wins == null) return;
        long now = SystemClock.elapsedRealtime();

        for (AccessibilityWindowInfo w : wins) {
            AccessibilityNodeInfo root = null;
            try {
                root = w.getRoot();
            } catch (Throwable ignored) {
            }
            if (root == null) continue;
            CharSequence cs = root.getPackageName();
            if (cs == null || !HICH.equals(cs.toString())) continue;

            List<AccessibilityNodeInfo> hits = root.findAccessibilityNodeInfosByText("ACCEPT");
            if (hits == null) continue;
            for (AccessibilityNodeInfo n : hits) {
                CharSequence t = n.getText();
                CharSequence d = n.getContentDescription();
                boolean exact = (t != null && "ACCEPT".equals(t.toString().trim()))
                        || (d != null && "ACCEPT".equals(d.toString().trim()));
                if (!exact || !n.isEnabled() || !n.isVisibleToUser()) continue;
                AccessibilityNodeInfo target = clickableOf(n);
                if (target == null) {
                    priorityNote(now, "hich ACCEPT button found but nothing clickable around it");
                    continue;
                }
                r.taps++;
                r.lastTap = now;
                boolean ok = target.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                logLine("HICH ACCEPT: tapped ACCEPT for ride #" + r.data.rideId
                        + ", click result=" + ok + ", tap " + r.taps);
                trigger("hich accept tap");
                afterTapShots();
                return;
            }
        }
        priorityNote(now, "hich ACCEPT: could not find the ACCEPT button to tap");
    }

    private void afterTapShots() {
        int[] delays = {500, 1800, 4000};
        for (int i = 0; i < delays.length; i++) {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!Store.isStandby(LoggerService.this)) {
                        capture("after hich accept tap", true, false);
                    }
                }
            }, delays[i]);
        }
    }

    private void trigger(final String why) {
        final long t = System.currentTimeMillis();
        io.execute(new Runnable() {
            @Override
            public void run() {
                promote(t, why);
            }
        });
    }

    private void promote(long t, String why) {
        keepUntil = Math.max(keepUntil, t + POST_ROLL_MS);
        int moved = 0;
        File[] files = Store.pendingDir(this).listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.lastModified() >= t - PRE_ROLL_MS) {
                    if (f.renameTo(new File(Store.dir(this), f.getName()))) moved++;
                }
            }
        }
        if (t - lastKeepLog > 2000) {
            lastKeepLog = t;
            logLine("KEEP (" + why + "): " + moved + " earlier shots kept");
        }
    }

    private void prunePending() {
        File[] files = Store.pendingDir(this).listFiles();
        if (files == null) return;
        long cutoff = System.currentTimeMillis() - PRE_ROLL_MS - 10000;
        for (File f : files) {
            if (f.lastModified() < cutoff) f.delete();
        }
    }

    private void shoot(final String base, final boolean force, final Rect ocrBounds) {
        if (Store.isStandby(this)) return;
        long nowMs = SystemClock.elapsedRealtime();
        long elapsed = nowMs - lastShot;
        if (elapsed < MIN_SHOT_GAP_MS) {
            if (ocrBounds != null) {
                Store.incDiag(this, Store.KEY_DIAG_OCR_RETRIES);
                scheduleOcrRetry(MIN_SHOT_GAP_MS - elapsed + 60);
            }
            return;
        }
        lastShot = nowMs;
        if (ocrBounds != null) {
            ocrBusy = true;
            lastOcr = nowMs;
        }
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, io,
                    new AccessibilityService.TakeScreenshotCallback() {
                        @Override
                        public void onSuccess(AccessibilityService.ScreenshotResult result) {
                            try {
                                saveShot(result, base, force, ocrBounds);
                            } catch (Throwable t) {
                                if (ocrBounds != null) ocrBusy = false;
                                logLine("screenshot save error: " + t);
                            }
                        }

                        @Override
                        public void onFailure(int errorCode) {
                            Store.incDiag(LoggerService.this, Store.KEY_DIAG_SHOT_FAILS);
                            if (ocrBounds != null) {
                                ocrBusy = false;
                                lastOcr = 0;
                                Store.incDiag(LoggerService.this, Store.KEY_DIAG_OCR_RETRIES);
                                logLine("ocr screenshot failed, code " + errorCode + " (scheduling retry)");
                                handler.post(() -> scheduleOcrRetry(400));
                            } else {
                                logLine("screenshot failed, code " + errorCode);
                            }
                        }
                    });
        } catch (Throwable t) {
            if (ocrBounds != null) {
                ocrBusy = false;
                lastOcr = 0;
            }
            logLine("screenshot error: " + t.getMessage());
        }
    }

    private void saveShot(AccessibilityService.ScreenshotResult result, String base,
                          boolean force, Rect ocrBounds) throws Exception {
        HardwareBuffer hb = result.getHardwareBuffer();
        Bitmap hw = Bitmap.wrapHardwareBuffer(hb, result.getColorSpace());
        if (hw == null) {
            hb.close();
            if (ocrBounds != null) ocrBusy = false;
            logLine("screenshot: could not read the screen buffer");
            return;
        }
        Bitmap sw = hw.copy(Bitmap.Config.ARGB_8888, false);
        hw.recycle();
        hb.close();
        if (sw == null) {
            if (ocrBounds != null) ocrBusy = false;
            return;
        }

        Rect activeOcrRect = ocrBounds != null ? ocrBounds : pendingOcrBounds;
        boolean runOcrNow = activeOcrRect != null && recognizer != null && (ocrBounds != null || !ocrBusy);
        Bitmap ocrCrop = null;
        if (runOcrNow) {
            ocrBusy = true;
            lastOcr = SystemClock.elapsedRealtime();
            ocrCrop = cropBitmapForOcr(sw, activeOcrRect);
            if (ocrCrop == null) {
                ocrBusy = false;
            }
        }

        int h = Math.max(1, Math.round(sw.getHeight() * (SHOT_WIDTH / (float) sw.getWidth())));
        Bitmap scaled = Bitmap.createScaledBitmap(sw, SHOT_WIDTH, h, true);
        if (scaled != sw) sw.recycle();

        int[] sig = signature(scaled);
        long sum = 0;
        for (int v : sig) sum += v;
        boolean isBlank = (sum / (double) sig.length < 3.0);
        if (isBlank) {
            logLine("screenshot looks BLANK (secure screen?) at " + base);
            if (ocrCrop != null) {
                ocrCrop.recycle();
                ocrCrop = null;
                ocrBusy = false;
            }
        }

        if (ocrCrop != null) {
            readCrop(ocrCrop);
        }

        long now = System.currentTimeMillis();

        boolean same = lastSig != null && meanDiff(sig, lastSig) < DUPLICATE_DIFF;
        if (same && !force) {
            prevSig = sig;
            scaled.recycle();
            return;
        }

        if (prevSig != null && watchedPresent
                && meanDiff(sig, prevSig) >= BIG_CHANGE_DIFF
                && now - lastVisualTrigger > VISUAL_COOLDOWN_MS) {
            lastVisualTrigger = now;
            promote(now, "big screen change");
        }
        prevSig = sig;
        lastSig = sig;

        boolean keepNow = force || now < keepUntil;
        File out = new File(keepNow ? Store.dir(this) : Store.pendingDir(this), base + ".jpg");
        FileOutputStream os = new FileOutputStream(out);
        try {
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, os);
        } finally {
            os.close();
        }
        scaled.recycle();

        if (!keepNow) prunePending();
        if (++writes % 100 == 0) Store.prune(this);
    }

    private static int[] signature(Bitmap b) {
        final int w = 24, h = 48;
        int[] s = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int px = b.getPixel(x * b.getWidth() / w, y * b.getHeight() / h);
                int r = (px >> 16) & 0xff, g = (px >> 8) & 0xff, bl = px & 0xff;
                s[y * w + x] = (r * 3 + g * 6 + bl) / 10;
            }
        }
        return s;
    }

    private static double meanDiff(int[] a, int[] b) {
        long d = 0;
        for (int i = 0; i < a.length; i++) d += Math.abs(a[i] - b[i]);
        return d / (double) a.length;
    }

    private void writeFile(String name, byte[] data) {
        try {
            FileOutputStream os = new FileOutputStream(new File(Store.dir(this), name));
            try {
                os.write(data);
            } finally {
                os.close();
            }
        } catch (Throwable t) {
            logLine("write error: " + t);
        }
        if (++writes % 100 == 0) Store.prune(this);
    }

    void logLine(String msg) {
        final String line = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date())
                + " " + msg + "\n";
        io.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    File f = new File(getFilesDir(), "events.log");
                    if (f.length() > 1000000) f.delete();
                    FileOutputStream os = new FileOutputStream(f, true);
                    try {
                        os.write(line.getBytes(StandardCharsets.UTF_8));
                    } finally {
                        os.close();
                    }
                } catch (Throwable ignored) {
                }
            }
        });
    }
}
