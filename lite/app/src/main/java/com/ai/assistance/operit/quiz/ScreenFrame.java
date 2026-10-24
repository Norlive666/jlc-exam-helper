package com.ai.assistance.operit.quiz;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ScreenFrame {
    private static final Pattern NATIVE_SELECTION = Pattern.compile(
        "\\[(-?\\d+),(-?\\d+)\\]\\[(-?\\d+),(-?\\d+)\\]:(true|false):(true|false):(true|false)");

    Boolean selectedAt(QuizProtocol.Tap tap) {
        Matcher nodes = NATIVE_SELECTION.matcher(selectionState);
        long area = Long.MAX_VALUE;
        Boolean selected = null;
        while (nodes.find()) {
            if (!Boolean.parseBoolean(nodes.group(5))) continue;
            int left = Integer.parseInt(nodes.group(1));
            int top = Integer.parseInt(nodes.group(2));
            int right = Integer.parseInt(nodes.group(3));
            int bottom = Integer.parseInt(nodes.group(4));
            long size = (long) (right - left) * (bottom - top);
            if (tap.x >= left && tap.x < right && tap.y >= top && tap.y < bottom && size < area) {
                area = size;
                selected = Boolean.parseBoolean(nodes.group(6));
            }
        }
        return selected;
    }

    static String normalizeUiText(String xml) {
        return xml.replaceAll("\\b\\d{1,2}:\\d{2}(?::\\d{2})?\\b", "")
            .replaceAll(" (?:checked|selected|focused)=\"[^\"]*\"", "")
            .replaceAll("(?i)[,，\\s]+(?:已选中|未选中|not selected|selected|unchecked|checked)(?=\")", "");
    }

    final byte[] jpeg;
    final int width;
    final int height;
    final String app;
    final int windowId;
    final int rotation;
    final String uiText;
    final String selectionState;
    private final long[] hash;
    private final int[] pixels;

    ScreenFrame(byte[] jpeg, int width, int height, String app, int windowId,
                int rotation, String uiText, String selectionState, long[] hash, int[] pixels) {
        this.jpeg = jpeg;
        this.width = width;
        this.height = height;
        this.app = app;
        this.windowId = windowId;
        this.rotation = rotation;
        this.uiText = uiText;
        this.selectionState = selectionState;
        this.hash = hash;
        this.pixels = pixels;
    }

    boolean sameScene(ScreenFrame other) {
        return sameWindow(other) && difference(other) <= 24;
    }

    boolean unchanged(ScreenFrame other) {
        return sameWindow(other) && selectionState.equals(other.selectionState) && difference(other) <= 2;
    }

    boolean sameSelection(ScreenFrame other, List<QuizProtocol.Tap> options) {
        if (!sameScene(other) || !selectionState.equals(other.selectionState)) return false;
        // Canvas/WebView choices may expose no checked nodes: compare the actual option rows too.
        for (QuizProtocol.Tap option : options) {
            int row = option.y * 128 / height;
            for (int y = Math.max(0, row - 3); y < Math.min(128, row + 4); y++) {
                for (int x = 0; x < 64; x++) {
                    int index = y * 64 + x;
                    int a = pixels[index];
                    int b = other.pixels[index];
                    int change = Math.abs(((a >> 16) & 255) - ((b >> 16) & 255))
                        + Math.abs(((a >> 8) & 255) - ((b >> 8) & 255))
                        + Math.abs((a & 255) - (b & 255));
                    if (change > 60) return false;
                }
            }
        }
        return true;
    }

    private boolean sameWindow(ScreenFrame other) {
        return other != null && width == other.width && height == other.height
            && windowId == other.windowId && rotation == other.rotation
            && app.equals(other.app) && uiText.equals(other.uiText);
    }

    private int difference(ScreenFrame other) {
        int bits = 0;
        for (int i = 0; i < hash.length; i++) bits += Long.bitCount(hash[i] ^ other.hash[i]);
        return bits;
    }
}
