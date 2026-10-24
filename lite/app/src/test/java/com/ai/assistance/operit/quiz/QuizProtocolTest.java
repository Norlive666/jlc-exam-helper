package com.ai.assistance.operit.quiz;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class QuizProtocolTest {
    private static final String SINGLE = """
        {"questions":[{"question":"2+2等于多少？","type":"single",
        "options":[{"label":"A","text":"3","x":250,"y":300,"selected":false},
                   {"label":"B","text":"4","x":250,"y":450,"selected":false}],
        "answers":["B"]}]}
        """;

    @Test public void correctChoiceUsesScreenshotCoordinates() throws Exception {
        List<QuizProtocol.Tap> taps = QuizProtocol.parse(SINGLE).get(0).changes(1080, 2400);
        assertEquals(1, taps.size());
        assertEquals(270, taps.get(0).x);
        assertEquals(1080, taps.get(0).y);
    }

    @Test public void selectedCorrectChoiceIsNotToggled() throws Exception {
        JSONObject reply = new JSONObject(SINGLE);
        reply.getJSONArray("questions").getJSONObject(0).getJSONArray("options")
            .getJSONObject(1).put("selected", true);
        assertTrue(QuizProtocol.parse(reply.toString()).get(0).changes(1080, 2400).isEmpty());
    }

    @Test public void multipleChoiceCorrectsSelectionState() throws Exception {
        String reply = """
            {"questions":[{"question":"选择偶数","type":"multiple",
            "options":[{"label":"A","text":"1","x":100,"y":200,"selected":true},
                       {"label":"B","text":"2","x":100,"y":300,"selected":true},
                       {"label":"C","text":"4","x":100,"y":400,"selected":false}],
            "answers":["B","C"]}]}
            """;
        List<QuizProtocol.Tap> taps = QuizProtocol.parse(reply).get(0).changes(1000, 1000);
        assertEquals(2, taps.size());
        assertEquals(200, taps.get(0).y);
        assertEquals(400, taps.get(1).y);
    }

    @Test public void requestContainsOneVisionModelAndScreenshot() throws Exception {
        JSONObject request = QuizProtocol.request("aGVsbG8=", "question text");
        assertEquals("deepseek-flash", request.getString("model"));
        assertEquals("json_object", request.getJSONObject("response_format").getString("type"));
        JSONArray parts = request.getJSONArray("messages").getJSONObject(1).getJSONArray("content");
        assertEquals("data:image/jpeg;base64,aGVsbG8=",
            parts.getJSONObject(1).getJSONObject("image_url").getString("url"));
    }

    @Test public void noQuestionProducesNoAction() throws Exception {
        assertTrue(QuizProtocol.parse("{\"questions\":[]}").isEmpty());
    }

    @Test public void edgeCoordinatesStayInsideScreen() throws Exception {
        String reply = SINGLE.replace("\"x\":250,\"y\":450", "\"x\":1000,\"y\":1000");
        QuizProtocol.Tap tap = QuizProtocol.parse(reply).get(0).changes(1080, 2400).get(0);
        assertEquals(1079, tap.x);
        assertEquals(2399, tap.y);
    }

    @Test(expected = IllegalArgumentException.class)
    public void answerMustReferenceAVisibleOption() throws Exception {
        QuizProtocol.parse(SINGLE.replace("\"answers\":[\"B\"]", "\"answers\":[\"Z\"]"));
    }
}
