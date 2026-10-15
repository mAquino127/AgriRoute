package com.gagambytes.agriroute.model;

import com.gagambytes.agriroute.logic.ArrivalChecker;

import java.util.ArrayList;
import java.util.List;

/** One analysed way to get from the origin trading center to the destination market. */
public class RouteOption {
    public String name = "";
    /** Main places the route passes, e.g. "Tuba, Rosario, TPLEX, NLEX". */
    public String via = "";
    public double distanceKm;
    public long minMillis;
    public long maxMillis;
    /** Route geometry as {lat, lon} pairs. */
    public List<double[]> points = new ArrayList<>();
    public List<Hazard> hazards = new ArrayList<>();

    public ArrivalChecker.Label label = ArrivalChecker.Label.CANT_TELL;
    public String reason = "";
    public long etaMinMillis;
    public long etaMaxMillis;

    public long departStartMillis;
    public long departEndMillis;
    public String departReason = "";
    public boolean departAvoidsRain = true;
    public boolean weatherMissing;
    /** True when Traffic Flow did not return valid observations for this candidate route. */
    public boolean trafficMissing = true;
}
