package com.ai.assistance.operit.quiz;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import org.junit.Test;
import static org.junit.Assert.*;

public class QuizSessionRetryTest {
    private static final String REPLY = """
        {"questions":[{"question":"2+2等于多少？","type":"single",
        "options":[{"label":"A","text":"3","x":250,"y":300,"selected":false},
                   {"label":"B","text":"4","x":250,"y":450,"selected":false}],
        "answers":["B"]}]}
        """;

    private static ScreenFrame frame(String text) {
        return new ScreenFrame(new byte[]{1}, 1080, 2400, "practice", 7, 0,
            text, "", new long[4], new int[64 * 128]);
    }

    private static class Phone implements QuizSession.Screen {
        ScreenFrame frame = frame("old question");
        final List<QuizProtocol.Tap> taps = new ArrayList<>();
        final List<String> events = new ArrayList<>();
        @Override public ScreenFrame capture() { return frame; }
        @Override public boolean tap(QuizProtocol.Tap tap) { taps.add(tap); return true; }
        @Override public void status(QuizSession.Status status) {}
        @Override public void trace(String stage, String detail) { events.add(stage + ": " + detail); }
    }

    private void run(QuizSession session, BooleanSupplier active, QuizSession.Sleeper sleeper) {
        try { session.run(active, sleeper); }
        catch (Exception error) { fail("异常应触发重试，而不是结束识题：" + error); }
    }

    @Test public void requestFailureReadsCurrentScreenAgainBeforeRetrying() {
        Phone phone = new Phone();
        AtomicBoolean active = new AtomicBoolean(true);
        List<String> requests = new ArrayList<>();
        List<Long> waits = new ArrayList<>();
        QuizSession session = new QuizSession(phone, current -> {
            requests.add(current.uiText);
            if (requests.size() == 1) throw new IOException("temporary network failure");
            return List.of();
        });
        run(session, active::get, delay -> {
            waits.add(delay);
            phone.frame = frame("new question");
            if (waits.size() == 2) active.set(false);
        });
        assertEquals(List.of("old question", "new question"), requests);
        assertEquals(List.of(3000L, 3000L), waits);
        assertTrue(String.join("\n", phone.events).contains("temporary network failure"));
    }

    @Test public void repeatedFailuresBackOffInsteadOfSendingRequestsContinuously() {
        Phone phone = new Phone();
        AtomicBoolean active = new AtomicBoolean(true);
        List<Long> waits = new ArrayList<>();
        QuizSession session = new QuizSession(phone, current -> {
            throw new IOException("offline");
        });
        run(session, active::get, delay -> {
            waits.add(delay);
            if (waits.size() == 7) active.set(false);
        });
        assertEquals(List.of(3000L, 6000L, 12000L, 24000L, 30000L, 30000L, 30000L), waits);
        assertTrue(phone.taps.isEmpty());
    }

    @Test public void successfulScanResetsDelayForTheNextFailure() {
        Phone phone = new Phone();
        AtomicBoolean active = new AtomicBoolean(true);
        int[] calls = {0};
        List<Long> waits = new ArrayList<>();
        QuizSession session = new QuizSession(phone, current -> {
            calls[0]++;
            if (calls[0] != 3) throw new IOException("temporary failure");
            return List.of();
        });
        run(session, active::get, delay -> {
            waits.add(delay);
            phone.frame = frame("question " + waits.size());
            if (waits.size() == 4) active.set(false);
        });
        assertEquals(4, calls[0]);
        assertEquals(List.of(3000L, 6000L, 3000L, 3000L), waits);
    }

    @Test public void stoppingDuringRetryWaitPreventsAnotherRequestOrClick() {
        Phone phone = new Phone();
        AtomicBoolean active = new AtomicBoolean(true);
        int[] calls = {0};
        QuizSession session = new QuizSession(phone, current -> {
            calls[0]++;
            throw new IOException("offline");
        });
        run(session, active::get, delay -> active.set(false));
        assertEquals(1, calls[0]);
        assertTrue(phone.taps.isEmpty());
    }

    @Test public void errorArrivingAfterStopDoesNotScheduleRetry() {
        Phone phone = new Phone();
        AtomicBoolean active = new AtomicBoolean(true);
        QuizSession session = new QuizSession(phone, current -> {
            active.set(false);
            throw new IOException("request closed after stop");
        });
        run(session, active::get, delay -> fail("关闭后不能等待或重试"));
        assertFalse(String.join("\n", phone.events).contains("自动重试"));
        assertTrue(phone.taps.isEmpty());
    }

    @Test public void interruptionExitsWithoutRetry() {
        Phone phone = new Phone();
        QuizSession session = new QuizSession(phone, current -> { throw new InterruptedException(); });
        assertThrows(InterruptedException.class, () -> session.run(() -> true,
            delay -> fail("中断后不能等待或重试")));
        assertFalse(String.join("\n", phone.events).contains("自动重试"));
    }

    @Test public void failedGestureDoesNotReplayOldAnswerOnRetry() {
        int[] attempts = {0};
        Phone phone = new Phone() {
            @Override public boolean tap(QuizProtocol.Tap tap) {
                if (++attempts[0] == 1) return false;
                return super.tap(tap);
            }
        };
        AtomicBoolean active = new AtomicBoolean(true);
        List<String> requests = new ArrayList<>();
        List<Long> waits = new ArrayList<>();
        QuizSession session = new QuizSession(phone, current -> {
            requests.add(current.uiText);
            return QuizProtocol.parse(requests.size() == 1
                ? REPLY.replace("\"answers\":[\"B\"]", "\"answers\":[\"A\"]") : REPLY);
        });
        run(session, active::get, delay -> {
            waits.add(delay);
            phone.frame = frame("new question");
            if (waits.size() == 2) active.set(false);
        });
        assertEquals(List.of("old question", "new question"), requests);
        assertEquals(2, attempts[0]);
        assertEquals(1, phone.taps.size());
        assertEquals("B", phone.taps.get(0).label);
    }
}
