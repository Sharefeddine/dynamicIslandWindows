package com.islandfx;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

/** Small, local-only store. Shelf entries are paths to the original files, not copies. */
final class CompanionStore {
    static final int MAX_ITEMS = 200;
    record Task(String id, String title, boolean done, long dueMillis, boolean notified) { }
    record State(LocalDate waterDate, int waterMl, int waterGoal, int glassMl, boolean waterReminder,
                 List<String> shelf, List<Task> tasks) {
        State {
            shelf = List.copyOf(shelf);
            tasks = List.copyOf(tasks);
        }
        static State defaults(LocalDate today) { return new State(today, 0, 2000, 250, false, List.of(), List.of()); }
    }

    private final Path path; // null is an in-memory store for previews and tests
    private State memory;

    CompanionStore(Path path) { this.path = path; }

    static Path defaultPath() {
        String localAppData = System.getenv("LOCALAPPDATA");
        Path root = localAppData == null || localAppData.isBlank()
                ? Path.of(System.getProperty("user.home"), ".islandfx") : Path.of(localAppData, "IslandFX");
        return root.resolve("companion.properties");
    }

    State load(LocalDate today) throws IOException {
        if (path == null) return memory == null ? State.defaults(today) : memory;
        if (!Files.exists(path)) return State.defaults(today);
        if (Files.size(path) > 2_000_000) throw new IOException("Local data file is too large");
        Properties props = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            props.load(input);
        } catch (IllegalArgumentException invalid) {
            throw new IOException("Local data file could not be read", invalid);
        }
        LocalDate date;
        try { date = LocalDate.parse(props.getProperty("water.date", today.toString())); }
        catch (RuntimeException invalid) { date = today; }
        int water = integer(props, "water.ml", 0, 0, 100_000);
        int goal = integer(props, "water.goal", 2000, 250, 10_000);
        int glass = integer(props, "water.glass", 250, 50, 2_000);
        boolean reminder = Boolean.parseBoolean(props.getProperty("water.reminder", "false"));
        List<String> shelf = new ArrayList<>();
        for (int i = 0, n = integer(props, "shelf.count", 0, 0, MAX_ITEMS); i < n; i++) {
            String value = props.getProperty("shelf." + i, "");
            if (!value.isBlank() && value.length() <= 8192 && !shelf.contains(value)) shelf.add(value);
        }
        List<Task> tasks = new ArrayList<>();
        for (int i = 0, n = integer(props, "tasks.count", 0, 0, MAX_ITEMS); i < n; i++) {
            String prefix = "task." + i + ".";
            String title = props.getProperty(prefix + "title", "").trim();
            if (title.isEmpty() || title.length() > 120) continue;
            String id = props.getProperty(prefix + "id", "");
            try { UUID.fromString(id); } catch (RuntimeException invalid) { id = UUID.randomUUID().toString(); }
            long due = longValue(props.getProperty(prefix + "due", "0"));
            tasks.add(new Task(id, title, Boolean.parseBoolean(props.getProperty(prefix + "done", "false")),
                    due, Boolean.parseBoolean(props.getProperty(prefix + "notified", "false"))));
        }
        return new State(date, water, goal, glass, reminder, shelf, tasks);
    }

    void save(State state) throws IOException {
        if (path == null) { memory = state; return; }
        Properties props = new Properties();
        props.setProperty("version", "1");
        props.setProperty("water.date", state.waterDate().toString());
        props.setProperty("water.ml", Integer.toString(state.waterMl()));
        props.setProperty("water.goal", Integer.toString(state.waterGoal()));
        props.setProperty("water.glass", Integer.toString(state.glassMl()));
        props.setProperty("water.reminder", Boolean.toString(state.waterReminder()));
        props.setProperty("shelf.count", Integer.toString(state.shelf().size()));
        for (int i = 0; i < state.shelf().size(); i++) props.setProperty("shelf." + i, state.shelf().get(i));
        props.setProperty("tasks.count", Integer.toString(state.tasks().size()));
        for (int i = 0; i < state.tasks().size(); i++) {
            Task task = state.tasks().get(i);
            String prefix = "task." + i + ".";
            props.setProperty(prefix + "id", task.id());
            props.setProperty(prefix + "title", task.title());
            props.setProperty(prefix + "done", Boolean.toString(task.done()));
            props.setProperty(prefix + "due", Long.toString(task.dueMillis()));
            props.setProperty(prefix + "notified", Boolean.toString(task.notified()));
        }
        Path absolute = path.toAbsolutePath();
        Path directory = absolute.getParent();
        Files.createDirectories(directory);
        Path temp = Files.createTempFile(directory, "companion-", ".tmp");
        try {
            try (OutputStream output = Files.newOutputStream(temp)) { props.store(output, "Island local data"); }
            try { Files.move(temp, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException unsupported) { Files.move(temp, absolute, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }

    private static int integer(Properties props, String key, int fallback, int min, int max) {
        try {
            int value = Integer.parseInt(props.getProperty(key, ""));
            return value >= min && value <= max ? value : fallback;
        } catch (NumberFormatException invalid) { return fallback; }
    }
    private static long longValue(String text) {
        try { return Math.max(0, Long.parseLong(text)); }
        catch (NumberFormatException invalid) { return 0; }
    }
}
