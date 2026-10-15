package com.gagambytes.agriroute.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.gagambytes.agriroute.BuildConfig;
import com.gagambytes.agriroute.R;
import com.gagambytes.agriroute.data.Places;
import com.gagambytes.agriroute.data.Store;
import com.gagambytes.agriroute.logic.ArrivalChecker;
import com.gagambytes.agriroute.logic.TimeFmt;
import com.gagambytes.agriroute.model.Delivery;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Tab 1: all deliveries, the next trip to plan, and what is on the road. */
public class DeliveriesFragment extends Fragment {

    private TextView appTitle, greeting, subGreeting, demoBanner, emptyText, sectionRoad, sectionOther;
    private TextView heroTag, heroRoute, heroLoad, heroAdvice, heroSecondary;
    private MaterialButton heroPrimary;
    private View heroCard;
    private LinearLayout roadList, otherList;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        View v = inflater.inflate(R.layout.fragment_deliveries, container, false);
        appTitle = v.findViewById(R.id.appTitle);
        greeting = v.findViewById(R.id.greeting);
        subGreeting = v.findViewById(R.id.subGreeting);
        demoBanner = v.findViewById(R.id.demoBanner);
        emptyText = v.findViewById(R.id.emptyText);
        sectionRoad = v.findViewById(R.id.sectionRoad);
        sectionOther = v.findViewById(R.id.sectionOther);
        heroCard = v.findViewById(R.id.heroCard);
        heroTag = v.findViewById(R.id.heroTag);
        heroRoute = v.findViewById(R.id.heroRoute);
        heroLoad = v.findViewById(R.id.heroLoad);
        heroAdvice = v.findViewById(R.id.heroAdvice);
        heroPrimary = v.findViewById(R.id.heroPrimary);
        heroSecondary = v.findViewById(R.id.heroSecondary);
        roadList = v.findViewById(R.id.roadList);
        otherList = v.findViewById(R.id.otherList);

        SpannableString title = new SpannableString("AgriRoute");
        title.setSpan(new ForegroundColorSpan(Ui.color(requireContext(), R.color.navy)), 0, 4, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        title.setSpan(new ForegroundColorSpan(Ui.color(requireContext(), R.color.green)), 4, 9, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        appTitle.setText(title);

        v.findViewById(R.id.btnNew).setOnClickListener(b ->
                startActivity(new Intent(requireContext(), NewDeliveryActivity.class)));
        greeting.setOnClickListener(b -> promptName());
        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        render();
    }

    private void promptName() {
        final EditText input = new EditText(requireContext());
        input.setText(Store.get(requireContext()).traderName());
        input.setSelectAllOnFocus(true);
        new AlertDialog.Builder(requireContext())
                .setTitle("Your name")
                .setView(input)
                .setPositiveButton("Save", (d, w) -> {
                    Store.get(requireContext()).setTraderName(input.getText().toString());
                    render();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void render() {
        if (!isAdded()) return;
        Store store = Store.get(requireContext());
        long now = System.currentTimeMillis();

        int hour = TimeFmt.hourOfDay(now);
        String hello = hour < 12 ? "Good morning" : (hour < 18 ? "Good afternoon" : "Good evening");
        greeting.setText(hello + ", " + store.traderName());

        List<Delivery> active = new ArrayList<>();
        for (Delivery d : store.deliveries()) if (!Delivery.DONE.equals(d.status)) active.add(d);
        subGreeting.setText(active.size() + (active.size() == 1 ? " active delivery" : " active deliveries")
                + " · " + TimeFmt.dateLong(now));

        StringBuilder banner = new StringBuilder();
        if (BuildConfig.TOMTOM_API_KEY.isEmpty()) {
            banner.append("Demo mode: no TomTom key. Route lines and ETAs are approximate; live traffic/weather markers are disabled.");
        }
        if (banner.length() > 0) {
            demoBanner.setText(banner.toString());
            demoBanner.setBackground(Ui.rounded(Ui.color(requireContext(), R.color.brown_bg), 12, requireContext()));
            demoBanner.setVisibility(View.VISIBLE);
        } else {
            demoBanner.setVisibility(View.GONE);
        }

        // Hero = the next trip that has not left yet (earliest planned departure).
        List<Delivery> waiting = new ArrayList<>();
        List<Delivery> onRoad = new ArrayList<>();
        for (Delivery d : active) {
            if (Delivery.ON_ROAD.equals(d.status)) onRoad.add(d); else waiting.add(d);
        }
        Collections.sort(waiting, new Comparator<Delivery>() {
            @Override
            public int compare(Delivery a, Delivery b) {
                return Long.compare(a.plannedDepartureMillis, b.plannedDepartureMillis);
            }
        });

        roadList.removeAllViews();
        otherList.removeAllViews();

        Delivery hero = waiting.isEmpty() ? null : waiting.get(0);
        if (hero != null) {
            bindHero(hero);
            heroCard.setVisibility(View.VISIBLE);
        } else {
            heroCard.setVisibility(View.GONE);
        }

        for (Delivery d : onRoad) roadList.addView(buildItem(d));
        for (int i = 1; i < waiting.size(); i++) otherList.addView(buildItem(waiting.get(i)));

        Ui.setGone(sectionRoad, onRoad.isEmpty());
        Ui.setGone(sectionOther, waiting.size() <= 1);
        Ui.setGone(emptyText, !active.isEmpty());
    }

    private void bindHero(final Delivery d) {
        final MainActivity main = (MainActivity) requireActivity();
        Places.Place o = Places.origin(d.originKey);
        Places.Place m = Places.market(d.destKey);
        heroRoute.setText(o.shortName + " → " + m.shortName);
        heroLoad.setText(d.loadLabel() + " · deadline " + TimeFmt.dayClock12(d.deadlineMillis));

        if (Delivery.SCHEDULED.equals(d.status)) {
            heroTag.setText("Scheduled • " + d.id);
            heroAdvice.setText(d.chosenRouteName + " · "
                    + labelText(d.chosenLabel) + ". Planned departure is " + TimeFmt.clock12(d.plannedDepartureMillis) + ".");
            heroPrimary.setText("Mark as departed");
            heroPrimary.setOnClickListener(b -> {
                d.status = Delivery.ON_ROAD;
                d.departedMillis = System.currentTimeMillis();
                Store.get(requireContext()).saveDelivery(d);
                render();
            });
            heroSecondary.setText("View route");
            heroSecondary.setOnClickListener(b -> main.openRoutes(d.id));
        } else {
            heroTag.setText("Before departure • " + d.id);
            if (!d.suggestedRoute.isEmpty()) {
                heroAdvice.setText("Leave by " + TimeFmt.clock12(d.suggestedLeaveByMillis) + " on " + d.suggestedRoute
                        + " to arrive on time. Planned departure is " + TimeFmt.clock12(d.plannedDepartureMillis) + ".");
            } else {
                heroAdvice.setText("Compare routes to see which one can still make the buyer's deadline. Planned departure is "
                        + TimeFmt.clock12(d.plannedDepartureMillis) + ".");
            }
            heroPrimary.setText("Compare routes →");
            heroPrimary.setOnClickListener(b -> main.openRoutes(d.id));
            heroSecondary.setText("Delete this delivery");
            heroSecondary.setOnClickListener(b -> confirmDelete(d));
        }
    }

    private View buildItem(final Delivery d) {
        View v = LayoutInflater.from(requireContext()).inflate(R.layout.item_delivery, null, false);
        Places.Place o = Places.origin(d.originKey);
        Places.Place m = Places.market(d.destKey);
        ((TextView) v.findViewById(R.id.itemRoute)).setText(o.shortName + " → " + m.shortName);
        String driverShort = d.driverName;
        String[] parts = d.driverName.trim().split("\\s+");
        if (parts.length > 1) driverShort = parts[0] + " " + parts[parts.length - 1].charAt(0) + ".";
        ((TextView) v.findViewById(R.id.itemLoad)).setText(d.loadLabel() + " · " + driverShort);
        TextView eta = v.findViewById(R.id.itemEta);
        TextView pill = v.findViewById(R.id.itemPill);
        ((TextView) v.findViewById(R.id.itemId)).setText(d.id);

        if (d.hasChosenRoute()) {
            eta.setText(d.chosenRouteName + " · arrive " + TimeFmt.hm(d.etaMinMillis) + " - " + TimeFmt.dayHm(d.etaMaxMillis));
            Ui.stylePill(pill, ArrivalChecker.Label.fromName(d.chosenLabel));
        } else {
            eta.setText("Route not chosen yet");
            pill.setText("Draft");
            pill.setTextColor(Ui.color(requireContext(), R.color.gray));
            pill.setBackground(Ui.rounded(Ui.color(requireContext(), R.color.gray_bg), 20, requireContext()));
        }
        v.setOnClickListener(b -> showActions(d));
        return v;
    }

    private String labelText(String name) {
        return ArrivalChecker.Label.fromName(name).text;
    }

    private void showActions(final Delivery d) {
        final MainActivity main = (MainActivity) requireActivity();
        final List<String> labels = new ArrayList<>();
        final List<Runnable> actions = new ArrayList<>();
        labels.add("Open routes");
        actions.add(() -> main.openRoutes(d.id));
        if (Delivery.SCHEDULED.equals(d.status)) {
            labels.add("Mark as departed");
            actions.add(() -> {
                d.status = Delivery.ON_ROAD;
                d.departedMillis = System.currentTimeMillis();
                Store.get(requireContext()).saveDelivery(d);
                render();
            });
        }
        if (Delivery.ON_ROAD.equals(d.status)) {
            labels.add("Mark as delivered");
            actions.add(() -> {
                d.status = Delivery.DONE;
                Store.get(requireContext()).saveDelivery(d);
                render();
            });
        }
        labels.add("Delete delivery");
        actions.add(() -> confirmDelete(d));

        new AlertDialog.Builder(requireContext())
                .setTitle(d.id)
                .setItems(labels.toArray(new String[0]), (dialog, which) -> actions.get(which).run())
                .show();
    }

    private void confirmDelete(final Delivery d) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete " + d.id + "?")
                .setMessage("This removes the delivery from this phone.")
                .setPositiveButton("Delete", (dialog, w) -> {
                    Store.get(requireContext()).deleteDelivery(d.id);
                    render();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
