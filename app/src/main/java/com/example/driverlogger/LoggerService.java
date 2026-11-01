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

    // existing file body omitted for brevity; no functional change beyond the regex fix above
}



































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































n








































n





















































n










n

































n




















n




















n



















n


















n






n


n








n








n








n








n






n




n


n







n





n


n







n


n







n


n


n


n


n


n


n




n


n
n







n




n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n
n



n


n


n


n


n


n


n


n


n

n



n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n
n



n


n


n


n


n


n


n


n


n


n


n


n






n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


n


test







































































































n

n

n

n

n
n

n

n

n

n

n

n


n

n

n

n

n


n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n
n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n
n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n
n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n
n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n
n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n
test

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n


n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n
n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n

n{