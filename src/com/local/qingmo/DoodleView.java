package com.local.qingmo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

final class DoodleView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    DoodleView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        float cx = width * 0.58f;
        float cy = height * 0.48f;
        float flowerRadius = Math.min(width, height) * 0.12f;

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x33FF9C88);
        canvas.drawCircle(width * 0.78f, height * 0.26f, width * 0.27f, paint);

        paint.setColor(0xFFFFFBF6);
        for (int index = 0; index < 6; index++) {
            float angle = (float) (Math.PI * 2 * index / 6f);
            float petalX = cx + (float) Math.cos(angle) * flowerRadius * 1.25f;
            float petalY = cy + (float) Math.sin(angle) * flowerRadius * 1.25f;
            RectF petal = new RectF(
                    petalX - flowerRadius,
                    petalY - flowerRadius * 0.72f,
                    petalX + flowerRadius,
                    petalY + flowerRadius * 0.72f
            );
            canvas.save();
            canvas.rotate((float) (index * 60), petal.centerX(), petal.centerY());
            canvas.drawOval(petal, paint);
            canvas.restore();
        }
        paint.setColor(0xFFFFB49E);
        canvas.drawCircle(cx, cy, flowerRadius * 0.62f, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, width * 0.014f));
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(0xFF8FBFA8);
        Path stem = new Path();
        stem.moveTo(cx + flowerRadius * 0.35f, cy + flowerRadius * 0.72f);
        stem.cubicTo(
                cx + flowerRadius * 0.85f,
                cy + flowerRadius * 2.2f,
                cx + flowerRadius * 0.25f,
                cy + flowerRadius * 3f,
                cx + flowerRadius * 0.75f,
                height * 0.92f
        );
        canvas.drawPath(stem, paint);

        paint.setStyle(Paint.Style.FILL);
        Path leaf = new Path();
        leaf.moveTo(cx + flowerRadius * 0.68f, height * 0.75f);
        leaf.quadTo(cx + flowerRadius * 2.3f, height * 0.62f, cx + flowerRadius * 1.65f, height * 0.84f);
        leaf.quadTo(cx + flowerRadius * 1f, height * 0.84f, cx + flowerRadius * 0.68f, height * 0.75f);
        canvas.drawPath(leaf, paint);

        paint.setColor(0x66FF8A7A);
        canvas.drawCircle(width * 0.19f, height * 0.2f, width * 0.018f, paint);
        canvas.drawCircle(width * 0.86f, height * 0.78f, width * 0.012f, paint);
        paint.setColor(Color.TRANSPARENT);
    }
}
