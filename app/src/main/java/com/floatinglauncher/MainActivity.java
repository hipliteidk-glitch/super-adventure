package com.floatinglauncher;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final int OVERLAY_PERMISSION_REQUEST = 123;
    private WindowManager windowManager;
    private View floatingView;
    private WindowManager.LayoutParams params;
    private String selectedAppPackage = null;
    private String selectedAppName = null;
    private boolean isFloatingShowing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Check overlay permission and show floating view immediately
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST);
        } else {
            // Show floating view directly
            showFloatingViewWithPicker();
        }
    }

    private void showFloatingViewWithPicker() {
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
        isFloatingShowing = true;

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
                        float dx = event.getRawX() - initialTouchX;
                        float dy = event.getRawY() - initialTouchY;
                        if (Math.abs(dx) < 10 && Math.abs(dy) < 10) {
                            // Tap - show app picker
                            showAppPickerDialog();
                        }
                        return true;
                }
                return false;
            }
        });

        // Close button
        floatingView.findViewById(R.id.closeButton).setOnClickListener(v -> {
            if (floatingView != null && windowManager != null) {
                windowManager.removeView(floatingView);
                isFloatingShowing = false;
            }
            finish();
        });

        // Set default app icon (or placeholder)
        ImageView icon = floatingView.findViewById(R.id.floatingIcon);
        if (selectedAppPackage != null) {
            try {
                icon.setImageDrawable(getPackageManager().getApplicationIcon(selectedAppPackage));
            } catch (PackageManager.NameNotFoundException e) {
                icon.setImageResource(android.R.drawable.ic_menu_compass);
            }
        } else {
            icon.setImageResource(android.R.drawable.ic_menu_compass);
        }
    }

    private void showAppPickerDialog() {
        PackageManager pm = getPackageManager();
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> apps = pm.queryIntentActivities(mainIntent, 0);
        final List<String> appNames = new ArrayList<>();
        final List<String> appPackages = new ArrayList<>();

        for (ResolveInfo info : apps) {
            appNames.add(info.loadLabel(pm).toString());
            appPackages.add(info.activityInfo.packageName);
        }

        // Show dialog with app list
        AlertDialog.Builder builder = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Light_Dialog);
        builder.setTitle("Select app");
        builder.setItems(appNames.toArray(new String[0]), (dialog, which) -> {
            selectedAppPackage = appPackages.get(which);
            selectedAppName = appNames.get(which);
            Toast.makeText(MainActivity.this, "Launching " + selectedAppName, Toast.LENGTH_SHORT).show();
            
            // Update icon
            try {
                ImageView icon = floatingView.findViewById(R.id.floatingIcon);
                icon.setImageDrawable(pm.getApplicationIcon(selectedAppPackage));
            } catch (PackageManager.NameNotFoundException e) {
                // Keep default
            }
            
            // Launch the app
            launchSelectedApp();
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
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
        } else {
            showAppPickerDialog();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == OVERLAY_PERMISSION_REQUEST) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                showFloatingViewWithPicker();
            } else {
                Toast.makeText(this, "Overlay permission required!", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (isFloatingShowing) {
            // Minimize to background
            moveTaskToBack(true);
        } else {
            super.onBackPressed();
        }
    }
}
