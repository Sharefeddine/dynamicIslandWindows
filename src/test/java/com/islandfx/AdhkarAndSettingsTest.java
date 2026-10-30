package com.islandfx;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AdhkarAndSettingsTest {
    @TempDir Path directory;
    @Test void bundledArabicCatalogKeepsCategoriesCountsAndSources() throws Exception {
        AdhkarCatalog catalog = AdhkarCatalog.load();
        var morning = catalog.forPeriod(AdhkarCatalog.Period.MORNING);
        var evening = catalog.forPeriod(AdhkarCatalog.Period.EVENING);
        assertEquals(26, morning.size());
        assertEquals(24, evening.size());
        assertEquals(1, morning.get(0).order());
        assertEquals(1, morning.get(0).count());
        assertTrue(morning.get(0).text().contains("الْحَمْدُ"));
        assertFalse(morning.get(0).source().isBlank());
        assertTrue(morning.stream().anyMatch(entry -> entry.order() == 7));
        assertFalse(evening.stream().anyMatch(entry -> entry.order() == 7));
        assertTrue(evening.stream().anyMatch(entry -> entry.order() == 8));
        assertFalse(morning.stream().anyMatch(entry -> entry.order() == 8));
        assertTrue(morning.stream().anyMatch(entry -> entry.count() == 100));
    }
    @Test void thirdPartyNoticesAreIncludedAsApplicationResources() {
        assertNotNull(AdhkarAndSettingsTest.class.getResource("/META-INF/licenses/adhkar-MIT.txt"));
        assertNotNull(AdhkarAndSettingsTest.class.getResource("/META-INF/licenses/makehuman-CC0.md"));
        assertNotNull(AdhkarAndSettingsTest.class.getResource("/META-INF/licenses/credits.txt"));
    }
    @Test void compactArabicWrappingKeepsEveryWordAndDiacritic() throws Exception {
        String original = AdhkarCatalog.load().forPeriod(AdhkarCatalog.Period.MORNING).get(1).text();
        String wrapped = IslandController.wrapArabic(original, 65);
        assertTrue(wrapped.contains("\n"));
        assertEquals(original.replaceAll("\\s+", " ").trim(), wrapped.replaceAll("\\s+", " ").trim());
    }
    @Test void reminderSettingsPersistFireOnceAndSkipTimesMissedWhileClosed() throws Exception {
        var clock = new CompanionModelTest.MutableClock(ZoneId.of("UTC"));
        Path file = directory.resolve("companion.properties");
        var notices = new ArrayList<String>();
        var nanos = new AtomicLong();
        try (CompanionModel model = new CompanionModel(new CompanionStore(file), clock, nanos::get,
                (title, body) -> notices.add(title + ": " + body))) {
            assertFalse(model.applyAdhkarSettings("25:00", true, "10:10", true));
            assertFalse(model.morningReminderProperty().get());
            assertTrue(model.applyAdhkarSettings("10:05", true, "10:10", true));
            model.selectAvatarStyle(CompanionStore.AvatarStyle.REALISTIC);
            model.toggleOnlineLyrics();
            clock.advance(Duration.ofMinutes(5)); model.tick(); model.tick();
            assertEquals(1, notices.size()); assertTrue(notices.get(0).contains("أذكار الصباح"));
            clock.advance(Duration.ofMinutes(5)); model.tick(); model.tick();
            assertEquals(2, notices.size()); assertTrue(notices.get(1).contains("أذكار المساء"));
        }
        clock.advance(Duration.ofDays(1)); // app was closed when the next scheduled times passed
        try (CompanionModel restored = new CompanionModel(new CompanionStore(file), clock, nanos::get,
                (title, body) -> notices.add(title + ": " + body))) {
            assertTrue(restored.morningReminderProperty().get());
            assertTrue(restored.eveningReminderProperty().get());
            assertEquals("10:05", restored.morningTimeProperty().get().toString());
            assertEquals(CompanionStore.AvatarStyle.REALISTIC, restored.avatarStyleProperty().get());
            assertFalse(restored.onlineLyricsProperty().get());
            restored.tick(); assertEquals(2, notices.size(), "no catch-up adhkar alerts after startup");
            clock.advance(Duration.ofHours(24).minusMinutes(5)); restored.tick();
            assertEquals(3, notices.size()); assertTrue(notices.get(2).contains("أذكار الصباح"));
            clock.advance(Duration.ofMinutes(5)); restored.tick(); assertEquals(4, notices.size());
        }
    }
    @Test void oldTaskFilesRemainReadableAndTaskTestUsesSameNotificationPath() throws Exception {
        Path path = directory.resolve("old.properties");
        Files.writeString(path, "version=1\nwater.ml=500\nwater.goal=2000\nwater.glass=250\n" +
                "shelf.count=0\ntasks.count=1\ntask.0.id=8a4387fe-0b78-47b7-a01a-6df6b8a6d5f3\n" +
                "task.0.title=Existing task\ntask.0.done=false\ntask.0.due=0\ntask.0.notified=false\n");
        var clock = new CompanionModelTest.MutableClock(ZoneId.of("UTC"));
        List<String> alerts = new ArrayList<>();
        try (CompanionModel model = new CompanionModel(new CompanionStore(path), clock, System::nanoTime,
                (title, body) -> alerts.add(title + " " + body))) {
            assertEquals("Existing task", model.tasks().get(0).title());
            assertEquals(CompanionStore.AvatarStyle.STYLIZED, model.avatarStyleProperty().get());
            assertFalse(model.morningReminderProperty().get());
            model.testNotification();
            assertEquals(1, alerts.size()); assertTrue(alerts.get(0).contains("Test notification"));
            model.selectAvatarStyle(CompanionStore.AvatarStyle.REALISTIC);
        }
        assertEquals("Existing task", new CompanionStore(path).load(java.time.LocalDate.now(clock)).tasks().get(0).title());
    }
}
