package com.gagambytes.agriroute.logic;

import java.util.List;
import java.util.Locale;

/**
 * Suggested departure time (proposal section 4.1, item 5): the departure window that meets the
 * deadline and avoids forecast heavy rain on hazard-prone sections.
 */
public final class DepartureSuggester {

    /**
     * mm of rain per hour that counts as "heavy". 7.5 mm/h is the lower edge of PAGASA's
     * heavy-rain band; verify against the PAGASA classification you cite in your paper.
     */
    public static final double HEAVY_MM_PER_HOUR = 7.5;

    private static final long STEP_MS = 30 * 60 * 1000L;
    private static final long MAX_SHIFT_MS = 12 * 60 * 60 * 1000L;

    /** Rain forecast lookup. Return NaN when no forecast is available. */
    public interface Forecast {
        double mmPerHour(double lat, double lon, long whenMillis);
    }

    /** A hazard-prone section on the route. */
    public static final class Zone {
        public final String name;
        public final double lat;
        public final double lon;
        /** 0..1 position along the route. */
        public final double fraction;

        public Zone(String name, double lat, double lon, double fraction) {
            this.name = name;
            this.lat = lat;
            this.lon = lon;
            this.fraction = fraction;
        }
    }

    public static final class Conflict {
        public final Zone zone;
        public final long atMillis;
        public final double mmPerHour;

        Conflict(Zone zone, long atMillis, double mmPerHour) {
            this.zone = zone;
            this.atMillis = atMillis;
            this.mmPerHour = mmPerHour;
        }
    }

    public static final class Window {
        public final long startMillis;
        public final long endMillis;
        public final boolean avoidsRain;
        public final String reason;

        Window(long startMillis, long endMillis, boolean avoidsRain, String reason) {
            this.startMillis = startMillis;
            this.endMillis = endMillis;
            this.avoidsRain = avoidsRain;
            this.reason = reason;
        }
    }

    private DepartureSuggester() { }

    /**
     * Would a truck leaving at departMillis be on a hazard-prone section while heavy rain is
     * forecast there? Returns the worst such moment, or null.
     */
    public static Conflict findConflict(long departMillis, long minMillis, long maxMillis,
                                        List<Zone> zones, Forecast forecast) {
        Conflict worst = null;
        for (Zone z : zones) {
            long from = departMillis + Math.round(z.fraction * minMillis);
            long to = departMillis + Math.round(z.fraction * maxMillis);
            for (long t = from; ; t += STEP_MS) {
                long at = Math.min(t, to);
                double mm = forecast.mmPerHour(z.lat, z.lon, at);
                if (!Double.isNaN(mm) && mm >= HEAVY_MM_PER_HOUR && (worst == null || mm > worst.mmPerHour)) {
                    worst = new Conflict(z, at, mm);
                }
                if (at >= to) break;
            }
        }
        return worst;
    }

    /**
     * Latest departure that still arrives on time at the longest usual travel time, moved earlier
     * in 30 minute steps (up to 12 hours) until no hazard-prone section meets forecast heavy rain.
     */
    public static Window suggest(long nowMillis, long deadlineMillis, long minMillis, long maxMillis,
                                 List<Zone> zones, Forecast forecast) {
        long latest = deadlineMillis - maxMillis;
        if (latest <= nowMillis) {
            return new Window(nowMillis, nowMillis, true,
                    "The deadline is tight: leave as soon as possible. Even leaving now may miss the longest usual travel time.");
        }
        long earliest = Math.max(nowMillis, latest - MAX_SHIFT_MS);
        for (long d = latest; d >= earliest; d -= STEP_MS) {
            Conflict c = findConflict(d, minMillis, maxMillis, zones, forecast);
            if (c == null) {
                long start = Math.max(nowMillis, d - STEP_MS);
                String reason;
                if (d == latest) {
                    reason = String.format(Locale.US,
                            "Leave between %s and %s to arrive before the deadline at the longest usual travel time.",
                            TimeFmt.hm(start), TimeFmt.hm(d));
                } else {
                    reason = String.format(Locale.US,
                            "Leave between %s and %s: earlier than the latest possible time so the truck passes hazard-prone sections before forecast heavy rain.",
                            TimeFmt.hm(start), TimeFmt.hm(d));
                }
                return new Window(start, d, true, reason);
            }
        }
        return new Window(Math.max(nowMillis, latest - STEP_MS), latest, false,
                String.format(Locale.US,
                        "Leave between %s and %s. Heavy rain is forecast over a hazard-prone section and no earlier time within 12 hours avoids it.",
                        TimeFmt.hm(Math.max(nowMillis, latest - STEP_MS)), TimeFmt.hm(latest)));
    }
}
