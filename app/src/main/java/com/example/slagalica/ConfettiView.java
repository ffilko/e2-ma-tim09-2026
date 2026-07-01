package com.example.slagalica;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import java.util.Random;

public class ConfettiView extends View {

    private static final int COUNT = 90;
    private final Random rnd = new Random();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final int[] colors = {
            Color.parseColor("#FFC107"), Color.parseColor("#2596be"),
            Color.parseColor("#E91E63"), Color.parseColor("#4CAF50"),
            Color.parseColor("#9C27B0"), Color.parseColor("#FF5722")
    };

    private float[] x, y, size, speed, angle, spin, rot;
    private int[] color;
    private boolean initialized = false;
    private boolean running = true;

    public ConfettiView(Context c) { super(c); }
    public ConfettiView(Context c, AttributeSet a) { super(c, a); }
    public ConfettiView(Context c, AttributeSet a, int s) { super(c, a, s); }

    private void initParticles(int w, int h) {
        x = new float[COUNT]; y = new float[COUNT]; size = new float[COUNT];
        speed = new float[COUNT]; angle = new float[COUNT];
        spin = new float[COUNT]; rot = new float[COUNT]; color = new int[COUNT];
        for (int i = 0; i < COUNT; i++) {
            x[i] = rnd.nextFloat() * w;
            y[i] = -rnd.nextFloat() * h;
            size[i] = 12 + rnd.nextFloat() * 18;
            speed[i] = 4 + rnd.nextFloat() * 8;
            angle[i] = (rnd.nextFloat() - 0.5f) * 2f;
            spin[i] = (rnd.nextFloat() - 0.5f) * 12f;
            rot[i] = rnd.nextFloat() * 360f;
            color[i] = colors[rnd.nextInt(colors.length)];
        }
        initialized = true;
    }

    public void stop() { running = false; }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        if (!initialized) initParticles(w, h);

        for (int i = 0; i < COUNT; i++) {
            paint.setColor(color[i]);
            canvas.save();
            canvas.translate(x[i], y[i]);
            canvas.rotate(rot[i]);
            canvas.drawRect(-size[i] / 2, -size[i] / 4, size[i] / 2, size[i] / 4, paint);
            canvas.restore();

            y[i] += speed[i];
            x[i] += angle[i] * 2;
            rot[i] += spin[i];
            if (y[i] > h + 20) {
                y[i] = -20;
                x[i] = rnd.nextFloat() * w;
            }
        }
        if (running) postInvalidateOnAnimation();
    }
}
