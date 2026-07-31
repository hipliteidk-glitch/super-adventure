package com.floatinglauncher;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;

public class MainActivity extends AppCompatActivity {
    private static final int OVERLAY_PERMISSION_REQUEST = 123;
    private static final int PICK_APP_REQUEST = 456;
    private WindowManager windowManager;
    private View floatingView;
    private WindowManager.LayoutParams params;
    private String selectedAppPackage = null;
    private String selectedAppName = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Check overlay permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST);
        } else {
            showAppPicker();
        }
    }

    private void showAppPicker() {
        // Show dialog with list of installed apps
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select app to launch");
        
        PackageManager pm = getPackageManager();
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        
        final java.util.List<ResolveInfo> apps = pm.queryIntentActivities(mainIntent, 0);
        String[] appNames = new String[apps.size()];
        final String[] appPackages = new String[apps.size()];
        
        for (int i = 0; i < apps.size(); i++) {
            appNames[i] = apps.get(i).loadLabel(pm).toString();
            appPackages[i] = apps.get(i).activityInfo.packageName;
        }
        
        builder.setItems(appNames, (dialog, which) -> {
            selectedAppPackage = appPackages[which];
            selectedAppName = appNames[which];
            Toast.makeText(MainActivity.this, "Selected: " + selectedAppName, Toast.LENGTH_SHORT).show();
            showFloatingView();
        });
        
        builder.setNegativeButton("Cancel", (dialog, which) -> finish());
        builder.show();
    }

    private void showFloatingView() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        // Inflate the floating view layout
        floatingView = getLayoutInflater().inflate(R.layout.floating_view, null);

        int layoutFlag;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutFlag = WindowManager.LayoutParams.TYPE_PHONE;
        }

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 100;
        params.y = 100;

        windowManager.addView(floatingView, params);

        // Drag listener
        floatingView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(floatingView, params);
                        return true;
                    case MotionEvent.ACTION_UP:
                        // Check if it was a tap (not a drag)
                        float dx = event.getRawX() - initialTouchX;
                        float dy = event.getRawY() - initialTouchY;
                        if (Math.abs(dx) < 10 && Math.abs(dy) < 10) {
                            launchSelectedApp();
                        }
                        return true;
                }
                return false;
            }
        });

        // Close button
        floatingView.findViewById(R.id.closeButton).setOnClickListener(v -> {
            windowManager.removeView(floatingView);
            finish();
        });
    }

    private void launchSelectedApp() {
        if (selectedAppPackage != null) {
            try {
                Intent launchIntent = getPackageManager().getLaunchIntentForPackage(selectedAppPackage);
                if (launchIntent != null) {
                    startActivity(launchIntent);
                } else {
                    Toast.makeText(this, "Cannot launch " + selectedAppName, Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == OVERLAY_PERMISSION_REQUEST) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                showAppPicker();
            } else {
                Toast.makeText(this, "Overlay permission required!", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    @Override
    public void onBackPressed() {
        // Minimize to background instead of closing
        moveTaskToBack(true);
    }
}
