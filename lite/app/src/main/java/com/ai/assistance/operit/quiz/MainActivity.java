package com.ai.assistance.operit.quiz;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.ComponentName;
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
    private EditText delay;
    private TextView status;
    private boolean editing;
    private boolean resumed;
    private boolean startRequested;
    private int requestedDelayMinutes = -1;
    private final Runnable automaticStart = this::startIfReady;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        settings = new AgentSettings(this);
        editing = getIntent().getBooleanExtra("edit_settings", false);
        startRequested = state == null && !editing && launcherRequestsStart(getIntent());
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
                startRequested = false;
                handler.removeCallbacks(automaticStart);
            }
        });
        content.addView(key, spaced());
        content.addView(text(R.string.api_caption, 13));
        content.addView(text(R.string.data_notice, 14), spaced());

        Button accessibility = button(R.string.permissions);
        accessibility.setOnClickListener(view -> {
            editing = false;
            startRequested = false;
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        });
        content.addView(accessibility, spaced());

        Button notifications = button(R.string.notifications);
        notifications.setOnClickListener(view -> {
            editing = false;
            startRequested = false;
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
        start.setOnClickListener(view -> requestStart(0));
        content.addView(start, spaced());

        delay = new EditText(this);
        delay.setSingleLine(true);
        delay.setHint(R.string.delay_hint);
        delay.setInputType(InputType.TYPE_CLASS_NUMBER);
        delay.setOnFocusChangeListener((view, focused) -> {
            if (focused) {
                editing = true;
                startRequested = false;
                handler.removeCallbacks(automaticStart);
            }
        });
        content.addView(delay, spaced());
        content.addView(text(R.string.delay_caption, 13));
        Button schedule = button(R.string.schedule);
        schedule.setOnClickListener(view -> {
            try {
                int minutes = Integer.parseInt(delay.getText().toString().trim());
                if (minutes <= 0) throw new NumberFormatException();
                requestStart(minutes);
            } catch (NumberFormatException error) {
                status.setText(R.string.delay_invalid);
            }
        });
        content.addView(schedule);

        Button recognize = button(R.string.recognize_now);
        recognize.setOnClickListener(view -> {
            startRequested = false;
            QuizAccessibilityService service = QuizAccessibilityService.current();
            if (service == null || !settings.hasApiKey()) {
                status.setText(R.string.test_configure_first);
                return;
            }
            service.recognizeNow();
            status.setText(settings.isRunning() ? R.string.running : R.string.ready);
        });
        content.addView(recognize, spaced());
        Button history = button(R.string.history);
        history.setOnClickListener(view -> startActivity(new Intent(this, QuizHistoryActivity.class)));
        content.addView(history);
        Button stop = button(R.string.stop);
        stop.setOnClickListener(view -> {
            startRequested = false;
            handler.removeCallbacks(automaticStart);
            QuizAccessibilityService service = QuizAccessibilityService.current();
            if (service != null) service.stopAgent();
            status.setText(R.string.stopped);
        });
        content.addView(stop);
        status = text(R.string.permission_needed, 14);
        content.addView(status);
        setContentView(scroll);
        content.requestFocus();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        editing = intent.getBooleanExtra("edit_settings", false);
        startRequested = !editing && launcherRequestsStart(intent);
        requestedDelayMinutes = -1;
    }

    private boolean launcherRequestsStart(Intent intent) {
        QuizAccessibilityService service = QuizAccessibilityService.current();
        return Intent.ACTION_MAIN.equals(intent.getAction())
            && intent.hasCategory(Intent.CATEGORY_LAUNCHER)
            && (service == null || !service.isScheduled());
    }

    private void requestStart(int minutes) {
        String apiKey = key.getText().toString().trim();
        if (apiKey.isEmpty()) { status.setText(R.string.key_needed); return; }
        try {
            settings.saveApiKey(apiKey);
            editing = false;
            startRequested = true;
            requestedDelayMinutes = minutes;
            QuizAccessibilityService service = QuizAccessibilityService.current();
            if (service != null) service.stopAgent();
            startIfReady();
        } catch (Exception error) {
            status.setText(getString(R.string.settings_error, error.getClass().getSimpleName()));
        }
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        handler.removeCallbacks(automaticStart);
        if (!editing && settings.hasApiKey()) handler.postDelayed(automaticStart, 700);
    }

    @Override protected void onPause() {
        resumed = false;
        startRequested = false;
        handler.removeCallbacks(automaticStart);
        super.onPause();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == 1 && resumed) handler.postDelayed(automaticStart, 300);
    }

    private void startIfReady() {
        handler.removeCallbacks(automaticStart);
        if (!resumed || editing || !settings.hasApiKey()) return;
        if (!getSystemService(NotificationManager.class).areNotificationsEnabled()) {
            status.setText(R.string.notification_needed);
            return;
        }
        if (!accessibilityEnabled()) {
            status.setText(R.string.accessibility_needed);
            return;
        }
        QuizAccessibilityService service = QuizAccessibilityService.current();
        if (service == null) {
            status.setText(R.string.accessibility_connecting);
            handler.postDelayed(automaticStart, 500);
            return;
        }
        if (service.isScheduled() && (!startRequested || requestedDelayMinutes < 0)) {
            startRequested = false;
            long seconds = (service.remainingStartMillis() + 999) / 1000;
            status.setText(getString(R.string.scheduled, seconds / 60, seconds % 60));
            handler.postDelayed(automaticStart, 1000);
            return;
        }
        if (!startRequested) {
            status.setText(settings.isRunning() ? R.string.running : R.string.ready);
            return;
        }
        try {
            if (requestedDelayMinutes > 0) {
                service.scheduleAgent(requestedDelayMinutes);
                startRequested = false;
                startIfReady();
                return;
            }
            service.startAgent();
            startRequested = false;
            status.setText(R.string.running);
            startActivity(new Intent(this, QuizTestActivity.class));
        } catch (Exception error) {
            status.setText(getString(R.string.error_prefix, error.getClass().getSimpleName()));
        }
    }

    private boolean accessibilityEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(),
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) return false;
        ComponentName own = new ComponentName(this, QuizAccessibilityService.class);
        for (String component : enabled.split(":")) {
            if (own.equals(ComponentName.unflattenFromString(component))) return true;
        }
        return false;
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
