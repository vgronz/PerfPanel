package com.example.perfpanel;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48, 120, 48, 48);

        TextView title = new TextView(this);
        title.setText("Perf Panel\nPil, sıcaklık ve RAM durumunu gösteren yüzen panel");
        title.setTextSize(18);
        root.addView(title);

        root.addView(makeButton("1) Overlay iznini ver", v -> {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(i);
        }));

        root.addView(makeButton("2) Paneli başlat", v -> {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Önce overlay iznini ver", Toast.LENGTH_LONG).show();
                return;
            }
            startForegroundService(new Intent(this, OverlayService.class));
        }));

        root.addView(makeButton("3) Paneli durdur", v ->
                stopService(new Intent(this, OverlayService.class))));

        setContentView(root);

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        }
    }

    private Button makeButton(String text, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setOnClickListener(listener);
        return b;
    }
}
