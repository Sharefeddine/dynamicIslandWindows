package com.islandfx;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Small, deliberately limited English command classifier; this is not a general AI model. */
public final class CommandParser {
    public enum Intent { PLAY_PAUSE, NEXT, PREVIOUS, TIME, BATTERY, HELP, STOP_LISTENING, UNKNOWN }
    private CommandParser() { }

    public static Intent parse(String input) {
        if (input == null) return Intent.UNKNOWN;
        String text = input.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
        if (text.isEmpty()) return Intent.UNKNOWN;
        Set<String> words = new HashSet<>(Arrays.asList(text.split(" +")));
        boolean sound = words.contains("music") || words.contains("song") || words.contains("track")
                || words.contains("audio") || words.contains("media") || words.contains("video")
                || words.contains("youtube");
        if ((words.contains("stop") || words.contains("cancel"))
                && (words.contains("listening") || words.contains("microphone"))) return Intent.STOP_LISTENING;
        if ((words.contains("next") || words.contains("skip"))
                && (sound || text.equals("next") || text.equals("skip"))) return Intent.NEXT;
        if ((words.contains("previous") || words.contains("back"))
                && (sound || text.equals("previous") || text.equals("back"))) return Intent.PREVIOUS;
        if ((words.contains("play") || words.contains("pause") || words.contains("resume"))
                && (sound || text.equals("play") || text.equals("pause") || text.equals("resume"))) return Intent.PLAY_PAUSE;
        if (words.contains("time") || words.contains("clock")) return Intent.TIME;
        if (words.contains("battery") || words.contains("charge")) return Intent.BATTERY;
        if (words.contains("help") || text.contains("what can you do")) return Intent.HELP;
        return Intent.UNKNOWN;
    }
}
