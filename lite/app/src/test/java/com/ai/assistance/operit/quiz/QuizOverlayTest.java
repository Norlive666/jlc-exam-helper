package com.ai.assistance.operit.quiz;

import android.view.View;
import android.view.WindowManager;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class QuizOverlayTest {
    @Test public void tapRecognizesAndLongPressOpensHistory() {
        int[] calls = {0, 0};
        QuizOverlay overlay = new QuizOverlay(RuntimeEnvironment.getApplication(),
            () -> calls[0]++, () -> calls[1]++);
        overlay.view.performClick();
        assertArrayEquals(new int[]{1, 0}, calls);
        overlay.view.performLongClick();
        assertArrayEquals(new int[]{1, 1}, calls);
        assertEquals(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, overlay.parameters.type);
        assertNotEquals(0, overlay.parameters.flags & WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
    }

    @Test public void captureHidesOverlayWithoutLosingStatus() {
        QuizOverlay overlay = new QuizOverlay(RuntimeEnvironment.getApplication(), () -> {}, () -> {});
        overlay.status("正在识别");
        overlay.captureHidden(true);
        assertEquals(View.GONE, overlay.view.getVisibility());
        overlay.captureHidden(false);
        assertEquals(View.VISIBLE, overlay.view.getVisibility());
        assertTrue(overlay.view.getText().toString().contains("正在识别"));
    }
}
