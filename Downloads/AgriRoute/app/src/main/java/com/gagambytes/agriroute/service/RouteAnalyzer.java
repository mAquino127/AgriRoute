package com.gagambytes.agriroute.service;

import android.content.Context;

import com.gagambytes.agriroute.BuildConfig;
import com.gagambytes.agriroute.data.Places;
import com.gagambytes.agriroute.data.Store;
import com.gagambytes.agriroute.logic.ArrivalChecker;
import com.gagambytes.agriroute.logic.DepartureSuggester;
import com.gagambytes.agriroute.logic.GeoUtils;
import com.gagambytes.agriroute.logic.TimeFmt;
import com.gagambytes.agriroute.logic.TravelTimeEstimator;
import com.gagambytes.agriroute.model.Delivery;
import com.gagambytes.agriroute.model.Hazard;
import com.gagambytes.agriroute.model.RouteOption;
import com.gagambytes.agriroute.net.DemoRoutes;
import com.gagambytes.agriroute.net.OpenMeteoForecast;
import com.gagambytes.agriroute.net.RawRoute;
import com.gagambytes.agriroute.net.TomTomClient;
import com.gagambytes.agriroute.net.TomTomTrafficClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * FEATURE 1: Route comparison + arrival check.
 * Gets every route option, adds closures / traffic / hazard-prone sections / rain, then labels
 * each route On time, Risky or Can't tell and suggests a departure window.
 * Must be called from a background thread (it uses the network).
 */
public final class RouteAnalyzer {

    public static final class Result {
        public final List<RouteOption> routes = new ArrayList<>();
        /** True when sample data was used because TOMTOM_API_KEY is empty. */
        public boolean demo;
        /** True when no TomTom Traffic Flow sample could be loaded for any route. */
        public boolean trafficMissing;

        public RouteOption find(String routeName) {
            if (routeName == null) return null;
            for (RouteOption r : routes) if (routeName.equals(r.name)) return r;
            return null;
        }
    }

    private static final Map<String, Result> CACHE = new HashMap<>();

    private RouteAnalyzer() { }

    public static synchronized Result cached(String deliveryId) {
        return CACHE.get(deliveryId);
    }

    public static Result analyze(Context ctx, Delivery d, long nowMillis) throws IOException {
        Places.Place origin = Places.origin(d.originKey);
        Places.Place dest = Places.market(d.destKey);
        boolean demo = BuildConfig.TOMTOM_API_KEY.isEmpty();

        List<RawRoute> raws = demo
                ? DemoRoutes.build(origin, dest, nowMillis)
                : TomTomClient.fetchRoutes(origin, dest, nowMillis);
        if (!demo) TomTomTrafficClient.enrich(raws, nowMillis);

        OpenMeteoForecast forecast = new OpenMeteoForecast();
        long depart = d.effectiveDeparture();

        Result result = new Result();
        result.demo = demo;
        result.trafficMissing = !demo;
        for (RawRoute raw : raws) {
            for (Hazard h : raw.hazards) {
                if (h.conditionObservation && "TomTom Traffic Flow API".equals(h.source)) {
                    result.trafficMissing = false;
                    break;
                }
            }
            if (!result.trafficMissing) break;
        }

        for (RawRoute raw : raws) {
            RouteOption r = new RouteOption();
            r.name = raw.name;
            r.via = raw.via;
            r.distanceKm = raw.distanceKm;
            r.points = raw.points;
            r.trafficMissing = true;
            for (Hazard h : raw.hazards) {
                if (h.conditionObservation && "TomTom Traffic Flow API".equals(h.source)) {
                    r.trafficMissing = false;
                    break;
                }
            }
            double[] cum = GeoUtils.cumulativeKm(raw.points);

            // Only provider-derived traffic and Open-Meteo forecast conditions are shown.
            // No hand-authored NOAH/DPWH sample advisories are injected into the route.
            List<Hazard> hs = new ArrayList<>(raw.hazards);

            // Sample several points along the actual route so the map can show conditions
            // along the journey, not only exceptional advisories at the midpoint.
            List<DepartureSuggester.Zone> zones = new ArrayList<>();
            List<double[]> probe = new ArrayList<>();
            for (Hazard h : hs) {
                if (h.proneZone) {
                    zones.add(new DepartureSuggester.Zone(h.title, h.lat, h.lon, h.fraction));
                    probe.add(new double[]{h.lat, h.lon});
                }
            }
            double[] weatherFractions = new double[]{0.25, 0.55, 0.85};
            for (double fraction : weatherFractions) {
                probe.add(GeoUtils.pointAtFraction(raw.points, cum, fraction));
            }
            boolean forecastOk = !demo && forecast.prefetch(probe);
            r.weatherMissing = !demo && !forecastOk;

            // Travel time range, plus delays the routing provider did not already include.
            long[] range = TravelTimeEstimator.range(raw.trafficSeconds, raw.noTrafficSeconds);
            long extraMs = 0;
            String blocked = null;
            String trafficRisk = null;
            String uncertain = null;
            for (Hazard h : hs) {
                if (Hazard.EFFECT_BLOCK.equals(h.effect) && blocked == null) {
                    blocked = h.title + " (" + h.source + ").";
                }
                if (Hazard.EFFECT_CONFLICT.equals(h.effect) && uncertain == null) {
                    uncertain = "Sources disagree: " + h.title + ".";
                }
                if (trafficRisk == null && h.type == Hazard.Type.TRAFFIC
                        && Hazard.EFFECT_DELAY.equals(h.effect)
                        && (h.delayMinutes >= 10 || h.title.toLowerCase(Locale.US).contains("severe traffic"))) {
                    trafficRisk = h.title + (h.detail == null || h.detail.isEmpty() ? "." : ": " + h.detail);
                }
                if (Hazard.EFFECT_DELAY.equals(h.effect) && !h.inTravelTime) {
                    extraMs += h.delayMinutes * 60_000L;
                }
            }
            long minMs = range[0] + extraMs;
            long maxMs = range[1] + extraMs;
            r.minMillis = minMs;
            r.maxMillis = maxMs;

            if (uncertain == null && !demo && !forecastOk) {
                uncertain = "Open-Meteo forecast could not be loaded, so weather risk cannot be confirmed.";
            }

            // Add a weather observation marker at each sample point, even when the forecast is
            // dry. Heavy rain is also passed into route-risk evaluation; ordinary weather is not
            // labelled as a risk merely to make a marker visible.
            String rainReason = null;
            for (double fraction : weatherFractions) {
                double[] point = GeoUtils.pointAtFraction(raw.points, cum, fraction);
                // Use the scheduled departure when it is in the future; if the delivery's
                // planned time is already past, use now so a forecast marker doesn't silently
                // disappear because Open-Meteo's forecast has no data for a past hour.
                long forecastStartMillis = Math.max(depart, nowMillis);
                long weatherAtMillis = forecastStartMillis + Math.max(0,
                        Math.round((minMs + (maxMs - minMs) / 2.0) * fraction));
                double precipitation = forecast.mmPerHour(point[0], point[1], weatherAtMillis);
                if (Double.isNaN(precipitation)) continue;

                Hazard weather = new Hazard();
                weather.type = Hazard.Type.RAIN;
                weather.conditionObservation = true;
                weather.id = "open-meteo-weather-" + raw.name + "-" + Math.round(fraction * 100)
                        + "-" + (nowMillis / 3_600_000L);
                weather.source = "Open-Meteo forecast";
                weather.sourceUrl = "https://open-meteo.com/";
                weather.postedAtMillis = nowMillis;
                weather.lat = point[0];
                weather.lon = point[1];
                weather.fraction = fraction;

                if (precipitation >= 5.0) {
                    weather.title = "Heavy rain forecast";
                    weather.detail = String.format(Locale.US,
                            "Open-Meteo forecasts %.1f mm of precipitation in the hour around %s, about %.1f km from the origin along %s. Heavy rain may reduce visibility and road grip.",
                            precipitation, TimeFmt.hm(weatherAtMillis), raw.distanceKm * fraction, raw.name);
                    weather.effect = Hazard.EFFECT_RAIN;
                    weather.severity = 2;
                    if (rainReason == null) {
                        rainReason = String.format(Locale.US,
                                "Open-Meteo forecasts heavy rain (%.1f mm in the forecast hour) around %s, about %.1f km into %s.",
                                precipitation, TimeFmt.hm(weatherAtMillis), raw.distanceKm * fraction, raw.name);
                    }
                } else if (precipitation > 0.1) {
                    weather.title = precipitation >= 1.0 ? "Rain forecast" : "Light rain possible";
                    weather.detail = String.format(Locale.US,
                            "Open-Meteo forecasts %.1f mm of precipitation in the hour around %s, about %.1f km from the origin along %s.",
                            precipitation, TimeFmt.hm(weatherAtMillis), raw.distanceKm * fraction, raw.name);
                    weather.effect = Hazard.EFFECT_NONE;
                    weather.severity = 1;
                } else {
                    weather.title = "Little or no rain forecast";
                    weather.detail = String.format(Locale.US,
                            "Open-Meteo forecasts %.1f mm of precipitation in the hour around %s, about %.1f km from the origin along %s.",
                            precipitation, TimeFmt.hm(weatherAtMillis), raw.distanceKm * fraction, raw.name);
                    weather.effect = Hazard.EFFECT_NONE;
                    weather.severity = 0;
                }
                hs.add(weather);
            }

            // Retain zone-timing logic for any future verified hazard zones.
            DepartureSuggester.Conflict c = DepartureSuggester.findConflict(depart, minMs, maxMs, zones, forecast);
            if (c != null && rainReason == null) {
                Hazard rain = new Hazard();
                rain.type = Hazard.Type.RAIN;
                rain.id = "rain-" + c.zone.name + "-" + (c.atMillis / (3 * 3_600_000L));
                rain.title = "Heavy rain warning ahead";
                rain.detail = String.format(Locale.US, "%.0f mm/hr forecast around %s over %s.",
                        c.mmPerHour, TimeFmt.hm(c.atMillis), c.zone.name);
                rain.source = "Open-Meteo forecast";
                rain.sourceUrl = "https://open-meteo.com/";
                rain.postedAtMillis = nowMillis;
                rain.lat = c.zone.lat;
                rain.lon = c.zone.lon;
                rain.fraction = c.zone.fraction;
                rain.effect = Hazard.EFFECT_RAIN;
                hs.add(rain);
                rainReason = String.format(Locale.US,
                        "Heavy rain (%.0f mm/hr) is forecast around %s over a hazard-prone section: %s.",
                        c.mmPerHour, TimeFmt.hm(c.atMillis), c.zone.name);
            }

            ArrivalChecker.Verdict v = ArrivalChecker.evaluate(depart, d.deadlineMillis, minMs, maxMs,
                    blocked, rainReason, trafficRisk, uncertain);
            r.label = v.label;
            r.reason = v.reason;
            r.etaMinMillis = v.etaMinMillis;
            r.etaMaxMillis = v.etaMaxMillis;

            DepartureSuggester.Window w = DepartureSuggester.suggest(nowMillis, d.deadlineMillis, minMs, maxMs, zones, forecast);
            r.departStartMillis = w.startMillis;
            r.departEndMillis = w.endMillis;
            r.departReason = w.reason;
            r.departAvoidsRain = w.avoidsRain;

            Collections.sort(hs, new Comparator<Hazard>() {
                @Override
                public int compare(Hazard a, Hazard b) {
                    return Double.compare(a.fraction, b.fraction);
                }
            });
            r.hazards = hs;
            result.routes.add(r);
        }

        Collections.sort(result.routes, new Comparator<RouteOption>() {
            @Override
            public int compare(RouteOption a, RouteOption b) {
                int byLabel = Integer.compare(a.label.rank(), b.label.rank());
                return byLabel != 0 ? byLabel : Long.compare(a.minMillis, b.minMillis);
            }
        });

        synchronized (RouteAnalyzer.class) {
            CACHE.put(d.id, result);
        }
        return result;
    }

}
