package com.gagambytes.agriroute.net;

import com.gagambytes.agriroute.logic.DepartureSuggester;
import com.gagambytes.agriroute.logic.GeoUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Hourly rain forecast from Open-Meteo (no API key needed).
 * NOTE: Open-Meteo's free tier is for non-commercial use. The proposal already lists a paid
 * license (and PAGASA) for production, so swap this class when you go commercial.
 */
public class OpenMeteoForecast implements DepartureSuggester.Forecast {

    private static final String URL = "https://api.open-meteo.com/v1/forecast";
    private static final long TTL_MS = 30 * 60 * 1000L;
    private static final double MAX_DISTANCE_KM = 40.0;

    private static final class Series {
        double lat;
        double lon;
        long[] timesSec;
        double[] mm;
        long fetchedAt;
    }

    private static final Map<String, Series> CACHE = new HashMap<>();

    private final List<Series> used = new ArrayList<>();

    /**
     * Download (or reuse cached) hourly rain for each point.
     *
     * @return true if at least one point has a forecast
     */
    public boolean prefetch(List<double[]> points) {
        boolean any = false;
        for (double[] p : points) {
            Series s = load(p[0], p[1]);
            if (s != null) {
                any = true;
                if (!used.contains(s)) used.add(s);
            }
        }
        return any;
    }

    private static Series load(double lat, double lon) {
        String key = String.format(Locale.US, "%.1f,%.1f", lat, lon);
        long now = System.currentTimeMillis();
        synchronized (CACHE) {
            Series cached = CACHE.get(key);
            if (cached != null && now - cached.fetchedAt < TTL_MS) return cached;
        }
        try {
            String url = URL + "?latitude=" + String.format(Locale.US, "%.4f", lat)
                    + "&longitude=" + String.format(Locale.US, "%.4f", lon)
                    + "&hourly=precipitation&forecast_days=3&timeformat=unixtime";
            JSONObject hourly = new JSONObject(Http.get(url)).getJSONObject("hourly");
            JSONArray times = hourly.getJSONArray("time");
            JSONArray precip = hourly.getJSONArray("precipitation");
            Series s = new Series();
            s.lat = lat;
            s.lon = lon;
            s.fetchedAt = now;
            int n = Math.min(times.length(), precip.length());
            s.timesSec = new long[n];
            s.mm = new double[n];
            for (int i = 0; i < n; i++) {
                s.timesSec[i] = times.getLong(i);
                s.mm[i] = precip.isNull(i) ? Double.NaN : precip.getDouble(i);
            }
            if (n == 0) return null;
            synchronized (CACHE) {
                CACHE.put(key, s);
            }
            return s;
        } catch (IOException | JSONException e) {
            return null;
        }
    }

    @Override
    public double mmPerHour(double lat, double lon, long whenMillis) {
        Series best = null;
        double bestD = Double.MAX_VALUE;
        for (Series s : used) {
            double d = GeoUtils.haversineKm(lat, lon, s.lat, s.lon);
            if (d < bestD) {
                bestD = d;
                best = s;
            }
        }
        if (best == null || bestD > MAX_DISTANCE_KM || best.timesSec.length == 0) return Double.NaN;
        long idx = Math.floorDiv(whenMillis / 1000 - best.timesSec[0], 3600L);
        if (idx < 0 || idx >= best.mm.length) return Double.NaN;
        return best.mm[(int) idx];
    }
}
