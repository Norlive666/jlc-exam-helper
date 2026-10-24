package com.ai.assistance.operit.quiz;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Serial screenshot -> answer -> option selection loop, with no navigation tools. */
final class QuizSession {
    enum Status { WAITING, ANALYZING, SELECTED, SCREEN_CHANGED }

    interface Screen {
        ScreenFrame capture() throws Exception;
        boolean tap(QuizProtocol.Tap tap) throws Exception;
        void status(Status status);
        default void trace(String stage, String detail) {}
    }

    interface Model {
        List<QuizProtocol.Question> analyze(ScreenFrame frame) throws Exception;
    }

    private final Screen screen;
    private final Model model;
    private ScreenFrame previous;
    private List<QuizProtocol.Tap> previousOptions = List.of();

    QuizSession(Screen screen, Model model) {
        this.screen = screen;
        this.model = model;
    }

    void scan(BooleanSupplier active) throws Exception {
        if (!active.getAsBoolean()) return;
        ScreenFrame before = screen.capture();
        if (before == null) {
            screen.trace("无法读取屏幕", "未获得截图，未请求模型");
            return;
        }
        if (before.unchanged(previous) && before.sameSelection(previous, previousOptions)) return;
        if (!active.getAsBoolean()) return;
        screen.trace("开始识别", before.app);
        screen.status(Status.ANALYZING);
        screen.trace("请求模型", "截图成功，正在请求 DeepSeek");
        List<QuizProtocol.Question> questions = model.analyze(before);
        if (!active.getAsBoolean()) return;
        if (questions.isEmpty()) screen.trace("未识别到题目", "模型未返回完整可作答的题目");
        for (QuizProtocol.Question question : questions) screen.trace("识别成功", question.summary());
        ScreenFrame current = screen.capture();
        if (!before.sameScene(current)) {
            screen.status(Status.SCREEN_CHANGED);
            screen.trace("页面已变化", "丢弃旧回答，未执行点击");
            return;
        }

        boolean clicked = false;
        ScreenFrame expected = before;
        List<QuizProtocol.Tap> allOptions = new ArrayList<>();
        for (QuizProtocol.Question question : questions) {
            allOptions.addAll(question.centers(before.width, before.height));
        }
        if (!before.sameSelection(current, allOptions)) {
            screen.status(Status.SCREEN_CHANGED);
            screen.trace("选项已变化", "回答期间选择状态改变，未执行点击");
            previous = null;
            return;
        }
        for (QuizProtocol.Question question : questions) {
            List<QuizProtocol.Tap> changes = question.changes(before.width, before.height);
            if (changes.isEmpty()) screen.trace("无需点击", "模型认为现有选项符合答案：" + question.text);
            for (QuizProtocol.Tap tap : changes) {
                if (!active.getAsBoolean()) return;
                // The user can change apps or questions while the model is answering.
                current = screen.capture();
                if (!expected.sameSelection(current, allOptions)) {
                    screen.status(Status.SCREEN_CHANGED);
                    screen.trace("选项已变化", "点击前页面或选择改变，取消剩余点击");
                    previous = null;
                    return;
                }
                if (!active.getAsBoolean()) return;
                screen.trace("准备点击", tap.description());
                if (!screen.tap(tap)) {
                    screen.trace("点击未完成", tap.description());
                    throw new IllegalStateException("选项点击未完成");
                }
                screen.trace("手势已完成", tap.description());
                clicked = true;
                if (!active.getAsBoolean()) return;
                // Rebase after our own click, so it is not mistaken for a manual selection change.
                ScreenFrame after = screen.capture();
                if (before.sameScene(after)) {
                    Boolean selected = after.selectedAt(tap);
                    if (selected != null) {
                        screen.trace(selected == tap.desired ? (tap.desired ? "已确认选中" : "已确认取消") : "未达到目标状态",
                            tap.description() + "；读取到原生控件状态：" + (selected ? "已选中" : "未选中"));
                    } else {
                        screen.trace(expected.sameSelection(after, List.of(tap)) ? "未观察到变化" : "界面有变化，待核对",
                            tap.description() + "；没有可确认的原生选中状态");
                    }
                } else {
                    screen.trace("页面已变化", "手势完成后页面改变，无法核对当前选项");
                }
                expected = after;
                current = expected;
                if (!before.sameScene(expected)) {
                    previous = null;
                    return;
                }
            }
        }
        if (!active.getAsBoolean()) return;
        previous = expected;
        previousOptions = allOptions;
        screen.status(clicked ? Status.SELECTED : Status.WAITING);
    }
}
