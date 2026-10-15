package com.gagambytes.agriroute.service;

import android.content.Context;

import com.gagambytes.agriroute.data.Store;
import com.gagambytes.agriroute.logic.ArrivalChecker;
import com.gagambytes.agriroute.logic.TimeFmt;
import com.gagambytes.agriroute.model.Delivery;
import com.gagambytes.agriroute.model.Hazard;
import com.gagambytes.agriroute.model.RouteOption;
import com.gagambytes.agriroute.model.UpdateItem;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;
import java.util.Locale;
import java.util.UUID;

/** Re-checks selected routes and records new API-derived traffic/weather conditions and risks. */
public final class UpdateChecker {

    public static final class Summary {
        public int newUpdates;
        public int deliveriesChecked;
        public final List<String> errors = new ArrayList<>();
    }

    private UpdateChecker() { }

    public static Summary checkAll(Context ctx) {
        Summary summary = new Summary();
        Store store = Store.get(ctx);
        long now = System.currentTimeMillis();

        for (Delivery d : store.deliveries()) {
            if (!d.isMonitored()) continue;
            summary.deliveriesChecked++;
            pruneOldObservationIds(d, now);
            try {
                RouteAnalyzer.Result res = RouteAnalyzer.analyze(ctx, d, now);
                RouteOption r = res.find(d.chosenRouteName);
                if (r == null) {
                    summary.errors.add(d.id + ": chosen route " + d.chosenRouteName + " is not currently returned by TomTom.");
                    continue;
                }

                String previousLabel = d.chosenLabel;
                d.chosenLabel = r.label.name();
                d.etaMinMillis = r.etaMinMillis;
                d.etaMaxMillis = r.etaMaxMillis;

                List<UpdateItem> items = new ArrayList<>();
                for (Hazard h : r.hazards) {
                    // Record routine API observations too (for example, normal traffic or dry
                    // weather), not only hazards. Observation IDs are hour-bucketed so a refresh
                    // doesn't duplicate the same reading repeatedly within the same hour.
                    if ((!h.isUpdateWorthy() && !h.conditionObservation)
                            || d.knownHazardIds.contains(h.id)) continue;
                    items.add(makeItem(d, r, h.type.name(), h.title, h.detail, h.source,
                            h.sourceUrl, h.postedAtMillis, now));
                    d.knownHazardIds.add(h.id);
                }

                // Also log a risk-status change, including a transition back to a lower-risk status.
                if (previousLabel != null && !previousLabel.isEmpty() && !previousLabel.equals(d.chosenLabel)) {
                    String title = "Route status changed to " + r.label.text;
                    String kind = r.reason != null && r.reason.toLowerCase(Locale.US).contains("rain")
                            ? Hazard.Type.RAIN.name() : Hazard.Type.TRAFFIC.name();
                    items.add(makeItem(d, r, kind, title, r.reason, "AgriRoute route analysis",
                            "", 0, now));
                }

                if (!items.isEmpty()) {
                    store.addUpdates(items);
                    summary.newUpdates += items.size();
                }
                store.saveDelivery(d);
            } catch (IOException e) {
                summary.errors.add(d.id + ": " + e.getMessage());
            }
        }
        return summary;
    }


    /** Keep hourly observation IDs for 48 hours so the local delivery record stays bounded. */
    private static void pruneOldObservationIds(Delivery d, long now) {
        if (d.knownHazardIds == null || d.knownHazardIds.isEmpty()) return;
        long cutoffHour = now / 3_600_000L - 48;
        Iterator<String> iterator = d.knownHazardIds.iterator();
        while (iterator.hasNext()) {
            String id = iterator.next();
            if (id == null) {
                iterator.remove();
                continue;
            }
            int sep = id.lastIndexOf('-');
            if (sep < 0 || sep == id.length() - 1) continue;
            try {
                long hour = Long.parseLong(id.substring(sep + 1));
                if (hour < cutoffHour) iterator.remove();
            } catch (NumberFormatException ignored) {
                // Preserve legacy IDs whose final component wasn't an epoch hour.
            }
        }
    }

    private static UpdateItem makeItem(Delivery d, RouteOption r, String type, String title,
                                       String detail, String source, String sourceUrl,
                                       long postedAt, long now) {
        UpdateItem u = new UpdateItem();
        u.id = UUID.randomUUID().toString();
        u.deliveryId = d.id;
        u.routeName = r.name;
        u.type = type;
        u.title = title == null ? "Route update" : title;
        u.detail = detail == null ? "" : detail;
        u.source = source == null ? "" : source;
        u.sourceUrl = sourceUrl == null ? "" : sourceUrl;
        u.postedAtMillis = postedAt;
        u.createdAtMillis = now;
        u.arrivalNote = arrivalNote(r, d);
        u.arrivalRisky = r.label != ArrivalChecker.Label.ON_TIME;
        return u;
    }

    public static String arrivalNote(RouteOption r, Delivery d) {
        switch (r.label) {
            case ON_TIME:
                return "Lower risk · ETA " + TimeFmt.hm(r.etaMinMillis) + "–" + TimeFmt.hm(r.etaMaxMillis);
            case RISKY:
                return "Risky · ETA " + TimeFmt.hm(r.etaMinMillis) + "–" + TimeFmt.hm(r.etaMaxMillis)
                        + ", deadline " + TimeFmt.hm(d.deadlineMillis);
            default:
                return "Risk can't be confirmed with the available data";
        }
    }
}
