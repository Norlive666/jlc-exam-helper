package com.ai.assistance.operit.quiz;

import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

final class DeepSeekClient implements QuizSession.Model, AutoCloseable {
    private final String apiKey;
    private final AtomicReference<HttpURLConnection> request = new AtomicReference<>();
    private volatile boolean closed;

    DeepSeekClient(String apiKey) { this.apiKey = apiKey; }

    @Override public List<QuizProtocol.Question> analyze(ScreenFrame frame) throws Exception {
        if (closed || Thread.currentThread().isInterrupted()) throw new InterruptedException();
        byte[] body = QuizProtocol.request(Base64.getEncoder().encodeToString(frame.jpeg), frame.uiText)
            .toString().getBytes(StandardCharsets.UTF_8);
        HttpURLConnection connection = (HttpURLConnection)
            new URL("https://api.deepseek.com/chat/completions").openConnection();
        request.set(connection);
        try {
            if (closed) throw new InterruptedException();
            connection.setRequestMethod("POST");
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(90000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setFixedLengthStreamingMode(body.length);
            try (OutputStream output = connection.getOutputStream()) { output.write(body); }
            int status = connection.getResponseCode();
            if (status != 200) throw new IOException("DeepSeek HTTP " + status);
            String response;
            try (InputStream input = connection.getInputStream();
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int length;
                while ((length = input.read(buffer)) != -1) {
                    if (closed || Thread.currentThread().isInterrupted()) throw new InterruptedException();
                    output.write(buffer, 0, length);
                }
                response = output.toString(StandardCharsets.UTF_8.name());
            }
            String content = new JSONObject(response).getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content");
            return QuizProtocol.parse(content);
        } finally {
            request.compareAndSet(connection, null);
            connection.disconnect();
        }
    }

    @Override public void close() {
        closed = true;
        HttpURLConnection connection = request.getAndSet(null);
        if (connection != null) connection.disconnect();
    }
}
