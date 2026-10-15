package com.gagambytes.agriroute.net;

import com.gagambytes.agriroute.BuildConfig;
import com.gagambytes.agriroute.data.Places;
import com.gagambytes.agriroute.logic.GeoUtils;
import com.gagambytes.agriroute.model.Hazard;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * TomTom Routing API (with live traffic). The API key comes from BuildConfig.TOMTOM_API_KEY,
 * which is filled from local.properties at build time.
 */
public final class TomTomClient {

    private static final String BASE = "https://api.tomtom.com/routing/1/calculateRoute/";

    private TomTomClient() { }

    public static List<RawRoute> fetchRoutes(Places.Place from, Places.Place to, long nowMillis) throws IOException {
        String key = URLEncoder.encode(BuildConfig.TOMTOM_API_KEY, "UTF-8");
        String path = BASE + coord(from.lat) + "," + coord(from.lon) + ":" + coord(to.lat) + "," + coord(to.lon) + "/json";
        String common = "?key=" + key
                + "&maxAlternatives=3&alternativeType=anyRoute&routeType=fastest&traffic=true&sectionType=traffic&instructionsType=text"
                + "&routeRepresentation=polyline&computeTravelTimeFor=all";
        String body;
        try {
            try {
                body = Http.get(path + common + "&travelMode=truck&vehicleCommercial=true");
            } catch (Http.HttpException e) {
                // Truck mode is "to be tested in Benguet" (proposal section 6): fall back to car routing.
                if (e.code == 400) {
                    body = Http.get(path + common + "&travelMode=car");
                } else {
                    throw e;
                }
            }
        } catch (Http.HttpException e) {
            if (e.code == 401 || e.code == 403) {
                throw new IOException("TomTom rejected the API key (HTTP " + e.code
                        + "). Check TOMTOM_API_KEY in local.properties and make sure the Routing API is enabled for that key.");
            }
            if (e.code == 429) {
                throw new IOException("TomTom rate limit reached (HTTP 429). Wait a minute and try again.");
            }
            throw new IOException("TomTom routing failed (HTTP " + e.code + ").");
        }
        return parse(body, nowMillis);
    }

    private static String coord(double v) {
        return String.format(Locale.US, "%.5f", v);
    }

    static List<RawRoute> parse(String body, long nowMillis) throws IOException {
        try {
            JSONObject root = new JSONObject(body);
            JSONArray routes = root.optJSONArray("routes");
            if (routes == null || routes.length() == 0) {
                throw new IOException("TomTom found no drivable route between these two places.");
            }
            List<RawRoute> out = new ArrayList<>();
            for (int i = 0; i < routes.length(); i++) {
                JSONObject r = routes.getJSONObject(i);
                JSONObject sum = r.getJSONObject("summary");
                RawRoute rr = new RawRoute();
                rr.distanceKm = sum.optDouble("lengthInMeters", 0) / 1000.0;
                rr.trafficSeconds = sum.optLong("liveTrafficIncidentsTravelTimeInSeconds", sum.optLong("travelTimeInSeconds", 0));
                rr.noTrafficSeconds = sum.optLong("noTrafficTravelTimeInSeconds", rr.trafficSeconds);

                JSONArray legs = r.getJSONArray("legs");
                for (int l = 0; l < legs.length(); l++) {
                    JSONArray pts = legs.getJSONObject(l).getJSONArray("points");
                    for (int p = 0; p < pts.length(); p++) {
                        JSONObject pt = pts.getJSONObject(p);
                        rr.points.add(new double[]{pt.getDouble("latitude"), pt.getDouble("longitude")});
                    }
                }
                double[] cum = GeoUtils.cumulativeKm(rr.points);
                nameFromGuidance(r, rr, i);
                addTrafficHazards(r, rr, cum, nowMillis);
                out.add(rr);
            }
            dedupeNames(out);
            return out;
        } catch (JSONException e) {
            throw new IOException("Unexpected answer from TomTom: " + e.getMessage());
        }
    }

    /** Names a route after the roads it uses the most, e.g. "Marcos Highway" via "Tuba, Rosario". */
    private static void nameFromGuidance(JSONObject route, RawRoute rr, int index) {
        Map<String, Double> meters = new HashMap<>();
        JSONObject g = route.optJSONObject("guidance");
        JSONArray ins = g == null ? null : g.optJSONArray("instructions");
        if (ins != null) {
            double total = rr.distanceKm * 1000.0;
            for (int i = 0; i < ins.length(); i++) {
                JSONObject a = ins.optJSONObject(i);
                if (a == null) continue;
                String street = a.optString("street", "").trim();
                if (street.isEmpty()) continue;
                double start = a.optDouble("routeOffsetInMeters", 0);
                double end = total;
                JSONObject next = ins.optJSONObject(i + 1);
                if (next != null) end = next.optDouble("routeOffsetInMeters", total);
                Double prev = meters.get(street);
                meters.put(street, (prev == null ? 0 : prev) + Math.max(0, end - start));
            }
        }
        List<Map.Entry<String, Double>> entries = new ArrayList<>(meters.entrySet());
        Collections.sort(entries, new Comparator<Map.Entry<String, Double>>() {
            @Override
            public int compare(Map.Entry<String, Double> a, Map.Entry<String, Double> b) {
                return Double.compare(b.getValue(), a.getValue());
            }
        });
        if (entries.isEmpty()) {
            rr.name = "Route " + (char) ('A' + index);
            rr.via = "";
            return;
        }
        rr.name = entries.get(0).getKey();
        StringBuilder via = new StringBuilder();
        for (int i = 1; i < Math.min(4, entries.size()); i++) {
            if (via.length() > 0) via.append(", ");
            via.append(entries.get(i).getKey());
        }
        rr.via = via.toString();
    }

    private static void addTrafficHazards(JSONObject route, RawRoute rr, double[] cum, long nowMillis) {
        JSONArray secs = route.optJSONArray("sections");
        if (secs == null || rr.points.isEmpty()) return;
        List<Hazard> found = new ArrayList<>();
        for (int i = 0; i < secs.length(); i++) {
            JSONObject s = secs.optJSONObject(i);
            if (s == null || !"TRAFFIC".equals(s.optString("sectionType"))) continue;
            String cat = s.optString("simpleCategory", "");
            int delaySec = s.optInt("delayInSeconds", 0);
            int start = Math.max(0, Math.min(s.optInt("startPointIndex", 0), rr.points.size() - 1));
            double[] p = rr.points.get(start);

            Hazard h = new Hazard();
            h.lat = p[0];
            h.lon = p[1];
            h.fraction = GeoUtils.fractionAt(cum, start);
            h.source = "TomTom Traffic";
            h.sourceUrl = "https://www.tomtom.com/products/traffic-apis/";
            h.postedAtMillis = nowMillis;
            h.delayMinutes = (int) Math.round(delaySec / 60.0);
            h.id = "tt-" + cat + "-" + String.format(Locale.US, "%.2f,%.2f", p[0], p[1])
                    + "-" + (nowMillis / 3_600_000L);

            if ("ROAD_CLOSURE".equals(cat)) {
                h.type = Hazard.Type.CLOSURE;
                h.title = "Road closure on this route";
                h.detail = String.format(Locale.US,
                        "TomTom Traffic reports a closed section on %s, approximately %.1f km from the route origin. Checked around %s.",
                        rr.name, rr.distanceKm * h.fraction, com.gagambytes.agriroute.logic.TimeFmt.hm(nowMillis));
                h.effect = Hazard.EFFECT_BLOCK;
            } else if ("ROAD_WORK".equals(cat) && delaySec >= 300) {
                h.type = Hazard.Type.WORKS;
                h.title = "Road works";
                h.detail = String.format(Locale.US,
                        "TomTom live traffic estimates about %d min delay on %s, approximately %.1f km from the route origin. Checked around %s.",
                        h.delayMinutes, rr.name, rr.distanceKm * h.fraction,
                        com.gagambytes.agriroute.logic.TimeFmt.hm(nowMillis));
                h.effect = Hazard.EFFECT_DELAY;
                h.inTravelTime = true;
            } else if (delaySec >= 600) {
                h.type = Hazard.Type.TRAFFIC;
                h.title = "Heavy traffic";
                h.detail = String.format(Locale.US,
                        "TomTom live traffic estimates about %d min delay on %s, approximately %.1f km from the route origin. Checked around %s.",
                        h.delayMinutes, rr.name, rr.distanceKm * h.fraction,
                        com.gagambytes.agriroute.logic.TimeFmt.hm(nowMillis));
                h.effect = Hazard.EFFECT_DELAY;
                h.inTravelTime = true;
            } else {
                continue;
            }
            found.add(h);
        }
        // Keep the five biggest traffic events so the route detail remains readable.
        Collections.sort(found, new Comparator<Hazard>() {
            @Override
            public int compare(Hazard a, Hazard b) {
                return Integer.compare(b.delayMinutes, a.delayMinutes);
            }
        });
        for (int i = 0; i < Math.min(5, found.size()); i++) rr.hazards.add(found.get(i));
    }

    private static void dedupeNames(List<RawRoute> routes) {
        Map<String, Integer> seen = new HashMap<>();
        for (RawRoute r : routes) {
            Integer n = seen.get(r.name);
            if (n == null) {
                seen.put(r.name, 1);
            } else {
                seen.put(r.name, n + 1);
                r.name = r.name + " (alt " + (n + 1) + ")";
            }
        }
    }
}
