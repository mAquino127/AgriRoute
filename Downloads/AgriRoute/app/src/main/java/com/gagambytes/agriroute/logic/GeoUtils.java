package com.gagambytes.agriroute.logic;

import java.util.List;

/** Distance helpers on {lat, lon} points. */
public final class GeoUtils {

    private static final double EARTH_RADIUS_KM = 6371.0088;

    private GeoUtils() { }

    public static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }

    /** Cumulative distance (km) at every point; first entry is 0. */
    public static double[] cumulativeKm(List<double[]> pts) {
        double[] cum = new double[pts.size()];
        for (int i = 1; i < pts.size(); i++) {
            double[] a = pts.get(i - 1);
            double[] b = pts.get(i);
            cum[i] = cum[i - 1] + haversineKm(a[0], a[1], b[0], b[1]);
        }
        return cum;
    }

    /** Index of the route point closest to (lat, lon), or -1 for an empty route. */
    public static int nearestIndex(List<double[]> pts, double lat, double lon) {
        int best = -1;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i < pts.size(); i++) {
            double[] p = pts.get(i);
            double d = haversineKm(lat, lon, p[0], p[1]);
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    /** Smallest distance (km) from (lat, lon) to any route vertex. */
    public static double distanceToRouteKm(List<double[]> pts, double lat, double lon) {
        int i = nearestIndex(pts, lat, lon);
        if (i < 0) return Double.MAX_VALUE;
        double[] p = pts.get(i);
        return haversineKm(lat, lon, p[0], p[1]);
    }

    /** Fraction (0..1) of the route length at which the point at index i lies. */
    public static double fractionAt(double[] cumKm, int index) {
        if (cumKm.length == 0) return 0;
        double total = cumKm[cumKm.length - 1];
        if (total <= 0) return 0;
        return Math.max(0, Math.min(1, cumKm[Math.max(0, Math.min(index, cumKm.length - 1))] / total));
    }

    /** The {lat, lon} point at a given fraction of the route (nearest vertex). */
    public static double[] pointAtFraction(List<double[]> pts, double[] cumKm, double fraction) {
        if (pts.isEmpty()) return new double[]{0, 0};
        double total = cumKm[cumKm.length - 1];
        double target = total * Math.max(0, Math.min(1, fraction));
        int best = 0;
        double bestDiff = Double.MAX_VALUE;
        for (int i = 0; i < cumKm.length; i++) {
            double diff = Math.abs(cumKm[i] - target);
            if (diff < bestDiff) {
                bestDiff = diff;
                best = i;
            }
        }
        return pts.get(best);
    }
}
