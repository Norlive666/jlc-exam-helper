package com.ai.assistance.operit.quiz;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

/** Accessibility-service overlay; it never takes focus from the question window. */
final class QuizOverlay {
    final TextView view;
    final WindowManager.LayoutParams parameters;
    private final WindowManager windows;
    private boolean attached;

    QuizOverlay(Context context, Runnable recognize, Runnable history) {
        windows = context.getSystemService(WindowManager.class);
        float density = context.getResources().getDisplayMetrics().density;
        int width = (int) (180 * density);
        parameters = new WindowManager.LayoutParams(width, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT);
        parameters.gravity = Gravity.TOP | Gravity.LEFT;
        parameters.x = Math.max(0, context.getResources().getDisplayMetrics().widthPixels - width - (int) (8 * density));
        parameters.y = (int) (72 * density);
        view = new TextView(context);
        view.setTextColor(Color.WHITE);
        view.setTextSize(12);
        view.setMaxLines(4);
        int padding = (int) (10 * density);
        view.setPadding(padding, padding, padding, padding);
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xED171717);
        background.setCornerRadius(8 * density);
        view.setBackground(background);
        view.setOnClickListener(ignored -> recognize.run());
        view.setOnLongClickListener(ignored -> { history.run(); return true; });
        GestureDetector detector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            private int initialX;
            private int initialY;
            @Override public boolean onDown(MotionEvent event) {
                initialX = parameters.x;
                initialY = parameters.y;
                return true;
            }
            @Override public boolean onSingleTapUp(MotionEvent event) {
                view.performClick();
                return true;
            }
            @Override public void onLongPress(MotionEvent event) { view.performLongClick(); }
            @Override public boolean onScroll(MotionEvent first, MotionEvent current, float dx, float dy) {
                if (first == null) return false;
                parameters.x = Math.max(0, Math.min(context.getResources().getDisplayMetrics().widthPixels - width,
                    initialX + (int) (current.getRawX() - first.getRawX())));
                parameters.y = Math.max(0, Math.min(context.getResources().getDisplayMetrics().heightPixels - view.getHeight(),
                    initialY + (int) (current.getRawY() - first.getRawY())));
                if (attached) windows.updateViewLayout(view, parameters);
                return true;
            }
        });
        view.setOnTouchListener((ignored, event) -> { detector.onTouchEvent(event); return true; });
        status("等待题目");
    }

    void show() {
        captureHidden(false);
        if (!attached) {
            windows.addView(view, parameters);
            attached = true;
        }
    }

    void close() {
        if (attached) {
            windows.removeViewImmediate(view);
            attached = false;
        }
    }

    void status(String value) { view.setText(value + "\n轻点重识别 · 长按历史"); }
    void captureHidden(boolean hidden) { view.setVisibility(hidden ? View.GONE : View.VISIBLE); }
}
