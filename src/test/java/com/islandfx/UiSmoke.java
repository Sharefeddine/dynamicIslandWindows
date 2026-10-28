package com.islandfx;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/** Standalone headless-capable integration test for real FXML views and controller state. */
public final class UiSmoke {
    private static final class FakeDevices implements DeviceServices {
        final List<MediaKey> keys = new ArrayList<>();
        Consumer<String> onPhrase;
        Consumer<MediaInfo> onMedia;
        BiConsumer<VoiceEvent, String> onVoiceState;
        int stopCalls;
        public OptionalInt batteryPercent() { return OptionalInt.empty(); }
        public boolean sendMediaKey(MediaKey key) { keys.add(key); return true; }
        public void startMedia(Consumer<MediaInfo> onChange) { onMedia = onChange; }
        public void startVoice(Consumer<String> phrase, BiConsumer<VoiceEvent, String> state) {
            onPhrase = phrase; onVoiceState = state; state.accept(VoiceEvent.LISTENING, "");
        }
        public void stopVoice() { stopCalls++; }
        public void close() { }
    }
    private static FakeDevices devices;
    private static AppModel model;
    private static CompanionModel companion;
    private static Views.IslandView island;
    private static Views.JarvisView core;
    private static Stage stage;
    private static boolean hidden;
    private UiSmoke() { }

    public static void main(String[] args) throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        Platform.startup(started::countDown);
        if (!started.await(10, TimeUnit.SECONDS)) throw new AssertionError("JavaFX did not start");
        try {
            runOnFx(() -> {
                devices = new FakeDevices(); model = new AppModel(devices);
                companion = new CompanionModel(new CompanionStore(null), java.time.Clock.systemDefaultZone(), System::nanoTime, (title, message) -> { });
                try { island = Views.island(); core = Views.jarvis(); }
                catch (Exception error) { throw new RuntimeException(error); }
                island.controller().attach(model, companion, () -> { });
                core.controller().attach(model, () -> { hidden = true; model.stopListening(); });
                Pane pane = new Pane(island.root(), core.root());
                stage = new Stage(); stage.setScene(new Scene(pane, 900, 700)); stage.show();
                pane.applyCss(); pane.layout();
                check(label(island.root(), "#batteryLabel").getText().equals("--"), "battery starts unavailable");
                check(label(core.root(), "#voiceLabel").getText().equals("Ready to listen"), "mic starts off");
                VBox details = (VBox) island.root().lookup("#details");
                check(!details.isVisible() && !details.isManaged(), "island starts compact");
                model.toggleExpanded();
                check(details.isVisible() && details.isManaged(), "island expands");
                ((Button) island.root().lookup("#focusTab")).fire();
                check(label(island.root(), "#focusTimeLabel").getText().equals("25:00"), "focus section shows a real timer");
                ((Button) island.root().lookup("#focusStartButton")).fire();
                check(companion.focusRunningProperty().get(), "focus starts");
                ((Button) island.root().lookup("#focusStartButton")).fire();
                check(!companion.focusRunningProperty().get(), "focus pauses");
                ((Button) island.root().lookup("#waterTab")).fire();
                ((Button) island.root().lookup("#drinkButton")).fire();
                check(label(island.root(), "#waterTotalLabel").getText().equals("250"), "water intake updates");
                ((TextField) island.root().lookup("#waterGoalField")).setText("1800");
                ((TextField) island.root().lookup("#glassField")).setText("300");
                ((Button) island.root().lookup("#waterSaveButton")).fire();
                ((Button) island.root().lookup("#waterReminderButton")).fire();
                check(companion.waterGoalProperty().get() == 1800 && companion.glassMlProperty().get() == 300,
                        "water settings update through the UI");
                check(companion.waterReminderProperty().get(), "water reminders can be enabled");
                ((Button) island.root().lookup("#tasksTab")).fire();
                ((TextField) island.root().lookup("#taskField")).setText("Test the island");
                ((Button) island.root().lookup("#addTaskButton")).fire();
                check(companion.tasks().size() == 1, "task added through UI");
                ((Button) island.root().lookup("#shelfTab")).fire();
                companion.addFiles(List.of(new java.io.File("/tmp/island-test-shortcut")));
                check(companion.shelf().size() == 1, "shelf stores shortcuts");
                ((javafx.scene.control.ListView<?>) island.root().lookup("#shelfList")).getSelectionModel().select(0);
                ((Button) island.root().lookup("#removeFileButton")).fire();
                check(companion.shelf().isEmpty(), "shelf shortcut can be removed through the UI");
                ((Button) island.root().lookup("#musicTab")).fire();
                for (Node node : island.root().lookupAll(".transport")) {
                    Button button = (Button) node;
                    if ("Play or pause".equals(button.getAccessibleText())) button.fire();
                }
                check(devices.keys.equals(List.of(DeviceServices.MediaKey.PLAY_PAUSE)), "media button sends toggle");
                model.startMediaMonitoring();
                devices.onMedia.accept(MediaInfo.session(MediaInfo.Playback.PLAYING, "A YouTube video", "Example channel", "chrome.exe"));
            });
            // Let the opening animation finish so the list's real virtualized cells are laid out.
            Thread.sleep(650);
            runOnFx(() -> {
                ((Button) island.root().lookup("#tasksTab")).fire();
                stage.getScene().getRoot().applyCss(); stage.getScene().getRoot().layout();
                javafx.scene.control.CheckBox done = (javafx.scene.control.CheckBox) island.root().lookup(".task-check");
                check(done != null, "task checkbox is rendered");
                done.fire();
                check(companion.tasks().get(0).done(), "task completion updates through the UI");
                Button remove = (Button) island.root().lookup(".task-delete");
                check(remove != null, "task remove button is rendered");
                remove.fire();
                check(companion.tasks().isEmpty(), "task can be removed through the UI");
                ((Button) island.root().lookup("#musicTab")).fire();
            });
            runOnFx(() -> {
                check(label(island.root(), "#mediaTitleLabel").getText().equals("A YouTube video"), "compact view shows actual title");
                check(label(island.root(), "#detailArtistLabel").getText().contains("Example channel"), "expanded view shows artist/channel");
                check(label(island.root(), "#playbackLabel").getText().equals("PLAYING"), "playback state shown");
                TextField field = (TextField) core.root().lookup("#commandField");
                Button send = (Button) core.root().lookup("#sendButton");
                field.setText("what time is it"); send.fire();
                check(label(core.root(), "#inputSourceLabel").getText().equals("TYPED"), "typed input indicated");
                check(label(core.root(), "#transcriptLabel").getText().equals("what time is it"), "typed command displayed");
                check(label(core.root(), "#responseLabel").getText().startsWith("It’s "), "typed command has a visible result");
                check(field.getText().isEmpty() && model.voiceProperty().get() == AppModel.VoiceState.OFF, "typing does not activate mic");
                field.setText("battery"); field.fireEvent(new javafx.event.ActionEvent());
                check(label(core.root(), "#responseLabel").getText().contains("No battery"), "Enter submits typed command");
                field.setText("turn on the lights"); send.fire();
                check(label(core.root(), "#responseLabel").getText().contains("didn't understand"), "unknown commands get honest feedback");
                field.setText("play video"); send.fire();
                check(devices.keys.equals(List.of(DeviceServices.MediaKey.PLAY_PAUSE, DeviceServices.MediaKey.PLAY_PAUSE)), "typed video command sends toggle");
                check(label(core.root(), "#responseLabel").getText().contains("toggle"), "toggle is described honestly");
                model.toggleListening();
                check(model.voiceProperty().get() == AppModel.VoiceState.STARTING, "voice starts asynchronously");
            });
            runOnFx(() -> {
                check(model.voiceProperty().get() == AppModel.VoiceState.LISTENING, "ready event enters listening state");
                check(label(core.root(), "#voiceLabel").getText().equals("Listening..."), "listening is indicated");
                devices.onVoiceState.accept(DeviceServices.VoiceEvent.PARTIAL, "next");
            });
            runOnFx(() -> {
                check(label(core.root(), "#inputSourceLabel").getText().equals("HEARING"), "tentative speech is marked");
                check(label(core.root(), "#transcriptLabel").getText().equals("next"), "tentative speech is shown");
                check(devices.keys.size() == 2, "tentative speech does not run a command");
                devices.onVoiceState.accept(DeviceServices.VoiceEvent.REJECTED, "next video");
            });
            runOnFx(() -> {
                check(label(core.root(), "#inputSourceLabel").getText().equals("NOT RECOGNIZED"), "rejected voice is visible");
                check(label(core.root(), "#transcriptLabel").getText().equals("next video"), "uncertain words shown");
                check(devices.keys.size() == 2, "rejected speech does not run a command");
                devices.onPhrase.accept("next video");
            });
            runOnFx(() -> {
                check(devices.keys.equals(List.of(DeviceServices.MediaKey.PLAY_PAUSE, DeviceServices.MediaKey.PLAY_PAUSE, DeviceServices.MediaKey.NEXT)), "spoken video command sent next");
                check(label(core.root(), "#inputSourceLabel").getText().equals("HEARD"), "recognized speech marked as heard");
                check(label(core.root(), "#transcriptLabel").getText().equals("next video"), "recognized speech displayed");
                check(label(core.root(), "#responseLabel").getText().contains("Sent next"), "spoken command has visible response");
                ((TextField) core.root().lookup("#commandField")).setText("stop listening");
                ((Button) core.root().lookup("#sendButton")).fire();
                check(model.voiceProperty().get() == AppModel.VoiceState.OFF, "typed stop listening stops the microphone");
                check(label(core.root(), "#responseLabel").getText().equals("Stopped listening."), "stop command has feedback");
                devices.onPhrase.accept("next video"); // An event from the old session must be ignored.
                devices.onMedia.accept(MediaInfo.noSession());
            });
            runOnFx(() -> {
                check(devices.keys.size() == 3, "stale recognition ignored after stopping");
                check(label(core.root(), "#voiceLabel").getText().equals("Ready to listen"), "mic off state displayed");
                check(label(island.root(), "#mediaSubtitleLabel").getText().equals("NO MEDIA DETECTED"), "absence of a session is honest");
                devices.onMedia.accept(MediaInfo.unavailable("Windows media access denied"));
                model.toggleListening();
            });
            runOnFx(() -> {
                check(label(island.root(), "#detailTitleLabel").getText().equals("Windows media access denied"), "media diagnostic shown");
                devices.onVoiceState.accept(DeviceServices.VoiceEvent.ERROR, "No speech recognizer installed");
            });
            runOnFx(() -> {
                check(label(core.root(), "#voiceLabel").getText().equals("Voice unavailable"), "voice failure displayed");
                check(label(core.root(), "#responseLabel").getText().contains("No speech recognizer installed"), "diagnostic detail displayed");
                ((Button) core.root().lookup("#closeButton")).fire();
                check(hidden && model.voiceProperty().get() == AppModel.VoiceState.OFF, "hiding core stops mic");
                check(devices.stopCalls >= 2, "voice worker stopped on request and when hidden");
                island.controller().close(); companion.close(); model.close(); stage.hide();
            });
            System.out.println("FXML views, animated sections, commands, media sessions, errors, and cleanup passed.");
        } finally { Platform.exit(); }
    }
    private static Label label(Node root, String selector) { return (Label) root.lookup(selector); }
    private static void check(boolean value, String description) { if (!value) throw new AssertionError(description); }
    private static void runOnFx(Runnable task) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            try { task.run(); } catch (Throwable failure) { error.set(failure); } finally { done.countDown(); }
        });
        if (!done.await(10, TimeUnit.SECONDS)) throw new AssertionError("JavaFX operation timed out");
        if (error.get() != null) throw new AssertionError("JavaFX smoke test failed", error.get());
    }
}
