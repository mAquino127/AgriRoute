package com.gagambytes.agriroute.logic;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Small, dependency-free time formatting helpers (24h clock, device time zone by default). */
public final class TimeFmt {

    private static TimeZone zone = TimeZone.getDefault();

    private TimeFmt() { }

    /** Override the zone (used by unit tests). */
    public static void setZone(TimeZone tz) { zone = tz; }

    private static String fmt(String pattern, long millis) {
        SimpleDateFormat f = new SimpleDateFormat(pattern, Locale.US);
        f.setTimeZone(zone);
        return f.format(new Date(millis));
    }

    /** 21:30 */
    public static String hm(long millis) { return fmt("HH:mm", millis); }

    /** Wed 05:00 */
    public static String dayHm(long millis) { return fmt("EEE HH:mm", millis); }

    /** Oct 15, 8:00 PM */
    public static String friendly(long millis) { return fmt("EEE, MMM d  h:mm a", millis); }

    /** 8:00 PM */
    public static String clock12(long millis) { return fmt("h:mm a", millis); }

    /** Wed 3:00 AM */
    public static String dayClock12(long millis) { return fmt("EEE h:mm a", millis); }

    /** Hour of day, 0-23 (used for the greeting). */
    public static int hourOfDay(long millis) { return Integer.parseInt(fmt("H", millis)); }

    /** Tue, Oct 13 */
    public static String dateLong(long millis) { return fmt("EEE, MMM d", millis); }

    /** 2026-10-15 (used to group updates by day) */
    public static String dayKey(long millis) { return fmt("yyyy-MM-dd", millis); }

    /** "5h 10m" */
    public static String duration(long millis) {
        long mins = Math.max(0, Math.round(millis / 60000.0));
        long h = mins / 60;
        long m = mins % 60;
        if (h == 0) return m + "m";
        if (m == 0) return h + "h";
        return h + "h " + m + "m";
    }

    /** "24 min ago", "5h ago", "3 days ago" */
    public static String ago(long thenMillis, long nowMillis) {
        long diff = Math.max(0, nowMillis - thenMillis);
        long mins = diff / 60000;
        if (mins < 1) return "just now";
        if (mins < 60) return mins + " min ago";
        long hours = mins / 60;
        if (hours < 24) return hours + "h ago";
        long days = hours / 24;
        if (days < 60) return days + (days == 1 ? " day ago" : " days ago");
        long months = days / 30;
        return months + (months == 1 ? " month ago" : " months ago");
    }
}
