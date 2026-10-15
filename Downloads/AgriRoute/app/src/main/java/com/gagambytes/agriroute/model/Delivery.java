package com.gagambytes.agriroute.model;

import java.util.ArrayList;
import java.util.List;

/** A produce delivery created by the trader. */
public class Delivery {

    public static final String DRAFT = "DRAFT";         // created, comparing routes
    public static final String SCHEDULED = "SCHEDULED"; // route chosen, not yet departed
    public static final String ON_ROAD = "ON_ROAD";
    public static final String DONE = "DONE";

    public String id;
    public String originKey;
    public String destKey;
    public String produce = "";
    public int loadKg;
    public long deadlineMillis;
    public long plannedDepartureMillis;
    public String truck = "";
    public String plate = "";
    public String driverName = "";
    public String buyerName = "";
    public String buyerPhone = "";
    public String status = DRAFT;

    /** Actual departure time once the trader taps "Mark as departed" (0 = not yet). */
    public long departedMillis;

    // Result of the last route comparison (shown on the Deliveries screen before a route is chosen)
    public String suggestedRoute = "";
    public long suggestedLeaveByMillis;

    // The chosen route
    public String chosenRouteName = "";
    public String chosenVia = "";
    public String chosenLabel = "";
    public long etaMinMillis;
    public long etaMaxMillis;
    public long departStartMillis;
    public long departEndMillis;
    /** Hazard ids already seen for this delivery, so updates only show newly detected items. */
    public List<String> knownHazardIds = new ArrayList<>();

    public long effectiveDeparture() {
        return departedMillis > 0 ? departedMillis : plannedDepartureMillis;
    }

    public boolean hasChosenRoute() {
        return chosenRouteName != null && !chosenRouteName.isEmpty();
    }

    public boolean isMonitored() {
        return hasChosenRoute() && (SCHEDULED.equals(status) || ON_ROAD.equals(status));
    }

    public String loadLabel() {
        double tonnes = loadKg / 1000.0;
        String t = (tonnes == Math.floor(tonnes)) ? String.valueOf((int) tonnes) : String.format(java.util.Locale.US, "%.1f", tonnes);
        return t + " t " + produce;
    }
}
