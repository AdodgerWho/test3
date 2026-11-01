package com.example.driverlogger;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Dark, quiet main screen for Driverade v3.5 with Standby Mode and Info Pill customization. */
public class MainActivity extends Activity {
    private static final int BG = 0xFF0E1116;
    private static final int CARD = 0xFF181D25;
    private static final int TEXT = 0xFFE8ECF1;
    private static final int MUTED = 0xFF8A94A3;
    private static final int GREEN = 0xFF2ECC71;
    private static final int AMBER = 0xFFF5A524;
    private static final int RED = 0xFFE5484D;
    private static final int LINE = 0xFF2A313C;

    private TextView statusChip;
    private Button modeLog;
    private Button modeAlert;
    private CheckBox standbyBox;
    private TextView modeHelp;
    private TextView statSeen;
    private TextView statGood;
    private TextView statBest;
    private TextView diagSummary;
    private LinearLayout feed;
    private LinearLayout setupBody;
    private TextView stats;
    private EditText minRate;
    private EditText hichMiles;
    private EditText hichSched;
    private SeekBar opacity;
    private TextView opacityLabel;

    private TextView pillPreview;
    private Button[] fmtButtons = new Button[4];
    private Button[] styleButtons = new Button[4];
    private SeekBar pillSizeBar;
    private TextView pillSizeLabel;
    private SeekBar pillPosBar;
    private TextView pillPosLabel;
    private SeekBar pillDurBar;
    private TextView pillDurLabel;

    private CheckBox vibrate;
    private CheckBox sound;
    private CheckBox alwaysBox;
    private CheckBox autoPriority;
    private CheckBox autoSched;
    private CheckBox autoOnline;
    private CheckBox autoOpen;
    private CheckBox autoAccept;
    private CheckBox quietOn;
    private EditText quietStart;
    private EditText quietEnd;
    private EditText appsBox;
    private final ExecutorService bg = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(36), dp(16), dp(28));

        // ---- header
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("Driverade", 26, TEXT, true);
        head.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        statusChip = text("", 14, TEXT, true);
        statusChip.setPadding(dp(12), dp(6), dp(12), dp(6));
        head.addView(statusChip);
        col.addView(head);
        col.addView(text("v3.5", 12, MUTED, false));

        // ---- mode & standby
        LinearLayout modeCard = card();
        modeCard.addView(text("MODE", 12, MUTED, true));
        LinearLayout seg = new LinearLayout(this);
        seg.setOrientation(LinearLayout.HORIZONTAL);
        modeLog = segButton("Log only", Store.MODE_LOG);
        modeAlert = segButton("Alert only", Store.MODE_ALERT);
        seg.addView(modeLog, new LinearLayout.LayoutParams(0, dp(48), 1f));
        seg.addView(modeAlert, new LinearLayout.LayoutParams(0, dp(48), 1f));
        LinearLayout.LayoutParams segLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        segLp.topMargin = dp(8);
        modeCard.addView(seg, segLp);

        standbyBox = check("Standby Mode (pause background polling while app is open)");
        standbyBox.setTextColor(AMBER);
        standbyBox.setOnCheckedChangeListener((b, checked) -> {
            Store.setStandby(MainActivity.this, checked);
            refresh();
        });
        LinearLayout.LayoutParams sbLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sbLp.topMargin = dp(8);
        modeCard.addView(standbyBox, sbLp);

        modeHelp = text("", 13, MUTED, false);
        modeHelp.setPadding(0, dp(10), 0, 0);
        modeCard.addView(modeHelp);
        col.addView(modeCard);

        // ---- today + diagnostics
        LinearLayout todayCard = card();
        todayCard.addView(text("TODAY", 12, MUTED, true));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        statSeen = bigStat(row, "Offers seen");
        statGood = bigStat(row, "Good");
        statBest = bigStat(row, "Best $/hr");
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = dp(8);
        todayCard.addView(row, rowLp);

        View diagDivider = new View(this);
        diagDivider.setBackgroundColor(LINE);
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1);
        divLp.topMargin = dp(12);
        divLp.bottomMargin = dp(8);
        todayCard.addView(diagDivider, divLp);

        diagSummary = text("", 12, MUTED, false);
        todayCard.addView(diagSummary);
        col.addView(todayCard);

        // ---- recent offers
        LinearLayout feedCard = card();
        feedCard.addView(text("RECENT OFFERS", 12, MUTED, true));
        feed = new LinearLayout(this);
        feed.setOrientation(LinearLayout.VERTICAL);
        feedCard.addView(feed);
        col.addView(feedCard);

        // ---- info pill appearance card
        LinearLayout pillCard = card();
        pillCard.addView(text("INFO PILL APPEARANCE", 12, MUTED, true));

        FrameLayout previewBox = new FrameLayout(this);
        previewBox.setBackground(round(0xFF0A0D12, 14));
        previewBox.setPadding(dp(12), dp(16), dp(12), dp(16));
        LinearLayout.LayoutParams prevBoxLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        prevBoxLp.topMargin = dp(10);
        pillPreview = new TextView(this);
        FrameLayout.LayoutParams pillPrevLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        pillPrevLp.gravity = Gravity.CENTER;
        previewBox.addView(pillPreview, pillPrevLp);
        pillCard.addView(previewBox, prevBoxLp);

        TextView fmtTitle = text("Hourly display format", 13, MUTED, false);
        fmtTitle.setPadding(0, dp(12), 0, dp(6));
        pillCard.addView(fmtTitle);
        String[] fmtLabels = {"Standard", "$/hr First", "$/hr Only", "2-Line Detail"};
        LinearLayout fmtRow1 = new LinearLayout(this);
        fmtRow1.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout fmtRow2 = new LinearLayout(this);
        fmtRow2.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            Button b = choiceButton(fmtLabels[i], new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Store.setPillFormat(MainActivity.this, idx);
                    updatePillPreview();
                }
            });
            fmtButtons[i] = b;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(42), 1f);
            if (i % 2 == 1) lp.leftMargin = dp(8);
            if (i < 2) fmtRow1.addView(b, lp);
            else fmtRow2.addView(b, lp);
        }
        pillCard.addView(fmtRow1);
        LinearLayout.LayoutParams r2Lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        r2Lp.topMargin = dp(8);
        pillCard.addView(fmtRow2, r2Lp);

        TextView styleTitle = text("Pill color theme", 13, MUTED, false);
        styleTitle.setPadding(0, dp(12), 0, dp(6));
        pillCard.addView(styleTitle);
        String[] styleLabels = {"Dark Slate", "Neon Green", "Matrix", "Gold"};
        LinearLayout styleRow1 = new LinearLayout(this);
        styleRow1.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout styleRow2 = new LinearLayout(this);
        styleRow2.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            Button b = choiceButton(styleLabels[i], new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Store.setPillStyle(MainActivity.this, idx);
                    updatePillPreview();
                }
            });
            styleButtons[i] = b;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(42), 1f);
            if (i % 2 == 1) lp.leftMargin = dp(8);
            if (i < 2) styleRow1.addView(b, lp);
            else styleRow2.addView(b, lp);
        }
        pillCard.addView(styleRow1);
        LinearLayout.LayoutParams sr2Lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sr2Lp.topMargin = dp(8);
        pillCard.addView(styleRow2, sr2Lp);

        pillSizeLabel = text("", 14, TEXT, false);
        pillSizeLabel.setPadding(0, dp(12), 0, 0);
        pillCard.addView(pillSizeLabel);
        pillSizeBar = new SeekBar(this);
        pillSizeBar.setMax(24);
        pillSizeBar.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                int sp = p + 14;
                pillSizeLabel.setText("Pill text size: " + sp + " sp");
                if (fromUser) {
                    Store.savePillSettings(MainActivity.this, sp,
                            pillPosBar.getProgress() + 3, pillDurBar.getProgress() + 2);
                    updatePillPreview();
                }
            }
        });
        pillCard.addView(pillSizeBar);

        pillPosLabel = text("", 14, TEXT, false);
        pillPosLabel.setPadding(0, dp(10), 0, 0);
        pillCard.addView(pillPosLabel);
        pillPosBar = new SeekBar(this);
        pillPosBar.setMax(82);
        pillPosBar.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                int pct = p + 3;
                String zone = pct < 20 ? " (Top)" : pct < 55 ? " (Middle)" : " (Bottom)";
                pillPosLabel.setText("Vertical screen position: " + pct + "%" + zone);
                if (fromUser) {
                    Store.savePillSettings(MainActivity.this, pillSizeBar.getProgress() + 14,
                            pct, pillDurBar.getProgress() + 2);
                }
            }
        });
        pillCard.addView(pillPosBar);

        pillDurLabel = text("", 14, TEXT, false);
        pillDurLabel.setPadding(0, dp(10), 0, 0);
        pillCard.addView(pillDurLabel);
        pillDurBar = new SeekBar(this);
        pillDurBar.setMax(6);
        pillDurBar.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                int sec = p + 2;
                pillDurLabel.setText("Pill display duration: " + sec + " sec");
                if (fromUser) {
                    Store.savePillSettings(MainActivity.this, pillSizeBar.getProgress() + 14,
                            pillPosBar.getProgress() + 3, sec);
                }
            }
        });
        pillCard.addView(pillDurBar);

        pillCard.addView(flatButton("Test Pulse & Pill on Screen", LINE, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Offers.Offer sample = sampleOffer();
                if (!LoggerService.triggerTestAlert(sample)) {
                    toast("Turn on Driverade in Accessibility settings first to test the screen overlay");
                }
            }
        }));
        col.addView(pillCard);

        // ---- thresholds & automation settings
        LinearLayout setCard = card();
        setCard.addView(text("THRESHOLDS & AUTOMATION", 12, MUTED, true));
        minRate = field(setCard, "Good offer / accept: at least $/hr");
        hichMiles = field(setCard, "hich live rides: pickup within (miles)");
        hichSched = field(setCard, "hich scheduled rides: accept if the trip amount is at least $ (or $/hr above is met)");
        opacityLabel = text("", 14, TEXT, false);
        opacityLabel.setPadding(0, dp(12), 0, 0);
        setCard.addView(opacityLabel);
        opacity = new SeekBar(this);
        opacity.setMax(55);
        opacity.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                opacityLabel.setText("Screen flash tint strength: " + (p + 5) + "%");
            }
        });
        setCard.addView(opacity);
        vibrate = check("Vibrate on good offers");
        sound = check("Short beep on good offers");
        setCard.addView(vibrate);
        setCard.addView(sound);
        autoPriority = check("Turn on Lyft Priority Mode automatically when available");
        autoPriority.setOnCheckedChangeListener((b, checked) -> Store.setAutoPriority(MainActivity.this, checked));
        setCard.addView(autoPriority);
        autoSched = check("Open hich scheduled rides automatically when waiting");
        autoSched.setOnCheckedChangeListener((b, checked) -> Store.setAutoSched(MainActivity.this, checked));
        setCard.addView(autoSched);
        autoOnline = check("Go online in hich automatically when open and offline");
        autoOnline.setOnCheckedChangeListener((b, checked) -> Store.setAutoOnline(MainActivity.this, checked));
        setCard.addView(autoOnline);
        autoOpen = check("Open hich ride notifications automatically");
        autoOpen.setOnCheckedChangeListener((b, checked) -> Store.setAutoOpen(MainActivity.this, checked));
        setCard.addView(autoOpen);
        autoAccept = check("Accept hich scheduled rides automatically when they qualify");
        autoAccept.setOnCheckedChangeListener((b, checked) -> Store.setAutoAccept(MainActivity.this, checked));
        setCard.addView(autoAccept);
        quietOn = check("Quiet hours: no alerts, no hich accepting, no going online");
        quietOn.setOnCheckedChangeListener((b, checked) -> Store.setQuietOn(MainActivity.this, checked));
        setCard.addView(quietOn);
        quietStart = clockField(setCard, "Quiet hours start (for example 11:00 PM)");
        quietEnd = clockField(setCard, "Quiet hours end (for example 7:30 AM)");
        setCard.addView(flatButton("Save settings", GREEN, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveSettings();
            }
        }));
        col.addView(setCard);

        // ---- setup & data
        LinearLayout setupCard = card();
        final Button toggle = flatButton("Setup & data  \u25be", LINE, null);
        toggle.setTextColor(TEXT);
        setupCard.addView(toggle);
        setupBody = new LinearLayout(this);
        setupBody.setOrientation(LinearLayout.VERTICAL);
        setupBody.setVisibility(View.GONE);
        toggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean open = setupBody.getVisibility() == View.VISIBLE;
                setupBody.setVisibility(open ? View.GONE : View.VISIBLE);
                toggle.setText(open ? "Setup & data  \u25be" : "Setup & data  \u25b4");
            }
        });
        setupBody.addView(text("Turn the service on under Accessibility > Installed apps > Driverade.", 13, MUTED, false));
        setupBody.addView(flatButton("Open Accessibility settings", LINE, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            }
        }));
        setupBody.addView(flatButton("Open App info", LINE, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                i.setData(Uri.parse("package:" + getPackageName()));
                startActivity(i);
            }
        }));
        alwaysBox = check("Keep short screenshot buffer even when no watched app is visible");
        alwaysBox.setOnCheckedChangeListener((b, checked) -> Store.setAlways(MainActivity.this, checked));
        setupBody.addView(alwaysBox);
        setupBody.addView(text("Apps being watched (one package per line):", 13, MUTED, false));
        appsBox = new EditText(this);
        appsBox.setMinLines(3);
        appsBox.setTextColor(TEXT);
        appsBox.setTextSize(13);
        setupBody.addView(appsBox);
        setupBody.addView(flatButton("Save app list", LINE, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Store.setAppsText(MainActivity.this, appsBox.getText().toString());
                toast("Saved");
            }
        }));
        stats = text("", 13, MUTED, false);
        stats.setPadding(0, dp(10), 0, 0);
        setupBody.addView(stats);
        setupBody.addView(flatButton("Export to Downloads (with screenshots)", LINE, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                export(true);
            }
        }));
        setupBody.addView(flatButton("Export to Downloads (text only)", LINE, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                export(false);
            }
        }));
        setupBody.addView(flatButton("Delete all captures", RED, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage("Delete all saved captures and offer history?")
                        .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which) {
                                Store.deleteAll(MainActivity.this);
                                refresh();
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        }));
        setupCard.addView(setupBody);
        col.addView(setupCard);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        sv.addView(col);
        setContentView(sv);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadSettings();
        refresh();
    }

    private Offers.Offer sampleOffer() {
        Offers.Offer o = new Offers.Offer();
        o.app = "uber";
        o.kind = "live";
        o.price = 18.68;
        o.rate = 45.0;
        o.pickupMin = 4;
        o.pickupMi = 1.2;
        o.tripMin = 16;
        o.tripMi = 8.3;
        o.verdict = "GOOD";
        return o;
    }

    private void updatePillPreview() {
        int fmt = Store.getPillFormat(this);
        int style = Store.getPillStyle(this);
        int sizeSp = Store.getPillSize(this);
        for (int i = 0; i < fmtButtons.length; i++) {
            styleSeg(fmtButtons[i], i == fmt);
        }
        for (int i = 0; i < styleButtons.length; i++) {
            styleSeg(styleButtons[i], i == style);
        }
        String sampleText = Flasher.formatPillText(sampleOffer(), fmt);
        Flasher.stylePillView(this, pillPreview, sampleText, style, sizeSp);
    }

    private void loadSettings() {
        minRate.setText(trim(Store.getMinRate(this)));
        hichMiles.setText(trim(Store.getHichMiles(this)));
        hichSched.setText(trim(Store.getHichScheduled(this)));
        opacity.setProgress(Store.getOpacity(this) - 5);
        opacityLabel.setText("Screen flash tint strength: " + Store.getOpacity(this) + "%");

        int pSize = Store.getPillSize(this);
        int pPos = Store.getPillPos(this);
        int pDur = Store.getPillDuration(this);
        pillSizeBar.setProgress(pSize - 14);
        pillSizeLabel.setText("Pill text size: " + pSize + " sp");
        pillPosBar.setProgress(pPos - 3);
        String zone = pPos < 20 ? " (Top)" : pPos < 55 ? " (Middle)" : " (Bottom)";
        pillPosLabel.setText("Vertical screen position: " + pPos + "%" + zone);
        pillDurBar.setProgress(pDur - 2);
        pillDurLabel.setText("Pill display duration: " + pDur + " sec");
        updatePillPreview();

        vibrate.setChecked(Store.getVibrate(this));
        sound.setChecked(Store.getSound(this));
        alwaysBox.setChecked(Store.isAlways(this));
        autoPriority.setChecked(Store.getAutoPriority(this));
        autoSched.setChecked(Store.getAutoSched(this));
        autoOnline.setChecked(Store.getAutoOnline(this));
        autoOpen.setChecked(Store.getAutoOpen(this));
        autoAccept.setChecked(Store.getAutoAccept(this));
        quietOn.setChecked(Store.getQuietOn(this));
        quietStart.setText(Rules.clockText(Store.getQuietStart(this)));
        quietEnd.setText(Rules.clockText(Store.getQuietEnd(this)));
        appsBox.setText(Store.getAppsText(this));
        standbyBox.setChecked(Store.isStandby(this));
    }

    private void saveSettings() {
        Store.saveSettings(this,
                num(minRate, 32f), num(hichMiles, 5f), num(hichSched, 20f),
                opacity.getProgress() + 5, vibrate.isChecked(), sound.isChecked());
        Store.savePillSettings(this,
                pillSizeBar.getProgress() + 14,
                pillPosBar.getProgress() + 3,
                pillDurBar.getProgress() + 2);
        int qs = Rules.parseClock(quietStart.getText().toString());
        int qe = Rules.parseClock(quietEnd.getText().toString());
        if (qs < 0 || qe < 0) {
            toast("Saved, but quiet hours need times like 11:00 PM or 23:00");
            return;
        }
        Store.setQuietHours(this, qs, qe);
        updatePillPreview();
        toast("Saved");
    }

    private void refresh() {
        boolean on = isServiceOn();
        boolean standby = Store.isStandby(this);

        if (standby) {
            statusChip.setText("\u25cf STANDBY");
            statusChip.setBackground(round(0x33F5A524, 20));
            statusChip.setTextColor(AMBER);
        } else {
            statusChip.setText(on ? "\u25cf ON" : "\u25cf OFF");
            statusChip.setBackground(round(on ? 0x332ECC71 : 0x33E5484D, 20));
            statusChip.setTextColor(on ? GREEN : RED);
        }

        int mode = Store.getMode(this);
        styleSeg(modeLog, mode == Store.MODE_LOG);
        styleSeg(modeAlert, mode == Store.MODE_ALERT);
        modeHelp.setText(standby
                ? "Standby Mode is active: background polling and accessibility processing are paused while you have the app open."
                : (mode == Store.MODE_ALERT
                ? "Good offers pulse the screen and show your customized hourly pill. On Uber and Lyft you tap Accept / Match yourself."
                : "Offers are only recorded. Nothing appears on screen while you drive."));

        double mb = Store.bytes(this) / (1024.0 * 1024.0);
        stats.setText(String.format(Locale.US, "%d text captures, %d screenshots, %.1f MB",
                Store.count(this, ".txt"), Store.count(this, ".jpg"), mb));

        int ocrRuns = Store.getDiag(this, Store.KEY_DIAG_OCR_RUNS);
        int ocrRetries = Store.getDiag(this, Store.KEY_DIAG_OCR_RETRIES);
        int ocrUnread = Store.getDiag(this, Store.KEY_DIAG_OCR_UNREAD);
        int shotFails = Store.getDiag(this, Store.KEY_DIAG_SHOT_FAILS);
        diagSummary.setText(String.format(Locale.US,
                "OCR reads: %d  \u00b7  Auto-retries: %d  \u00b7  Unread: %d  \u00b7  Shot blocks: %d",
                ocrRuns, ocrRetries, ocrUnread, shotFails));

        List<JSONObject> offers = Store.recentOffers(this, 200);
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long dayStart = cal.getTimeInMillis();
        int seen = 0, good = 0;
        double best = -1;
        for (JSONObject o : offers) {
            if (o.optLong("t") < dayStart) continue;
            seen++;
            if ("GOOD".equals(o.optString("verdict"))) good++;
            best = Math.max(best, o.optDouble("rate", -1));
        }
        statSeen.setText(String.valueOf(seen));
        statGood.setText(String.valueOf(good));
        statBest.setText(best >= 0 ? "$" + Math.round(best) : "-");

        feed.removeAllViews();
        if (offers.isEmpty()) {
            TextView none = text("No offers yet. They appear here as they are seen.", 14, MUTED, false);
            none.setPadding(0, dp(10), 0, 0);
            feed.addView(none);
        }
        SimpleDateFormat tf = new SimpleDateFormat("h:mm a", Locale.US);
        for (int i = 0; i < Math.min(12, offers.size()); i++) feed.addView(offerRow(offers.get(i), tf));
    }

    private View offerRow(final JSONObject o, SimpleDateFormat tf) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(10), 0, dp(10));

        String app = o.optString("app");
        int appColor = app.equals("uber") ? 0xFFB8C0CC : app.equals("lyft") ? 0xFFFF4FA3 : 0xFF4FC3F7;
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.addView(text(app.toUpperCase(Locale.US) + (o.optString("kind").equals("live") ? "" : "  " + o.optString("kind")),
                12, appColor, true));
        left.addView(text(tf.format(new Date(o.optLong("t"))), 12, MUTED, false));
        r.addView(left, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        double price = o.optDouble("price", -1);
        TextView p = text(price > 0 ? String.format(Locale.US, "$%.2f", price) : "?", 20, TEXT, true);
        p.setPadding(0, 0, dp(14), 0);
        r.addView(p);

        double rate = o.optDouble("rate", -1);
        String v = o.optString("verdict");
        int vc = v.equals("GOOD") ? GREEN : (v.equals("SKIP") || v.equals("QUIET")) ? MUTED : AMBER;
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.END);
        right.addView(text(rate >= 0 ? "$" + Math.round(rate) + "/hr" : "-", 16, vc, true));
        right.addView(text(v, 11, vc, true));
        r.addView(right);

        r.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String msg = o.optString("detail") + "\nread by: " + o.optString("source")
                        + "\nmode: " + (o.optInt("mode") == Store.MODE_ALERT ? "alert" : "log");
                String raw = o.optString("raw");
                if (!raw.isEmpty()) msg += "\n\nOCR text:\n" + raw;
                new AlertDialog.Builder(MainActivity.this).setMessage(msg).setPositiveButton("Close", null).show();
            }
        });
        View line = new View(this);
        line.setBackgroundColor(LINE);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.addView(r);
        wrap.addView(line, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
        return wrap;
    }

    private boolean isServiceOn() {
        String s = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return s != null && s.contains(getPackageName() + "/");
    }

    private void export(final boolean withShots) {
        toast("Exporting...");
        bg.execute(new Runnable() {
            @Override
            public void run() {
                String msg;
                try {
                    msg = "Saved to Downloads: " + Store.exportZip(MainActivity.this, withShots);
                } catch (Exception ex) {
                    msg = "Export failed: " + ex.getMessage();
                }
                final String m = msg;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, m, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private Button segButton(String label, final int mode) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Store.setMode(MainActivity.this, mode);
                refresh();
            }
        });
        return b;
    }

    private Button choiceButton(String label, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setOnClickListener(l);
        return b;
    }

    private void styleSeg(Button b, boolean selected) {
        if (b == null) return;
        b.setBackground(round(selected ? GREEN : LINE, 12));
        b.setTextColor(selected ? 0xFF06210F : TEXT);
        b.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
    }

    private TextView bigStat(LinearLayout row, String label) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        TextView n = text("-", 26, TEXT, true);
        box.addView(n);
        box.addView(text(label, 12, MUTED, false));
        row.addView(box, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return n;
    }

    private EditText field(LinearLayout parent, String label) {
        TextView l = text(label, 13, MUTED, false);
        l.setPadding(0, dp(12), 0, 0);
        parent.addView(l);
        EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        e.setTextColor(TEXT);
        e.setTextSize(18);
        e.setSingleLine(true);
        parent.addView(e);
        return e;
    }

    private EditText clockField(LinearLayout parent, String label) {
        TextView l = text(label, 13, MUTED, false);
        l.setPadding(0, dp(12), 0, 0);
        parent.addView(l);
        EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_TEXT);
        e.setTextColor(TEXT);
        e.setTextSize(18);
        e.setSingleLine(true);
        parent.addView(e);
        return e;
    }

    private CheckBox check(String label) {
        CheckBox c = new CheckBox(this);
        c.setText(label);
        c.setTextColor(TEXT);
        c.setTextSize(14);
        return c;
    }

    private Button flatButton(String label, int color, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(color == GREEN ? 0xFF06210F : TEXT);
        b.setBackground(round(color, 12));
        if (l != null) b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        lp.topMargin = dp(10);
        b.setLayoutParams(lp);
        return b;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(14), dp(16), dp(16));
        c.setBackground(round(CARD, 18));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(14);
        c.setLayoutParams(lp);
        return c;
    }

    private TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(null, Typeface.BOLD);
        return t;
    }

    private GradientDrawable round(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private static String trim(float f) {
        return f == Math.rint(f) ? String.valueOf((long) f) : String.valueOf(f);
    }

    private static float num(EditText e, float fallback) {
        try {
            return Float.parseFloat(e.getText().toString().trim());
        } catch (Throwable t) {
            return fallback;
        }
    }

    private void toast(String m) {
        Toast.makeText(this, m, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private abstract static class SimpleSeekListener implements SeekBar.OnSeekBarChangeListener {
        @Override
        public void onStartTrackingTouch(SeekBar s) {}

        @Override
        public void onStopTrackingTouch(SeekBar s) {}
    }
}
