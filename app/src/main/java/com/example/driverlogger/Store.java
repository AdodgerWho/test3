package com.example.driverlogger;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Settings, capture folder, pruning, export, and Standby Mode. */
final class Store {
    static final String PREFS = "logger";
    static final String KEY_APPS = "apps";
    static final String KEY_ALWAYS = "always";
    static final String KEY_STANDBY = "standby";

    static final int MAX_TXT = 4000;
    static final int MAX_JPG = 1500;

    static final String KEY_MODE = "mode";
    static final String KEY_MIN_RATE = "minRate";
    static final String KEY_HICH_MILES = "hichMiles";
    static final String KEY_HICH_SCHED = "hichSched";
    static final String KEY_OPACITY = "opacity";
    static final String KEY_VIBRATE = "vibrate";
    static final String KEY_SOUND = "sound";
    static final String KEY_AUTO_PRIORITY = "autoPriority";
    static final String KEY_AUTO_SCHED = "autoSched";
    static final String KEY_AUTO_ONLINE = "autoOnline";
    static final String KEY_AUTO_OPEN = "autoOpen";
    static final String KEY_AUTO_ACCEPT = "autoAccept";
    static final String KEY_QUIET_ON = "quietOn";
    static final String KEY_QUIET_START = "quietStart";
    static final String KEY_QUIET_END = "quietEnd";

    static final String KEY_PILL_FORMAT = "pillFormat";
    static final String KEY_PILL_STYLE = "pillStyle";
    static final String KEY_PILL_SIZE = "pillSize";
    static final String KEY_PILL_POS = "pillPos";
    static final String KEY_PILL_DURATION = "pillDuration";

    static final String KEY_DIAG_OCR_RUNS = "diagOcrRuns";
    static final String KEY_DIAG_OCR_RETRIES = "diagOcrRetries";
    static final String KEY_DIAG_OCR_UNREAD = "diagOcrUnread";
    static final String KEY_DIAG_SHOT_FAILS = "diagShotFails";

    static final int MODE_LOG = 0;
    static final int MODE_ALERT = 1;

    static final int FORMAT_STANDARD = 0;
    static final int FORMAT_RATE_FIRST = 1;
    static final int FORMAT_RATE_ONLY = 2;
    static final int FORMAT_DETAILED = 3;

    static final int STYLE_DARK_SLATE = 0;
    static final int STYLE_NEON_GREEN = 1;
    static final int STYLE_MATRIX = 2;
    static final int STYLE_GOLD = 3;

    static final String DEFAULT_APPS =
            "com.ubercab.driver\ncom.lyft.android.driver\nus.mi.hich.driver";

    private Store() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static File dir(Context c) {
        File d = new File(c.getFilesDir(), "captures");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    static File pendingDir(Context c) {
        File d = new File(c.getFilesDir(), "pending");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    static void clearPending(Context c) {
        File[] files = pendingDir(c).listFiles();
        if (files == null) return;
        for (File f : files) f.delete();
    }

    static boolean isStandby(Context c) {
        return prefs(c).getBoolean(KEY_STANDBY, false);
    }

    static void setStandby(Context c, boolean v) {
        prefs(c).edit().putBoolean(KEY_STANDBY, v).apply();
    }

    static String getAppsText(Context c) {
        return prefs(c).getString(KEY_APPS, DEFAULT_APPS);
    }

    static List<String> getApps(Context c) {
        List<String> out = new ArrayList<>();
        for (String s : getAppsText(c).split("[\\s,]+")) {
            if (!s.trim().isEmpty()) out.add(s.trim());
        }
        return out;
    }

    static void setAppsText(Context c, String text) {
        prefs(c).edit().putString(KEY_APPS, text).apply();
    }

    static boolean isAlways(Context c) {
        return prefs(c).getBoolean(KEY_ALWAYS, true);
    }

    static void setAlways(Context c, boolean v) {
        prefs(c).edit().putBoolean(KEY_ALWAYS, v).apply();
    }

    static int getMode(Context c) {
        return prefs(c).getInt(KEY_MODE, MODE_LOG);
    }

    static void setMode(Context c, int m) {
        prefs(c).edit().putInt(KEY_MODE, m).apply();
    }

    static float getMinRate(Context c) {
        return prefs(c).getFloat(KEY_MIN_RATE, 32f);
    }

    static float getHichMiles(Context c) {
        return prefs(c).getFloat(KEY_HICH_MILES, 5f);
    }

    static float getHichScheduled(Context c) {
        return prefs(c).getFloat(KEY_HICH_SCHED, 20f);
    }

    static int getOpacity(Context c) {
        return prefs(c).getInt(KEY_OPACITY, 28);
    }

    static boolean getVibrate(Context c) {
        return prefs(c).getBoolean(KEY_VIBRATE, true);
    }

    static boolean getSound(Context c) {
        return prefs(c).getBoolean(KEY_SOUND, false);
    }

    static int getPillFormat(Context c) {
        return prefs(c).getInt(KEY_PILL_FORMAT, FORMAT_STANDARD);
    }

    static void setPillFormat(Context c, int fmt) {
        prefs(c).edit().putInt(KEY_PILL_FORMAT, fmt).apply();
    }

    static int getPillStyle(Context c) {
        return prefs(c).getInt(KEY_PILL_STYLE, STYLE_DARK_SLATE);
    }

    static void setPillStyle(Context c, int style) {
        prefs(c).edit().putInt(KEY_PILL_STYLE, style).apply();
    }

    static int getPillSize(Context c) {
        return prefs(c).getInt(KEY_PILL_SIZE, 22);
    }

    static int getPillPos(Context c) {
        return prefs(c).getInt(KEY_PILL_POS, 8);
    }

    static int getPillDuration(Context c) {
        return prefs(c).getInt(KEY_PILL_DURATION, 3);
    }

    static void savePillSettings(Context c, int sizeSp, int posPct, int durationSec) {
        prefs(c).edit()
                .putInt(KEY_PILL_SIZE, Math.max(14, Math.min(38, sizeSp)))
                .putInt(KEY_PILL_POS, Math.max(3, Math.min(85, posPct)))
                .putInt(KEY_PILL_DURATION, Math.max(2, Math.min(8, durationSec)))
                .apply();
    }

    static synchronized void incDiag(Context c, String key) {
        SharedPreferences p = prefs(c);
        p.edit().putInt(key, p.getInt(key, 0) + 1).apply();
    }

    static int getDiag(Context c, String key) {
        return prefs(c).getInt(key, 0);
    }

    static void resetDiag(Context c) {
        prefs(c).edit()
                .putInt(KEY_DIAG_OCR_RUNS, 0)
                .putInt(KEY_DIAG_OCR_RETRIES, 0)
                .putInt(KEY_DIAG_OCR_UNREAD, 0)
                .putInt(KEY_DIAG_SHOT_FAILS, 0)
                .apply();
    }

    static boolean getAutoOpen(Context c) {
        return prefs(c).getBoolean(KEY_AUTO_OPEN, true);
    }

    static void setAutoOpen(Context c, boolean v) {
        prefs(c).edit().putBoolean(KEY_AUTO_OPEN, v).apply();
    }

    static boolean getAutoAccept(Context c) {
        return prefs(c).getBoolean(KEY_AUTO_ACCEPT, true);
    }

    static void setAutoAccept(Context c, boolean v) {
        prefs(c).edit().putBoolean(KEY_AUTO_ACCEPT, v).apply();
    }

    static boolean getQuietOn(Context c) {
        return prefs(c).getBoolean(KEY_QUIET_ON, true);
    }

    static void setQuietOn(Context c, boolean v) {
        prefs(c).edit().putBoolean(KEY_QUIET_ON, v).apply();
    }

    static int getQuietStart(Context c) {
        return prefs(c).getInt(KEY_QUIET_START, 23 * 60);
    }

    static int getQuietEnd(Context c) {
        return prefs(c).getInt(KEY_QUIET_END, 7 * 60 + 30);
    }

    static void setQuietHours(Context c, int start, int end) {
        prefs(c).edit().putInt(KEY_QUIET_START, start).putInt(KEY_QUIET_END, end).apply();
    }

    static boolean quietNow(Context c) {
        Calendar cal = Calendar.getInstance();
        int m = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
        return Rules.inQuiet(m, getQuietOn(c), getQuietStart(c), getQuietEnd(c));
    }

    static boolean getAutoOnline(Context c) {
        return prefs(c).getBoolean(KEY_AUTO_ONLINE, true);
    }

    static void setAutoOnline(Context c, boolean v) {
        prefs(c).edit().putBoolean(KEY_AUTO_ONLINE, v).apply();
    }

    static boolean getAutoSched(Context c) {
        return prefs(c).getBoolean(KEY_AUTO_SCHED, true);
    }

    static void setAutoSched(Context c, boolean v) {
        prefs(c).edit().putBoolean(KEY_AUTO_SCHED, v).apply();
    }

    static boolean getAutoPriority(Context c) {
        return prefs(c).getBoolean(KEY_AUTO_PRIORITY, true);
    }

    static void setAutoPriority(Context c, boolean v) {
        prefs(c).edit().putBoolean(KEY_AUTO_PRIORITY, v).apply();
    }

    static void saveSettings(Context c, float minRate, float hichMiles, float hichSched,
                             int opacity, boolean vibrate, boolean sound) {
        prefs(c).edit()
                .putFloat(KEY_MIN_RATE, minRate)
                .putFloat(KEY_HICH_MILES, hichMiles)
                .putFloat(KEY_HICH_SCHED, hichSched)
                .putInt(KEY_OPACITY, opacity)
                .putBoolean(KEY_VIBRATE, vibrate)
                .putBoolean(KEY_SOUND, sound)
                .apply();
    }

    private static File offersFile(Context c) {
        return new File(c.getFilesDir(), "offers.jsonl");
    }

    static synchronized void addOffer(Context c, JSONObject o) {
        try {
            File f = offersFile(c);
            if (f.length() > 600000) {
                List<JSONObject> keep = readOffers(c);
                f.delete();
                for (int i = Math.max(0, keep.size() - 150); i < keep.size(); i++) appendLine(f, keep.get(i).toString());
            }
            appendLine(f, o.toString());
        } catch (Throwable ignored) {
        }
    }

    private static void appendLine(File f, String line) throws IOException {
        java.io.FileOutputStream os = new java.io.FileOutputStream(f, true);
        try {
            os.write((line + "\n").getBytes("UTF-8"));
        } finally {
            os.close();
        }
    }

    private static List<JSONObject> readOffers(Context c) {
        List<JSONObject> out = new ArrayList<>();
        File f = offersFile(c);
        if (!f.exists()) return out;
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = r.readLine()) != null) {
                try {
                    out.add(new JSONObject(line));
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    static synchronized List<JSONObject> recentOffers(Context c, int max) {
        List<JSONObject> all = readOffers(c);
        Collections.reverse(all);
        return all.size() > max ? new ArrayList<>(all.subList(0, max)) : all;
    }

    private static File[] sorted(Context c) {
        File[] files = dir(c).listFiles();
        if (files == null) return new File[0];
        Arrays.sort(files, (a, b) -> a.getName().compareTo(b.getName()));
        return files;
    }

    static int count(Context c, String ext) {
        int n = 0;
        for (File f : sorted(c)) if (f.getName().endsWith(ext)) n++;
        return n;
    }

    static long bytes(Context c) {
        long total = 0;
        for (File f : sorted(c)) total += f.length();
        return total;
    }

    static void prune(Context c) {
        pruneExt(c, ".txt", MAX_TXT);
        pruneExt(c, ".jpg", MAX_JPG);
    }

    private static void pruneExt(Context c, String ext, int max) {
        List<File> list = new ArrayList<>();
        for (File f : sorted(c)) if (f.getName().endsWith(ext)) list.add(f);
        int extra = list.size() - max;
        for (int i = 0; i < extra; i++) list.get(i).delete();
    }

    static void deleteAll(Context c) {
        for (File f : sorted(c)) f.delete();
        clearPending(c);
        new File(c.getFilesDir(), "events.log").delete();
        offersFile(c).delete();
        resetDiag(c);
    }

    static String exportZip(Context c, boolean withShots) throws IOException {
        File[] files = sorted(c);
        if (files.length == 0) throw new IOException("nothing to export yet");

        String stamp = new SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(new Date());
        String name = "Driverade_" + stamp + (withShots ? "" : "_text") + ".zip";

        ContentValues v = new ContentValues();
        v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        v.put(MediaStore.MediaColumns.MIME_TYPE, "application/zip");
        v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
        Uri uri = c.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
        if (uri == null) throw new IOException("could not create the file in Downloads");

        OutputStream os = c.getContentResolver().openOutputStream(uri);
        if (os == null) throw new IOException("could not open the file in Downloads");

        try (ZipOutputStream zos = new ZipOutputStream(os)) {
            for (File f : files) {
                if (!withShots && f.getName().endsWith(".jpg")) continue;
                addEntry(zos, f.getName(), f);
            }
            File log = new File(c.getFilesDir(), "events.log");
            if (log.exists()) addEntry(zos, "events.log", log);
            File offers = offersFile(c);
            if (offers.exists()) addEntry(zos, "offers.jsonl", offers);
        }
        return name;
    }

    private static void addEntry(ZipOutputStream zos, String name, File f) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        try (InputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) zos.write(buf, 0, n);
        }
        zos.closeEntry();
    }
}
