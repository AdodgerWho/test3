package com.example.driverlogger;

import android.accessibilityservice.AccessibilityService;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.Locale;

final class Flasher {
    static final int PULSES = 4;
    static final long PULSE_MS = 500;
    static final long TOTAL_MS = PULSES * PULSE_MS;

    private final AccessibilityService svc;
    private final WindowManager wm;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private FrameLayout root;
    private ObjectAnimator anim;
    private final Runnable removeRunnable = new Runnable() {
        @Override
        public void run() {
            remove();
        }
    };

    Flasher(AccessibilityService svc) {
        this.svc = svc;
        this.wm = (WindowManager) svc.getSystemService(AccessibilityService.WINDOW_SERVICE);
    }

    static String formatPillText(Offers.Offer o, int format) {
        String app = "uber".equals(o.app) ? "Uber" : "lyft".equals(o.app) ? "Lyft" : "hich";
        String priceStr = o.price > 0 ? String.format(Locale.US, "$%.2f", o.price) : "";
        String rateStr = o.rate >= 0 ? "$" + Math.round(o.rate) + "/hr" : "";

        switch (format) {
            case Store.FORMAT_RATE_FIRST:
                if (!rateStr.isEmpty() && !priceStr.isEmpty()) return rateStr + " · " + app + " " + priceStr;
                if (!rateStr.isEmpty()) return rateStr + " · " + app;
                return app + (priceStr.isEmpty() ? "" : " " + priceStr);

            case Store.FORMAT_RATE_ONLY:
                if (!rateStr.isEmpty()) return rateStr;
                return app + (priceStr.isEmpty() ? "" : " " + priceStr);

            case Store.FORMAT_DETAILED: {
                String line1 = !rateStr.isEmpty()
                        ? rateStr + " · " + app + (priceStr.isEmpty() ? "" : " " + priceStr)
                        : app + (priceStr.isEmpty() ? "" : " " + priceStr);
                double totalMin = (o.pickupMin >= 0 ? o.pickupMin : 0) + (o.tripMin >= 0 ? o.tripMin : 0);
                double totalMi = (o.pickupMi >= 0 ? o.pickupMi : 0) + (o.tripMi >= 0 ? o.tripMi : 0);
                if (totalMin > 0 || totalMi > 0) {
                    String line2 = (totalMin > 0 ? Math.round(totalMin) + " min" : "")
                            + (totalMin > 0 && totalMi > 0 ? " · " : "")
                            + (totalMi > 0 ? String.format(Locale.US, "%.1f mi", totalMi) : "");
                    return line1 + "\n" + line2;
                }
                return line1;
            }

            case Store.FORMAT_STANDARD:
            default: {
                StringBuilder sb = new StringBuilder(app);
                if (!priceStr.isEmpty()) sb.append(" ").append(priceStr);
                if (!rateStr.isEmpty()) sb.append(" · ").append(rateStr);
                return sb.toString();
            }
        }
    }

    static void stylePillView(Context ctx, TextView pill, String text, int style, int sizeSp) {
        pill.setText(text);
        pill.setGravity(Gravity.CENTER);
        pill.setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.max(14, Math.min(38, sizeSp)));
        pill.setTypeface(null, Typeface.BOLD);

        int bgColor, strokeColor, textColor;
        int strokeDp = 2;

        switch (style) {
            case Store.STYLE_NEON_GREEN:
                bgColor = 0xF22ECC71;
                strokeColor = 0xFFFFFFFF;
                textColor = 0xFF06210F;
                break;
            case Store.STYLE_MATRIX:
                bgColor = 0xF205140B;
                strokeColor = 0xFF00E676;
                textColor = 0xFF00E676;
                strokeDp = 3;
                break;
            case Store.STYLE_GOLD:
                bgColor = 0xF21F1706;
                strokeColor = 0xFFF5A524;
                textColor = 0xFFFFD166;
                break;
            case Store.STYLE_DARK_SLATE:
            default:
                bgColor = 0xE611161C;
                strokeColor = 0xFF2ECC71;
                textColor = 0xFFFFFFFF;
                break;
        }

        pill.setTextColor(textColor);
        float density = ctx.getResources().getDisplayMetrics().density;
        int px = Math.round((sizeSp * 0.85f) * density);
        int py = Math.round((sizeSp * 0.40f) * density);
        pill.setPadding(px, py, px, py);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(bgColor);
        bg.setCornerRadius(Math.round(28 * density));
        bg.setStroke(Math.round(strokeDp * density), strokeColor);
        pill.setBackground(bg);
    }

    void flash(String label, int color, int opacityPct, boolean vibrate, boolean sound) {
        remove();
        int holdSec = Store.getPillDuration(svc);
        long totalDurationMs = Math.max(TOTAL_MS, holdSec * 1000L);
        try {
            root = new FrameLayout(svc);

            View tint = new View(svc);
            tint.setBackgroundColor(color);
            tint.setAlpha(0f);
            root.addView(tint, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

            if (label != null && !label.isEmpty()) {
                TextView pill = new TextView(svc);
                stylePillView(svc, pill, label, Store.getPillStyle(svc), Store.getPillSize(svc));

                DisplayMetrics dm = svc.getResources().getDisplayMetrics();
                int posPct = Store.getPillPos(svc);
                int topMarginPx = Math.round(dm.heightPixels * (Math.max(3, Math.min(85, posPct)) / 100f));

                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
                lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
                lp.topMargin = topMarginPx;
                root.addView(pill, lp);
            }

            int overlayType;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                overlayType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            } else {
                overlayType = WindowManager.LayoutParams.TYPE_PHONE;
            }

            WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    overlayType,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);
            wm.addView(root, p);

            float peak = Math.max(5, Math.min(60, opacityPct)) / 100f;
            anim = ObjectAnimator.ofFloat(tint, View.ALPHA, 0f, peak, 0f);
            anim.setDuration(PULSE_MS);
            anim.setRepeatCount(PULSES - 1);
            final long extraHoldMs = Math.max(400L, totalDurationMs - TOTAL_MS);
            anim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator a) {
                    handler.postDelayed(removeRunnable, extraHoldMs);
                }
            });
            anim.start();
            handler.postDelayed(removeRunnable, totalDurationMs + 2500);
        } catch (Throwable t) {
            remove();
        }

        if (vibrate) buzz();
        if (sound) beep();
    }

    void remove() {
        handler.removeCallbacks(removeRunnable);
        if (anim != null) {
            anim.removeAllListeners();
            anim.cancel();
            anim = null;
        }
        if (root != null) {
            try {
                wm.removeViewImmediate(root);
            } catch (Throwable ignored) {
            }
            root = null;
        }
    }

    private void buzz() {
        try {
            Vibrator v = (Vibrator) svc.getSystemService(AccessibilityService.VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) {
                v.vibrate(VibrationEffect.createWaveform(new long[]{0, 140, 90, 140, 90, 140}, -1));
            }
        } catch (Throwable ignored) {
        }
    }

    private void beep() {
        try {
            final ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70);
            tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 350);
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    tg.release();
                }
            }, 700);
        } catch (Throwable ignored) {
        }
    }
}
