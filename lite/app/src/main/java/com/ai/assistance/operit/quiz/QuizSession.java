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
        if (before == null || (before.unchanged(previous) && before.sameSelection(previous, previousOptions))) return;
        if (!active.getAsBoolean()) return;
        screen.status(Status.ANALYZING);
        List<QuizProtocol.Question> questions = model.analyze(before);
        if (!active.getAsBoolean()) return;
        ScreenFrame current = screen.capture();
        if (!before.sameScene(current)) {
            screen.status(Status.SCREEN_CHANGED);
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
            previous = null;
            return;
        }
        for (QuizProtocol.Question question : questions) {
            for (QuizProtocol.Tap tap : question.changes(before.width, before.height)) {
                if (!active.getAsBoolean()) return;
                // The user can change apps or questions while the model is answering.
                current = screen.capture();
                if (!expected.sameSelection(current, allOptions)) {
                    screen.status(Status.SCREEN_CHANGED);
                    previous = null;
                    return;
                }
                if (!active.getAsBoolean()) return;
                if (!screen.tap(tap)) throw new IllegalStateException("选项点击未完成");
                clicked = true;
                if (!active.getAsBoolean()) return;
                // Rebase after our own click, so it is not mistaken for a manual selection change.
                expected = screen.capture();
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
