package com.gagambytes.agriroute.ui;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.gagambytes.agriroute.R;
import com.gagambytes.agriroute.logic.ArrivalChecker;
import com.gagambytes.agriroute.logic.TimeFmt;
import com.gagambytes.agriroute.model.Hazard;

import java.util.Calendar;

/** Small UI helpers shared by all screens. */
public final class Ui {

    private Ui() { }

    public static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    public static GradientDrawable rounded(int color, float radiusDp, Context c) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    public static GradientDrawable roundedStroke(int fill, int stroke, float radiusDp, Context c) {
        GradientDrawable g = rounded(fill, radiusDp, c);
        g.setStroke(dp(c, 1.5f), stroke);
        return g;
    }

    public static GradientDrawable circle(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        return g;
    }

    public static int color(Context c, int resId) {
        return ContextCompat.getColor(c, resId);
    }

    // ------------------------------------------------------------ route labels

    public static int labelColor(Context c, ArrivalChecker.Label l) {
        switch (l) {
            case ON_TIME: return color(c, R.color.green);
            case RISKY: return color(c, R.color.orange);
            default: return color(c, R.color.gray);
        }
    }

    public static int labelBg(Context c, ArrivalChecker.Label l) {
        switch (l) {
            case ON_TIME: return color(c, R.color.green_bg);
            case RISKY: return color(c, R.color.orange_bg);
            default: return color(c, R.color.gray_bg);
        }
    }

    /** Turns a TextView into a rounded status pill ("On time", "Risky", "Can't tell"). */
    public static void stylePill(TextView tv, ArrivalChecker.Label l) {
        Context c = tv.getContext();
        tv.setText(l.text);
        tv.setTextColor(labelColor(c, l));
        tv.setBackground(rounded(labelBg(c, l), 20, c));
    }

    // ----------------------------------------------------------------- hazards

    public static String glyph(Hazard.Type t) {
        switch (t) {
            case CLOSURE: return "X";
            case TRAFFIC: return "T";
            case WORKS: return "!";
            case LANDSLIDE: return "^";
            case FLOOD: return "~";
            case RAIN: return "W";
            default: return "!";
        }
    }

    /** Color API observation markers by condition severity, while preserving legacy hazard colors. */
    public static int hazardColor(Context c, Hazard h) {
        if (h != null && h.conditionObservation) {
            if (h.severity <= 0) return color(c, R.color.green);
            if (h.severity == 1) return color(c, R.color.orange);
            return color(c, R.color.red);
        }
        return hazardColor(c, h == null ? Hazard.Type.TRAFFIC : h.type);
    }

    public static int hazardBg(Context c, Hazard h) {
        if (h != null && h.conditionObservation) {
            if (h.severity <= 0) return color(c, R.color.green_bg);
            if (h.severity == 1) return color(c, R.color.orange_bg);
            return color(c, R.color.red_bg);
        }
        return hazardBg(c, h == null ? Hazard.Type.TRAFFIC : h.type);
    }

    public static int hazardColor(Context c, Hazard.Type t) {
        switch (t) {
            case CLOSURE: return color(c, R.color.red);
            case TRAFFIC:
            case WORKS: return color(c, R.color.orange);
            case LANDSLIDE: return color(c, R.color.brown);
            case FLOOD:
            case RAIN: return color(c, R.color.blue);
            default: return color(c, R.color.gray);
        }
    }

    public static int hazardBg(Context c, Hazard.Type t) {
        switch (t) {
            case CLOSURE: return color(c, R.color.red_bg);
            case TRAFFIC:
            case WORKS: return color(c, R.color.orange_bg);
            case LANDSLIDE: return color(c, R.color.brown_bg);
            case FLOOD:
            case RAIN: return color(c, R.color.blue_bg);
            default: return color(c, R.color.gray_bg);
        }
    }

    public static String typeLabel(Hazard.Type t) {
        switch (t) {
            case CLOSURE: return "Closure or restriction";
            case TRAFFIC: return "Traffic delay";
            case WORKS: return "Road works / advisory";
            case LANDSLIDE: return "Landslide-prone section";
            case FLOOD: return "Flood-prone section";
            case RAIN: return "Weather forecast condition";
            default: return "Condition";
        }
    }

    public static String postedText(Hazard h, long now) {
        if (h.postedAtMillis <= 0) return "Static hazard map layer";
        String when = TimeFmt.ago(h.postedAtMillis, now) + " (" + TimeFmt.friendly(h.postedAtMillis) + ")";
        return h.conditionObservation ? "checked " + when : "posted " + when;
    }

    /** Detail view from the proposal: what it is, source, when posted, how long ago, source link. */
    public static void showHazardDialog(final Context c, final Hazard h) {
        long now = System.currentTimeMillis();
        StringBuilder sb = new StringBuilder();
        sb.append(typeLabel(h.type)).append("\n\n");
        if (!h.detail.isEmpty()) sb.append(h.detail).append("\n\n");
        sb.append("Source: ").append(h.source).append('\n');
        sb.append("When: ").append(postedText(h, now));
        AlertDialog.Builder b = new AlertDialog.Builder(c)
                .setTitle(h.title)
                .setMessage(sb.toString())
                .setPositiveButton("Close", null);
        if (h.sourceUrl != null && !h.sourceUrl.isEmpty()) {
            b.setNeutralButton("Open source", (d, w) -> openUrl(c, h.sourceUrl));
        }
        b.show();
    }

    public static void openUrl(Context c, String url) {
        try {
            c.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception ignored) {
            // no browser installed
        }
    }

    public static void dial(Context c, String number) {
        try {
            c.startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number)));
        } catch (Exception ignored) {
            // no dialer
        }
    }

    // ------------------------------------------------------------ date + time

    public interface DateTimeCallback {
        void onPicked(long millis);
    }

    public static void pickDateTime(final Context c, long initialMillis, final DateTimeCallback cb) {
        final Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(initialMillis > 0 ? initialMillis : System.currentTimeMillis());
        new DatePickerDialog(c, (view, y, m, d) -> {
            cal.set(Calendar.YEAR, y);
            cal.set(Calendar.MONTH, m);
            cal.set(Calendar.DAY_OF_MONTH, d);
            new TimePickerDialog(c, (tv, h, min) -> {
                cal.set(Calendar.HOUR_OF_DAY, h);
                cal.set(Calendar.MINUTE, min);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                cb.onPicked(cal.getTimeInMillis());
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false).show();
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    // -------------------------------------------------------------------- misc

    public static String initials(String name) {
        if (name == null || name.trim().isEmpty()) return "?";
        String[] parts = name.trim().split("\\s+");
        String s = parts[0].substring(0, 1);
        if (parts.length > 1) s += parts[parts.length - 1].substring(0, 1);
        return s.toUpperCase();
    }

    public static void setGone(View v, boolean gone) {
        v.setVisibility(gone ? View.GONE : View.VISIBLE);
    }

    /** Normalises a Philippine mobile number: strips spaces/dashes. Returns null if invalid. */
    public static String cleanPhone(String raw) {
        if (raw == null) return null;
        String s = raw.replaceAll("[\\s\\-()]", "");
        if (s.matches("^(09|639)\\d{9}$") || s.matches("^\\+639\\d{9}$")) return s;
        return null;
    }

    public static int parseColorSafe(String hex, int fallback) {
        try {
            return Color.parseColor(hex);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
