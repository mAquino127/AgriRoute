package com.gagambytes.agriroute.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.gagambytes.agriroute.R;
import com.gagambytes.agriroute.data.Places;
import com.gagambytes.agriroute.data.Store;
import com.gagambytes.agriroute.logic.TimeFmt;
import com.gagambytes.agriroute.model.Delivery;
import com.google.android.material.textfield.TextInputEditText;


/** Create a delivery: the details the proposal asks for (origin, market, load, deadline, truck, driver). */
public class NewDeliveryActivity extends AppCompatActivity {

    private Spinner spOrigin, spMarket, spTruck;
    private TextInputEditText etProduce, etLoad, etDeadline, etDepart, etPlate, etDriverName,
            etBuyerName, etBuyerPhone;
    private long deadlineMillis;
    private long departMillis;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_delivery);

        spOrigin = findViewById(R.id.spOrigin);
        spMarket = findViewById(R.id.spMarket);
        spTruck = findViewById(R.id.spTruck);
        etProduce = findViewById(R.id.etProduce);
        etLoad = findViewById(R.id.etLoad);
        etDeadline = findViewById(R.id.etDeadline);
        etDepart = findViewById(R.id.etDepart);
        etPlate = findViewById(R.id.etPlate);
        etDriverName = findViewById(R.id.etDriverName);
        etBuyerName = findViewById(R.id.etBuyerName);
        etBuyerPhone = findViewById(R.id.etBuyerPhone);

        spOrigin.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, Places.ORIGINS));
        spMarket.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, Places.MARKETS));
        spTruck.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, Places.TRUCKS));

        // Sensible defaults: depart this evening, deadline tomorrow 5 AM.
        long now = System.currentTimeMillis();
        departMillis = roundUpToHour(now + 2 * 3_600_000L);
        deadlineMillis = departMillis + 10 * 3_600_000L;
        showTimes();

        etDeadline.setOnClickListener(v -> Ui.pickDateTime(this, deadlineMillis, m -> {
            deadlineMillis = m;
            showTimes();
        }));
        etDepart.setOnClickListener(v -> Ui.pickDateTime(this, departMillis, m -> {
            departMillis = m;
            showTimes();
        }));

        findViewById(R.id.btnClose).setOnClickListener(v -> finish());
        findViewById(R.id.btnCheckRoutes).setOnClickListener(v -> save());
    }

    private static long roundUpToHour(long millis) {
        long hour = 3_600_000L;
        return ((millis + hour - 1) / hour) * hour;
    }

    private void showTimes() {
        etDeadline.setText(TimeFmt.friendly(deadlineMillis));
        etDepart.setText(TimeFmt.friendly(departMillis));
    }

    private String text(TextInputEditText e) {
        return e.getText() == null ? "" : e.getText().toString().trim();
    }

    private void save() {
        String produce = text(etProduce);
        String loadStr = text(etLoad);
        String driverName = text(etDriverName);
        String buyerPhoneRaw = text(etBuyerPhone);

        etProduce.setError(null);
        etLoad.setError(null);
        etDriverName.setError(null);
        etBuyerPhone.setError(null);

        boolean ok = true;
        if (produce.isEmpty()) {
            etProduce.setError("Required");
            ok = false;
        }

        int loadKg = 0;
        try {
            // Convert tonnes entered by the user into kilograms.
            double loadTonnes = Double.parseDouble(loadStr);

            if (Double.isFinite(loadTonnes)
                    && loadTonnes > 0
                    && loadTonnes <= 1000000) {
                loadKg = (int) Math.round(loadTonnes * 1000.0);
            }
        } catch (NumberFormatException ignored) {
            // Invalid input is handled below.
        }

        if (loadKg <= 0) {
            etLoad.setError("Enter a load in tonnes, e.g. 5 or 2.5");
            ok = false;
        }

        String buyerPhone = "";
        if (!buyerPhoneRaw.isEmpty()) {
            buyerPhone = Ui.cleanPhone(buyerPhoneRaw);
            if (buyerPhone == null) {
                etBuyerPhone.setError("Use a PH mobile number");
                ok = false;
                buyerPhone = "";
            }
        }
        if (deadlineMillis <= departMillis) {
            Toast.makeText(this, "The deadline must be later than the planned departure.", Toast.LENGTH_LONG).show();
            ok = false;
        }
        if (!ok) return;

        Store store = Store.get(this);
        Delivery d = new Delivery();
        d.id = store.nextDeliveryId();
        d.originKey = ((Places.Place) spOrigin.getSelectedItem()).key;
        d.destKey = ((Places.Place) spMarket.getSelectedItem()).key;
        d.produce = produce;
        d.loadKg = loadKg;
        d.deadlineMillis = deadlineMillis;
        d.plannedDepartureMillis = departMillis;
        d.truck = (String) spTruck.getSelectedItem();
        d.plate = text(etPlate);
        d.driverName = driverName;
        d.buyerName = text(etBuyerName);
        d.buyerPhone = buyerPhone;
        d.status = Delivery.DRAFT;
        store.saveDelivery(d);

        Intent i = new Intent(this, MainActivity.class);
        i.putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_ROUTES);
        i.putExtra(MainActivity.EXTRA_DELIVERY_ID, d.id);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
        finish();
    }
}
