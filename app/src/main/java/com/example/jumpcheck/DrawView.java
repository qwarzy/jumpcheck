package com.example.jumpcheck;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import com.google.mlkit.vision.pose.Pose;
import com.google.mlkit.vision.pose.PoseLandmark;

public class DrawView extends View {
    private Pose pose;
    private final Paint paint = new Paint();

    public DrawView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint.setColor(Color.GREEN);
        paint.setStrokeWidth(10f);
        paint.setStyle(Paint.Style.STROKE);
    }

    public void setPose(Pose pose) {
        this.pose = pose;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (pose == null) return;

        drawEdge(canvas, PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER);
        drawEdge(canvas, PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP);
        drawEdge(canvas, PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_KNEE);
        drawEdge(canvas, PoseLandmark.LEFT_KNEE, PoseLandmark.LEFT_ANKLE);
        drawEdge(canvas, PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_KNEE);
        drawEdge(canvas, PoseLandmark.RIGHT_KNEE, PoseLandmark.RIGHT_ANKLE);
    }

    private void drawEdge(Canvas canvas, int startId, int endId) {
        PoseLandmark start = pose.getPoseLandmark(startId);
        PoseLandmark end = pose.getPoseLandmark(endId);
        if (start != null && end != null) {
            canvas.drawLine(start.getPosition().x, start.getPosition().y,
                    end.getPosition().x, end.getPosition().y, paint);
        }
    }
}