package com.gagambytes.agriroute.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.gagambytes.agriroute.model.Delivery;
import com.gagambytes.agriroute.model.UpdateItem;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Local storage on the phone (SharedPreferences + JSON). Enough for a class project; the planned
 * Supabase backend would replace this class.
 */
public final class Store {

    private static final String PREFS = "agriroute_store";
    private static final String K_DELIVERIES = "deliveries";
    private static final String K_UPDATES = "updates";
    private static final String K_COUNTER = "delivery_counter";
    private static final String K_NAME = "trader_name";

    private static Store instance;

    private final SharedPreferences sp;

    private Store(Context ctx) {
        sp = ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static synchronized Store get(Context ctx) {
        if (instance == null) instance = new Store(ctx);
        return instance;
    }

    // ------------------------------------------------------------------ trader

    public synchronized String traderName() { return sp.getString(K_NAME, "Trader"); }

    public synchronized void setTraderName(String name) {
        sp.edit().putString(K_NAME, name == null || name.trim().isEmpty() ? "Trader" : name.trim()).apply();
    }

    // -------------------------------------------------------------- deliveries

    public synchronized String nextDeliveryId() {
        int n = sp.getInt(K_COUNTER, 1000) + 1;
        sp.edit().putInt(K_COUNTER, n).apply();
        return "AR-" + n;
    }

    public synchronized List<Delivery> deliveries() {
        List<Delivery> out = new ArrayList<>();
        JSONArray arr = readArray(K_DELIVERIES);
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o != null) out.add(decodeDelivery(o));
        }
        return out;
    }

    public synchronized Delivery delivery(String id) {
        if (id == null) return null;
        for (Delivery d : deliveries()) if (id.equals(d.id)) return d;
        return null;
    }

    public synchronized void saveDelivery(Delivery d) {
        JSONArray arr = readArray(K_DELIVERIES);
        JSONArray next = new JSONArray();
        boolean replaced = false;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            if (d.id.equals(o.optString("id"))) {
                next.put(encodeDelivery(d));
                replaced = true;
            } else {
                next.put(o);
            }
        }
        if (!replaced) next.put(encodeDelivery(d));
        sp.edit().putString(K_DELIVERIES, next.toString()).apply();
    }

    public synchronized void deleteDelivery(String id) {
        JSONArray arr = readArray(K_DELIVERIES);
        JSONArray next = new JSONArray();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o != null && !id.equals(o.optString("id"))) next.put(o);
        }
        sp.edit().putString(K_DELIVERIES, next.toString()).apply();
    }

    // ----------------------------------------------------------------- updates

    /** Newest first. */
    public synchronized List<UpdateItem> updates() {
        List<UpdateItem> out = new ArrayList<>();
        JSONArray arr = readArray(K_UPDATES);
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o != null) {
                UpdateItem item = decodeUpdate(o);
                String legacyText = ((item.source == null ? "" : item.source) + " "
                        + (item.title == null ? "" : item.title) + " "
                        + (item.detail == null ? "" : item.detail)).toLowerCase(java.util.Locale.US);
                if (legacyText.contains("dpwh") || legacyText.contains("project noah")
                        || legacyText.contains("sample advisory") || legacyText.contains("(demo)")) continue;
                out.add(item);
            }
        }
        Collections.sort(out, new Comparator<UpdateItem>() {
            @Override
            public int compare(UpdateItem a, UpdateItem b) {
                return Long.compare(b.createdAtMillis, a.createdAtMillis);
            }
        });
        return out;
    }

    public synchronized void addUpdates(List<UpdateItem> items) {
        if (items.isEmpty()) return;
        JSONArray arr = readArray(K_UPDATES);
        for (UpdateItem u : items) arr.put(encodeUpdate(u));
        sp.edit().putString(K_UPDATES, arr.toString()).apply();
    }

    public synchronized void markUpdatesRead() {
        JSONArray arr = readArray(K_UPDATES);
        JSONArray next = new JSONArray();
        try {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                o.put("read", true);
                next.put(o);
            }
        } catch (JSONException ignored) {
            return;
        }
        sp.edit().putString(K_UPDATES, next.toString()).apply();
    }

    public synchronized int unreadCount() {
        int n = 0;
        for (UpdateItem u : updates()) if (!u.read) n++;
        return n;
    }

    // ------------------------------------------------------------------ codecs

    private JSONArray readArray(String key) {
        try {
            return new JSONArray(sp.getString(key, "[]"));
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    private static JSONObject encodeDelivery(Delivery d) {
        JSONObject o = new JSONObject();
        try {
            o.put("id", d.id);
            o.put("originKey", d.originKey);
            o.put("destKey", d.destKey);
            o.put("produce", d.produce);
            o.put("loadKg", d.loadKg);
            o.put("deadline", d.deadlineMillis);
            o.put("planned", d.plannedDepartureMillis);
            o.put("truck", d.truck);
            o.put("plate", d.plate);
            o.put("driverName", d.driverName);
            o.put("buyerName", d.buyerName);
            o.put("buyerPhone", d.buyerPhone);
            o.put("status", d.status);
            o.put("departed", d.departedMillis);
            o.put("suggestedRoute", d.suggestedRoute);
            o.put("suggestedLeaveBy", d.suggestedLeaveByMillis);
            o.put("chosenRoute", d.chosenRouteName);
            o.put("chosenVia", d.chosenVia);
            o.put("chosenLabel", d.chosenLabel);
            o.put("etaMin", d.etaMinMillis);
            o.put("etaMax", d.etaMaxMillis);
            o.put("departStart", d.departStartMillis);
            o.put("departEnd", d.departEndMillis);
            JSONArray known = new JSONArray();
            for (String k : d.knownHazardIds) known.put(k);
            o.put("known", known);
        } catch (JSONException ignored) {
            // keys are constants; cannot happen
        }
        return o;
    }

    private static Delivery decodeDelivery(JSONObject o) {
        Delivery d = new Delivery();
        d.id = o.optString("id");
        d.originKey = o.optString("originKey");
        d.destKey = o.optString("destKey");
        d.produce = o.optString("produce");
        d.loadKg = o.optInt("loadKg");
        d.deadlineMillis = o.optLong("deadline");
        d.plannedDepartureMillis = o.optLong("planned");
        d.truck = o.optString("truck");
        d.plate = o.optString("plate");
        d.driverName = o.optString("driverName");
        d.buyerName = o.optString("buyerName");
        d.buyerPhone = o.optString("buyerPhone");
        d.status = o.optString("status", Delivery.DRAFT);
        d.departedMillis = o.optLong("departed");
        d.suggestedRoute = o.optString("suggestedRoute");
        d.suggestedLeaveByMillis = o.optLong("suggestedLeaveBy");
        d.chosenRouteName = o.optString("chosenRoute");
        d.chosenVia = o.optString("chosenVia");
        d.chosenLabel = o.optString("chosenLabel");
        d.etaMinMillis = o.optLong("etaMin");
        d.etaMaxMillis = o.optLong("etaMax");
        d.departStartMillis = o.optLong("departStart");
        d.departEndMillis = o.optLong("departEnd");
        JSONArray known = o.optJSONArray("known");
        if (known != null) {
            for (int i = 0; i < known.length(); i++) d.knownHazardIds.add(known.optString(i));
        }
        return d;
    }

    private static JSONObject encodeUpdate(UpdateItem u) {
        JSONObject o = new JSONObject();
        try {
            o.put("id", u.id == null ? UUID.randomUUID().toString() : u.id);
            o.put("deliveryId", u.deliveryId);
            o.put("routeName", u.routeName);
            o.put("type", u.type);
            o.put("title", u.title);
            o.put("detail", u.detail);
            o.put("source", u.source);
            o.put("sourceUrl", u.sourceUrl);
            o.put("posted", u.postedAtMillis);
            o.put("created", u.createdAtMillis);
            o.put("arrivalNote", u.arrivalNote);
            o.put("arrivalRisky", u.arrivalRisky);
            o.put("read", u.read);
        } catch (JSONException ignored) {
            // constant keys
        }
        return o;
    }

    private static UpdateItem decodeUpdate(JSONObject o) {
        UpdateItem u = new UpdateItem();
        u.id = o.optString("id");
        u.deliveryId = o.optString("deliveryId");
        u.routeName = o.optString("routeName");
        u.type = o.optString("type", "TRAFFIC");
        u.title = o.optString("title");
        u.detail = o.optString("detail");
        u.source = o.optString("source");
        u.sourceUrl = o.optString("sourceUrl");
        u.postedAtMillis = o.optLong("posted");
        u.createdAtMillis = o.optLong("created");
        u.arrivalNote = o.optString("arrivalNote");
        u.arrivalRisky = o.optBoolean("arrivalRisky");
        u.read = o.optBoolean("read");
        return u;
    }
}
