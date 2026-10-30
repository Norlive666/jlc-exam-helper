package com.ai.assistance.operit.quiz;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Temporary in-memory diagnostics; contains no images, credentials or request headers. */
final class QuizHistory {
    private final ArrayDeque<Round> rounds = new ArrayDeque<>();
    private final SimpleDateFormat time = new SimpleDateFormat("HH:mm:ss", Locale.ROOT);
    private long nextId;
    private long version;

    synchronized long begin(String app) {
        Round round = new Round(++nextId, time.format(new Date()), app);
        rounds.addFirst(round);
        while (rounds.size() > 30) rounds.removeLast();
        version++;
        return round.id;
    }

    synchronized void event(long id, String stage, String detail) {
        for (Round round : rounds) {
            if (round.id != id) continue;
            String value = stage + (detail.isEmpty() ? "" : "：" + detail);
            if (value.equals(round.lastEvent)) return;
            round.lastEvent = value;
            round.events.add(time.format(new Date()) + "  " + value);
            version++;
            return;
        }
    }

    synchronized int size() { return rounds.size(); }
    synchronized long version() { return version; }
    synchronized void clear() { rounds.clear(); version++; }

    synchronized String text() {
        if (rounds.isEmpty()) return "暂无记录。可从配置页或测试页开始识别，再返回题目页面。";
        StringBuilder text = new StringBuilder();
        for (Round round : rounds) {
            text.append("第 ").append(round.id).append(" 次 · ").append(round.time)
                .append("\n应用：").append(round.app).append('\n');
            for (String event : round.events) text.append(event).append('\n');
            text.append('\n');
        }
        return text.toString();
    }

    private static final class Round {
        final long id;
        final String time;
        final String app;
        final List<String> events = new ArrayList<>();
        String lastEvent = "";

        Round(long id, String time, String app) {
            this.id = id;
            this.time = time;
            this.app = app;
        }
    }
}
