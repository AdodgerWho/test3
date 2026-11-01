package com.example.driverlogger;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class Offers {
    private Offers() {}

    static final class Offer {
        String app;
        String kind;
        double price = -1;
        double rate = -1;
        double pickupMin = -1, pickupMi = -1, tripMin = -1, tripMi = -1;
        String verdict = "UNREAD";
        String source = "tree";
        String detail = "";
        String raw = "";

        String key() {
            return app + "|" + kind + "|" + price + "|" + Math.round(rate) + "|" + pickupMi + "|" + tripMi;
        }
    }

    private static final Pattern WIN = Pattern.compile("=== WINDOW (\S+) layer=(-?\d+) ===\n");
    private static final Pattern LYFT_RATE =
            Pattern.compile("text=\"\$(\d+(?:\.\d+)?)/hr est\. rate for this ride\"");
    private static final Pattern MONEY_TEXT = Pattern.compile("text=\"\$(\d+\.\d\d)\"");
    private static final Pattern LYFT_LEG =
            Pattern.compile("text=\"(\d+) min • (\d+(?:\.\d+)?) mi\"");

    private static final Pattern HICH_PRICE = Pattern.compile("desc=\"\$(\d+\.\d\d)\"");
    private static final Pattern HICH_AWAY =
            Pattern.compile("desc=\"(\d+) mins? \((\d+(?:\.\d+)?) mi\) away\"");
    private static final Pattern HICH_TRIP =
            Pattern.compile("desc=\"(\d+) mins? \((\d+(?:\.\d+)?) mi\) trip\"");

    private static final Pattern UBER_RES_BTN = Pattern.compile("text=\"Accept • \$(\d+\.\d\d)\"");
    private static final Pattern UBER_RES_BANNER =
            Pattern.compile("reservation request[^\"]*\$(\d+\.\d\d)[^\"]*(?:Pickup\s+([^\"]+))?",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern UBER_RES_TRIP =
            Pattern.compile("text=\"(\d+) mins? \((\d+(?:\.\d+)?) mi\)(?: trip)?\"");
    private static final Pattern UBER_RES_WHEN =
            Pattern.compile("upfront_offer_header_subtitle text=\"([^\"]*)\"");

    private static final Pattern OCR_PRICE = Pattern.compile(
            "(?<!\+)(?<!\+\s)\$\s*(\d{1,3}(?:\.\d{2})?)(?!\s*(?:/\s*h|hr|included|surge|flash|perk|bonus))",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern OCR_LEG = Pattern.compile(
            "(?:(\d+)\s*hrs?\s*)?(\d+)\s*m[iIl1]ns?\b[^0-9A-Za-z]{0,6}(\d+(?:\.\d+)?)\s*(m[iIl1]|ft)\b",
            Pattern.CASE_INSENSITIVE);

    static List<Offer> fromTree(String body, double minRate, double hichMaxMiles, double hichMinScheduled) {
        List<Offer> out = new ArrayList<>();
        Matcher m = WIN.matcher(body);
        List<int[]> spans = new ArrayList<>();
        List<String> pkgs = new ArrayList<>();
        while (m.find()) {
            pkgs.add(m.group(1));
            spans.add(new int[]{m.end(), body.length(), m.start()});
        }
        for (int i = 0; i + 1 < spans.size(); i++) spans.get(i)[1] = spans.get(i + 1)[2];
        for (int i = 0; i < spans.size(); i++) {
            String seg = body.substring(spans.get(i)[0], spans.get(i)[1]);
            String pkg = pkgs.get(i);
            Offer o = null;
            if (pkg.equals("com.lyft.android.driver")) o = lyft(seg, minRate);
            else if (pkg.equals("us.mi.hich.driver")) o = hich(seg, hichMaxMiles, hichMinScheduled, minRate);
            else if (pkg.equals("com.ubercab.driver")) o = uberReservation(seg, minRate);
            if (o != null) out.add(o);
        }
        return out;
    }

    private static Offer lyft(String seg, double minRate) {
        Matcher r = LYFT_RATE.matcher(seg);
        if (!r.find()) return null;
        Offer o = new Offer();
        o.app = "lyft";
        o.kind = "live";
        o.rate = Double.parseDouble(r.group(1));
        Matcher mm = MONEY_TEXT.matcher(seg);
        while (mm.find() && mm.end() <= r.start()) o.price = Double.parseDouble(mm.group(1));
        Matcher legs = LYFT_LEG.matcher(seg);
        if (legs.find(r.end())) {
            o.pickupMin = Double.parseDouble(legs.group(1));
            o.pickupMi = Double.parseDouble(legs.group(2));
            if (legs.find()) {
                o.tripMin = Double.parseDouble(legs.group(1));
                o.tripMi = Double.parseDouble(legs.group(2));
            }
        }
        o.verdict = o.rate >= minRate ? "GOOD" : "SKIP";
        o.detail = "pickup " + fmt(o.pickupMin) + " min / " + fmt(o.pickupMi) + " mi, trip "
                + fmt(o.tripMin) + " min / " + fmt(o.tripMi) + " mi";
        return o;
    }

    private static Offer hich(String seg, double maxMiles, double minScheduled, double minRate) {
        if (!seg.contains("desc=\"ACCEPT\"")) return null;
        Matcher p = HICH_PRICE.matcher(seg);
        if (!p.find()) return null;
        Offer o = new Offer();
        o.app = "hich";
        o.price = Double.parseDouble(p.group(1));
        Matcher a = HICH_AWAY.matcher(seg);
        if (a.find()) {
            o.pickupMin = Double.parseDouble(a.group(1));
            o.pickupMi = Double.parseDouble(a.group(2));
        }
        Matcher t = HICH_TRIP.matcher(seg);
        if (t.find()) {
            o.tripMin = Double.parseDouble(t.group(1));
            o.tripMi = Double.parseDouble(t.group(2));
        }
        if (o.pickupMin >= 0 && o.tripMin >= 0 && o.pickupMin + o.tripMin > 0) {
            o.rate = o.price / ((o.pickupMin + o.tripMin) / 60.0);
        }
        boolean scheduled = seg.contains("Schedule Bookings") || seg.contains("Scheduled");
        if (scheduled) {
            o.kind = "scheduled";
            o.verdict = (o.price >= minScheduled || (o.rate >= 0 && o.rate >= minRate)) ? "GOOD" : "SKIP";
        } else {
            o.kind = "live";
            if (o.pickupMi < 0) o.verdict = "UNREAD";
            else o.verdict = o.pickupMi <= maxMiles ? "GOOD" : "SKIP";
        }
        o.detail = "pickup " + fmt(o.pickupMin) + " min / " + fmt(o.pickupMi) + " mi, trip "
                + fmt(o.tripMin) + " min / " + fmt(o.tripMi) + " mi";
        return o;
    }

    private static Offer uberReservation(String seg, double minRate) {
        Matcher b = UBER_RES_BTN.matcher(seg);
        if (!b.find()) {
            Matcher banner = UBER_RES_BANNER.matcher(seg);
            if (!banner.find()) return null;
            Offer o = new Offer();
            o.app = "uber";
            o.kind = "reservation";
            o.price = Double.parseDouble(banner.group(1));
            String when = banner.group(2) != null ? banner.group(2).trim() : "";
            o.verdict = "SKIP";
            o.detail = "reservation banner" + (when.isEmpty() ? "" : " (" + when + ")");
            return o;
        }
        Offer o = new Offer();
        o.app = "uber";
        o.kind = "reservation";
        o.price = Double.parseDouble(b.group(1));
        Matcher t = UBER_RES_TRIP.matcher(seg);
        if (t.find()) {
            o.tripMin = Double.parseDouble(t.group(1));
            o.tripMi = Double.parseDouble(t.group(2));
            if (o.tripMin > 0) o.rate = o.price / (o.tripMin / 60.0);
        }
        Matcher w = UBER_RES_WHEN.matcher(seg);
        String when = w.find() ? w.group(1) : "";
        o.verdict = o.rate < 0 ? "UNREAD" : (o.rate >= minRate ? "GOOD" : "SKIP");
        o.detail = "reservation " + when + ", trip " + fmt(o.tripMin) + " min / " + fmt(o.tripMi) + " mi";
        return o;
    }

    static Offer fromUberOcr(String ocr, double minRate) {
        Offer o = new Offer();
        o.app = "uber";
        o.kind = "live";
        o.source = "ocr";
        o.raw = ocr == null ? "" : ocr;
        if (ocr == null) return o;

        Matcher p = OCR_PRICE.matcher(ocr);
        while (p.find()) {
            double candidate = Double.parseDouble(p.group(1));
            if (candidate >= 3.00) {
                o.price = candidate;
                break;
            } else if (o.price < 0) {
                o.price = candidate;
            }
        }

        Matcher l = OCR_LEG.matcher(ocr);
        if (l.find()) {
            double hrs = l.group(1) != null ? Double.parseDouble(l.group(1)) : 0;
            o.pickupMin = hrs * 60.0 + Double.parseDouble(l.group(2));
            double dist = Double.parseDouble(l.group(3));
            o.pickupMi = "ft".equalsIgnoreCase(l.group(4)) ? Math.round((dist / 5280.0) * 100.0) / 100.0 : dist;
            if (l.find()) {
                double thrs = l.group(1) != null ? Double.parseDouble(l.group(1)) : 0;
                o.tripMin = thrs * 60.0 + Double.parseDouble(l.group(2));
                double tdist = Double.parseDouble(l.group(3));
                o.tripMi = "ft".equalsIgnoreCase(l.group(4)) ? Math.round((tdist / 5280.0) * 100.0) / 100.0 : tdist;
            }
        }

        if (o.price > 0 && o.pickupMin >= 0 && o.tripMin > 0) {
            o.rate = o.price / ((o.pickupMin + o.tripMin) / 60.0);
            o.verdict = o.rate >= minRate ? "GOOD" : "SKIP";
        }
        o.detail = "pickup " + fmt(o.pickupMin) + " min / " + fmt(o.pickupMi) + " mi, trip "
                + fmt(o.tripMin) + " min / " + fmt(o.tripMi) + " mi";
        return o;
    }

    private static String fmt(double d) {
        if (d < 0) return "?";
        if (d == Math.rint(d)) return String.valueOf((long) d);
        return String.valueOf(d);
    }
}
