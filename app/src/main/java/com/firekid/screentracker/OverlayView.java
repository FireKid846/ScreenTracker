package com.firekid.screentracker;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

public class OverlayView extends View {

    private WindowManager windowManager;
    private WindowManager.LayoutParams params;

    private Paint paintCrosshair;
    private Paint paintDetection;
    private Paint paintText;

    private int screenWidth;
    private int screenHeight;

    private int midpointX = -1;
    private int midpointY = -1;
    private boolean selectingMidpoint = false;

    private ObjectDetector.Detection[] detections;
    private int panOffsetX = 0;
    private int panOffsetY = 0;

    private MidpointListener midpointListener;

    public interface MidpointListener {
        void onMidpointSelected(int x, int y);
    }

    public OverlayView(Context context, int width, int height) {
        super(context);

        this.screenWidth = width;
        this.screenHeight = height;

        windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);

        paintCrosshair = new Paint();
        paintCrosshair.setColor(Color.YELLOW);
        paintCrosshair.setStrokeWidth(4);
        paintCrosshair.setStyle(Paint.Style.STROKE);
        paintCrosshair.setAntiAlias(true);

        paintDetection = new Paint();
        paintDetection.setColor(Color.GREEN);
        paintDetection.setStrokeWidth(3);
        paintDetection.setStyle(Paint.Style.STROKE);
        paintDetection.setAntiAlias(true);

        paintText = new Paint();
        paintText.setColor(Color.WHITE);
        paintText.setTextSize(30);
        paintText.setAntiAlias(true);

        int layoutFlag;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutFlag = WindowManager.LayoutParams.TYPE_PHONE;
        }

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.x = 0;
        params.y = 0;

        windowManager.addView(this, params);
    }

    public void setMidpointListener(MidpointListener listener) {
        this.midpointListener = listener;
    }

    public void enableMidpointSelection() {
        this.selectingMidpoint = true;
        params.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        windowManager.updateViewLayout(this, params);
        invalidate();
    }

    public void setMidpoint(int x, int y) {
        this.midpointX = x;
        this.midpointY = y;
        this.selectingMidpoint = false;
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        windowManager.updateViewLayout(this, params);
        invalidate();
    }

    public void updateDetections(ObjectDetector.Detection[] detections) {
        this.detections = detections;
        invalidate();
    }

    public void setPanOffset(int x, int y) {
        this.panOffsetX = x;
        this.panOffsetY = y;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (selectingMidpoint) {
            canvas.drawColor(0x40000000);
            String text = "TAP TO SET MIDPOINT";
            float textWidth = paintText.measureText(text);
            canvas.drawText(text, (screenWidth - textWidth) / 2, screenHeight / 2, paintText);
        }

        if (detections != null) {
            for (ObjectDetector.Detection det : detections) {
                canvas.drawRect(det.x1, det.y1, det.x2, det.y2, paintDetection);
                String label = String.format("%.2f", det.confidence);
                canvas.drawText(label, det.x1, det.y1 - 10, paintText);
            }
        }

        if (midpointX >= 0 && midpointY >= 0) {
            int size = 40;
            canvas.drawLine(midpointX - size, midpointY, midpointX + size, midpointY, paintCrosshair);
            canvas.drawLine(midpointX, midpointY - size, midpointX, midpointY + size, paintCrosshair);
            canvas.drawCircle(midpointX, midpointY, 8, paintCrosshair);
        }

        if (panOffsetX != 0 || panOffsetY != 0) {
            String offsetText = String.format("Pan: (%d, %d)", panOffsetX, panOffsetY);
            canvas.drawText(offsetText, 20, 50, paintText);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (selectingMidpoint && event.getAction() == MotionEvent.ACTION_DOWN) {
            int x = (int) event.getX();
            int y = (int) event.getY();

            if (midpointListener != null) {
                midpointListener.onMidpointSelected(x, y);
            }

            return true;
        }

        return super.onTouchEvent(event);
    }

    public void remove() {
        try {
            if (windowManager != null) {
                windowManager.removeView(this);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
