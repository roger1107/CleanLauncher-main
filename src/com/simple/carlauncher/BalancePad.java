package com.simple.carlauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;

/** Top view of the cabin; the glowing dot is where the sound is focused (balance x, fader y). */
public class BalancePad extends View {

    public interface Listener {
        void onChanged(int balance, int fader, boolean done);
    }

    private final int max;
    private int balance, fader;
    private Listener listener;
    private final Paint body = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint seat = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final float d;

    public BalancePad(Context c, int max) {
        super(c);
        this.max = max;
        d = c.getResources().getDisplayMetrics().density;
        body.setStyle(Paint.Style.STROKE);
        body.setStrokeWidth(2 * d);
        body.setColor(0x66FFFFFF);
        seat.setColor(0x1FFFFFFF);
        label.setColor(0x99FFFFFF);
        label.setTextSize(11 * d);
        label.setTextAlign(Paint.Align.CENTER);
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public void setValues(int balance, int fader) {
        this.balance = balance;
        this.fader = fader;
        invalidate();
    }

    private RectF cabin() {
        float w = getWidth(), h = getHeight(), cw = Math.min(w * 0.55f, h * 0.5f), ch = h - 30 * d;
        return new RectF((w - cw) / 2, 15 * d, (w + cw) / 2, 15 * d + ch);
    }

    @Override
    protected void onDraw(Canvas c) {
        RectF cab = cabin();
        c.drawRoundRect(cab, cab.width() * 0.35f, cab.width() * 0.35f, body);
        float sw = cab.width() * 0.28f, sh = cab.height() * 0.16f;
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 2; col++) {
                float cx = cab.left + cab.width() * (col == 0 ? 0.3f : 0.7f);
                float cy = cab.top + cab.height() * (row == 0 ? 0.38f : 0.68f);
                r.set(cx - sw / 2, cy - sh / 2, cx + sw / 2, cy + sh / 2);
                c.drawRoundRect(r, 6 * d, 6 * d, seat);
            }
        }
        c.drawText("ADELANTE", cab.centerX(), cab.top - 3 * d, label);
        c.drawText("ATRÁS", cab.centerX(), cab.bottom + 12 * d, label);

        float x = cab.left + cab.width() * balance / max;
        float y = cab.top + cab.height() * fader / max;
        dot.setShader(new RadialGradient(x, y, 26 * d, 0xCC2EE6FF, 0x002EE6FF, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, 26 * d, dot);
        dot.setShader(null);
        dot.setColor(0xFFFFFFFF);
        c.drawCircle(x, y, 7 * d, dot);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        RectF cab = cabin();
        int b = Math.round(Math.max(0, Math.min(1, (e.getX() - cab.left) / cab.width())) * max);
        int f = Math.round(Math.max(0, Math.min(1, (e.getY() - cab.top) / cab.height())) * max);
        boolean done = e.getAction() == MotionEvent.ACTION_UP;
        if (e.getAction() == MotionEvent.ACTION_DOWN) getParent().requestDisallowInterceptTouchEvent(true);
        if (b != balance || f != fader || done) {
            balance = b;
            fader = f;
            invalidate();
            if (listener != null) listener.onChanged(b, f, done);
        }
        return true;
    }
}
