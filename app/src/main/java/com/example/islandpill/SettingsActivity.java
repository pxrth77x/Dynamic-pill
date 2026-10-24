package com.example.islandpill;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

public class SettingsActivity extends Activity {
    private SharedPreferences sp;
    private LinearLayout root;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("settings", MODE_PRIVATE);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad * 2, pad, pad);

        TextView title = new TextView(this);
        title.setText("Island Pill");
        title.setTextSize(24);
        root.addView(title);

        TextView hint = new TextView(this);
        hint.setText("Enable the module in LSPosed (scope: System UI). Pill shows while music plays. Changes apply live.");
        hint.setPadding(0, pad / 2, 0, pad);
        root.addView(hint);

        addSlider("Width", "w", 80, 250, 140);
        addSlider("Height", "h", 24, 80, 34);
        addSlider("Top margin", "top", 0, 40, 8);

        ScrollView sv = new ScrollView(this);
        sv.addView(root, new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(sv);
    }

    private void addSlider(String label, String key, int min, int max, int def) {
        TextView tv = new TextView(this);
        tv.setTextSize(16);
        SeekBar sb = new SeekBar(this);
        sb.setMax(max - min);
        int cur = sp.getInt(key, def);
        sb.setProgress(cur - min);
        tv.setText(label + ": " + cur + " dp");
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                int v = p + min;
                tv.setText(label + ": " + v + " dp");
                if (fromUser) { sp.edit().putInt(key, v).apply(); push(); }
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        root.addView(tv);
        root.addView(sb);
    }

    private void push() {
        sendBroadcast(new Intent(IslandController.ACTION)
            .setPackage("com.android.systemui")
            .putExtra("w", sp.getInt("w", 140))
            .putExtra("h", sp.getInt("h", 34))
            .putExtra("top", sp.getInt("top", 8)));
    }
}
