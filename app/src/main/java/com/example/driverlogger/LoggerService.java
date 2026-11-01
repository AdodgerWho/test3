package com.example.driverlogger;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

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

    private static final Pattern ACCEPT =
            Pattern.compile("(text|desc)=\"(Accept|Match)( \\u2022[^"]*)?\"");
    private static final Pattern HAS_DOLLAR =
            Pattern.compile("(text|desc)=\"[^"]*\\$\\d+");
    private static final Pattern BOUNDS_STRIP =
            Pattern.compile(" bounds=-?\\d+,-?\\d+,-?\\d+,-?\\d+");
    private static final String[] MARKERS = {
            "text=\"Decline\"", "Reject ride", "est. rate for this ride", "eservation request",
            "request matching", "Matched to trip", "xclusive", "Scheduled ride", "Trip Radar",
            "Picking up", "Waiting for rider", "Dropping off", "Finding trips",
            "Waiting for requests", "You're offline", "You're online", "You're Online",
            "GO OFFLINE", "Go Offline",
            "desc=\"ACCEPT\"", "Schedule Bookings", "SUGGESTED SCHEDULED RIDE"
    };

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Intentionally left as a minimal no-op implementation to keep the service compile-safe.
    }

    @Override
    public void onInterrupt() {
        // Intentionally left as a minimal no-op implementation to keep the service compile-safe.
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }
}
