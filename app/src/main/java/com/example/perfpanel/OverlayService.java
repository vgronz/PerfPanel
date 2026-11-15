package com.example.perfpanel;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class OverlayService extends Service {

    private WindowManager wm;
    private TextView bubble;
    private LinearLayout panel;
    private TextView stats;
    private WindowManager.LayoutParams bubbleLp;
    private WindowManager.LayoutParams panelLp;
    private boolean panelShown = false;
    private boolean created = false;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable updater = new Runnable() {
        @Override
        public void run() {
            if (panelShown) stats.setText(readStats());
            handler.postDelayed(this, 2000);
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startAsForeground();
        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!created) {
            createViews();
            created = true;
            handler.post(updater);
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(updater);
        if (created) {
            try {
                if (panelShown) wm.removeView(panel);
                wm.removeView(bubble);
            } catch (Exception ignored) {
            }
        }
        super.onDestroy();
    }

    private void startAsForeground() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(
                new NotificationChannel("perf", "Perf Panel", NotificationManager.IMPORTANCE_LOW));
        Notification n = new Notification.Builder(this, "perf")
                .setContentTitle("Perf Panel çalışıyor")
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(1, n);
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private void createViews() {
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        int type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;

        // Yüzen buton
        bubble = new TextView(this);
        bubble.setText("⚡");
        bubble.setTextSize(22);
        bubble.setGravity(Gravity.CENTER);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(0xFF1E88E5);
        bubble.setBackground(circle);

        bubbleLp = new WindowManager.LayoutParams(dp(48), dp(48), type, flags, PixelFormat.TRANSLUCENT);
        bubbleLp.gravity = Gravity.TOP | Gravity.START;
        bubbleLp.x = dp(16);
        bubbleLp.y = dp(120);

        // Panel
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xEE202124);
        bg.setCornerRadius(dp(16));
        panel.setBackground(bg);
        panel.setPadding(dp(14), dp(14), dp(14), dp(14));

        stats = new TextView(this);
        stats.setTextColor(0xFFFFFFFF);
        stats.setTextSize(14);
        stats.setText(readStats());
        panel.addView(stats);

        panel.addView(makeButton("Pil tasarrufu ayarları", v -> openSettings(Settings.ACTION_BATTERY_SAVER_SETTINGS)));
        panel.addView(makeButton("Ekran ayarları", v -> openSettings(Settings.ACTION_DISPLAY_SETTINGS)));
        panel.addView(makeButton("Paneli kapat", v -> stopSelf()));

        panelLp = new WindowManager.LayoutParams(dp(230), WindowManager.LayoutParams.WRAP_CONTENT,
                type, flags, PixelFormat.TRANSLUCENT);
        panelLp.gravity = Gravity.TOP | Gravity.START;
        updatePanelPosition();

        bubble.setOnTouchListener(new View.OnTouchListener() {
            int startX, startY;
            float touchX, touchY;
            boolean moved;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = bubbleLp.x;
                        startY = bubbleLp.y;
                        touchX = e.getRawX();
                        touchY = e.getRawY();
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = e.getRawX() - touchX;
                        float dy = e.getRawY() - touchY;
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) moved = true;
                        bubbleLp.x = startX + (int) dx;
                        bubbleLp.y = startY + (int) dy;
                        wm.updateViewLayout(bubble, bubbleLp);
                        if (panelShown) {
                            updatePanelPosition();
                            wm.updateViewLayout(panel, panelLp);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!moved) togglePanel();
                        return true;
                }
                return false;
            }
        });

        wm.addView(bubble, bubbleLp);
    }

    private void updatePanelPosition() {
        panelLp.x = bubbleLp.x;
        panelLp.y = bubbleLp.y + dp(56);
    }

    private void togglePanel() {
        if (panelShown) {
            wm.removeView(panel);
        } else {
            stats.setText(readStats());
            updatePanelPosition();
            wm.addView(panel, panelLp);
        }
        panelShown = !panelShown;
    }

    private Button makeButton(String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        return b;
    }

    private void openSettings(String action) {
        try {
            Intent i = new Intent(action);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception ignored) {
        }
    }

    private String readStats() {
        Intent b = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int level = -1, scale = 100;
        float temp = 0f;
        if (b != null) {
            level = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            scale = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            temp = b.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f;
        }
        ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        long freeMb = mi.availMem / 1048576L;
        long totalMb = mi.totalMem / 1048576L;
        int pct = level < 0 ? -1 : level * 100 / scale;
        return "Pil: %" + pct + "\nSıcaklık: " + temp + " °C\nRAM boş: " + freeMb + " / " + totalMb + " MB";
    }
}
