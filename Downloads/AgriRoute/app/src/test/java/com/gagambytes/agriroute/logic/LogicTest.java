package com.gagambytes.agriroute.logic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

/** Plain JVM tests for the pure logic (run with: ./gradlew test). */
public class LogicTest {

    private static final long H = 60 * 60 * 1000L;
    private static final long MIN = 60 * 1000L;
    /** Tuesday 2026-10-13 12:00 in Asia/Manila (04:00 UTC). */
    private static final long T0 = 1791864000000L;

    @BeforeClass
    public static void zone() {
        TimeFmt.setZone(TimeZone.getTimeZone("Asia/Manila"));
    }

    // ---------- ArrivalChecker ----------

    @Test
    public void onTimeWhenEvenLongestTimeMakesDeadline() {
        ArrivalChecker.Verdict v = ArrivalChecker.evaluate(T0, T0 + 10 * H, 5 * H, 7 * H, null, null, null);
        assertEquals(ArrivalChecker.Label.ON_TIME, v.label);
        assertEquals(T0 + 5 * H, v.etaMinMillis);
        assertEquals(T0 + 7 * H, v.etaMaxMillis);
    }

    @Test
    public void riskyWhenOnlyShortestTimeMakesDeadline() {
        ArrivalChecker.Verdict v = ArrivalChecker.evaluate(T0, T0 + 6 * H, 5 * H, 7 * H, null, null, null);
        assertEquals(ArrivalChecker.Label.RISKY, v.label);
    }

    @Test
    public void riskyWhenAlreadyLateAtShortestTime() {
        ArrivalChecker.Verdict v = ArrivalChecker.evaluate(T0, T0 + 4 * H, 5 * H, 7 * H, null, null, null);
        assertEquals(ArrivalChecker.Label.RISKY, v.label);
    }

    @Test
    public void riskyWhenHeavyRainEvenIfTimeIsFine() {
        ArrivalChecker.Verdict v = ArrivalChecker.evaluate(T0, T0 + 10 * H, 5 * H, 7 * H, null, "rain", null);
        assertEquals(ArrivalChecker.Label.RISKY, v.label);
        assertEquals("rain", v.reason);
    }

    @Test
    public void riskyWhenRouteIsBlocked() {
        ArrivalChecker.Verdict v = ArrivalChecker.evaluate(T0, T0 + 10 * H, 5 * H, 7 * H, "closed", null, null);
        assertEquals(ArrivalChecker.Label.RISKY, v.label);
    }

    @Test
    public void cantTellWhenInformationMissing() {
        ArrivalChecker.Verdict v = ArrivalChecker.evaluate(T0, T0 + 10 * H, 5 * H, 7 * H, null, null, "no forecast");
        assertEquals(ArrivalChecker.Label.CANT_TELL, v.label);
    }

    // ---------- TravelTimeEstimator ----------

    @Test
    public void rangeUsesWorseOfTrafficAndNoTraffic() {
        long[] r = TravelTimeEstimator.range(5 * 3600, 4 * 3600);
        assertEquals(5 * H, r[0]);
        assertEquals(Math.round(5 * H * TravelTimeEstimator.MAX_FACTOR), r[1]);
        assertTrue(r[1] >= r[0]);
    }

    // ---------- DepartureSuggester ----------

    private static DepartureSuggester.Forecast rainBetween(final long from, final long to, final double mm) {
        return (lat, lon, when) -> (when >= from && when <= to) ? mm : 0.0;
    }

    private static List<DepartureSuggester.Zone> oneZone() {
        List<DepartureSuggester.Zone> zs = new ArrayList<>();
        zs.add(new DepartureSuggester.Zone("Km 18-24, Tuba", 16.3, 120.56, 0.5));
        return zs;
    }

    @Test
    public void noConflictWhenNoRain() {
        assertNull(DepartureSuggester.findConflict(T0, 5 * H, 7 * H, oneZone(), (a, b, c) -> 0.0));
    }

    @Test
    public void conflictWhenHeavyRainOverZoneAtPassingTime() {
        // Zone is passed 2.5h-3.5h after leaving; rain 3h after leaving.
        DepartureSuggester.Conflict c = DepartureSuggester.findConflict(T0, 5 * H, 7 * H, oneZone(),
                rainBetween(T0 + 3 * H, T0 + 4 * H, 20));
        assertNotNull(c);
        assertEquals(20, c.mmPerHour, 0.001);
    }

    @Test
    public void unknownForecastIsNotAConflict() {
        assertNull(DepartureSuggester.findConflict(T0, 5 * H, 7 * H, oneZone(), (a, b, c) -> Double.NaN));
    }

    @Test
    public void suggestsLatestDepartureWhenNoRain() {
        long deadline = T0 + 20 * H;
        DepartureSuggester.Window w = DepartureSuggester.suggest(T0, deadline, 5 * H, 7 * H, oneZone(), (a, b, c) -> 0.0);
        assertEquals(deadline - 7 * H, w.endMillis);
        assertEquals(deadline - 7 * H - 30 * MIN, w.startMillis);
        assertTrue(w.avoidsRain);
    }

    @Test
    public void suggestsEarlierDepartureToPassBeforeRain() {
        long deadline = T0 + 20 * H;
        long latest = deadline - 7 * H;                  // T0 + 13h
        // Heavy rain over the zone from T0+15h; leaving at "latest" passes it at 15.5h-16.5h.
        DepartureSuggester.Forecast f = rainBetween(T0 + 15 * H, T0 + 30 * H, 20);
        DepartureSuggester.Window w = DepartureSuggester.suggest(T0, deadline, 5 * H, 7 * H, oneZone(), f);
        assertTrue(w.avoidsRain);
        assertTrue("must leave earlier than the latest time", w.endMillis < latest);
        assertNull(DepartureSuggester.findConflict(w.endMillis, 5 * H, 7 * H, oneZone(), f));
    }

    @Test
    public void flagsWhenRainCannotBeAvoided() {
        long deadline = T0 + 20 * H;
        DepartureSuggester.Window w = DepartureSuggester.suggest(T0, deadline, 5 * H, 7 * H, oneZone(),
                (a, b, c) -> 20.0);
        assertFalse(w.avoidsRain);
    }

    @Test
    public void tightDeadlineMeansLeaveNow() {
        DepartureSuggester.Window w = DepartureSuggester.suggest(T0, T0 + 3 * H, 5 * H, 7 * H, oneZone(), (a, b, c) -> 0.0);
        assertEquals(T0, w.startMillis);
        assertEquals(T0, w.endMillis);
    }

    // ---------- GeoUtils / TimeFmt ----------

    @Test
    public void haversineBaguioToManilaIsAbout210Km() {
        double d = GeoUtils.haversineKm(16.4023, 120.5960, 14.5995, 120.9842);
        assertTrue("distance " + d, d > 190 && d < 230);
    }

    @Test
    public void fractionAndPointAtFraction() {
        List<double[]> pts = new ArrayList<>();
        pts.add(new double[]{16.0, 120.0});
        pts.add(new double[]{16.1, 120.0});
        pts.add(new double[]{16.2, 120.0});
        double[] cum = GeoUtils.cumulativeKm(pts);
        assertEquals(0.5, GeoUtils.fractionAt(cum, 1), 0.01);
        assertEquals(16.1, GeoUtils.pointAtFraction(pts, cum, 0.5)[0], 0.0001);
        assertEquals(1, GeoUtils.nearestIndex(pts, 16.11, 120.0));
    }

    @Test
    public void durationAndAgoFormatting() {
        assertEquals("5h 10m", TimeFmt.duration(5 * H + 10 * MIN));
        assertEquals("7h", TimeFmt.duration(7 * H));
        assertEquals("45m", TimeFmt.duration(45 * MIN));
        assertEquals("24 min ago", TimeFmt.ago(T0, T0 + 24 * MIN));
        assertEquals("5h ago", TimeFmt.ago(T0, T0 + 5 * H));
    }
}
