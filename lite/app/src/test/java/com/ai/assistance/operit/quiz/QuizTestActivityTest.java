package com.ai.assistance.operit.quiz;

import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.RadioButton;
import android.widget.TextView;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class QuizTestActivityTest {
    private ActivityController<QuizTestActivity> controller;

    private QuizTestActivity open() {
        controller = Robolectric.buildActivity(QuizTestActivity.class).setup();
        return controller.get();
    }

    @After public void close() {
        if (controller != null) controller.pause().stop().destroy();
    }

    @Test public void singleChoiceOnlyChecksAfterUserRequestsIt() {
        QuizTestActivity activity = open();
        assertFalse(((RadioButton) find(activity, "B. 5")).isChecked());
        find(activity, "A. 4").performClick();
        find(activity, "核对本题").performClick();
        assertNotNull(find(activity, "回答错误，请重做本题。"));
        find(activity, "B. 5").performClick();
        find(activity, "核对本题").performClick();
        assertNotNull(find(activity, "回答正确。"));
    }

    @Test public void multipleChoiceRequiresAllCorrectOptionsAndNoExtraOption() {
        QuizTestActivity activity = open();
        find(activity, "下一题").performClick();
        find(activity, "A. 2").performClick();
        find(activity, "核对本题").performClick();
        assertNotNull(find(activity, "回答错误，请重做本题。"));
        find(activity, "B. 3").performClick();
        find(activity, "D. 5").performClick();
        find(activity, "核对本题").performClick();
        assertNotNull(find(activity, "回答正确。"));
        find(activity, "C. 4").performClick();
        find(activity, "核对本题").performClick();
        assertNotNull(find(activity, "回答错误，请重做本题。"));
    }

    @Test public void resetAndNextQuestionClearSelection() {
        QuizTestActivity activity = open();
        find(activity, "B. 5").performClick();
        find(activity, "重做本题").performClick();
        assertFalse(((RadioButton) find(activity, "B. 5")).isChecked());
        find(activity, "下一题").performClick();
        assertFalse(((CheckBox) find(activity, "A. 2")).isChecked());
        find(activity, "下一题").performClick();
        find(activity, "A. 正确").performClick();
        find(activity, "核对本题").performClick();
        assertNotNull(find(activity, "回答正确。"));
    }

    @Test public void onlyFocusedResumedTestPageAllowsOwnScreenCapture() {
        QuizTestActivity activity = open();
        activity.onWindowFocusChanged(true);
        assertTrue(QuizTestActivity.allowsOwnScreenCapture());
        activity.onWindowFocusChanged(false);
        assertFalse(QuizTestActivity.allowsOwnScreenCapture());
        activity.onWindowFocusChanged(true);
        controller.pause();
        assertFalse(QuizTestActivity.allowsOwnScreenCapture());
        controller.resume();
        activity.onWindowFocusChanged(true);
        assertTrue(QuizTestActivity.allowsOwnScreenCapture());
        assertEquals(0, activity.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_SECURE);
    }

    @Test public void settingsScreenRemainsProtectedAfterLeavingTestPage() {
        QuizTestActivity activity = open();
        activity.onWindowFocusChanged(true);
        controller.pause();
        ActivityController<MainActivity> settings = Robolectric.buildActivity(MainActivity.class).setup();
        try {
            assertFalse(QuizTestActivity.allowsOwnScreenCapture());
            assertNotEquals(0, settings.get().getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_SECURE);
        } finally {
            settings.pause().stop().destroy();
            controller.resume();
        }
    }

    private View find(QuizTestActivity activity, String text) {
        View match = find(activity.getWindow().getDecorView(), text);
        assertNotNull("Missing view: " + text, match);
        return match;
    }

    private View find(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = find(group.getChildAt(i), text);
                if (found != null) return found;
            }
        }
        return null;
    }
}
