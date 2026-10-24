package com.ai.assistance.operit.quiz;

import org.junit.Test;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

public class QuizSessionTest {
    private static final String REPLY = """
        {"questions":[{"question":"2+2等于多少？","type":"single",
        "options":[{"label":"A","text":"3","x":250,"y":300,"selected":false},
                   {"label":"B","text":"4","x":250,"y":450,"selected":false}],
        "answers":["B"]}]}
        """;

    private static ScreenFrame frame(String app, String text) {
        return new ScreenFrame(new byte[]{1}, 1080, 2400, app, 7, 0,
            text, "", new long[]{0, 0, 0, 0}, new int[64 * 128]);
    }

    private static class Phone implements QuizSession.Screen {
        ScreenFrame frame = frame("practice", "2+2 A3 B4");
        final List<QuizProtocol.Tap> taps = new ArrayList<>();
        @Override public ScreenFrame capture() { return frame; }
        @Override public boolean tap(QuizProtocol.Tap tap) { taps.add(tap); return true; }
        @Override public void status(QuizSession.Status status) {}
    }

    @Test public void repeatedScreenDoesNotRequestOrClickAgain() throws Exception {
        Phone phone = new Phone();
        int[] requests = {0};
        QuizSession session = new QuizSession(phone, frame -> {
            requests[0]++;
            return QuizProtocol.parse(REPLY);
        });
        session.scan(() -> true);
        session.scan(() -> true);
        assertEquals(1, requests[0]);
        assertEquals(1, phone.taps.size());
    }

    @Test public void movingToAnotherAppDiscardsOutstandingAnswer() throws Exception {
        Phone phone = new Phone();
        QuizSession session = new QuizSession(phone, frame -> {
            phone.frame = frame("other-app", "different question");
            return QuizProtocol.parse(REPLY);
        });
        session.scan(() -> true);
        assertTrue(phone.taps.isEmpty());
    }

    @Test public void stoppingWhileModelRespondsPreventsClicks() throws Exception {
        Phone phone = new Phone();
        AtomicBoolean active = new AtomicBoolean(true);
        QuizSession session = new QuizSession(phone, frame -> {
            active.set(false);
            return QuizProtocol.parse(REPLY);
        });
        session.scan(active::get);
        assertTrue(phone.taps.isEmpty());
    }

    @Test public void manualSelectionWhileModelRespondsIsNotToggledOff() throws Exception {
        Phone phone = new Phone();
        QuizSession session = new QuizSession(phone, original -> {
            phone.frame = new ScreenFrame(new byte[]{1}, 1080, 2400, "practice", 7, 0,
                "2+2 A3 B4", "B checked", new long[]{0, 0, 0, 0}, new int[64 * 128]);
            return QuizProtocol.parse(REPLY);
        });
        session.scan(() -> true);
        assertTrue(phone.taps.isEmpty());
    }

    @Test public void canvasSelectionChangeAlsoInvalidatesAnswer() throws Exception {
        Phone phone = new Phone();
        QuizSession session = new QuizSession(phone, original -> {
            int[] pixels = new int[64 * 128];
            pixels[57 * 64 + 8] = 0x00FFFFFF;
            phone.frame = new ScreenFrame(new byte[]{1}, 1080, 2400, "practice", 7, 0,
                "2+2 A3 B4", "", new long[]{0, 0, 0, 0}, pixels);
            return QuizProtocol.parse(REPLY);
        });
        session.scan(() -> true);
        assertTrue(phone.taps.isEmpty());
    }

    @Test public void ownMultiChoiceClicksDoNotInvalidateRemainingChoices() throws Exception {
        Phone phone = new Phone() {
            @Override public boolean tap(QuizProtocol.Tap tap) {
                super.tap(tap);
                frame = new ScreenFrame(new byte[]{1}, 1080, 2400, "practice", 7, 0,
                    "2+2 A3 B4", "selected " + taps.size(), new long[]{0, 0, 0, 0}, new int[64 * 128]);
                return true;
            }
        };
        QuizSession session = new QuizSession(phone, original -> QuizProtocol.parse(
            REPLY.replace("\"type\":\"single\"", "\"type\":\"multiple\"")
                 .replace("\"answers\":[\"B\"]", "\"answers\":[\"A\",\"B\"]")));
        session.scan(() -> true);
        assertEquals(2, phone.taps.size());
    }

    @Test public void manualChangeInLaterQuestionInvalidatesEntireResponse() throws Exception {
        Phone phone = new Phone();
        QuizSession session = new QuizSession(phone, original -> {
            JSONObject reply = new JSONObject(REPLY);
            JSONObject second = new JSONObject(REPLY).getJSONArray("questions").getJSONObject(0);
            second.put("question", "另一道题");
            second.getJSONArray("options").getJSONObject(0).put("y", 700);
            second.getJSONArray("options").getJSONObject(1).put("y", 800);
            reply.getJSONArray("questions").put(second);
            int[] pixels = new int[64 * 128];
            pixels[102 * 64 + 8] = 0x00FFFFFF;
            phone.frame = new ScreenFrame(new byte[]{1}, 1080, 2400, "practice", 7, 0,
                "2+2 A3 B4", "", new long[]{0, 0, 0, 0}, pixels);
            return QuizProtocol.parse(reply.toString());
        });
        session.scan(() -> true);
        assertTrue(phone.taps.isEmpty());
    }

    @Test public void returningToAnUnansweredQuestionCanSelectAgain() throws Exception {
        Phone phone = new Phone();
        QuizSession session = new QuizSession(phone, frame ->
            frame.uiText.equals("intermediate screen") ? List.of() : QuizProtocol.parse(REPLY));
        session.scan(() -> true);
        phone.frame = frame("practice", "intermediate screen");
        session.scan(() -> true);
        phone.frame = frame("practice", "2+2 A3 B4");
        session.scan(() -> true);
        assertEquals(2, phone.taps.size());
    }

    @Test public void newQuestionAfterAutomaticNavigationIsAnalyzed() throws Exception {
        Phone phone = new Phone() {
            @Override public boolean tap(QuizProtocol.Tap tap) {
                super.tap(tap);
                frame = frame("practice", "next question");
                return true;
            }
        };
        int[] requests = {0};
        QuizSession session = new QuizSession(phone, frame -> {
            requests[0]++;
            return requests[0] == 1 ? QuizProtocol.parse(REPLY) : List.of();
        });
        session.scan(() -> true);
        session.scan(() -> true);
        assertEquals(2, requests[0]);
    }
}
