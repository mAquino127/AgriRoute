package com.gagambytes.agriroute.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.gagambytes.agriroute.R;
import com.gagambytes.agriroute.model.Hazard;
import com.gagambytes.agriroute.model.RouteOption;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws the route options and the hazards of the selected route.
 * It is a schematic map drawn with Canvas, so no Google Maps key is needed.
 */
public class RouteMapView extends View {

    public interface OnHazardTapListener {
        void onHazardTapped(Hazard hazard);
    }

    private static final int MAX_POINTS_PER_ROUTE = 1200;

    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint casing = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private List<RouteOption> routes = new ArrayList<>();
    private int selected = 0;
    private OnHazardTapListener listener;

    private final List<Path> paths = new ArrayList<>();
    private final List<float[]> markerXY = new ArrayList<>();
    private final List<Hazard> markerHazards = new ArrayList<>();
    private float startX, startY, endX, endY;
    private boolean laidOut;

    public RouteMapView(Context c) {
        super(c);
        init();
    }

    public RouteMapView(Context c, AttributeSet a) {
        super(c, a);
        init();
    }

    public RouteMapView(Context c, AttributeSet a, int s) {
        super(c, a, s);
        init();
    }

    private void init() {
        bgPaint.setColor(Ui.color(getContext(), R.color.map_bg));
        casing.setStyle(Paint.Style.STROKE);
        casing.setStrokeCap(Paint.Cap.ROUND);
        casing.setStrokeJoin(Paint.Join.ROUND);
        casing.setColor(0xFFFFFFFF);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        markerText.setTextAlign(Paint.Align.CENTER);
        markerText.setFakeBoldText(true);
        markerText.setTextSize(Ui.dp(getContext(), 14));
        iconFill.setStyle(Paint.Style.FILL);
        iconStroke.setStyle(Paint.Style.STROKE);
        iconStroke.setStrokeCap(Paint.Cap.ROUND);
        iconStroke.setStrokeJoin(Paint.Join.ROUND);
        hint.setColor(Ui.color(getContext(), R.color.text_secondary));
        hint.setTextSize(Ui.dp(getContext(), 11));
    }

    public void setOnHazardTapListener(OnHazardTapListener l) {
        listener = l;
    }

    public void setData(List<RouteOption> routes, int selected) {
        this.routes = routes == null ? new ArrayList<RouteOption>() : routes;
        this.selected = selected;
        rebuild();
        invalidate();
    }

    public void setSelected(int selected) {
        this.selected = selected;
        rebuild();
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        laidOut = w > 0 && h > 0;
        rebuild();
    }

    /** Projects all route points to screen coordinates (aspect-correct) and builds the paths. */
    private void rebuild() {
        paths.clear();
        markerXY.clear();
        markerHazards.clear();
        if (!laidOut || routes.isEmpty()) return;

        double minLat = 90, maxLat = -90, minLon = 180, maxLon = -180;
        for (RouteOption r : routes) {
            for (double[] p : r.points) {
                minLat = Math.min(minLat, p[0]);
                maxLat = Math.max(maxLat, p[0]);
                minLon = Math.min(minLon, p[1]);
                maxLon = Math.max(maxLon, p[1]);
            }
        }
        if (minLat > maxLat) return;

        float pad = Ui.dp(getContext(), 28);
        float w = getWidth() - 2 * pad;
        float h = getHeight() - 2 * pad;
        double midLat = (minLat + maxLat) / 2;
        double lonScale = Math.cos(Math.toRadians(midLat));
        double spanX = Math.max(1e-6, (maxLon - minLon) * lonScale);
        double spanY = Math.max(1e-6, maxLat - minLat);
        double scale = Math.min(w / spanX, h / spanY);
        double offX = pad + (w - spanX * scale) / 2;
        double offY = pad + (h - spanY * scale) / 2;

        for (int i = 0; i < routes.size(); i++) {
            RouteOption r = routes.get(i);
            Path path = new Path();
            int step = Math.max(1, r.points.size() / MAX_POINTS_PER_ROUTE);
            boolean first = true;
            for (int k = 0; k < r.points.size(); k += step) {
                double[] p = r.points.get(k);
                float x = (float) (offX + (p[1] - minLon) * lonScale * scale);
                float y = (float) (offY + (maxLat - p[0]) * scale);
                if (first) {
                    path.moveTo(x, y);
                    first = false;
                } else {
                    path.lineTo(x, y);
                }
            }
            paths.add(path);
            if (i == 0 && !r.points.isEmpty()) {
                double[] a = r.points.get(0);
                double[] b = r.points.get(r.points.size() - 1);
                startX = (float) (offX + (a[1] - minLon) * lonScale * scale);
                startY = (float) (offY + (maxLat - a[0]) * scale);
                endX = (float) (offX + (b[1] - minLon) * lonScale * scale);
                endY = (float) (offY + (maxLat - b[0]) * scale);
            }
        }

        if (selected >= 0 && selected < routes.size()) {
            for (Hazard hz : routes.get(selected).hazards) {
                float x = (float) (offX + (hz.lon - minLon) * lonScale * scale);
                float y = (float) (offY + (maxLat - hz.lat) * scale);
                // Separate traffic (T) and weather (W) observations when they fall near one another.
                if (hz.type == Hazard.Type.TRAFFIC) x -= Ui.dp(getContext(), 10);
                else if (hz.type == Hazard.Type.RAIN) x += Ui.dp(getContext(), 10);
                markerXY.add(new float[]{x, y});
                markerHazards.add(hz);
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        RectF all = new RectF(0, 0, getWidth(), getHeight());
        canvas.drawRect(all, bgPaint);
        if (paths.isEmpty()) return;

        Context c = getContext();
        // Other routes first (thin), selected route last (thick, on top).
        for (int i = 0; i < paths.size(); i++) {
            if (i == selected) continue;
            casing.setStrokeWidth(Ui.dp(c, 5));
            canvas.drawPath(paths.get(i), casing);
            line.setStrokeWidth(Ui.dp(c, 3));
            line.setColor(Ui.labelColor(c, routes.get(i).label));
            line.setAlpha(150);
            canvas.drawPath(paths.get(i), line);
            line.setAlpha(255);
        }
        if (selected >= 0 && selected < paths.size()) {
            casing.setStrokeWidth(Ui.dp(c, 10));
            canvas.drawPath(paths.get(selected), casing);
            line.setStrokeWidth(Ui.dp(c, 6));
            line.setColor(Ui.labelColor(c, routes.get(selected).label));
            canvas.drawPath(paths.get(selected), line);
        }

        // Origin and destination
        dot.setColor(Ui.color(c, R.color.navy));
        canvas.drawCircle(startX, startY, Ui.dp(c, 7), dot);
        canvas.drawCircle(endX, endY, Ui.dp(c, 7), dot);
        dot.setColor(0xFFFFFFFF);
        canvas.drawCircle(startX, startY, Ui.dp(c, 3), dot);
        canvas.drawCircle(endX, endY, Ui.dp(c, 3), dot);

        // Hazard markers of the selected route
        for (int i = 0; i < markerXY.size(); i++) {
            float[] xy = markerXY.get(i);
            Hazard hz = markerHazards.get(i);
            markerFill.setColor(0xFFFFFFFF);
            canvas.drawCircle(xy[0], xy[1], Ui.dp(c, 15), markerFill);
            markerFill.setColor(Ui.hazardBg(c, hz));
            canvas.drawCircle(xy[0], xy[1], Ui.dp(c, 12), markerFill);
            if (hz.type == Hazard.Type.TRAFFIC) {
                drawTrafficIcon(canvas, xy[0], xy[1]);
            } else if (hz.type == Hazard.Type.RAIN) {
                drawWeatherIcon(canvas, xy[0], xy[1]);
            } else {
                // Keep distinct symbols for any other supported hazard types.
                markerText.setColor(Ui.hazardColor(c, hz));
                canvas.drawText(Ui.glyph(hz.type), xy[0], xy[1] + Ui.dp(c, 5), markerText);
            }
        }

        canvas.drawText("Traffic light = traffic  ·  Cloud/sun = weather  ·  Tap for details",
                Ui.dp(c, 10), getHeight() - Ui.dp(c, 10), hint);
    }

    /** Draw a recognizable traffic-light pictogram; marker background still shows severity. */
    private void drawTrafficIcon(Canvas canvas, float cx, float cy) {
        float d = Ui.dp(getContext(), 1);
        float radius = Ui.dp(getContext(), 1.7f);

        // Dark traffic-signal housing.
        iconFill.setColor(0xFF263238);
        canvas.drawRoundRect(new RectF(cx - 4.4f * d, cy - 8.2f * d,
                cx + 4.4f * d, cy + 6.5f * d), 2.2f * d, 2.2f * d, iconFill);

        // Familiar red / amber / green signal lights. Risk severity is encoded by the marker
        // background, so these colors identify the icon rather than claiming a signal state.
        iconFill.setColor(0xFFFF5252);
        canvas.drawCircle(cx, cy - 4.6f * d, radius, iconFill);
        iconFill.setColor(0xFFFFCA28);
        canvas.drawCircle(cx, cy - 0.8f * d, radius, iconFill);
        iconFill.setColor(0xFF69F0AE);
        canvas.drawCircle(cx, cy + 3.0f * d, radius, iconFill);

        iconStroke.setColor(0xFF263238);
        iconStroke.setStrokeWidth(1.5f * d);
        canvas.drawLine(cx, cy + 6.5f * d, cx, cy + 8.4f * d, iconStroke);
        canvas.drawLine(cx - 2.3f * d, cy + 8.4f * d, cx + 2.3f * d, cy + 8.4f * d, iconStroke);
    }

    /** Draw a cloud-and-sun weather pictogram; marker background communicates forecast severity. */
    private void drawWeatherIcon(Canvas canvas, float cx, float cy) {
        float d = Ui.dp(getContext(), 1);

        // Sun behind the cloud.
        iconFill.setColor(0xFFFFB300);
        canvas.drawCircle(cx + 3.3f * d, cy - 4.4f * d, 2.7f * d, iconFill);
        iconStroke.setColor(0xFFFFB300);
        iconStroke.setStrokeWidth(1.2f * d);
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * i / 4.0;
            float x1 = cx + 3.3f * d + (float) Math.cos(a) * 3.3f * d;
            float y1 = cy - 4.4f * d + (float) Math.sin(a) * 3.3f * d;
            float x2 = cx + 3.3f * d + (float) Math.cos(a) * 4.2f * d;
            float y2 = cy - 4.4f * d + (float) Math.sin(a) * 4.2f * d;
            canvas.drawLine(x1, y1, x2, y2, iconStroke);
        }

        // Filled cloud outline.
        Path cloud = new Path();
        cloud.moveTo(cx - 6.7f * d, cy + 3.3f * d);
        cloud.cubicTo(cx - 9.0f * d, cy + 3.3f * d, cx - 9.0f * d, cy - 0.5f * d,
                cx - 6.3f * d, cy - 1.2f * d);
        cloud.cubicTo(cx - 6.2f * d, cy - 4.5f * d, cx - 2.4f * d, cy - 5.6f * d,
                cx - 0.1f * d, cy - 3.4f * d);
        cloud.cubicTo(cx + 2.2f * d, cy - 5.1f * d, cx + 5.9f * d, cy - 3.8f * d,
                cx + 6.0f * d, cy - 0.9f * d);
        cloud.cubicTo(cx + 9.2f * d, cy - 0.4f * d, cx + 8.7f * d, cy + 3.3f * d,
                cx + 5.9f * d, cy + 3.3f * d);
        cloud.close();
        iconFill.setColor(0xFFFFFFFF);
        canvas.drawPath(cloud, iconFill);
        iconStroke.setColor(0xFF1565C0);
        iconStroke.setStrokeWidth(1.4f * d);
        canvas.drawPath(cloud, iconStroke);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_DOWN) return !markerXY.isEmpty();
        if (e.getAction() == MotionEvent.ACTION_UP) {
            float radius = Ui.dp(getContext(), 26);
            int best = -1;
            float bestD = Float.MAX_VALUE;
            for (int i = 0; i < markerXY.size(); i++) {
                float[] xy = markerXY.get(i);
                float d = (float) Math.hypot(e.getX() - xy[0], e.getY() - xy[1]);
                if (d < radius && d < bestD) {
                    bestD = d;
                    best = i;
                }
            }
            if (best >= 0 && listener != null) {
                performClick();
                listener.onHazardTapped(markerHazards.get(best));
                return true;
            }
        }
        return super.onTouchEvent(e);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
