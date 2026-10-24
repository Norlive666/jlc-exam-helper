package com.ai.assistance.operit.quiz;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Insets;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class QuizHistoryActivity extends Activity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private TextView records;
    private long rendered = -1;
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            QuizHistory history = QuizAccessibilityService.HISTORY;
            long version = history.version();
            if (version != rendered) {
                records.setText(history.text());
                rendered = version;
            }
            main.postDelayed(this, 1000);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setDecorFitsSystemWindows(false);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackgroundColor(Color.WHITE);
        int padding = Math.round(16 * getResources().getDisplayMetrics().density);
        content.setPadding(padding, padding, padding, padding);
        content.setOnApplyWindowInsetsListener((view, insets) -> {
            Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
            content.setPadding(padding + bars.left, padding + bars.top,
                padding + bars.right, padding + bars.bottom);
            return insets;
        });
        TextView heading = new TextView(this);
        heading.setText("识题历史\n本次运行最近30次，不保存截图。返回题目页后可轻点悬浮窗重试。");
        heading.setTextColor(Color.BLACK);
        heading.setTextSize(17);
        content.addView(heading);
        Button clear = new Button(this);
        clear.setText("清空历史");
        clear.setOnClickListener(view -> { QuizAccessibilityService.HISTORY.clear(); rendered = -1; });
        content.addView(clear);
        ScrollView scroll = new ScrollView(this);
        records = new TextView(this);
        records.setTextSize(14);
        records.setTextColor(Color.BLACK);
        records.setTextIsSelectable(true);
        scroll.addView(records);
        content.addView(scroll, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(content);
    }

    @Override protected void onResume() { super.onResume(); main.post(refresh); }
    @Override protected void onPause() { main.removeCallbacks(refresh); super.onPause(); }
}
