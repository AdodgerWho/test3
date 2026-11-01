package com.example.driverlogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * v3.5: accept rules for hich scheduled rides and quiet-hours window.
 */
final class Rules {
    private Rules() {}

    static final class Scheduled {
        String rideId = "";
        String status = "";
        double price = -1;
        double tripMin = -1;
        double pickupMin = -1;
        int pickupMinuteOfDay = -1;
        String pickupClock = "";
        String dateText = "";
        boolean hasAccept = false;

        void merge(Scheduled n) {
            if (!n.rideId.isEmpty()) rideId = n.rideId;
            if (!n.status.isEmpty()) status = n.status;
            if (n.price >= 0) price = n.price;
            if (n.tripMin >= 0) tripMin = n.tripMin;
            if (n.pickupMin >= 0) pickupMin = n.pickupMin;
            if (n.pickupMinuteOfDay >= 0) {
                pickupMinuteOfDay = n.pickupMinuteOfDay;
                pickupClock = n.pickupClock;
            }
            if (!n.dateText.isEmpty()) dateText = n.dateText;
            hasAccept = n.hasAccept;
        }
    }

    static final class Decision {
        boolean accept;
        String verdict = "UNREAD";
        String reason = "";
        double rate = -1;
    }

    private static final Pattern RIDE_ID = Pattern.compile("Ride ID #(\\d+)");
    private static final Pattern STATUS = Pattern.compile("Trip Status (\\w+)");
    private static final Pattern AMOUNT = Pattern.compile("Trip amount\\s+\\$(\\d+(?:\\.\\d{1,2})?)");
    private static final Pattern DURATION = Pattern.compile("Est Duration (.*?) Pickup Time");
    private static final Pattern CLOCK = Pattern.compile("Pickup Time (\\d{1,2}):(\\d{2})\\s*([AaPp][Mm])");
    private static final Pattern AWAY =
            Pattern.compile("(\\d+) mins? \\((\\d+(?:\\.\\d+)?) mi\\) away");
    private static final Pattern DATE = Pattern.compile("Date (\\w{3}, \\w{3} \\d{1,2})");
    private static final Pattern WIN = Pattern.compile("=== WINDOW (\\S+) layer=(-?\\d+) ===\\n");

    static List<String> segmentsFor(String body, String pkg) {
        List<String> out = new ArrayList<>();
        Matcher m = WIN.matcher(body);
        List<Integer> starts = new ArrayList<>();
        List<Integer> ends = new ArrayList<>();
        List<String> pkgs = new ArrayList<>();
        while (m.find()) {
            pkgs.add(m.group(1));
            starts.add(m.end());
            ends.add(m.start());
        }
        for (int i = 0; i < starts.size(); i++) {
            if (!pkgs.get(i).equals(pkg)) continue;
            int end = i + 1 < starts.size() ? ends.get(i + 1) : body.length();
            out.add(body.substring(starts.get(i), end));
        }
        return out;
    }

    static Scheduled parseHichTripDetails(String seg) {
        if (!seg.contains("desc=\"Trip Details\"")) return null;
        Scheduled s = new Scheduled();
        Matcher m = RIDE_ID.matcher(seg);
        if (m.find()) s.rideId = m.group(1);
        m = STATUS.matcher(seg);
        if (m.find()) s.status = m.group(1);
        m = AMOUNT.matcher(seg);
        if (m.find()) s.price = Double.parseDouble(m.group(1));
        m = DURATION.matcher(seg);
        if (m.find()) s.tripMin = parseDuration(m.group(1));
        m = CLOCK.matcher(seg);
        if (m.find()) {
            int h = Integer.parseInt(m.group(1)) % 12;
            if (m.group(3).equalsIgnoreCase("PM")) h += 12;
            s.pickupMinuteOfDay = h * 60 + Integer.parseInt(m.group(2));
            s.pickupClock = m.group(1) + ":" + m.group(2) + " " + m.group(3).toUpperCase(Locale.US);
        }
        m = AWAY.matcher(seg);
        if (m.find()) s.pickupMin = Double.parseDouble(m.group(1));
        m = DATE.matcher(seg);
        if (m.find()) s.dateText = m.group(1);
        s.hasAccept = seg.contains("desc=\"ACCEPT\"");
        return s;
    }

    static double parseDuration(String t) {
        double total = 0;
        boolean found = false;
        Matcher h = Pattern.compile("(\\d+)\\s*h").matcher(t);
        if (h.find()) {
            total += 60 * Double.parseDouble(h.group(1));
            found = true;
        }
        Matcher mi = Pattern.compile("(\\d+)\\s*m").matcher(t);
        if (mi.find()) {
            total += Double.parseDouble(mi.group(1));
            found = true;
        }
        return found ? total : -1;
    }

    static Decision decide(Scheduled s, double minAmount, double minRate,
                            boolean quietOn, int quietStart, int quietEnd, long waitedMs) {
        Decision d = new Decision();
        if (s.price < 0 || s.pickupMinuteOfDay < 0) {
            d.verdict = "UNREAD";
            d.reason = "amount or pickup time not readable";
            return d;
        }
        if (s.tripMin > 0 && s.pickupMin >= 0) {
            d.rate = s.price / ((s.pickupMin + s.tripMin) / 60.0);
        }
        if (inQuiet(s.pickupMinuteOfDay, quietOn, quietStart, quietEnd)) {
            d.verdict = "QUIET";
            d.reason = "pickup " + s.pickupClock + " is inside quiet hours "
                    + clockText(quietStart) + " to " + clockText(quietEnd);
            return d;
        }
        boolean flat = s.price >= minAmount;
        boolean hourly = d.rate >= 0 && d.rate >= minRate;
        if (!flat && d.rate < 0 && waitedMs < 1500) {
            d.verdict = "WAIT";
            d.reason = "waiting for drive-to-pickup minutes";
            return d;
        }
        d.accept = flat || hourly;
        d.verdict = d.accept ? "GOOD" : "SKIP";
        String r = d.rate >= 0 ? String.format(Locale.US, "$%.0f/hr", d.rate) : "$/hr unknown";
        if (flat && hourly) d.reason = "amount and hourly both qualify (" + r + ")";
        else if (flat) d.reason = "amount qualifies (at least $" + trim(minAmount) + "), " + r;
        else if (hourly) d.reason = "hourly qualifies (" + r + " is at least $" + trim(minRate) + ")";
        else d.reason = "amount under $" + trim(minAmount) + " and " + r + " under $" + trim(minRate);
        return d;
    }

    static boolean inQuiet(int minuteOfDay, boolean on, int start, int end) {
        if (!on || start == end || minuteOfDay < 0) return false;
        if (start < end) return minuteOfDay >= start && minuteOfDay < end;
        return minuteOfDay >= start || minuteOfDay < end;
    }

    static int parseClock(String s) {
        if (s == null) return -1;
        Matcher m = Pattern.compile("^\\s*(\\d{1,2})(?:[:.](\\d{2}))?\\s*([AaPp][Mm])?\\s*$").matcher(s);
        if (!m.find()) return -1;
        int h = Integer.parseInt(m.group(1));
        int min = m.group(2) == null ? 0 : Integer.parseInt(m.group(2));
        if (min > 59) return -1;
        if (m.group(3) != null) {
            if (h < 1 || h > 12) return -1;
            h = h % 12;
            if (m.group(3).equalsIgnoreCase("PM")) h += 12;
        } else if (h > 23) {
            return -1;
        }
        return h * 60 + min;
    }

    static String clockText(int minuteOfDay) {
        int h = minuteOfDay / 60, m = minuteOfDay % 60;
        int h12 = h % 12 == 0 ? 12 : h % 12;
        return String.format(Locale.US, "%d:%02d %s", h12, m, h < 12 ? "AM" : "PM");
    }

    private static String trim(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.format(Locale.US, "%.2f", d);
    }
}

