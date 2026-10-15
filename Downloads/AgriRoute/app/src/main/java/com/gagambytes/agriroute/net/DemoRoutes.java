package com.gagambytes.agriroute.net;

import com.gagambytes.agriroute.data.Places;
import com.gagambytes.agriroute.logic.GeoUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Offline UI fallback used only when TOMTOM_API_KEY is missing. Its geometry and ETAs are
 * approximate and must never be presented as live routes. It intentionally contains no sample
 * traffic, weather, DPWH, or Project NOAH advisories/markers.
 */
public final class DemoRoutes {
    private static final int STEPS = 80;
    private DemoRoutes() { }

    public static List<RawRoute> build(Places.Place from, Places.Place to, long nowMillis) {
        List<RawRoute> out = new ArrayList<>();
        double straightKm = GeoUtils.haversineKm(from.lat, from.lon, to.lat, to.lon);
        out.add(make(from, to, straightKm, "Marcos Highway", "Approximate demo route", 1.25, 48, 0.05));
        out.add(make(from, to, straightKm, "Naguilian Road", "Approximate demo route", 1.30, 46, 0.20));
        out.add(make(from, to, straightKm, "Kennon Road", "Approximate demo route", 1.27, 44, -0.18));
        return out;
    }

    private static RawRoute make(Places.Place from, Places.Place to, double straightKm,
                                 String name, String via, double distFactor, double avgKmh, double bow) {
        RawRoute r = new RawRoute();
        r.name = name;
        r.via = via;
        r.distanceKm = straightKm * distFactor;
        r.trafficSeconds = Math.round(r.distanceKm / avgKmh * 3600);
        r.noTrafficSeconds = r.trafficSeconds;
        double midLat = (from.lat + to.lat) / 2;
        double midLon = (from.lon + to.lon) / 2;
        double dLat = to.lat - from.lat;
        double dLon = to.lon - from.lon;
        double ctrlLat = midLat + (-dLon) * bow * 2;
        double ctrlLon = midLon + dLat * bow * 2;
        for (int i = 0; i <= STEPS; i++) {
            double t = i / (double) STEPS;
            double a = (1 - t) * (1 - t);
            double b = 2 * (1 - t) * t;
            double c = t * t;
            r.points.add(new double[]{a * from.lat + b * ctrlLat + c * to.lat,
                    a * from.lon + b * ctrlLon + c * to.lon});
        }
        return r;
    }
}
