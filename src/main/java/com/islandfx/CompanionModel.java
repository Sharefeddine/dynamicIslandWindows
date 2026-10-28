package com.islandfx;

import java.io.File;
import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.LongSupplier;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/** Local state for the island's pages. The timer and reminders run only while the app is open. */
final class CompanionModel implements AutoCloseable {
    enum Page { MUSIC, FOCUS, WATER, SHELF, TASKS }
    enum FocusMode {
        FOCUS("Focus time", 25 * 60), BREAK("Short break", 5 * 60);
        final String label;
        final int seconds;
        FocusMode(String label, int seconds) { this.label = label; this.seconds = seconds; }
    }
    private static final Duration WATER_INTERVAL = Duration.ofHours(1);
    private static final DateTimeFormatter REMINDER_TIME = DateTimeFormatter.ofPattern("H:mm").withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter CLOCK_TIME = DateTimeFormatter.ofPattern("HH:mm");
    private final CompanionStore store;
    private final Clock clock;
    private final LongSupplier nanoTime;
    private final BiConsumer<String, String> notify;
    private final ObjectProperty<Page> page = new SimpleObjectProperty<>(Page.MUSIC);
    private final ObjectProperty<FocusMode> focusMode = new SimpleObjectProperty<>(FocusMode.FOCUS);
    private final IntegerProperty focusRemaining = new SimpleIntegerProperty(FocusMode.FOCUS.seconds);
    private final BooleanProperty focusRunning = new SimpleBooleanProperty(false);
    private final StringProperty focusStatus = new SimpleStringProperty("Ready when you are");
    private final IntegerProperty waterMl = new SimpleIntegerProperty();
    private final IntegerProperty waterGoal = new SimpleIntegerProperty(2000);
    private final IntegerProperty glassMl = new SimpleIntegerProperty(250);
    private final BooleanProperty waterReminder = new SimpleBooleanProperty(false);
    private final ObservableList<String> shelf = FXCollections.observableArrayList();
    private final ObservableList<CompanionStore.Task> tasks = FXCollections.observableArrayList();
    private final ObservableList<String> shelfView = FXCollections.unmodifiableObservableList(shelf);
    private final ObservableList<CompanionStore.Task> taskView = FXCollections.unmodifiableObservableList(tasks);
    private final StringProperty notice = new SimpleStringProperty("");
    private final StringProperty storageWarning = new SimpleStringProperty("");
    private final StringProperty clockText = new SimpleStringProperty("");
    private LocalDate waterDate;
    private Instant nextWaterReminder;
    private long focusEndNanos;
    private boolean loadFailed;
    private boolean closed;
    private Timeline ticker;

    CompanionModel(CompanionStore store, Clock clock, LongSupplier nanoTime, BiConsumer<String, String> notify) {
        this.store = store;
        this.clock = clock;
        this.nanoTime = nanoTime;
        this.notify = notify;
        LocalDate today = LocalDate.now(clock);
        CompanionStore.State state;
        try { state = store.load(today); }
        catch (IOException | RuntimeException failure) {
            state = CompanionStore.State.defaults(today);
            loadFailed = true;
            storageWarning.set("Local data could not be read; changes are temporary.");
        }
        waterDate = today;
        waterMl.set(state.waterDate().equals(today) ? state.waterMl() : 0);
        waterGoal.set(state.waterGoal());
        glassMl.set(state.glassMl());
        waterReminder.set(state.waterReminder());
        shelf.addAll(state.shelf());
        tasks.addAll(state.tasks());
        nextWaterReminder = clock.instant().plus(WATER_INTERVAL);
        clockText.set(LocalTime.now(clock).format(CLOCK_TIME));
    }
    static CompanionModel create(BiConsumer<String, String> notify) {
        return new CompanionModel(new CompanionStore(CompanionStore.defaultPath()), Clock.systemDefaultZone(), System::nanoTime, notify);
    }
    ObjectProperty<Page> pageProperty() { return page; }
    ObjectProperty<FocusMode> focusModeProperty() { return focusMode; }
    IntegerProperty focusRemainingProperty() { return focusRemaining; }
    BooleanProperty focusRunningProperty() { return focusRunning; }
    StringProperty focusStatusProperty() { return focusStatus; }
    IntegerProperty waterMlProperty() { return waterMl; }
    IntegerProperty waterGoalProperty() { return waterGoal; }
    IntegerProperty glassMlProperty() { return glassMl; }
    BooleanProperty waterReminderProperty() { return waterReminder; }
    ObservableList<String> shelf() { return shelfView; }
    ObservableList<CompanionStore.Task> tasks() { return taskView; }
    StringProperty noticeProperty() { return notice; }
    StringProperty storageWarningProperty() { return storageWarning; }
    StringProperty clockTextProperty() { return clockText; }

    void startTicker() {
        if (closed || ticker != null) return;
        tick();
        ticker = new Timeline(new KeyFrame(javafx.util.Duration.seconds(1), event -> tick()));
        ticker.setCycleCount(Animation.INDEFINITE);
        ticker.play();
    }
    void selectPage(Page selected) { if (selected != null) page.set(selected); }
    void movePage(int delta) {
        Page[] values = Page.values();
        page.set(values[Math.floorMod(page.get().ordinal() + delta, values.length)]);
    }

    void selectFocusMode(FocusMode mode) {
        focusMode.set(mode);
        focusRunning.set(false);
        focusRemaining.set(mode.seconds);
        focusStatus.set("Ready when you are");
    }
    void toggleFocus() {
        if (focusRunning.get()) {
            updateFocus();
            if (focusRunning.get()) { focusRunning.set(false); focusStatus.set("Paused"); }
        } else {
            if (focusRemaining.get() <= 0) focusRemaining.set(focusMode.get().seconds);
            focusEndNanos = nanoTime.getAsLong() + focusRemaining.get() * 1_000_000_000L;
            focusRunning.set(true);
            focusStatus.set(focusMode.get().label + " in progress");
        }
    }
    void resetFocus() {
        focusRunning.set(false);
        focusRemaining.set(focusMode.get().seconds);
        focusStatus.set("Ready when you are");
    }
    private void updateFocus() {
        if (!focusRunning.get()) return;
        long left = focusEndNanos - nanoTime.getAsLong();
        if (left <= 0) {
            focusRemaining.set(0);
            focusRunning.set(false);
            focusStatus.set("Session complete");
            publish("Focus timer", focusMode.get().label + " is complete.");
        } else {
            focusRemaining.set((int) Math.min(focusMode.get().seconds, (left + 999_999_999L) / 1_000_000_000L));
        }
    }

    void drink() {
        rollWaterDay();
        waterMl.set(Math.min(100_000, waterMl.get() + glassMl.get()));
        nextWaterReminder = clock.instant().plus(WATER_INTERVAL);
        notice.set("Added " + glassMl.get() + " ml of water.");
        save();
    }
    void resetWater() {
        rollWaterDay();
        waterMl.set(0);
        nextWaterReminder = clock.instant().plus(WATER_INTERVAL);
        notice.set("Today's water total was reset.");
        save();
    }
    void applyWaterSettings(String goalText, String glassText) {
        try {
            int goal = Integer.parseInt(goalText.trim()), glass = Integer.parseInt(glassText.trim());
            if (goal < 250 || goal > 10_000 || glass < 50 || glass > 2_000) throw new NumberFormatException();
            waterGoal.set(goal); glassMl.set(glass);
            notice.set("Water settings saved.");
            save();
        } catch (NumberFormatException | NullPointerException invalid) {
            notice.set("Use a goal of 250–10000 ml and a glass of 50–2000 ml.");
        }
    }
    void toggleWaterReminder() {
        waterReminder.set(!waterReminder.get());
        nextWaterReminder = clock.instant().plus(WATER_INTERVAL);
        notice.set(waterReminder.get() ? "Water reminders are on: every hour while Island is open." : "Water reminders are off.");
        save();
    }
    private void rollWaterDay() {
        LocalDate today = LocalDate.now(clock);
        if (!today.equals(waterDate)) {
            waterDate = today;
            waterMl.set(0);
            nextWaterReminder = clock.instant().plus(WATER_INTERVAL);
            save();
        }
    }

    void addFiles(List<File> files) {
        if (files == null) return;
        int added = 0;
        for (File file : files) {
            if (file == null || shelf.size() >= CompanionStore.MAX_ITEMS) continue;
            String value = file.toPath().toAbsolutePath().normalize().toString();
            if (value.length() > 8192 || containsPath(value)) continue;
            shelf.add(value); added++;
        }
        notice.set(added > 0 ? "Added " + added + (added == 1 ? " shortcut." : " shortcuts.") : "No new shortcuts added.");
        if (added > 0) save();
    }
    private boolean containsPath(String candidate) {
        boolean windows = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).startsWith("windows");
        return shelf.stream().anyMatch(item -> windows ? item.equalsIgnoreCase(candidate) : item.equals(candidate));
    }
    void removeFile(String value) {
        if (value != null && shelf.remove(value)) {
            notice.set("Shortcut removed; the original file was not deleted.");
            save();
        }
    }
    static String fileName(String value) {
        try {
            Path path = Path.of(value);
            Path name = path.getFileName();
            return name == null ? value : name.toString();
        } catch (InvalidPathException invalid) { return value; }
    }

    boolean addTask(String rawTitle, String rawTime) {
        String title = rawTitle == null ? "" : rawTitle.replaceAll("\\p{Cc}", " ").trim();
        if (title.isEmpty()) { notice.set("Enter a task first."); return false; }
        if (title.length() > 120) { notice.set("Tasks can be up to 120 characters."); return false; }
        if (tasks.size() >= CompanionStore.MAX_ITEMS) { notice.set("The task list is full."); return false; }
        long due = 0;
        String time = rawTime == null ? "" : rawTime.trim();
        if (!time.isEmpty()) {
            try {
                LocalTime local = LocalTime.parse(time, REMINDER_TIME);
                var dateTime = LocalDate.now(clock).atTime(local).atZone(clock.getZone());
                if (!dateTime.toInstant().isAfter(clock.instant())) dateTime = dateTime.plusDays(1);
                due = dateTime.toInstant().toEpochMilli();
            } catch (DateTimeParseException invalid) {
                notice.set("Use a reminder time like 14:30, or leave it empty."); return false;
            }
        }
        tasks.add(new CompanionStore.Task(UUID.randomUUID().toString(), title, false, due, false));
        notice.set(due == 0 ? "Task added." : "Task added with a local reminder.");
        save();
        return true;
    }
    void setTaskDone(String id, boolean done) {
        for (int i = 0; i < tasks.size(); i++) {
            CompanionStore.Task old = tasks.get(i);
            if (old.id().equals(id)) {
                tasks.set(i, new CompanionStore.Task(old.id(), old.title(), done, old.dueMillis(), old.notified()));
                save(); return;
            }
        }
    }
    void removeTask(String id) {
        if (tasks.removeIf(task -> task.id().equals(id))) { notice.set("Task removed."); save(); }
    }

    /** Run at least once per second while open; exposed for deterministic tests. */
    void tick() {
        if (closed) return;
        clockText.set(LocalTime.now(clock).format(CLOCK_TIME));
        rollWaterDay();
        updateFocus();
        Instant now = clock.instant();
        if (waterReminder.get() && waterMl.get() < waterGoal.get() && !now.isBefore(nextWaterReminder)) {
            nextWaterReminder = now.plus(WATER_INTERVAL);
            publish("Water reminder", "Time for a glass of water.");
        }
        boolean changed = false;
        for (int i = 0; i < tasks.size(); i++) {
            CompanionStore.Task task = tasks.get(i);
            if (!task.done() && !task.notified() && task.dueMillis() > 0 && task.dueMillis() <= now.toEpochMilli()) {
                tasks.set(i, new CompanionStore.Task(task.id(), task.title(), false, task.dueMillis(), true));
                publish("Task reminder", task.title());
                changed = true;
            }
        }
        if (changed) save();
    }
    private void publish(String title, String message) {
        notice.set(message);
        try { notify.accept(title, message); } catch (RuntimeException ignored) { /* A missing system tray must not stop the timer. */ }
    }
    private void save() {
        if (loadFailed) return; // Do not overwrite a file that could not be read.
        try {
            store.save(new CompanionStore.State(waterDate, waterMl.get(), waterGoal.get(), glassMl.get(), waterReminder.get(), shelf, tasks));
            storageWarning.set("");
        } catch (IOException | RuntimeException failure) {
            storageWarning.set("Could not save local data; changes may be lost.");
        }
    }
    @Override public void close() {
        closed = true;
        if (ticker != null) ticker.stop();
    }
}
