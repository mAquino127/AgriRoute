package com.gagambytes.agriroute.model;

/**
 * Something along a route that the trader/driver should know about:
 * a closure, a traffic incident, road works, a landslide/flood-prone section or a rain warning.
 */
public class Hazard {

    public enum Type { CLOSURE, TRAFFIC, WORKS, LANDSLIDE, FLOOD, RAIN }

    /** How the hazard changes the trip. */
    public static final String EFFECT_NONE = "none";       // informational (e.g. hazard-prone section)
    public static final String EFFECT_DELAY = "delay";     // adds delayMinutes
    public static final String EFFECT_BLOCK = "block";     // route not usable for trucks
    public static final String EFFECT_CONFLICT = "conflict"; // sources disagree -> "Can't tell"
    public static final String EFFECT_RAIN = "rain";       // heavy rain over a hazard-prone section

    public String id;
    public Type type = Type.TRAFFIC;
    public String title = "";
    public String detail = "";
    public String source = "";
    public String sourceUrl = "";
    public long postedAtMillis;
    public double lat;
    public double lon;
    /** Position along the route, 0.0 (origin) to 1.0 (destination). */
    public double fraction;
    public int delayMinutes;
    public String effect = EFFECT_NONE;
    /** True for landslide/flood-prone sections: rain over these makes a route risky. */
    public boolean proneZone;
    /** True when the routing provider's travel time already includes this delay (avoid double counting). */
    public boolean inTravelTime;
    /** True for an API observation marker, including normal conditions that are not risk alerts. */
    public boolean conditionObservation;
    /** 0 = normal/low, 1 = caution, 2 = high risk. Used to color API condition markers. */
    public int severity = 1;

    public String shortText() {
        return title;
    }

    /** Hazards worth recording as a route update (not static prone-zones). */
    public boolean isUpdateWorthy() {
        return !proneZone && !EFFECT_NONE.equals(effect);
    }
}
