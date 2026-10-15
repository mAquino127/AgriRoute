package com.gagambytes.agriroute.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Origin trading centers and destination markets from the proposal (section 1.1).
 * Coordinates are APPROXIMATE city/market locations. Check them against a map before the demo.
 */
public final class Places {

    public static final class Place {
        public final String key;
        public final String name;
        /** Compact name shown in route summaries. */
        public final String shortName;
        public final double lat;
        public final double lon;

        Place(String key, String name, String shortName, double lat, double lon) {
            this.key = key;
            this.name = name;
            this.shortName = shortName;
            this.lat = lat;
            this.lon = lon;
        }

        @Override
        public String toString() { return name; }
    }

    public static final List<Place> ORIGINS;
    public static final List<Place> MARKETS;

    static {
        List<Place> o = new ArrayList<>();
        o.add(new Place("baptc", "BAPTC (Benguet Agri-Pinoy Trading Center)", "BAPTC", 16.4500, 120.5900));
        o.add(new Place("lt_post", "La Trinidad Vegetable Trading Post", "LT Trading Post", 16.4569, 120.5918));
        ORIGINS = Collections.unmodifiableList(o);

        List<Place> m = new ArrayList<>();
        m.add(new Place("divisoria", "Divisoria, Manila", "Divisoria", 14.6010, 120.9715));
        m.add(new Place("balintawak", "Balintawak, Quezon City", "Balintawak", 14.6572, 121.0043));
        m.add(new Place("urdaneta", "Urdaneta, Pangasinan", "Urdaneta", 15.9761, 120.5711));
        m.add(new Place("batangas", "Batangas City", "Batangas", 13.7565, 121.0583));
        m.add(new Place("tacloban", "Tacloban City", "Tacloban", 11.2543, 124.9632));
        MARKETS = Collections.unmodifiableList(m);
    }

    private Places() { }

    public static Place origin(String key) {
        for (Place p : ORIGINS) if (p.key.equals(key)) return p;
        return ORIGINS.get(0);
    }

    public static Place market(String key) {
        for (Place p : MARKETS) if (p.key.equals(key)) return p;
        return MARKETS.get(0);
    }

    public static final String[] TRUCKS = {
            "10-wheeler", "Hino 500", "Isuzu Forward", "Fuso Fighter", "Elf / 6-wheeler", "Other"
    };
}
