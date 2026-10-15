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
import androidx.fragment.app.Fragment;

import com.gagambytes.agriroute.R;
import com.gagambytes.agriroute.data.Store;
import com.gagambytes.agriroute.logic.TimeFmt;
import com.gagambytes.agriroute.model.Delivery;
import com.gagambytes.agriroute.model.Hazard;
import com.gagambytes.agriroute.model.UpdateItem;
import com.gagambytes.agriroute.service.Async;
import com.gagambytes.agriroute.service.UpdateChecker;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

/** Tab 3: new traffic/weather observations and risk changes on chosen routes. */
public class UpdatesFragment extends Fragment {

    private MaterialButton btnCheck;
    private View progress;
    private LinearLayout filterRow, list;
    private String filterDeliveryId; // null = all
    private boolean checking;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        View v = inflater.inflate(R.layout.fragment_updates, container, false);
        btnCheck = v.findViewById(R.id.btnCheck);
        progress = v.findViewById(R.id.checkProgress);
        filterRow = v.findViewById(R.id.filterRow);
        list = v.findViewById(R.id.updatesList);
        btnCheck.setOnClickListener(b -> runCheck());
        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        render(true);
    }

    private List<Delivery> monitored() {
        List<Delivery> out = new ArrayList<>();
        for (Delivery d : Store.get(requireContext()).deliveries()) if (d.isMonitored()) out.add(d);
        return out;
    }

    /** @param markRead true when the trader is looking at the list, so the tab badge clears */
    private void render(boolean markRead) {
        if (!isAdded()) return;
        Context ctx = requireContext();
        Store store = Store.get(ctx);

        buildFilters(store);

        List<UpdateItem> items = new ArrayList<>();
        for (UpdateItem u : store.updates()) {
            if (filterDeliveryId == null || filterDeliveryId.equals(u.deliveryId)) items.add(u);
        }

        list.removeAllViews();
        if (items.isEmpty()) {
            TextView t = new TextView(ctx);
            t.setText(monitored().isEmpty()
                    ? "Nothing to watch yet.\nChoose a route to start monitoring traffic and weather risks."
                    : "No conditions recorded yet.\nTap \"Check for new updates\" to fetch current traffic and weather readings.");
            t.setGravity(android.view.Gravity.CENTER);
            t.setTextColor(Ui.color(ctx, R.color.text_secondary));
            t.setPadding(Ui.dp(ctx, 24), Ui.dp(ctx, 48), Ui.dp(ctx, 24), 0);
            list.addView(t);
        } else {
            long now = System.currentTimeMillis();
            String lastDay = "";
            LayoutInflater inf = LayoutInflater.from(ctx);
            for (UpdateItem u : items) {
                String day = TimeFmt.dayKey(u.createdAtMillis);
                if (!day.equals(lastDay)) {
                    TextView h = new TextView(ctx);
                    boolean today = day.equals(TimeFmt.dayKey(now));
                    h.setText(today ? "Today" : TimeFmt.dateLong(u.createdAtMillis));
                    h.setTextColor(Ui.color(ctx, R.color.text_secondary));
                    h.setTextSize(13);
                    h.setPadding(Ui.dp(ctx, 20), Ui.dp(ctx, 16), Ui.dp(ctx, 20), Ui.dp(ctx, 6));
                    list.addView(h);
                    lastDay = day;
                }
                list.addView(buildItem(inf, u, now));
            }
        }

        if (markRead) {
            store.markUpdatesRead();
            ((MainActivity) requireActivity()).refreshBadge();
        }
    }

    private void buildFilters(Store store) {
        Context ctx = requireContext();
        filterRow.removeAllViews();
        addChip("All", null);
        for (Delivery d : store.deliveries()) {
            if (d.hasChosenRoute()) addChip(d.id, d.id);
        }
        if (filterDeliveryId != null && store.delivery(filterDeliveryId) == null) filterDeliveryId = null;
    }

    private void addChip(String text, final String deliveryId) {
        Context ctx = requireContext();
        TextView chip = new TextView(ctx);
        boolean sel = (filterDeliveryId == null && deliveryId == null)
                || (filterDeliveryId != null && filterDeliveryId.equals(deliveryId));
        chip.setText(text);
        chip.setTextSize(13);
        chip.setPadding(Ui.dp(ctx, 14), Ui.dp(ctx, 8), Ui.dp(ctx, 14), Ui.dp(ctx, 8));
        chip.setTextColor(sel ? Ui.color(ctx, R.color.white) : Ui.color(ctx, R.color.navy));
        chip.setBackground(sel
                ? Ui.rounded(Ui.color(ctx, R.color.navy), 20, ctx)
                : Ui.roundedStroke(Ui.color(ctx, R.color.white), Ui.color(ctx, R.color.line), 20, ctx));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(Ui.dp(ctx, 8));
        chip.setLayoutParams(lp);
        chip.setOnClickListener(v -> {
            filterDeliveryId = deliveryId;
            render(false);
        });
        filterRow.addView(chip);
    }

    private View buildItem(LayoutInflater inf, final UpdateItem u, long now) {
        final Context ctx = requireContext();
        View v = inf.inflate(R.layout.item_update, list, false);
        Hazard.Type type;
        try {
            type = Hazard.Type.valueOf(u.type);
        } catch (IllegalArgumentException e) {
            type = Hazard.Type.TRAFFIC;
        }
        TextView icon = v.findViewById(R.id.upIcon);
        icon.setText(Ui.glyph(type));
        icon.setTextColor(Ui.hazardColor(ctx, type));
        icon.setBackground(Ui.circle(Ui.hazardBg(ctx, type)));

        ((TextView) v.findViewById(R.id.upRoute)).setText(u.deliveryId + " · " + u.routeName);
        ((TextView) v.findViewById(R.id.upTime)).setText(TimeFmt.ago(u.createdAtMillis, now));
        ((TextView) v.findViewById(R.id.upTitle)).setText(u.title);
        TextView detail = v.findViewById(R.id.upDetail);
        detail.setText(u.detail);
        detail.setVisibility(u.detail == null || u.detail.isEmpty() ? View.GONE : View.VISIBLE);
        String posted = u.postedAtMillis > 0 ? " · checked " + TimeFmt.ago(u.postedAtMillis, now) : "";
        ((TextView) v.findViewById(R.id.upSource)).setText("Source: " + u.source + posted);

        TextView arrival = v.findViewById(R.id.upArrival);
        arrival.setText(u.arrivalNote);
        arrival.setTextColor(Ui.color(ctx, u.arrivalRisky ? R.color.orange : R.color.green));
        arrival.setBackground(Ui.rounded(Ui.color(ctx, u.arrivalRisky ? R.color.orange_bg : R.color.green_bg), 12, ctx));


        Delivery d = Store.get(ctx).delivery(u.deliveryId);
        MaterialButton call = v.findViewById(R.id.upCallBuyer);
        if (u.arrivalRisky && d != null && !d.buyerPhone.isEmpty()) {
            final String phone = d.buyerPhone;
            call.setText("Call " + (d.buyerName.isEmpty() ? "buyer" : d.buyerName) + " about a late delivery");
            call.setVisibility(View.VISIBLE);
            call.setOnClickListener(b -> Ui.dial(ctx, phone));
        }
        v.setOnClickListener(b -> {
            if (u.source != null && !u.source.isEmpty()) {
                Toast.makeText(ctx, u.source, Toast.LENGTH_SHORT).show();
            }
        });
        return v;
    }

    private void runCheck() {
        if (checking) return;
        checking = true;
        btnCheck.setEnabled(false);
        progress.setVisibility(View.VISIBLE);
        final Context app = requireContext().getApplicationContext();
        Async.run(() -> {
            final UpdateChecker.Summary s = UpdateChecker.checkAll(app);
            if (!isAdded() || getActivity() == null) {
                checking = false;
                return;
            }
            requireActivity().runOnUiThread(() -> {
                checking = false;
                if (!isAdded()) return;
                btnCheck.setEnabled(true);
                progress.setVisibility(View.GONE);
                String msg;
                if (s.deliveriesChecked == 0) {
                    msg = "No scheduled or on-the-road deliveries to check.";
                } else if (!s.errors.isEmpty() && s.newUpdates == 0) {
                    msg = s.errors.get(0);
                } else if (s.newUpdates == 0) {
                    msg = "Checked " + s.deliveriesChecked + (s.deliveriesChecked == 1 ? " delivery" : " deliveries")
                            + ". Nothing new on your routes.";
                } else {
                    msg = s.newUpdates + " new update" + (s.newUpdates == 1 ? "" : "s")
                            + " recorded.";
                }
                Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
                render(true);
            });
        });
    }

}
