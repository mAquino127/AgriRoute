package com.gagambytes.agriroute.ui;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.gagambytes.agriroute.R;
import com.gagambytes.agriroute.data.Store;
import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.bottomnavigation.BottomNavigationView;

/** Hosts the three core tabs: Deliveries, Routes, and route Alerts. */
public class MainActivity extends AppCompatActivity {

    public static final String EXTRA_TAB = "tab";
    public static final String EXTRA_DELIVERY_ID = "delivery_id";
    public static final String TAB_DELIVERIES = "deliveries";
    public static final String TAB_ROUTES = "routes";
    public static final String TAB_UPDATES = "updates";

    private static final String STATE_ROUTES_ID = "routes_id";

    private BottomNavigationView nav;
    private String routesDeliveryId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        nav = findViewById(R.id.nav);
        nav.setOnItemSelectedListener(item -> {
            showTab(item.getItemId());
            return true;
        });

        if (savedInstanceState != null) {
            routesDeliveryId = savedInstanceState.getString(STATE_ROUTES_ID);
        } else if (!handleIntent(getIntent())) {
            select(R.id.nav_deliveries);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_ROUTES_ID, routesDeliveryId);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshBadge();
    }

    private boolean handleIntent(Intent intent) {
        String tab = intent == null ? null : intent.getStringExtra(EXTRA_TAB);
        if (tab == null) return false;
        String id = intent.getStringExtra(EXTRA_DELIVERY_ID);
        if (id != null) {
            routesDeliveryId = id;
        }
        switch (tab) {
            case TAB_ROUTES: select(R.id.nav_routes); break;
            case TAB_UPDATES: select(R.id.nav_updates); break;
            default: select(R.id.nav_deliveries); break;
        }
        intent.removeExtra(EXTRA_TAB);
        return true;
    }

    /** Selects a tab; re-shows it if it is already selected (so new arguments take effect). */
    private void select(int itemId) {
        if (nav.getSelectedItemId() == itemId && getSupportFragmentManager().findFragmentById(R.id.container) != null) {
            showTab(itemId);
        } else {
            nav.setSelectedItemId(itemId);
        }
    }

    private void showTab(int itemId) {
        Fragment f;
        if (itemId == R.id.nav_routes) f = new RoutesFragment();
        else if (itemId == R.id.nav_updates) f = new UpdatesFragment();
        else f = new DeliveriesFragment();
        getSupportFragmentManager().beginTransaction().replace(R.id.container, f).commitAllowingStateLoss();
    }

    // ---- called by fragments ----

    public void openRoutes(String deliveryId) {
        routesDeliveryId = deliveryId;
        select(R.id.nav_routes);
    }


    public String getRoutesDeliveryId() { return routesDeliveryId; }

    public void setRoutesDeliveryId(String id) { routesDeliveryId = id; }

    public void refreshBadge() {
        int unread = Store.get(this).unreadCount();
        if (unread > 0) {
            BadgeDrawable b = nav.getOrCreateBadge(R.id.nav_updates);
            b.setNumber(unread);
            b.setVisible(true);
        } else {
            nav.removeBadge(R.id.nav_updates);
        }
    }
}
