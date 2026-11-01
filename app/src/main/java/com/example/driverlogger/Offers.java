package com.example.driverlogger;

/**
 * Simple offer model used by the app UI and alert logic.
 *
 * The project previously had an accidental duplicate of the Rules class inside this file,
 * which caused Gradle duplicate-class errors. This file now defines the actual Offers model.
 */
final class Offers {
    private Offers() {
    }

    static final class Offer {
        String app = "";
        String kind = "live";
        double price = -1;
        double rate = -1;
        double pickupMin = -1;
        double pickupMi = -1;
        double tripMin = -1;
        double tripMi = -1;
        String verdict = "UNREAD";
        String detail = "";
        String source = "";
        String raw = "";
    }
}
