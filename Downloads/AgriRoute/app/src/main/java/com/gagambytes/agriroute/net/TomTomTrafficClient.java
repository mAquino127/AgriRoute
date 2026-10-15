package com.gagambytes.agriroute.net;

import com.gagambytes.agriroute.BuildConfig;
import com.gagambytes.agriroute.logic.GeoUtils;
import com.gagambytes.agriroute.model.Hazard;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.List;
import java.util.Locale;

/**
 * Samples TomTom Traffic Flow along each candidate route so the map can show traffic conditions,
 * including normal flow, rather than drawing markers only when a severe incident is reported.
 * Routing's traffic-aware ETA remains the source of each route's travel-time estimate.
 */
public final class TomTomTrafficClient {
    private static final String BASE = "https://api.tomtom.com/traffic/services/4/flowSegmentData/absolute/10/json";
    private static final double SEVERE_SPEED_RATIO = 0.55;
    private static final double SLOW_SPEED_RATIO = 0.80;
    private static final double MIN_CONFIDENCE = 0.40;
    // Spread markers along the route; weather markers use 25%, 55%, and 85%.
    private static final double[] SAMPLE_FRACTIONS = new double[]{0.15, 0.48, 0.80};

    private TomTomTrafficClient() { }

    public static void enrich(List<RawRoute> routes, long nowMillis) {
        if (BuildConfig.TOMTOM_API_KEY == null || BuildConfig.TOMTOM_API_KEY.trim().isEmpty()) return;
        for (RawRoute route : routes) {
            if (route == null || route.points == null || route.points.isEmpty()) continue;
            double[] cumulative = GeoUtils.cumulativeKm(route.points);
            for (double fraction : SAMPLE_FRACTIONS) {
                double[] point = GeoUtils.pointAtFraction(route.points, cumulative, fraction);
                try {
                    Hazard h = fetchFlowHazard(point[0], point[1], fraction, nowMillis,
                            route.name, route.distanceKm);
                    if (h != null) route.hazards.add(h);
                } catch (IOException ignored) {
                    // Routing still provides the live-traffic-aware ETA if a flow sample fails.
                    // Don't invent a marker when TomTom cannot provide a condition reading.
                }
            }
        }
    }

    private static Hazard fetchFlowHazard(double lat, double lon, double fraction,
                                          long nowMillis, String routeName, double distanceKm)
            throws IOException {
        final String key;
        try {
            key = URLEncoder.encode(BuildConfig.TOMTOM_API_KEY, "UTF-8");
        } catch (Exception e) {
            return null;
        }
        String point = String.format(Locale.US, "%.5f,%.5f", lat, lon);
        String url = BASE + "?key=" + key + "&point=" + point + "&unit=kmph";
        final JSONObject flow;
        try {
            flow = new JSONObject(Http.get(url)).getJSONObject("flowSegmentData");
        } catch (JSONException e) {
            throw new IOException("TomTom Traffic Flow returned an unexpected response.");
        }

        int currentSpeed = flow.optInt("currentSpeed", -1);
        int freeFlowSpeed = flow.optInt("freeFlowSpeed", -1);
        int currentTime = flow.optInt("currentTravelTime", -1);
        int freeFlowTime = flow.optInt("freeFlowTravelTime", -1);
        double confidence = flow.optDouble("confidence", 0.0);
        boolean closed = flow.optBoolean("roadClosure", false);
        if (!closed && (currentSpeed < 0 || freeFlowSpeed <= 0)) return null;

        Hazard h = new Hazard();
        h.conditionObservation = true;
        h.id = "tt-flow-" + routeName + "-" + Math.round(fraction * 100) + "-"
                + String.format(Locale.US, "%.3f,%.3f", lat, lon) + "-" + (nowMillis / 3_600_000L);
        h.lat = lat;
        h.lon = lon;
        h.fraction = fraction;
        h.source = "TomTom Traffic Flow API";
        h.sourceUrl = "https://docs.tomtom.com/traffic-api/documentation/tomtom-maps/v1/traffic-flow/flow-segment-data";
        h.postedAtMillis = nowMillis;
        h.inTravelTime = true; // TomTom Routing ETA already uses available live traffic.

        double ratio = freeFlowSpeed > 0 && currentSpeed >= 0
                ? currentSpeed / (double) freeFlowSpeed : 1.0;
        h.delayMinutes = (currentTime > 0 && freeFlowTime > 0)
                ? Math.max(0, (int) Math.round((currentTime - freeFlowTime) / 60.0)) : 0;
        String where = String.format(Locale.US, "about %.1f km from the route origin along %s",
                distanceKm * fraction, routeName);

        if (closed) {
            h.type = Hazard.Type.CLOSURE;
            h.title = "Possible road closure";
            h.detail = "TomTom Traffic Flow reports a road closure " + where
                    + ". Verify the closure before departure.";
            h.effect = Hazard.EFFECT_BLOCK;
            h.severity = 2;
        } else if (confidence < MIN_CONFIDENCE) {
            h.type = Hazard.Type.TRAFFIC;
            h.title = "Traffic reading has low confidence";
            h.detail = String.format(Locale.US,
                    "TomTom returned current speed %d km/h and free-flow speed %d km/h, but confidence is only %.0f%% near %s. Treat this observation as uncertain.",
                    currentSpeed, freeFlowSpeed, confidence * 100.0, where);
            h.effect = Hazard.EFFECT_NONE;
            h.severity = 1;
        } else if (ratio <= SEVERE_SPEED_RATIO) {
            h.type = Hazard.Type.TRAFFIC;
            h.title = "Severe traffic slowdown";
            h.detail = String.format(Locale.US,
                    "TomTom reports about %d km/h versus %d km/h free-flow speed (confidence %.0f%%) near %s. The route ETA already accounts for available live traffic.",
                    currentSpeed, freeFlowSpeed, confidence * 100.0, where);
            h.effect = Hazard.EFFECT_DELAY;
            h.severity = 2;
        } else if (ratio < SLOW_SPEED_RATIO) {
            h.type = Hazard.Type.TRAFFIC;
            h.title = "Slow traffic";
            h.detail = String.format(Locale.US,
                    "TomTom reports about %d km/h versus %d km/h free-flow speed (confidence %.0f%%) near %s. The route ETA already accounts for available live traffic.",
                    currentSpeed, freeFlowSpeed, confidence * 100.0, where);
            h.effect = h.delayMinutes >= 10 ? Hazard.EFFECT_DELAY : Hazard.EFFECT_NONE;
            h.severity = 1;
        } else {
            h.type = Hazard.Type.TRAFFIC;
            h.title = "Traffic flowing normally";
            h.detail = String.format(Locale.US,
                    "TomTom reports about %d km/h versus %d km/h free-flow speed (confidence %.0f%%) near %s. No significant slowdown was detected at this sample point.",
                    currentSpeed, freeFlowSpeed, confidence * 100.0, where);
            h.effect = Hazard.EFFECT_NONE;
            h.severity = 0;
        }
        return h;
    }
}
