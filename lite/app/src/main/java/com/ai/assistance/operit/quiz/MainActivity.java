package com.ai.assistance.operit.quiz;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Insets;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private AgentSettings settings;
    private EditText key;
    private TextView status;
    private boolean editing;
    private final Runnable automaticStart = this::startIfReady;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        settings = new AgentSettings(this);
        editing = getIntent().getBooleanExtra("edit_settings", false);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().setDecorFitsSystemWindows(false);

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setFocusableInTouchMode(true);
        content.setBackgroundColor(Color.WHITE);
        int padding = dp(24);
        content.setPadding(padding, padding, padding, padding);
        scroll.addView(content);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
            content.setPadding(padding + bars.left, padding + bars.top,
                padding + bars.right, padding + bars.bottom);
            return insets;
        });

        TextView heading = text(R.string.heading, 28);
        heading.setTextColor(Color.BLACK);
        content.addView(heading);
        content.addView(text(R.string.intro, 16), spaced());

        key = new EditText(this);
        key.setSingleLine(true);
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        key.setHint(R.string.api_key_hint);
        key.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        try {
            if (settings.hasApiKey()) key.setText(settings.apiKey());
        } catch (Exception error) {
            settings.setRunning(false);
            Log.e("OperitQuiz", "Cannot decrypt saved API key: " + error.getClass().getSimpleName());
        }
        key.setOnFocusChangeListener((view, focused) -> {
            if (focused) {
                editing = true;
                handler.removeCallbacks(automaticStart);
            }
        });
        content.addView(key, spaced());
        content.addView(text(R.string.api_caption, 13));
        content.addView(text(R.string.data_notice, 14), spaced());

        Button accessibility = button(R.string.permissions);
        accessibility.setOnClickListener(view -> {
            editing = false;
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        });
        content.addView(accessibility, spaced());

        Button notifications = button(R.string.notifications);
        notifications.setOnClickListener(view -> {
            editing = false;
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
            } else {
                startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
            }
        });
        content.addView(notifications);

        Button start = button(R.string.start);
        start.setOnClickListener(view -> {
            String apiKey = key.getText().toString().trim();
            if (apiKey.isEmpty()) { status.setText(R.string.key_needed); return; }
            try {
                settings.saveApiKey(apiKey);
                editing = false;
                QuizAccessibilityService service = QuizAccessibilityService.current();
                if (service != null) service.stopAgent();
                startIfReady();
            } catch (Exception error) {
                status.setText(getString(R.string.settings_error, error.getClass().getSimpleName()));
            }
        });
        content.addView(start, spaced());
        status = text(R.string.permission_needed, 14);
        content.addView(status);
        setContentView(scroll);
        content.requestFocus();
    }

    @Override protected void onResume() {
        super.onResume();
        if (!editing && settings.hasApiKey()) handler.postDelayed(automaticStart, 700);
    }

    @Override protected void onPause() {
        handler.removeCallbacks(automaticStart);
        super.onPause();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == 1) handler.postDelayed(automaticStart, 300);
    }

    private void startIfReady() {
        if (editing || !settings.hasApiKey()) return;
        if (!getSystemService(NotificationManager.class).areNotificationsEnabled()) {
            status.setText(R.string.permission_needed);
            return;
        }
        QuizAccessibilityService service = QuizAccessibilityService.current();
        if (service == null) {
            status.setText(R.string.permission_needed);
            return;
        }
        try {
            service.startAgent();
            status.setText(R.string.running);
            startActivity(new Intent(this, QuizTestActivity.class));
        } catch (Exception error) {
            status.setText(getString(R.string.error_prefix, error.getClass().getSimpleName()));
        }
    }

    private TextView text(int resource, int size) {
        TextView text = new TextView(this);
        text.setText(resource);
        text.setTextSize(size);
        text.setTextColor(Color.rgb(65, 65, 65));
        return text;
    }

    private Button button(int resource) {
        Button button = new Button(this);
        button.setText(resource);
        button.setAllCaps(false);
        return button;
    }

    private LinearLayout.LayoutParams spaced() {
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        layout.topMargin = dp(20);
        return layout;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
