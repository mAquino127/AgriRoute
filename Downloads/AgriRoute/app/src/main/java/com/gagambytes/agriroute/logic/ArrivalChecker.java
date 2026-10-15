package com.gagambytes.agriroute.logic;

/**
 * Arrival check from the proposal (section 4.1, item 4).
 *
 * ON_TIME   arrives before the deadline even at the longest usual travel time
 * RISKY     arrives on time only at the shortest usual time, or a delay / heavy rain on a
 *           hazard-prone section affects the route (we also use it for closed routes and for
 *           routes that miss the deadline even at the shortest time - see README "semantic notes")
 * CANT_TELL information is missing or contradicting
 */
public final class ArrivalChecker {

    public enum Label {
        ON_TIME("On time"), RISKY("Risky"), CANT_TELL("Can't tell");

        public final String text;

        Label(String text) { this.text = text; }

        /** Lower is better; used to sort route options. */
        public int rank() { return ordinal(); }

        public static Label fromName(String name) {
            for (Label l : values()) if (l.name().equals(name)) return l;
            return CANT_TELL;
        }
    }

    public static final class Verdict {
        public final Label label;
        public final String reason;
        public final long etaMinMillis;
        public final long etaMaxMillis;

        Verdict(Label label, String reason, long etaMinMillis, long etaMaxMillis) {
            this.label = label;
            this.reason = reason;
            this.etaMinMillis = etaMinMillis;
            this.etaMaxMillis = etaMaxMillis;
        }
    }

    private ArrivalChecker() { }

    /**
     * @param departMillis     when the truck leaves (planned or actual)
     * @param deadlineMillis   the buyer's deadline
     * @param minMillis        shortest usual travel time (already includes known delays)
     * @param maxMillis        longest usual travel time (already includes known delays)
     * @param blockedReason    non-null if an advisory says trucks cannot use this route
     * @param rainReason       non-null if heavy rain is forecast over a hazard-prone section
     * @param uncertainReason  non-null if information is missing or contradicting
     */
    public static Verdict evaluate(long departMillis, long deadlineMillis, long minMillis, long maxMillis,
                                   String blockedReason, String rainReason, String uncertainReason) {
        return evaluate(departMillis, deadlineMillis, minMillis, maxMillis,
                blockedReason, rainReason, null, uncertainReason);
    }

    public static Verdict evaluate(long departMillis, long deadlineMillis, long minMillis, long maxMillis,
                                   String blockedReason, String rainReason, String trafficReason,
                                   String uncertainReason) {
        long etaMin = departMillis + minMillis;
        long etaMax = departMillis + maxMillis;

        if (blockedReason != null) {
            return new Verdict(Label.RISKY, blockedReason, etaMin, etaMax);
        }
        if (uncertainReason != null) {
            return new Verdict(Label.CANT_TELL, uncertainReason, etaMin, etaMax);
        }
        if (etaMin > deadlineMillis) {
            return new Verdict(Label.RISKY,
                    "Arrives after the buyer's deadline even at the shortest usual travel time.", etaMin, etaMax);
        }
        if (rainReason != null) {
            return new Verdict(Label.RISKY, rainReason, etaMin, etaMax);
        }
        if (trafficReason != null) {
            return new Verdict(Label.RISKY, trafficReason, etaMin, etaMax);
        }
        if (etaMax > deadlineMillis) {
            return new Verdict(Label.RISKY,
                    "Arrives before the deadline only at the shortest usual travel time.", etaMin, etaMax);
        }
        return new Verdict(Label.ON_TIME,
                "No severe traffic or heavy rain risk was detected along the checked route, and the ETA range fits the buyer's deadline.", etaMin, etaMax);
    }
}
