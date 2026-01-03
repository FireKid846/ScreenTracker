package com.firekid.screentracker;

import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final int REQUEST_MEDIA_PROJECTION = 1001;
    private static final int REQUEST_OVERLAY_PERMISSION = 1002;

    private Button btnStart;
    private Button btnStop;
    private Button btnSetMidpoint;
    private TextView tvStatus;

    private MediaProjectionManager projectionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnStart = findViewById(R.id.btnStart);
        btnStop = findViewById(R.id.btnStop);
        btnSetMidpoint = findViewById(R.id.btnSetMidpoint);
        tvStatus = findViewById(R.id.tvStatus);

        projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);

        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startTracking();
            }
        });

        btnStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopTracking();
            }
        });

        btnSetMidpoint.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setMidpoint();
            }
        });

        updateUI(false);
        checkOverlayPermission();
    }

    private void checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION);
            }
        }
    }

    private void startTracking() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Overlay permission required", Toast.LENGTH_SHORT).show();
                checkOverlayPermission();
                return;
            }
        }

        Intent captureIntent = projectionManager.createScreenCaptureIntent();
        startActivityForResult(captureIntent, REQUEST_MEDIA_PROJECTION);
    }

    private void stopTracking() {
        Intent intent = new Intent(this, ScreenCaptureService.class);
        intent.setAction("STOP");
        startService(intent);
        updateUI(false);
        tvStatus.setText("Tracking stopped");
    }

    private void setMidpoint() {
        Intent intent = new Intent(this, ScreenCaptureService.class);
        intent.setAction("SELECT_MIDPOINT");
        startService(intent);
        Toast.makeText(this, "Tap screen to set tracking point", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode == RESULT_OK) {
                Intent intent = new Intent(this, ScreenCaptureService.class);
                intent.setAction("START");
                intent.putExtra("resultCode", resultCode);
                intent.putExtra("data", data);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent);
                } else {
                    startService(intent);
                }

                updateUI(true);
                tvStatus.setText("Tracking started - Tap Set Midpoint");
                moveTaskToBack(true);
            } else {
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void updateUI(boolean tracking) {
        btnStart.setEnabled(!tracking);
        btnStop.setEnabled(tracking);
        btnSetMidpoint.setEnabled(tracking);
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean running = ScreenCaptureService.isRunning();
        updateUI(running);
        tvStatus.setText(running ? "Tracking active" : "Ready to start");
    }
}
