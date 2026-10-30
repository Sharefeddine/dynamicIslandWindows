package com.islandfx;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class LyricsModelTest {
    private static MediaInfo media(String title, long position) {
        return MediaInfo.session(MediaInfo.Playback.PLAYING, title, "Artist", "player", position, 120);
    }
    private static LyricsService.Result result(String title) {
        return new LyricsService.Result(LyricsService.Kind.SYNCED, title, "Artist",
                List.of(new LyricsService.Line(1000, title + " first"), new LyricsService.Line(5000, title + " second")), "");
    }
    @Test void lateResponseCannotOverwriteTheNewTrackAndSeeksUpdateHighlight() throws Exception {
        CountDownLatch firstStarted = new CountDownLatch(1), releaseFirst = new CountDownLatch(1);
        ArrayBlockingQueue<Runnable> ui = new ArrayBlockingQueue<>(4);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        LyricsService.Lookup lookup = media -> {
            if (media.title().equals("Old")) {
                firstStarted.countDown();
                // Simulate a provider that ignores cancellation and completes late.
                while (true) {
                    try { if (releaseFirst.await(3, TimeUnit.SECONDS)) break; }
                    catch (InterruptedException ignored) { }
                }
            }
            return result(media.title());
        };
        try (LyricsModel model = new LyricsModel(lookup, executor, ui::add)) {
            model.update(media("Old", 2)); assertTrue(firstStarted.await(3, TimeUnit.SECONDS));
            model.update(media("New", 6));
            Runnable newer = ui.poll(3, TimeUnit.SECONDS); assertNotNull(newer); newer.run();
            assertEquals("New first", model.lines().get(0).text());
            assertEquals(1, model.activeIndexProperty().get());
            model.update(media("New", 2)); assertEquals(0, model.activeIndexProperty().get());
            model.update(MediaInfo.session(MediaInfo.Playback.PAUSED, "New", "Artist", "player"));
            assertEquals(-1, model.activeIndexProperty().get());
            assertTrue(model.statusProperty().get().contains("not supplied"));
            releaseFirst.countDown();
            Runnable older = ui.poll(3, TimeUnit.SECONDS); assertNotNull(older); older.run();
            assertEquals("New first", model.lines().get(0).text());
            model.update(MediaInfo.noSession());
            assertTrue(model.lines().isEmpty());
            assertEquals(-1, model.activeIndexProperty().get());
        } finally { releaseFirst.countDown(); executor.shutdownNow(); }
    }
    @Test void disabledOnlineLookupSendsNothingUntilReenabled() throws Exception {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        ArrayBlockingQueue<Runnable> ui = new ArrayBlockingQueue<>(2);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (LyricsModel model = new LyricsModel(track -> { calls.incrementAndGet(); return result(track.title()); }, executor, ui::add)) {
            model.setEnabled(false); model.update(media("Private track", 5)); model.retry();
            assertEquals(0, calls.get()); assertTrue(model.lines().isEmpty());
            assertTrue(model.statusProperty().get().contains("off"));
            model.setEnabled(true);
            Runnable completed = ui.poll(3, TimeUnit.SECONDS); assertNotNull(completed); completed.run();
            assertEquals(1, calls.get()); assertEquals("Private track first", model.lines().get(0).text());
        }
    }
    @Test void errorsAreVisibleAndRetryUsesSameTrack() throws Exception {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        ArrayBlockingQueue<Runnable> ui = new ArrayBlockingQueue<>(2);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (LyricsModel model = new LyricsModel(media -> {
            if (calls.incrementAndGet() == 1) throw new java.io.IOException("offline");
            return new LyricsService.Result(LyricsService.Kind.PLAIN, media.title(), media.artist(),
                    List.of(new LyricsService.Line(-1, "hello")), "");
        }, executor, ui::add)) {
            model.update(media("Song", 3));
            Runnable first = ui.poll(3, TimeUnit.SECONDS); assertNotNull(first); first.run();
            assertTrue(model.statusProperty().get().contains("unavailable"));
            model.retry();
            Runnable second = ui.poll(3, TimeUnit.SECONDS); assertNotNull(second); second.run();
            assertEquals(2, calls.get());
            assertEquals("hello", model.lines().get(0).text());
            assertEquals(-1, model.activeIndexProperty().get());
            assertTrue(model.statusProperty().get().contains("Plain"));
        }
    }
}
