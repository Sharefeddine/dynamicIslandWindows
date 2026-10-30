package com.islandfx;

import java.util.List;
import java.util.concurrent.Executors;
import javafx.application.Platform;

/** Invented, offline sample lyrics for renders and UI tests; never contacts LRCLIB. */
final class PreviewData {
    private PreviewData() { }
    static LyricsModel lyrics() {
        return new LyricsModel(media -> new LyricsService.Result(LyricsService.Kind.SYNCED, media.title(), media.artist(),
                List.of(new LyricsService.Line(180_000, "A quiet morning, a brand new day"),
                        new LyricsService.Line(190_000, "Un nouveau matin se lève"),
                        new LyricsService.Line(200_000, "صَبَاحٌ جَدِيدٌ يَبْدَأُ")), ""),
                Executors.newSingleThreadExecutor(task -> { Thread thread = new Thread(task, "sample-lyrics"); thread.setDaemon(true); return thread; }),
                Platform::runLater);
    }
}
