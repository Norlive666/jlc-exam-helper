package com.ai.assistance.operit.quiz;

import org.junit.Test;
import static org.junit.Assert.*;

public class QuizHistoryTest {
    @Test public void keepsRecognitionAndClickStagesInOneRound() {
        QuizHistory history = new QuizHistory();
        long id = history.begin("practice");
        history.event(id, "识别成功", "2+2，模型答案 B");
        history.event(id, "手势已完成", "B @ 270,1080");
        history.event(id, "未观察到变化", "请核对选项是否命中");
        String text = history.text();
        assertTrue(text.contains("2+2，模型答案 B"));
        assertTrue(text.indexOf("识别成功") < text.indexOf("手势已完成"));
        assertTrue(text.contains("未观察到变化"));
        assertEquals(1, history.size());
    }

    @Test public void retainsOnlyThirtyRoundsAndClearDoesNotReuseOldIds() {
        QuizHistory history = new QuizHistory();
        long first = history.begin("old-app");
        for (int i = 0; i < 30; i++) history.begin("new-app-" + i);
        assertEquals(30, history.size());
        assertFalse(history.text().contains("old-app"));
        history.clear();
        long current = history.begin("current-app");
        history.event(first, "迟到事件", "不应出现");
        assertNotEquals(first, current);
        assertFalse(history.text().contains("不应出现"));
    }
}
