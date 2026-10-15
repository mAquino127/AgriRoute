package com.gagambytes.agriroute.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.gagambytes.agriroute.R;
import com.gagambytes.agriroute.data.Places;
import com.gagambytes.agriroute.data.Store;
import com.gagambytes.agriroute.logic.ArrivalChecker;
import com.gagambytes.agriroute.logic.TimeFmt;
import com.gagambytes.agriroute.model.Delivery;
import com.gagambytes.agriroute.model.Hazard;
import com.gagambytes.agriroute.model.RouteOption;
import com.gagambytes.agriroute.service.Async;
import com.gagambytes.agriroute.service.RouteAnalyzer;
import com.google.android.material.button.MaterialButton;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Tab 2 (FEATURE 1): compare every route, see On time / Risky / Can't tell, and pick one. */
public class RoutesFragment extends Fragment {

    private View header;
    private TextView hdrTop, hdrTitle, hdrSub, changeDelivery;
    private View empty, loading, error, scroll;
    private TextView errorText, demoBanner;
    private RouteMapView map;
    private LinearLayout tabRow, hazardList, departBox;
    private TextView labelPill, chosenPill, routeName, routeVia, routeReason;
    private TextView statTime, statArrives, statDistance, departTitle, departReason;
    private MaterialButton btnChoose;

    private Delivery delivery;
    private RouteAnalyzer.Result result;
    private int selected;
    private int requestToken;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        View v = inflater.inflate(R.layout.fragment_routes, container, false);
        header = v.findViewById(R.id.routesHeader);
        hdrTop = v.findViewById(R.id.hdrTop);
        hdrTitle = v.findViewById(R.id.hdrTitle);
        hdrSub = v.findViewById(R.id.hdrSub);
        changeDelivery = v.findViewById(R.id.changeDelivery);
        empty = v.findViewById(R.id.routesEmpty);
        loading = v.findViewById(R.id.routesLoading);
        error = v.findViewById(R.id.routesError);
        errorText = v.findViewById(R.id.routesErrorText);
        scroll = v.findViewById(R.id.routesScroll);
        demoBanner = v.findViewById(R.id.routesDemoBanner);
        map = v.findViewById(R.id.mapView);
        tabRow = v.findViewById(R.id.tabRow);
        hazardList = v.findViewById(R.id.hazardList);
        departBox = v.findViewById(R.id.departBox);
        labelPill = v.findViewById(R.id.labelPill);
        chosenPill = v.findViewById(R.id.chosenPill);
        routeName = v.findViewById(R.id.routeName);
        routeVia = v.findViewById(R.id.routeVia);
        routeReason = v.findViewById(R.id.routeReason);
        statTime = v.findViewById(R.id.statTime);
        statArrives = v.findViewById(R.id.statArrives);
        statDistance = v.findViewById(R.id.statDistance);
        departTitle = v.findViewById(R.id.departTitle);
        departReason = v.findViewById(R.id.departReason);
        btnChoose = v.findViewById(R.id.btnChoose);

        map.setOnHazardTapListener(h -> Ui.showHazardDialog(requireContext(), h));
        changeDelivery.setOnClickListener(b -> pickDelivery());
        v.findViewById(R.id.routesRetry).setOnClickListener(b -> load());
        btnChoose.setOnClickListener(b -> onChoose());
        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        MainActivity main = (MainActivity) requireActivity();
        Store store = Store.get(requireContext());
        String id = main.getRoutesDeliveryId();
        Delivery d = store.delivery(id);
        if (d == null) {
            // default to the first delivery that still needs a route, then any active one
            for (Delivery x : store.deliveries()) {
                if (Delivery.DRAFT.equals(x.status)) { d = x; break; }
            }
            if (d == null) {
                for (Delivery x : store.deliveries()) {
                    if (!Delivery.DONE.equals(x.status)) { d = x; break; }
                }
            }
            if (d != null) main.setRoutesDeliveryId(d.id);
        }
        delivery = d;
        if (delivery == null) {
            showOnly(empty);
            header.setVisibility(View.GONE);
            btnChoose.setVisibility(View.GONE);
            return;
        }
        header.setVisibility(View.VISIBLE);
        bindHeader();
        load();
    }

    @Override
    public void onDestroyView() {
        requestToken++;
        super.onDestroyView();
    }

    private void bindHeader() {
        Places.Place o = Places.origin(delivery.originKey);
        Places.Place m = Places.market(delivery.destKey);
        String truckLabel = delivery.truck == null || delivery.truck.trim().isEmpty()
                ? "Truck not specified" : delivery.truck.trim();
        hdrTop.setText("🚚 " + truckLabel + " · " + delivery.loadLabel());
        hdrTitle.setText(o.shortName + " → " + m.shortName);
        hdrSub.setText("Deadline " + TimeFmt.dayClock12(delivery.deadlineMillis)
                + " · Departs " + TimeFmt.dayClock12(delivery.effectiveDeparture()));
    }

    private void pickDelivery() {
        final List<Delivery> all = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (Delivery d : Store.get(requireContext()).deliveries()) {
            if (Delivery.DONE.equals(d.status)) continue;
            all.add(d);
            names.add(d.id + "  " + Places.origin(d.originKey).shortName + " → " + Places.market(d.destKey).shortName);
        }
        if (all.isEmpty()) return;
        new AlertDialog.Builder(requireContext())
                .setTitle("Choose a delivery")
                .setItems(names.toArray(new String[0]), (dlg, which) -> {
                    delivery = all.get(which);
                    ((MainActivity) requireActivity()).setRoutesDeliveryId(delivery.id);
                    bindHeader();
                    load();
                })
                .show();
    }

    private void showOnly(View which) {
        for (View v : new View[]{empty, loading, error, scroll}) {
            v.setVisibility(v == which ? View.VISIBLE : View.GONE);
        }
    }

    private void load() {
        if (delivery == null) return;
        showOnly(loading);
        btnChoose.setEnabled(false);
        final int token = ++requestToken;
        final Delivery d = delivery;
        final android.content.Context appCtx = requireContext().getApplicationContext();
        Async.run(() -> {
            RouteAnalyzer.Result res = null;
            String err = null;
            try {
                res = RouteAnalyzer.analyze(appCtx, d, System.currentTimeMillis());
            } catch (IOException e) {
                err = e.getMessage();
            } catch (RuntimeException e) {
                err = "Something went wrong while checking the routes: " + e.getMessage();
            }
            final RouteAnalyzer.Result fres = res;
            final String ferr = err;
            if (!isAdded() || getActivity() == null) return;
            requireActivity().runOnUiThread(() -> {
                if (!isAdded() || token != requestToken) return;
                if (ferr != null || fres == null || fres.routes.isEmpty()) {
                    errorText.setText(ferr != null ? ferr : "No routes were found.");
                    showOnly(error);
                    return;
                }
                result = fres;
                selected = indexOfChosen(d);
                afterAnalysis(d);
                bindResult();
            });
        });
    }

    private int indexOfChosen(Delivery d) {
        if (d.hasChosenRoute()) {
            for (int i = 0; i < result.routes.size(); i++) {
                if (result.routes.get(i).name.equals(d.chosenRouteName)) return i;
            }
        }
        return 0;
    }

    /** Stores the best suggestion on a draft delivery so the Deliveries tab can show it. */
    private void afterAnalysis(Delivery d) {
        if (d.hasChosenRoute() || result.routes.isEmpty()) return;
        RouteOption best = result.routes.get(0);
        d.suggestedRoute = best.name;
        d.suggestedLeaveByMillis = best.departEndMillis;
        Store.get(requireContext()).saveDelivery(d);
    }

    private void bindResult() {
        showOnly(scroll);
        updateProviderBanner();
        map.setData(result.routes, selected);
        buildTabs();
        bindRoute();
    }

    private void updateProviderBanner() {
        if (result == null || result.routes.isEmpty()) {
            demoBanner.setVisibility(View.GONE);
        } else if (result.demo) {
            demoBanner.setText("Demo data: add TOMTOM_API_KEY to local.properties for real routes and traffic. "
                    + "API-based traffic/weather condition markers are disabled in demo mode.");
            demoBanner.setVisibility(View.VISIBLE);
        } else if (result.routes.get(selected).trafficMissing) {
            demoBanner.setText("TomTom Routing is active, but TomTom Traffic Flow did not return condition samples for this route. "
                    + "Check Traffic API access, quota, and internet connection. Weather markers will still appear when Open-Meteo returns a forecast.");
            demoBanner.setVisibility(View.VISIBLE);
        } else if (result.routes.get(selected).weatherMissing) {
            demoBanner.setText("TomTom traffic markers are available, but Open-Meteo did not return forecast data for this route. Check the connection and refresh.");
            demoBanner.setVisibility(View.VISIBLE);
        } else {
            demoBanner.setVisibility(View.GONE);
        }
    }

    private void buildTabs() {
        tabRow.removeAllViews();
        LayoutInflater inf = LayoutInflater.from(requireContext());
        for (int i = 0; i < result.routes.size(); i++) {
            final int idx = i;
            RouteOption r = result.routes.get(i);
            View t = inf.inflate(R.layout.item_route_tab, tabRow, false);
            TextView name = t.findViewById(R.id.tabName);
            TextView status = t.findViewById(R.id.tabStatus);
            name.setText(r.name);
            status.setText(r.label.text);
            status.setTextColor(Ui.labelColor(requireContext(), r.label));
            boolean sel = i == selected;
            t.setBackground(sel
                    ? Ui.roundedStroke(Ui.color(requireContext(), R.color.green_bg), Ui.color(requireContext(), R.color.green), 16, requireContext())
                    : Ui.roundedStroke(Ui.color(requireContext(), R.color.white), Ui.color(requireContext(), R.color.line), 16, requireContext()));
            t.setOnClickListener(b -> {
                selected = idx;
                map.setSelected(selected);
                updateProviderBanner();
                buildTabs();
                bindRoute();
            });
            tabRow.addView(t);
        }
    }

    private void bindRoute() {
        final Context ctx = requireContext();
        final RouteOption r = result.routes.get(selected);
        Ui.stylePill(labelPill, r.label);
        boolean isChosen = delivery.hasChosenRoute() && delivery.chosenRouteName.equals(r.name);
        chosenPill.setVisibility(isChosen ? View.VISIBLE : View.GONE);
        chosenPill.setBackground(Ui.rounded(Ui.color(ctx, R.color.lime), 20, ctx));

        routeName.setText(r.name);
        routeVia.setText(r.via.isEmpty() ? "" : "via " + r.via);
        routeVia.setVisibility(r.via.isEmpty() ? View.GONE : View.VISIBLE);
        routeReason.setText(r.reason + (r.weatherMissing ? "\nRain forecast was not available." : ""));

        statTime.setText(TimeFmt.duration(r.minMillis) + " - " + TimeFmt.duration(r.maxMillis));
        statArrives.setText(TimeFmt.hm(r.etaMinMillis) + " - " + TimeFmt.hm(r.etaMaxMillis));
        statDistance.setText(String.format(Locale.US, "%.0f km", r.distanceKm));

        departBox.setBackground(Ui.rounded(Ui.color(ctx, R.color.surface), 16, ctx));
        if (r.departStartMillis == r.departEndMillis) {
            departTitle.setText("Leave now (" + TimeFmt.clock12(r.departEndMillis) + ")");
        } else {
            departTitle.setText(TimeFmt.clock12(r.departStartMillis) + " - " + TimeFmt.clock12(r.departEndMillis));
        }
        departReason.setText(r.departReason);

        hazardList.removeAllViews();
        long now = System.currentTimeMillis();
        LayoutInflater inf = LayoutInflater.from(ctx);
        if (r.hazards.isEmpty()) {
            TextView none = new TextView(ctx);
            none.setText("Nothing reported along this route right now.");
            none.setTextColor(Ui.color(ctx, R.color.text_secondary));
            none.setPadding(0, Ui.dp(ctx, 10), 0, 0);
            hazardList.addView(none);
        }
        for (final Hazard h : r.hazards) {
            View row = inf.inflate(R.layout.item_hazard, hazardList, false);
            TextView icon = row.findViewById(R.id.hzIcon);
            icon.setText(Ui.glyph(h.type));
            icon.setTextColor(Ui.hazardColor(ctx, h));
            icon.setBackground(Ui.circle(Ui.hazardBg(ctx, h)));
            ((TextView) row.findViewById(R.id.hzTitle)).setText(h.title);
            ((TextView) row.findViewById(R.id.hzMeta)).setText(h.source + " · " + Ui.postedText(h, now));
            row.setOnClickListener(b -> Ui.showHazardDialog(ctx, h));
            hazardList.addView(row);
        }

        btnChoose.setVisibility(View.VISIBLE);
        btnChoose.setEnabled(true);
        if (Delivery.ON_ROAD.equals(delivery.status) || Delivery.DONE.equals(delivery.status)) {
            btnChoose.setText("Trip already under way");
            btnChoose.setEnabled(false);
        } else if (isChosen) {
            btnChoose.setText("Route selected");
            btnChoose.setEnabled(false);
        } else if (delivery.hasChosenRoute()) {
            btnChoose.setText("Switch to this route");
        } else {
            btnChoose.setText("Choose route");
        }
    }

    private void onChoose() {
        if (result == null || delivery == null) return;
        final RouteOption r = result.routes.get(selected);
        if (r.label == ArrivalChecker.Label.RISKY || r.label == ArrivalChecker.Label.CANT_TELL) {
            String msg = r.label == ArrivalChecker.Label.RISKY
                    ? "This route is marked Risky:\n\n" + r.reason + "\n\nChoose it anyway?"
                    : "This route is marked Can't tell:\n\n" + r.reason + "\n\nChoose it anyway?";
            new AlertDialog.Builder(requireContext())
                    .setTitle(r.name)
                    .setMessage(msg)
                    .setPositiveButton("Choose anyway", (d, w) -> saveChosenRoute(r))
                    .setNegativeButton("Cancel", null)
                    .show();
        } else {
            saveChosenRoute(r);
        }
    }

    private void saveChosenRoute(RouteOption r) {
        if (delivery == null || r == null) return;
        delivery.chosenRouteName = r.name;
        delivery.chosenVia = r.via;
        delivery.chosenLabel = r.label.name();
        delivery.etaMinMillis = r.etaMinMillis;
        delivery.etaMaxMillis = r.etaMaxMillis;
        delivery.departStartMillis = r.departStartMillis;
        delivery.departEndMillis = r.departEndMillis;
        delivery.knownHazardIds.clear();
        for (Hazard h : r.hazards) delivery.knownHazardIds.add(h.id);
        if (Delivery.DRAFT.equals(delivery.status)) delivery.status = Delivery.SCHEDULED;
        Store.get(requireContext()).saveDelivery(delivery);
        bindRoute();
        Toast.makeText(requireContext(), "Route saved: " + r.name + " · " + r.label.text, Toast.LENGTH_LONG).show();
    }
}
