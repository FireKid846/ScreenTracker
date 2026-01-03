package com.firekid.screentracker;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import java.nio.ByteBuffer;

public class ScreenCaptureService extends Service {

    private static final String CHANNEL_ID = "ScreenTrackerChannel";
    private static final int NOTIFICATION_ID = 1;
    private static boolean isRunning = false;

    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private Handler handler;

    private int screenWidth;
    private int screenHeight;
    private int screenDensity;

    private OverlayView overlayView;
    private ObjectDetector objectDetector;
    private TrackingManager trackingManager;

    public static boolean isRunning() {
        return isRunning;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(metrics);
        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;
        screenDensity = metrics.densityDpi;

        handler = new Handler(Looper.getMainLooper());
        objectDetector = new ObjectDetector(this);
        trackingManager = new TrackingManager();

        isRunning = true;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();

            if ("START".equals(action)) {
                int resultCode = intent.getIntExtra("resultCode", -1);
                Intent data = intent.getParcelableExtra("data");
                if (resultCode != -1 && data != null) {
                    startForeground(NOTIFICATION_ID, createNotification());
                    startCapture(resultCode, data);
                }
            } else if ("STOP".equals(action)) {
                stopCapture();
                stopSelf();
            } else if ("SELECT_MIDPOINT".equals(action)) {
                if (overlayView != null) {
                    overlayView.enableMidpointSelection();
                }
            }
        }

        return START_STICKY;
    }

    private void startCapture(int resultCode, Intent data) {
        MediaProjectionManager manager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        mediaProjection = manager.getMediaProjection(resultCode, data);

        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2);

        virtualDisplay = mediaProjection.createVirtualDisplay(
                "ScreenTracker",
                screenWidth, screenHeight, screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(),
                null, handler
        );

        overlayView = new OverlayView(this, screenWidth, screenHeight);
        overlayView.setMidpointListener(new OverlayView.MidpointListener() {
            @Override
            public void onMidpointSelected(int x, int y) {
                trackingManager.setMidpoint(x, y);
                overlayView.setMidpoint(x, y);
            }
        });

        startProcessing();
    }

    private void startProcessing() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (mediaProjection == null) return;

                try {
                    Image image = imageReader.acquireLatestImage();
                    if (image != null) {
                        Bitmap bitmap = imageToBitmap(image);
                        if (bitmap != null) {
                            processFrame(bitmap);
                            bitmap.recycle();
                        }
                        image.close();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                handler.postDelayed(this, 66);
            }
        });
    }

    private Bitmap imageToBitmap(Image image) {
        try {
            Image.Plane[] planes = image.getPlanes();
            ByteBuffer buffer = planes[0].getBuffer();
            int pixelStride = planes[0].getPixelStride();
            int rowStride = planes[0].getRowStride();
            int rowPadding = rowStride - pixelStride * screenWidth;

            Bitmap bitmap = Bitmap.createBitmap(
                    screenWidth + rowPadding / pixelStride,
                    screenHeight,
                    Bitmap.Config.ARGB_8888
            );
            bitmap.copyPixelsFromBuffer(buffer);
            return bitmap;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void processFrame(Bitmap bitmap) {
        ObjectDetector.Detection[] detections = objectDetector.detect(bitmap);

        if (overlayView != null) {
            overlayView.updateDetections(detections);
        }

        if (trackingManager.hasMidpoint() && detections.length > 0) {
            ObjectDetector.Detection closest = findClosestDetection(detections);
            if (closest != null) {
                int[] offset = trackingManager.calculatePanOffset(closest.centerX, closest.centerY);
                if (overlayView != null) {
                    overlayView.setPanOffset(offset[0], offset[1]);
                }
            }
        }
    }

    private ObjectDetector.Detection findClosestDetection(ObjectDetector.Detection[] detections) {
        if (!trackingManager.hasMidpoint()) return null;

        int[] midpoint = trackingManager.getMidpoint();
        float minDist = Float.MAX_VALUE;
        ObjectDetector.Detection closest = null;

        for (ObjectDetector.Detection det : detections) {
            float dx = det.centerX - midpoint[0];
            float dy = det.centerY - midpoint[1];
            float dist = (float) Math.sqrt(dx * dx + dy * dy);

            if (dist < minDist) {
                minDist = dist;
                closest = det;
            }
        }

        return closest;
    }

    private void stopCapture() {
        if (overlayView != null) {
            overlayView.remove();
            overlayView = null;
        }

        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }

        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }

        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }

        if (objectDetector != null) {
            objectDetector.close();
        }

        isRunning = false;
    }

    private Notification createNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Screen Tracker",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        return builder
                .setContentTitle("Screen Tracker")
                .setContentText("Tracking active")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopCapture();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
