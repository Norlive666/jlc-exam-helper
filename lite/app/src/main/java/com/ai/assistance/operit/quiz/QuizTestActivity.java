package com.ai.assistance.operit.quiz;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Insets;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/** Temporary manual test surface. It never fills answers or calls the model itself. */
public final class QuizTestActivity extends Activity {
    private static QuizTestActivity foreground;
    private static final String[] TYPES = {"单选题", "多选题", "判断题"};
    private static final String[] QUESTIONS = {
        "计算：2 + 3 = ?", "以下哪些数是素数？", "1 千米等于 1000 米。"
    };
    private static final String[][] OPTIONS = {
        {"A. 4", "B. 5", "C. 6", "D. 7"},
        {"A. 2", "B. 3", "C. 4", "D. 5"},
        {"A. 正确", "B. 错误"}
    };
    private static final int[] ANSWER_MASKS = {2, 11, 1};
    private final List<CompoundButton> options = new ArrayList<>();
    private LinearLayout questionArea;
    private TextView feedback;
    private TextView status;
    private boolean focused;
    private int question;

    static boolean allowsOwnScreenCapture() {
        return foreground != null && foreground.focused;
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) question = state.getInt("question", 0);
        getWindow().setDecorFitsSystemWindows(false);
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setFocusableInTouchMode(true);
        content.setBackgroundColor(Color.WHITE);
        int padding = dp(16);
        content.setPadding(padding, padding, padding, padding);
        scroll.addView(content);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
            content.setPadding(padding + bars.left, padding + bars.top,
                padding + bars.right, padding + bars.bottom);
            return insets;
        });
        content.addView(text(getString(R.string.test_heading), 24));
        content.addView(text(getString(R.string.test_intro), 14));
        questionArea = new LinearLayout(this);
        questionArea.setOrientation(LinearLayout.VERTICAL);
        content.addView(questionArea);
        feedback = text("", 16);
        feedback.setMinHeight(dp(32));
        content.addView(feedback);

        LinearLayout answers = row(content);
        button(answers, R.string.test_check, () -> {
            int selected = 0;
            for (int i = 0; i < options.size(); i++) {
                if (options.get(i).isChecked()) selected |= 1 << i;
            }
            feedback.setText(selected == ANSWER_MASKS[question]
                ? R.string.test_correct : R.string.test_incorrect);
        });
        button(answers, R.string.test_reset, this::renderQuestion);
        button(answers, R.string.test_next, () -> {
            question = (question + 1) % QUESTIONS.length;
            renderQuestion();
        });
        LinearLayout controls = row(content);
        button(controls, R.string.test_start, this::startAgent);
        button(controls, R.string.test_settings, () -> startActivity(
            new Intent(this, MainActivity.class).putExtra("edit_settings", true)));
        button(controls, R.string.test_background, () -> moveTaskToBack(true));
        LinearLayout diagnostics = row(content);
        button(diagnostics, R.string.recognize_now, () -> {
            QuizAccessibilityService service = QuizAccessibilityService.current();
            if (service == null || !new AgentSettings(this).hasApiKey()) {
                status.setText(R.string.test_configure_first);
                return;
            }
            service.recognizeNow();
            status.setText(new AgentSettings(this).isRunning() ? R.string.test_started : R.string.test_configure_first);
        });
        button(diagnostics, R.string.history, () -> startActivity(new Intent(this, QuizHistoryActivity.class)));
        status = text(getString(R.string.test_status), 13);
        content.addView(status);
        setContentView(scroll);
        renderQuestion();
        content.requestFocus();
    }

    private void renderQuestion() {
        options.clear();
        questionArea.removeAllViews();
        questionArea.addView(text(getString(R.string.test_progress,
            question + 1, QUESTIONS.length, TYPES[question]), 16));
        questionArea.addView(text(QUESTIONS[question], 21));
        LinearLayout choices;
        if (question == 1) {
            choices = new LinearLayout(this);
            choices.setOrientation(LinearLayout.VERTICAL);
        } else {
            choices = new RadioGroup(this);
        }
        questionArea.addView(choices);
        for (String option : OPTIONS[question]) {
            CompoundButton input = question == 1 ? new CheckBox(this) : new RadioButton(this);
            input.setId(View.generateViewId());
            input.setText(option);
            input.setTextSize(18);
            input.setTextColor(Color.BLACK);
            input.setMinHeight(dp(48));
            choices.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            options.add(input);
        }
        feedback.setText("");
    }

    private void startAgent() {
        QuizAccessibilityService service = QuizAccessibilityService.current();
        if (service == null || !new AgentSettings(this).hasApiKey()) {
            status.setText(R.string.test_configure_first);
            return;
        }
        try {
            service.startAgent();
            status.setText(R.string.test_started);
        } catch (Exception error) {
            status.setText(getString(R.string.error_prefix, error.getClass().getSimpleName()));
        }
    }

    @Override protected void onResume() {
        super.onResume();
        foreground = this;
    }

    @Override protected void onPause() {
        focused = false;
        if (foreground == this) foreground = null;
        super.onPause();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        focused = hasFocus;
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putInt("question", question);
        super.onSaveInstanceState(state);
    }

    private TextView text(String value, int size) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(Color.BLACK);
        view.setPadding(0, dp(6), 0, dp(6));
        return view;
    }

    private LinearLayout row(LinearLayout parent) {
        LinearLayout row = new LinearLayout(this);
        parent.addView(row);
        return row;
    }

    private void button(LinearLayout row, int label, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setOnClickListener(view -> action.run());
        row.addView(button, new LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1));
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
