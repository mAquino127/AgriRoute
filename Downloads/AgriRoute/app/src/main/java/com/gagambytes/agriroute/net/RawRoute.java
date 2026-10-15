package com.gagambytes.agriroute.net;

import com.gagambytes.agriroute.model.Hazard;

import java.util.ArrayList;
import java.util.List;

/** A route straight from the routing provider (or the demo generator), before analysis. */
public class RawRoute {
    public String name = "";
    public String via = "";
    public double distanceKm;
    public long trafficSeconds;
    public long noTrafficSeconds;
    public List<double[]> points = new ArrayList<>();
    /** Hazards the provider itself already knows about (live traffic, or demo hazards). */
    public List<Hazard> hazards = new ArrayList<>();
}
