package com.islandfx;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/** Track-scoped asynchronous lookup. All state changes happen on the UI dispatcher. */
final class LyricsModel implements AutoCloseable {
    private record TrackKey(String title, String artist) { }
    private final LyricsService.Lookup lookup;
    private final ExecutorService worker;
    private final Consumer<Runnable> dispatch;
    private final ObservableList<LyricsService.Line> lines = FXCollections.observableArrayList();
    private final ObservableList<LyricsService.Line> view = FXCollections.unmodifiableObservableList(lines);
    private final IntegerProperty activeIndex = new SimpleIntegerProperty(-1);
    private final StringProperty status = new SimpleStringProperty("Play a track to find lyrics online.");
    private final StringProperty match = new SimpleStringProperty("LRCLIB · title and artist are sent online");
    private MediaInfo media = MediaInfo.loading();
    private TrackKey key;
    private LyricsService.Result result;
    private Future<?> pending;
    private long generation;
    private boolean enabled = true;
    private boolean closed;

    static LyricsModel create() {
        LyricsService service = new LyricsService();
        ExecutorService executor = Executors.newFixedThreadPool(2, task -> {
            Thread thread = new Thread(task, "island-online-lyrics"); thread.setDaemon(true); return thread;
        });
        return new LyricsModel(service::find, executor, Platform::runLater);
    }
    LyricsModel(LyricsService.Lookup lookup, ExecutorService worker, Consumer<Runnable> dispatch) {
        this.lookup = lookup; this.worker = worker; this.dispatch = dispatch;
    }
    ObservableList<LyricsService.Line> lines() { return view; }
    IntegerProperty activeIndexProperty() { return activeIndex; }
    StringProperty statusProperty() { return status; }
    StringProperty matchProperty() { return match; }

    void setEnabled(boolean value) {
        if (closed || enabled == value) return;
        enabled = value;
        if (enabled) update(media);
        else { clear(); status.set("Online lyric lookup is off."); }
    }
    void update(MediaInfo snapshot) {
        if (closed) return;
        media = snapshot == null ? MediaInfo.unavailable() : snapshot;
        if (!enabled) return;
        if (media.availability() != MediaInfo.Availability.AVAILABLE || media.title().isBlank()) {
            if (key != null || result != null || pending != null) clear();
            status.set(media.availability() == MediaInfo.Availability.LOADING ? "Checking Windows media…"
                    : "Play a track with a title to find lyrics online.");
            return;
        }
        TrackKey next = new TrackKey(media.title(), media.artist());
        if (Objects.equals(key, next)) { updateHighlight(); return; }
        clear();
        key = next;
        long request = generation;
        MediaInfo query = media;
        status.set("Searching LRCLIB online…");
        pending = worker.submit(() -> {
            LyricsService.Result found;
            try { found = lookup.find(query); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return; }
            catch (Exception failure) { found = LyricsService.Result.error("Online lyrics unavailable. Check your connection or retry."); }
            LyricsService.Result safe = found == null ? LyricsService.Result.error("Lyrics provider returned no response.") : found;
            dispatch.accept(() -> apply(request, next, safe));
        });
    }
    void retry() {
        if (closed || !enabled || media.availability() != MediaInfo.Availability.AVAILABLE || media.title().isBlank()) return;
        MediaInfo current = media;
        clear(); update(current);
    }
    private void clear() {
        generation++;
        if (pending != null) pending.cancel(true);
        pending = null; key = null; result = null;
        lines.clear(); activeIndex.set(-1);
        match.set("LRCLIB · title and artist are sent online");
    }
    private void apply(long request, TrackKey requestedKey, LyricsService.Result found) {
        if (closed || generation != request || !Objects.equals(key, requestedKey)) return;
        pending = null; result = found;
        lines.setAll(found.lines());
        String by = found.artist().isBlank() ? "" : " — " + found.artist();
        match.set(found.track().isBlank() ? "LRCLIB · title and artist are sent online" : "LRCLIB match: " + found.track() + by);
        updateHighlight();
    }
    private void updateHighlight() {
        if (result == null) return;
        switch (result.kind()) {
            case SYNCED -> {
                if (media.hasTimeline()) {
                    status.set("Timed lyrics · follows the playback time reported by Windows");
                    activeIndex.set(lineAt(result.lines(), media.positionSeconds() * 1000));
                } else {
                    status.set("Timed lyrics · Windows has not supplied a playback time");
                    activeIndex.set(-1);
                }
            }
            case PLAIN -> { status.set("Plain lyrics · timing unavailable"); activeIndex.set(-1); }
            case INSTRUMENTAL, NOT_FOUND, ERROR -> { status.set(result.detail()); activeIndex.set(-1); }
        }
    }
    static int lineAt(List<LyricsService.Line> lines, long millis) {
        int low = 0, high = lines.size() - 1, answer = -1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            if (lines.get(middle).millis() <= millis) { answer = middle; low = middle + 1; }
            else high = middle - 1;
        }
        return answer;
    }
    static boolean isRtl(String text) {
        if (text == null) return false;
        for (int offset = 0; offset < text.length();) {
            int code = text.codePointAt(offset); offset += Character.charCount(code);
            byte direction = Character.getDirectionality(code);
            if (direction == Character.DIRECTIONALITY_LEFT_TO_RIGHT) return false;
            if (direction == Character.DIRECTIONALITY_RIGHT_TO_LEFT || direction == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC) return true;
        }
        return false;
    }
    @Override public void close() {
        closed = true; clear(); worker.shutdownNow();
    }
}
