package com.islandfx;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** LRCLIB search and parsing; no local lyrics are persisted. */
final class LyricsService {
    enum Kind { SYNCED, PLAIN, INSTRUMENTAL, NOT_FOUND, ERROR }
    record Line(long millis, String text) { }
    record Result(Kind kind, String track, String artist, List<Line> lines, String detail) {
        Result { lines = List.copyOf(lines); }
        static Result error(String detail) { return new Result(Kind.ERROR, "", "", List.of(), detail); }
    }
    @FunctionalInterface interface Lookup { Result find(MediaInfo media) throws Exception; }
    private static final int MAX_BYTES = 1_000_000;
    private static final int MAX_LINES = 3_000;
    private static final Pattern STAMP = Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:\\.(\\d{1,3}))?\\]");
    private static final Pattern OFFSET = Pattern.compile("(?im)^\\s*\\[offset:([+-]?\\d+)\\]\\s*$");
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6))
            .followRedirects(HttpClient.Redirect.NORMAL).build();

    static URI searchUri(MediaInfo media) {
        String query = "https://lrclib.net/api/search?track_name=" + encode(media.title());
        if (!media.artist().isBlank()) query += "&artist_name=" + encode(media.artist());
        return URI.create(query);
    }
    Result find(MediaInfo media) throws IOException, InterruptedException {
        Result last = new Result(Kind.NOT_FOUND, "", "", List.of(), "No matching lyrics found online.");
        for (MediaInfo query : searchVariants(media)) {
            HttpRequest request = HttpRequest.newBuilder(searchUri(query)).timeout(Duration.ofSeconds(12))
                    .header("Accept", "application/json")
                    .header("User-Agent", "IslandForWindows/1.0 (https://github.com/Sharefeddine/dynamicIslandWindows)")
                    .GET().build();
            HttpResponse<byte[]> response = http.send(request, ignored -> new BoundedBody(MAX_BYTES));
            if (response.statusCode() != 200) throw new IOException("Lyrics provider returned HTTP " + response.statusCode());
            last = parseResponse(new String(response.body(), StandardCharsets.UTF_8), query);
            if (last.kind() != Kind.NOT_FOUND) return last;
        }
        return last;
    }
    /** Handle common YouTube titles without using window titles or browser history. */
    static List<MediaInfo> searchVariants(MediaInfo media) {
        String cleaned = media.title().replaceAll("(?iu)\\s*[\\[(](?:official|lyrics?|audio|video|visuali[sz]er|clip officiel|paroles|كلمات|مترجمة)[^\\])]*[\\])]\\s*$", "").trim();
        cleaned = cleaned.replaceAll("(?iu)\\s*[-–—]\\s*(?:official (?:video|audio|music video)|lyrics? video|clip officiel|paroles)\\s*$", "").trim();
        String title = cleaned, artist = media.artist();
        String[] parts = cleaned.split("\\s+[-–—]\\s+", 2);
        if (parts.length == 2 && !parts[0].isBlank() && !parts[1].isBlank()
                && !parts[1].matches("(?iu)^(?:live|remaster(?:ed)?|radio edit|acoustic|instrumental|mix)\\b.*")) {
            artist = parts[0].trim(); title = parts[1].trim();
        }
        if (title.isBlank() || (title.equals(media.title()) && artist.equals(media.artist()))) return List.of(media);
        MediaInfo inferred = MediaInfo.session(media.playback(), title, artist, media.source(), media.positionSeconds(), media.durationSeconds());
        return List.of(inferred, media);
    }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }

    /** Match conservatively; the displayed provider title lets the listener judge the result. */
    static Result parseResponse(String json, MediaInfo media) throws IOException {
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonArray()) throw new IOException("Invalid response from lyrics provider");
            JsonArray candidates = root.getAsJsonArray();
            JsonObject best = null;
            int bestScore = Integer.MIN_VALUE;
            String wantedTitle = normalized(media.title());
            String wantedArtist = normalized(media.artist());
            for (JsonElement candidate : candidates) {
                if (!candidate.isJsonObject()) continue;
                JsonObject object = candidate.getAsJsonObject();
                String title = normalized(string(object, "trackName"));
                String artist = normalized(string(object, "artistName"));
                if (title.isBlank() || !title.equals(wantedTitle)) continue;
                if (!wantedArtist.isBlank() && (artist.isBlank() || !(artist.equals(wantedArtist)
                        || artist.contains(wantedArtist) || wantedArtist.contains(artist)))) continue;
                int score = 100 + (artist.equals(wantedArtist) && !artist.isBlank() ? 20 : 0);
                double duration = number(object, "duration");
                if (media.hasTimeline() && duration > 0) {
                    double difference = Math.abs(duration - media.durationSeconds());
                    if (difference <= 5) score += 20;
                    else if (difference <= 20) score += 10;
                    else if (difference > 60) score -= 20;
                }
                if (!string(object, "syncedLyrics").isBlank()) score += 3;
                if (score > bestScore) { best = object; bestScore = score; }
            }
            if (best == null) return new Result(Kind.NOT_FOUND, "", "", List.of(), "No matching lyrics found online.");
            String title = string(best, "trackName"), artist = string(best, "artistName");
            List<Line> synced = parseLrc(string(best, "syncedLyrics"));
            if (!synced.isEmpty()) return new Result(Kind.SYNCED, title, artist, synced, "");
            List<Line> plain = parsePlain(string(best, "plainLyrics"));
            if (!plain.isEmpty()) return new Result(Kind.PLAIN, title, artist, plain, "");
            boolean instrumental = best.has("instrumental") && !best.get("instrumental").isJsonNull()
                    && best.get("instrumental").getAsBoolean();
            return new Result(instrumental ? Kind.INSTRUMENTAL : Kind.NOT_FOUND, title, artist, List.of(),
                    instrumental ? "LRCLIB marks this track as instrumental." : "No lyrics found for this track.");
        } catch (RuntimeException invalid) {
            throw new IOException("Invalid response from lyrics provider", invalid);
        }
    }

    static List<Line> parseLrc(String text) {
        if (text == null || text.isBlank()) return List.of();
        long offset = 0;
        Matcher offsetMatcher = OFFSET.matcher(text);
        if (offsetMatcher.find()) {
            try { offset = Math.max(-3_600_000, Math.min(3_600_000, Long.parseLong(offsetMatcher.group(1)))); }
            catch (NumberFormatException ignored) { }
        }
        List<Line> result = new ArrayList<>();
        for (String row : text.split("\\R")) {
            Matcher matcher = STAMP.matcher(row);
            List<Long> times = new ArrayList<>();
            int end = 0;
            while (matcher.find()) {
                int seconds = Integer.parseInt(matcher.group(2));
                if (seconds >= 60) continue;
                String fraction = matcher.group(3);
                int millis = fraction == null ? 0 : Integer.parseInt((fraction + "00").substring(0, 3));
                times.add(Math.max(0, Long.parseLong(matcher.group(1)) * 60_000 + seconds * 1000L + millis + offset));
                end = matcher.end();
            }
            String lyric = end == 0 ? "" : row.substring(end).trim();
            if (lyric.isEmpty()) continue;
            for (long time : times) {
                result.add(new Line(time, lyric));
                if (result.size() >= MAX_LINES) break;
            }
            if (result.size() >= MAX_LINES) break;
        }
        result.sort(Comparator.comparingLong(Line::millis));
        return List.copyOf(result);
    }
    static List<Line> parsePlain(String text) {
        if (text == null || text.isBlank()) return List.of();
        List<Line> result = new ArrayList<>();
        for (String row : text.split("\\R", -1)) {
            // Keep stanza breaks but never permit unbounded content in the view.
            result.add(new Line(-1, row));
            if (result.size() >= MAX_LINES) break;
        }
        return List.copyOf(result);
    }
    static String normalized(String text) {
        if (text == null) return "";
        return Normalizer.normalize(text, Normalizer.Form.NFKD).toLowerCase(Locale.ROOT)
                .replaceAll("\\p{M}+", "").replaceAll("[^\\p{L}\\p{N}]+", " ").trim().replaceAll(" +", " ");
    }
    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() || !value.isJsonPrimitive() ? "" : value.getAsString();
    }
    private static double number(JsonObject object, String key) {
        try { return object.get(key).getAsDouble(); } catch (RuntimeException invalid) { return -1; }
    }

    /** Reject oversized bodies rather than accumulating an unbounded network response. */
    private static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final int max;
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private Flow.Subscription subscription;
        BoundedBody(int max) { this.max = max; }
        @Override public CompletionStage<byte[]> getBody() { return body; }
        @Override public void onSubscribe(Flow.Subscription value) { subscription = value; value.request(1); }
        @Override public void onNext(List<ByteBuffer> chunks) {
            for (ByteBuffer chunk : chunks) {
                if (chunk.remaining() > max - bytes.size()) {
                    subscription.cancel(); body.completeExceptionally(new IOException("Lyrics response is too large")); return;
                }
                byte[] part = new byte[chunk.remaining()]; chunk.get(part); bytes.write(part, 0, part.length);
            }
            subscription.request(1);
        }
        @Override public void onError(Throwable failure) { body.completeExceptionally(failure); }
        @Override public void onComplete() { body.complete(bytes.toByteArray()); }
    }
}
