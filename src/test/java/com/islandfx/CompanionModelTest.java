package com.islandfx;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CompanionModelTest {
    @TempDir Path directory;

    static final class MutableClock extends Clock {
        private Instant current = Instant.parse("2026-10-28T10:00:00Z");
        private final ZoneId zone;
        MutableClock(ZoneId zone) { this.zone = zone; }
        void advance(Duration duration) { current = current.plus(duration); }
        @Override public ZoneId getZone() { return zone; }
        @Override public Clock withZone(ZoneId zone) { MutableClock copy = new MutableClock(zone); copy.current = current; return copy; }
        @Override public Instant instant() { return current; }
    }
    private CompanionModel model(CompanionStore store, MutableClock clock, AtomicLong nanos, List<String> notifications) {
        return new CompanionModel(store, clock, nanos::get, (title, message) -> notifications.add(title + ": " + message));
    }

    @Test void focusTimerCanPauseResumeFinishAndReset() {
        MutableClock clock = new MutableClock(ZoneId.of("UTC"));
        AtomicLong nanos = new AtomicLong();
        List<String> notices = new ArrayList<>();
        try (CompanionModel model = model(new CompanionStore(null), clock, nanos, notices)) {
            assertEquals(1500, model.focusRemainingProperty().get());
            model.toggleFocus();
            nanos.addAndGet(42_000_000_000L); model.tick();
            assertEquals(1458, model.focusRemainingProperty().get());
            model.toggleFocus();
            assertFalse(model.focusRunningProperty().get());
            nanos.addAndGet(100_000_000_000L); model.tick();
            assertEquals(1458, model.focusRemainingProperty().get());
            model.toggleFocus();
            nanos.addAndGet(1458_000_000_000L); model.tick(); model.tick();
            assertEquals(0, model.focusRemainingProperty().get());
            assertFalse(model.focusRunningProperty().get());
            assertEquals(1, notices.size());
            assertTrue(notices.get(0).contains("complete"));
            model.selectFocusMode(CompanionModel.FocusMode.BREAK);
            assertEquals(300, model.focusRemainingProperty().get());
            model.toggleFocus(); model.resetFocus();
            assertFalse(model.focusRunningProperty().get());
            assertEquals(300, model.focusRemainingProperty().get());
        }
    }

    @Test void waterSettingsPersistAndTheDailyTotalResetsAtMidnight() {
        MutableClock clock = new MutableClock(ZoneId.of("UTC"));
        AtomicLong nanos = new AtomicLong();
        Path path = directory.resolve("data.properties");
        try (CompanionModel model = model(new CompanionStore(path), clock, nanos, new ArrayList<>())) {
            model.applyWaterSettings("1800", "300");
            model.drink(); model.drink(); model.toggleWaterReminder();
            assertEquals(600, model.waterMlProperty().get());
            assertTrue(model.waterReminderProperty().get());
            model.applyWaterSettings("-1", "0");
            assertEquals(1800, model.waterGoalProperty().get(), "invalid settings do not replace valid values");
        }
        try (CompanionModel restored = model(new CompanionStore(path), clock, nanos, new ArrayList<>())) {
            assertEquals(600, restored.waterMlProperty().get());
            assertEquals(1800, restored.waterGoalProperty().get());
            assertEquals(300, restored.glassMlProperty().get());
            assertTrue(restored.waterReminderProperty().get());
            clock.advance(Duration.ofHours(15)); restored.tick();
            assertEquals(0, restored.waterMlProperty().get());
            assertEquals(1800, restored.waterGoalProperty().get());
            restored.drink();
        }
        try (CompanionModel restored = model(new CompanionStore(path), clock, nanos, new ArrayList<>())) {
            assertEquals(300, restored.waterMlProperty().get());
        }
    }

    @Test void waterRemindersAreOptInHourlyAndStopOnceTheGoalIsReached() {
        MutableClock clock = new MutableClock(ZoneId.of("UTC"));
        List<String> notices = new ArrayList<>();
        try (CompanionModel model = model(new CompanionStore(null), clock, new AtomicLong(), notices)) {
            clock.advance(Duration.ofHours(2)); model.tick(); assertTrue(notices.isEmpty());
            model.toggleWaterReminder();
            clock.advance(Duration.ofMinutes(59)); model.tick(); assertTrue(notices.isEmpty());
            clock.advance(Duration.ofMinutes(1)); model.tick(); model.tick(); assertEquals(1, notices.size());
            model.applyWaterSettings("250", "250"); model.drink();
            clock.advance(Duration.ofHours(2)); model.tick(); assertEquals(1, notices.size());
            model.resetWater(); clock.advance(Duration.ofHours(1)); model.tick(); assertEquals(2, notices.size());
        }
    }

    @Test void tasksPersistCanBeCompletedAndNotifyOnlyOnce() {
        MutableClock clock = new MutableClock(ZoneId.of("UTC"));
        AtomicLong nanos = new AtomicLong();
        List<String> notices = new ArrayList<>();
        Path path = directory.resolve("tasks.properties");
        String id;
        try (CompanionModel model = model(new CompanionStore(path), clock, nanos, notices)) {
            assertFalse(model.addTask(" ", ""));
            assertFalse(model.addTask("Bad time", "25:70"));
            assertFalse(model.addTask("Midnight is not 24:00", "24:00"));
            assertTrue(model.addTask("Call Zoë", "10:05"));
            assertTrue(model.addTask("Finish report", ""));
            id = model.tasks().get(0).id();
            clock.advance(Duration.ofMinutes(5)); model.tick(); model.tick();
            assertEquals(1, notices.size());
            assertTrue(notices.get(0).contains("Call Zoë"));
            assertTrue(model.tasks().get(0).notified());
            model.setTaskDone(id, true);
        }
        try (CompanionModel restored = model(new CompanionStore(path), clock, nanos, notices)) {
            assertEquals(2, restored.tasks().size());
            assertEquals("Call Zoë", restored.tasks().get(0).title());
            assertTrue(restored.tasks().get(0).done());
            restored.tick(); assertEquals(1, notices.size());
            restored.removeTask(id);
            assertEquals(1, restored.tasks().size());
        }
    }

    @Test void reminderTimeThatHasPassedMeansTheNextDay() {
        MutableClock clock = new MutableClock(ZoneId.of("UTC"));
        List<String> notices = new ArrayList<>();
        try (CompanionModel model = model(new CompanionStore(null), clock, new AtomicLong(), notices)) {
            assertTrue(model.addTask("Tomorrow", "09:00"));
            clock.advance(Duration.ofHours(22)); model.tick(); assertTrue(notices.isEmpty());
            clock.advance(Duration.ofHours(1)); model.tick(); assertEquals(1, notices.size());
        }
    }

    @Test void shelfStoresShortcutsAndRemovingNeverDeletesTheOriginal() throws Exception {
        MutableClock clock = new MutableClock(ZoneId.of("UTC"));
        Path original = Files.writeString(directory.resolve("document.txt"), "keep me");
        Path path = directory.resolve("shelf.properties");
        try (CompanionModel model = model(new CompanionStore(path), clock, new AtomicLong(), new ArrayList<>())) {
            model.addFiles(List.of(original.toFile(), original.toFile(), directory.toFile()));
            assertEquals(2, model.shelf().size());
            assertEquals("document.txt", CompanionModel.fileName(model.shelf().get(0)));
        }
        try (CompanionModel restored = model(new CompanionStore(path), clock, new AtomicLong(), new ArrayList<>())) {
            assertEquals(2, restored.shelf().size());
            restored.removeFile(original.toAbsolutePath().normalize().toString());
            assertEquals(1, restored.shelf().size());
            assertEquals("keep me", Files.readString(original));
        }
    }

    @Test void unreadableDataIsNotOverwrittenAndSaveFailuresAreVisible() throws Exception {
        MutableClock clock = new MutableClock(ZoneId.of("UTC"));
        Path corrupt = directory.resolve("corrupt.properties");
        Files.writeString(corrupt, "broken=\\uXXXX\n");
        try (CompanionModel model = model(new CompanionStore(corrupt), clock, new AtomicLong(), new ArrayList<>())) {
            assertTrue(model.storageWarningProperty().get().contains("could not be read"));
            assertTrue(model.addTask("temporary", ""));
            assertEquals("broken=\\uXXXX\n", Files.readString(corrupt));
        }
        Path blocker = Files.writeString(directory.resolve("not-a-directory"), "data");
        try (CompanionModel model = model(new CompanionStore(blocker.resolve("file")), clock, new AtomicLong(), new ArrayList<>())) {
            model.drink();
            assertFalse(model.storageWarningProperty().get().isBlank());
        }
    }
}
