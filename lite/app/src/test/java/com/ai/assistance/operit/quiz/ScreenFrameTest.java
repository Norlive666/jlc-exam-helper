package com.ai.assistance.operit.quiz;

import org.junit.Test;
import static org.junit.Assert.*;

public class ScreenFrameTest {
    @Test public void selectionAnnouncementsDoNotChangeQuestionIdentity() {
        String before = "<node text=\"A，未选中\" content-desc=\"A, unchecked\" checked=\"false\" selected=\"false\" focused=\"false\" />";
        String after = "<node text=\"A，已选中\" content-desc=\"A, checked\" checked=\"true\" selected=\"true\" focused=\"true\" />";
        assertEquals(ScreenFrame.normalizeUiText(before), ScreenFrame.normalizeUiText(after));
    }

    @Test public void differentOptionTextStillInvalidatesXmlScene() {
        assertNotEquals(ScreenFrame.normalizeUiText("<node text=\"A. 北京\" />"),
            ScreenFrame.normalizeUiText("<node text=\"A. 上海\" />"));
    }

    private ScreenFrame frame(String text, int rotation, long bits) {
        return new ScreenFrame(new byte[]{1}, 1080, 2400, "practice", 7, rotation,
            text, "", new long[]{bits, 0, 0, 0}, new int[64 * 128]);
    }

    @Test public void changedQuestionInvalidatesSameLookingScreen() {
        assertFalse(frame("Question one", 0, 0).sameScene(frame("Question two", 0, 0)));
    }

    @Test public void rotationInvalidatesCoordinates() {
        assertFalse(frame("Question one", 0, 0).sameScene(frame("Question one", 1, 0)));
    }

    @Test public void selectionHighlightCanChangeWithoutChangingQuestion() {
        assertTrue(frame("Question one", 0, 0).sameScene(frame("Question one", 0, 15)));
        assertFalse(frame("Question one", 0, 0).unchanged(frame("Question one", 0, 15)));
    }
}
