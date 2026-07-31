package com.floatinglauncher;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
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
    private SharedPreferences prefs;
    private int bubbleSize = 56;
    private float alpha = 0.9f;
    private long lastTapTime = 0;
    private boolean useFreeform = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("floating_launcher", MODE_PRIVATE);
        bubbleSize = prefs.getInt("bubble_size", 56);
        alpha = prefs.getFloat("bubble_alpha", 0.9f);
        useFreeform = prefs.getBoolean("use_freeform", false);
        selectedAppPackage = prefs.getString("selected_app_package", null);
        selectedAppName = prefs.getString("selected_app_name", null);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST);
        } else {
            showFloatingViewWithPicker();
        }
    }

    private void showFloatingViewWithPicker() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
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
        params.x = prefs.getInt("bubble_x", 100);
        params.y = prefs.getInt("bubble_y", 200);
        params.alpha = alpha;

        windowManager.addView(floatingView, params);
        isFloatingShowing = true;

        applyBubbleSize();
        updateIcon();

        floatingView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            private long touchStartTime;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        touchStartTime = System.currentTimeMillis();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(floatingView, params);
                        return true;
                    case MotionEvent.ACTION_UP:
                        float dx = event.getRawX() - initialTouchX;
                        float dy = event.getRawY() - initialTouchY;
                        long duration = System.currentTimeMillis() - touchStartTime;
                        
                        if (duration > 800) {
                            showSettingsDialog();
                            return true;
                        }
                        
                        if (Math.abs(dx) < 10 && Math.abs(dy) < 10) {
                            long now = System.currentTimeMillis();
                            if (now - lastTapTime < 300) {
                                // Double tap - launch in freeform if enabled
                                if (useFreeform) {
                                    launchFreeformApp();
                                } else {
                                    showAppPickerDialog();
                                }
                                lastTapTime = 0;
                            } else {
                                if (selectedAppPackage != null) {
                                    launchApp(useFreeform);
                                } else {
                                    showAppPickerDialog();
                                }
                                lastTapTime = now;
                            }
                        }
                        prefs.edit().putInt("bubble_x", params.x).putInt("bubble_y", params.y).apply();
                        return true;
                }
                return false;
            }
        });

        floatingView.findViewById(R.id.closeButton).setOnClickListener(v -> {
            if (floatingView != null && windowManager != null) {
                windowManager.removeView(floatingView);
                isFloatingShowing = false;
            }
            finish();
        });
    }

    private void applyBubbleSize() {
        View container = floatingView.findViewById(R.id.bubbleContainer);
        ImageView icon = floatingView.findViewById(R.id.floatingIcon);
        View closeBtn = floatingView.findViewById(R.id.closeButton);
        
        if (container != null) {
            container.getLayoutParams().width = bubbleSize;
            container.getLayoutParams().height = bubbleSize;
        }
        if (icon != null) {
            icon.getLayoutParams().width = bubbleSize - 12;
            icon.getLayoutParams().height = bubbleSize - 12;
        }
        if (closeBtn != null) {
            closeBtn.getLayoutParams().width = bubbleSize / 3;
            closeBtn.getLayoutParams().height = bubbleSize / 3;
        }
    }

    private void updateIcon() {
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

    private void showSettingsDialog() {
        String freeformStatus = useFreeform ? "ON ✅" : "OFF ❌";
        String[] options = {
            "Change app",
            "Freeform mode: " + freeformStatus,
            "Bubble size: " + bubbleSize + "px",
            "Transparency: " + Math.round(alpha * 100) + "%",
            "Reset position"
        };
        
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Light_Dialog)
            .setTitle("⚙️ Settings")
            .setItems(options, (dialog, which) -> {
                switch (which) {
                    case 0:
                        showAppPickerDialog();
                        break;
                    case 1:
                        toggleFreeform();
                        break;
                    case 2:
                        showSizePicker();
                        break;
                    case 3:
                        showAlphaPicker();
                        break;
                    case 4:
                        params.x = 100;
                        params.y = 200;
                        windowManager.updateViewLayout(floatingView, params);
                        prefs.edit().putInt("bubble_x", 100).putInt("bubble_y", 200).apply();
                        Toast.makeText(this, "Position reset", Toast.LENGTH_SHORT).show();
                        break;
                }
            })
            .setNegativeButton("Close", null)
            .show();
    }

    private void toggleFreeform() {
        useFreeform = !useFreeform;
        prefs.edit().putBoolean("use_freeform", useFreeform).apply();
        Toast.makeText(this, "Freeform mode: " + (useFreeform ? "ON" : "OFF"), Toast.LENGTH_SHORT).show();
        showSettingsDialog();
    }

    private void showSizePicker() {
        int[] sizes = {36, 48, 56, 64, 72, 80};
        String[] labels = {"Small (36)", "Medium (48)", "Default (56)", "Large (64)", "XL (72)", "XXL (80)"};
        
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Light_Dialog)
            .setTitle("Bubble size")
            .setSingleChoiceItems(labels, getIndex(sizes, bubbleSize), (dialog, which) -> {
                bubbleSize = sizes[which];
                prefs.edit().putInt("bubble_size", bubbleSize).apply();
                applyBubbleSize();
                windowManager.updateViewLayout(floatingView, params);
                dialog.dismiss();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private int getIndex(int[] arr, int val) {
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] == val) return i;
        }
        return 2;
    }

    private void showAlphaPicker() {
        float[] alphas = {0.3f, 0.5f, 0.7f, 0.9f, 1.0f};
        String[] labels = {"30%", "50%", "70%", "90%", "100%"};
        
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Light_Dialog)
            .setTitle("Transparency")
            .setSingleChoiceItems(labels, getIndexF(alphas, alpha), (dialog, which) -> {
                alpha = alphas[which];
                prefs.edit().putFloat("bubble_alpha", alpha).apply();
                params.alpha = alpha;
                windowManager.updateViewLayout(floatingView, params);
                dialog.dismiss();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private int getIndexF(float[] arr, float val) {
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] == val) return i;
        }
        return 3;
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

        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Light_Dialog)
            .setTitle("Select app")
            .setItems(appNames.toArray(new String[0]), (dialog, which) -> {
                selectedAppPackage = appPackages.get(which);
                selectedAppName = appNames.get(which);
                prefs.edit()
                    .putString("selected_app_package", selectedAppPackage)
                    .putString("selected_app_name", selectedAppName)
                    .apply();
                Toast.makeText(this, "App set: " + selectedAppName, Toast.LENGTH_SHORT).show();
                updateIcon();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void launchApp(boolean freeform) {
        if (selectedAppPackage == null) {
            showAppPickerDialog();
            return;
        }
        try {
            Intent launchIntent = getPackageManager().getLaunchIntentForPackage(selectedAppPackage);
            if (launchIntent == null) {
                Toast.makeText(this, "Cannot launch", Toast.LENGTH_SHORT).show();
                return;
            }
            
            if (freeform && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // Launch in freeform mode (requires system support)
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT);
                startActivity(launchIntent, getFreeformOptions());
            } else {
                startActivity(launchIntent);
            }
        } catch (Exception e) {
            // Fallback to normal launch
            try {
                Intent fallback = getPackageManager().getLaunchIntentForPackage(selectedAppPackage);
                if (fallback != null) startActivity(fallback);
            } catch (Exception ex) {
                Toast.makeText(this, "Error: " + ex.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private Bundle getFreeformOptions() {
        Bundle options = new Bundle();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            options.putInt("android.activity.launchDisplayId", 0);
            options.putInt("android.activity.freeformWindow", 1);
        }
        return options;
    }

    private void launchFreeformApp() {
        launchApp(true);
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
            moveTaskToBack(true);
        } else {
            super.onBackPressed();
        }
    }
}
