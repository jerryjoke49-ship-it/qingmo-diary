package com.local.qingmo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class Mood {
    static final Mood[] ALL = new Mood[]{
            new Mood("calm", "☁️", "平静", 0xFF252D36),
            new Mood("happy", "☀️", "开心", 0xFF342D20),
            new Mood("soft", "🌿", "松弛", 0xFF243229),
            new Mood("low", "🌧️", "低落", 0xFF2E2735),
            new Mood("tired", "🌙", "疲惫", 0xFF352720)
    };

    final String key;
    final String emoji;
    final String label;
    final int tint;

    Mood(String key, String emoji, String label, int tint) {
        this.key = key;
        this.emoji = emoji;
        this.label = label;
        this.tint = tint;
    }

    static Mood byKey(String key) {
        for (Mood mood : ALL) {
            if (mood.key.equals(key)) {
                return mood;
            }
        }
        return ALL[0];
    }
}

final class Note {
    long id;
    String title;
    String content;
    String mood;
    long createdAt;
    long updatedAt;
    final ArrayList<String> images = new ArrayList<>();

    static String joinImages(List<String> images) {
        if (images == null || images.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (String image : images) {
            if (image == null || image.trim().isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('|');
            }
            builder.append(image);
        }
        return builder.toString();
    }

    static ArrayList<String> splitImages(String raw) {
        ArrayList<String> result = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) {
            return result;
        }
        result.addAll(Arrays.asList(raw.split("\\|")));
        return result;
    }
}
