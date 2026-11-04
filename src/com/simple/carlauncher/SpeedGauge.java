package com.simple.carlauncher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.view.View;

/** Speedometer: 270 degree arc with ticks and the speed (GPS, km/h) in the middle. */
public class SpeedGauge extends View {

    private static final float MAX_KMH = 180f;
    private static final float START = 135f, SWEEP = 270f;
    private static final int TICKS = 10; // one every 20 km/h

    private final float d;
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint num = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint unit = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint over = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint overGlow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint limitMark = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint limitText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int limit; // km/h, 0 = no alert
    private final RectF oval = new RectF();

    private float shown;          // value drawn by the arc (animated)
    private float target = -2f;   // -1 = no GPS fix
    private float cx, cy, radius;
    private ValueAnimator anim;

    public SpeedGauge(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        setLayerType(LAYER_TYPE_SOFTWARE, null); // BlurMaskFilter needs it

        for (Paint p : new Paint[]{track, arc, glow, over, overGlow}) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
        }
        track.setStrokeWidth(10 * d);
        track.setColor(0x2EFFFFFF);
        arc.setStrokeWidth(10 * d);
        glow.setStrokeWidth(16 * d);
        glow.setMaskFilter(new BlurMaskFilter(7 * d, BlurMaskFilter.Blur.NORMAL));
        over.setStrokeWidth(10 * d);
        over.setColor(0xFFFF4D6A);
        overGlow.setStrokeWidth(16 * d);
        overGlow.setColor(0xFFFF4D6A);
        overGlow.setMaskFilter(new BlurMaskFilter(7 * d, BlurMaskFilter.Blur.NORMAL));
        limitMark.setStyle(Paint.Style.STROKE);
        limitMark.setStrokeWidth(3 * d);
        limitMark.setColor(0xFFFFC94D);
        limitText.setColor(0xFFFFC94D);
        limitText.setTextAlign(Paint.Align.CENTER);
        limitText.setTypeface(Typeface.DEFAULT_BOLD);

        tick.setStyle(Paint.Style.STROKE);
        tick.setStrokeWidth(2 * d);
        tick.setColor(0x59FFFFFF);

        num.setColor(0xFFFFFFFF);
        num.setTextAlign(Paint.Align.CENTER);
        num.setTypeface(Typeface.DEFAULT_BOLD);
        unit.setColor(0xB3FFFFFF);
        unit.setTextAlign(Paint.Align.CENTER);
    }

    /** Speed alert limit in km/h (0 = off): shown on the dial, and the arc turns red above it. */
    public void setLimit(int kmh) {
        limit = kmh;
        invalidate();
    }

    /** Speed in km/h, or a negative value when there is no GPS fix (shows "--"). */
    public void setSpeed(float kmh) {
        if (kmh == target) return;
        target = kmh;
        float to = kmh < 0 ? 0 : Math.min(kmh, MAX_KMH);
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(shown, to);
        anim.setDuration(600);
        anim.addUpdateListener(a -> {
            shown = (float) a.getAnimatedValue();
            invalidate();
        });
        anim.start();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        float s = Math.min(w, h);
        cx = w / 2f;
        cy = h / 2f + s * 0.02f;
        radius = s / 2f - 14 * d;
        oval.set(cx - radius, cy - radius, cx + radius, cy + radius);

        SweepGradient g = new SweepGradient(cx, cy,
                new int[]{0xFF2EE6FF, 0xFF4F7BFF, 0xFFB45CFF, 0xFFB45CFF},
                new float[]{0f, 0.35f, 0.75f, 1f});
        Matrix m = new Matrix();
        m.setRotate(START, cx, cy); // gradient starts where the arc starts
        g.setLocalMatrix(m);
        arc.setShader(g);
        glow.setShader(g);

        num.setTextSize(radius * 0.8f);
        unit.setTextSize(radius * 0.26f);
        limitText.setTextSize(radius * 0.2f);
    }

    @Override
    protected void onDraw(Canvas c) {
        c.drawArc(oval, START, SWEEP, false, track);

        for (int i = 0; i < TICKS; i++) {
            double a = Math.toRadians(START + SWEEP * i / (TICKS - 1f));
            float in = radius - 15 * d, out = radius - 9 * d;
            c.drawLine(cx + (float) Math.cos(a) * in, cy + (float) Math.sin(a) * in,
                    cx + (float) Math.cos(a) * out, cy + (float) Math.sin(a) * out, tick);
        }

        boolean tooFast = limit > 0 && target > limit;
        if (shown > 0.5f) {
            float sweep = SWEEP * shown / MAX_KMH;
            c.drawArc(oval, START, sweep, false, tooFast ? overGlow : glow);
            c.drawArc(oval, START, sweep, false, tooFast ? over : arc);
        }
        if (limit > 0) {
            double a = Math.toRadians(START + SWEEP * Math.min(limit, MAX_KMH) / MAX_KMH);
            float in = radius - 9 * d, out = radius + 9 * d;
            c.drawLine(cx + (float) Math.cos(a) * in, cy + (float) Math.sin(a) * in,
                    cx + (float) Math.cos(a) * out, cy + (float) Math.sin(a) * out, limitMark);
            c.drawText("LÍMITE " + limit, cx, cy - radius * 0.46f, limitText);
        }

        String text = target < 0 ? "--" : String.valueOf(Math.round(target));
        c.drawText(text, cx, cy + num.getTextSize() * 0.34f, num);
        c.drawText("km/h", cx, cy + radius * 0.86f, unit);
    }
}
