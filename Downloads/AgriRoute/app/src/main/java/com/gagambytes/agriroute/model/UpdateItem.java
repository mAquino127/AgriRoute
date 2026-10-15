package com.gagambytes.agriroute.model;

/** One new hazard or risk-status change detected on a chosen route. */
public class UpdateItem {
    public String id;
    public String deliveryId;
    public String routeName = "";
    public String type = "TRAFFIC";   // Hazard.Type name
    public String title = "";
    public String detail = "";
    public String source = "";
    public String sourceUrl = "";
    public long postedAtMillis;
    public long createdAtMillis;
    /** e.g. "Arrival now risky - ETA 21:10-23:20, deadline 22:30" or "Still on time". */
    public String arrivalNote = "";
    public boolean arrivalRisky;
    public boolean read;
}
