package com.islandfx;

import java.util.Locale;
import java.util.Objects;

/** A snapshot reported by Windows, not a guess based on a browser window title. */
public record MediaInfo(Availability availability, Playback playback, String title, String artist, String source, String detail) {
    public enum Availability { LOADING, UNAVAILABLE, NO_SESSION, AVAILABLE }
    public enum Playback { PLAYING, PAUSED, STOPPED, UNKNOWN }

    public MediaInfo {
        Objects.requireNonNull(availability);
        Objects.requireNonNull(playback);
        title = clean(title);
        artist = clean(artist);
        source = clean(source);
        detail = clean(detail);
    }
    public static MediaInfo loading() { return new MediaInfo(Availability.LOADING, Playback.UNKNOWN, "", "", "", ""); }
    public static MediaInfo unavailable() { return unavailable(""); }
    public static MediaInfo unavailable(String detail) { return new MediaInfo(Availability.UNAVAILABLE, Playback.UNKNOWN, "", "", "", detail); }
    public static MediaInfo noSession() { return new MediaInfo(Availability.NO_SESSION, Playback.UNKNOWN, "", "", "", ""); }
    public static MediaInfo session(Playback playback, String title, String artist, String source) {
        return new MediaInfo(Availability.AVAILABLE, playback, title, artist, source, "");
    }
    public static Playback playbackFromWindows(String value) {
        if (value == null) return Playback.UNKNOWN;
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "playing" -> Playback.PLAYING;
            case "paused" -> Playback.PAUSED;
            case "stopped", "closed" -> Playback.STOPPED;
            default -> Playback.UNKNOWN;
        };
    }
    public String sourceLabel() {
        String id = source.toLowerCase(Locale.ROOT);
        if (id.contains("youtube")) return "YouTube";
        if (id.contains("spotify")) return "Spotify";
        if (id.contains("msedge") || id.contains("microsoftedge")) return "Microsoft Edge";
        if (id.contains("chrome")) return "Google Chrome";
        if (id.contains("firefox")) return "Firefox";
        if (id.contains("brave")) return "Brave";
        if (id.contains("opera")) return "Opera";
        return "Windows media";
    }
    private static String clean(String value) {
        if (value == null) return "";
        String result = value.replaceAll("\\p{Cc}", " ").trim();
        return result.length() <= 500 ? result : result.substring(0, 500);
    }
}
