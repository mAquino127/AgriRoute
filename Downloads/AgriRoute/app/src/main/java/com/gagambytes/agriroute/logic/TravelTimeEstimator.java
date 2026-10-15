package com.gagambytes.agriroute.logic;

/**
 * Turns the routing provider's single travel time into a range ("5 to 7 hours").
 *
 * IMPORTANT: this is an ESTIMATE. The factors below are placeholders until the team has real trip
 * data from validation interviews. Change them in one place here when you have measured values.
 */
public final class TravelTimeEstimator {

    /** Shortest usual time = provider time (with current traffic). */
    public static final double MIN_FACTOR = 1.0;
    /** Longest usual time = provider time x 1.3 (loaded 10-wheelers on mountain roads, stops, queues). */
    public static final double MAX_FACTOR = 1.3;

    private TravelTimeEstimator() { }

    /** @return {minMillis, maxMillis} */
    public static long[] range(long trafficSeconds, long noTrafficSeconds) {
        long base = Math.max(trafficSeconds, noTrafficSeconds) * 1000L;
        long min = Math.round(base * MIN_FACTOR);
        long max = Math.round(base * MAX_FACTOR);
        return new long[]{min, Math.max(min, max)};
    }
}
