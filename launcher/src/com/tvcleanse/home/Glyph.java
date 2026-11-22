package com.tvcleanse.home;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/** Small line icons drawn in code, so the app needs no image files. */
public class Glyph extends Drawable {

    public static final int GEAR = 0;
    public static final int INPUT = 1;
    public static final int PLUS = 2;
    public static final int CHECK = 3;

    private final int kind;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public Glyph(int kind, int color) {
        this.kind = kind;
        paint.setColor(color);
    }

    public void setColor(int color) {
        paint.setColor(color);
        invalidateSelf();
    }

    @Override
    public void draw(Canvas canvas) {
        Rect b = getBounds();
        float s = Math.min(b.width(), b.height());
        float x0 = b.left + (b.width() - s) / 2f;
        float y0 = b.top + (b.height() - s) / 2f;
        if (kind == GEAR) {
            drawGear(canvas, x0, y0, s);
        } else if (kind == PLUS) {
            drawPlus(canvas, x0, y0, s);
        } else if (kind == CHECK) {
            drawCheck(canvas, x0, y0, s);
        } else {
            drawInput(canvas, x0, y0, s);
        }
    }

    private void drawPlus(Canvas c, float x0, float y0, float s) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(s * 0.09f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        c.drawLine(x0 + s * 0.5f, y0 + s * 0.2f, x0 + s * 0.5f, y0 + s * 0.8f, paint);
        c.drawLine(x0 + s * 0.2f, y0 + s * 0.5f, x0 + s * 0.8f, y0 + s * 0.5f, paint);
    }

    private void drawCheck(Canvas c, float x0, float y0, float s) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(s * 0.16f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        Path p = new Path();
        p.moveTo(x0 + s * 0.16f, y0 + s * 0.54f);
        p.lineTo(x0 + s * 0.4f, y0 + s * 0.76f);
        p.lineTo(x0 + s * 0.84f, y0 + s * 0.28f);
        c.drawPath(p, paint);
    }

    private void drawGear(Canvas c, float x0, float y0, float s) {
        float cx = x0 + s / 2f, cy = y0 + s / 2f;
        float outer = s * 0.46f, body = s * 0.34f, hole = s * 0.14f, tooth = s * 0.17f;
        Path gear = new Path();
        gear.addCircle(cx, cy, body, Path.Direction.CW);
        for (int i = 0; i < 8; i++) {
            Path t = new Path();
            t.addRoundRect(new RectF(cx - tooth / 2f, cy - outer, cx + tooth / 2f, cy - body + s * 0.04f),
                    s * 0.03f, s * 0.03f, Path.Direction.CW);
            android.graphics.Matrix m = new android.graphics.Matrix();
            m.setRotate(i * 45f, cx, cy);
            t.transform(m);
            gear.op(t, Path.Op.UNION);
        }
        Path h = new Path();
        h.addCircle(cx, cy, hole, Path.Direction.CW);
        gear.op(h, Path.Op.DIFFERENCE);
        paint.setStyle(Paint.Style.FILL);
        c.drawPath(gear, paint);
    }

    private void drawInput(Canvas c, float x0, float y0, float s) {
        float w = s * 0.085f;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(w);
        paint.setStrokeCap(Paint.Cap.ROUND);
        RectF screen = new RectF(x0 + s * 0.1f, y0 + s * 0.18f, x0 + s * 0.9f, y0 + s * 0.7f);
        c.drawRoundRect(screen, s * 0.08f, s * 0.08f, paint);
        c.drawLine(x0 + s * 0.34f, y0 + s * 0.84f, x0 + s * 0.66f, y0 + s * 0.84f, paint);
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        paint.setColorFilter(cf);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
