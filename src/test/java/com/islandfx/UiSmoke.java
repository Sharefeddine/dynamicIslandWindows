package com.islandfx;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.OptionalLong;
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
        long idleMillis = -1;
        public OptionalInt batteryPercent() { return OptionalInt.empty(); }
        public OptionalLong idleMillis() { return idleMillis < 0 ? OptionalLong.empty() : OptionalLong.of(idleMillis); }
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
    private static final List<String> notifications = new ArrayList<>();
    private static boolean hidden;
    private UiSmoke() { }

    public static void main(String[] args) throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        Platform.startup(started::countDown);
        if (!started.await(10, TimeUnit.SECONDS)) throw new AssertionError("JavaFX did not start");
        try {
            runOnFx(() -> {
                devices = new FakeDevices(); model = new AppModel(devices);
                companion = new CompanionModel(new CompanionStore(null), java.time.Clock.systemDefaultZone(), System::nanoTime,
                        (title, message) -> notifications.add(title + ": " + message));
                try { island = Views.island(); core = Views.jarvis(); }
                catch (Exception error) { throw new RuntimeException(error); }
                island.controller().attach(model, companion, () -> { }, PreviewData.lyrics());
                core.controller().attach(model, () -> { hidden = true; model.stopListening(); });
                Pane pane = new Pane(island.root(), core.root());
                stage = new Stage(); stage.setScene(new Scene(pane, 900, 700)); stage.show();
                pane.applyCss(); pane.layout();
                try (Avatar3D probe = new Avatar3D()) {
                    probe.setStyle(CompanionStore.AvatarStyle.REALISTIC);
                    var real = probe.getRoot().lookup("#avatarRealistic3D");
                    var soft = probe.getRoot().lookup("#avatarStylized3D");
                    var eyes = probe.getRoot().lookup("#avatarRealisticEyes3D");
                    var arm = probe.getRoot().lookup("#avatarWavingArm3D");
                    check(real != null && real.isVisible() && soft != null && !soft.isVisible(), "3D styles switch scene graph nodes");
                    probe.showPose(MascotMood.Pose.SLEEPING);
                    check(eyes != null && eyes.getScaleY() < 0.1 && !arm.isVisible() && !probe.isBlinking(), "3D sleeping eyes close");
                    probe.showPose(MascotMood.Pose.WAVING);
                    check(arm.isVisible() && eyes.getScaleY() == 1 && probe.isBlinking(), "3D waving wakes the eyes and starts blinking");
                } catch (java.io.IOException failure) { throw new AssertionError("3D head failed to load", failure); }
                check(label(island.root(), "#batteryLabel").getText().equals("--"), "battery starts unavailable");
                check(label(core.root(), "#voiceLabel").getText().equals("Ready to listen"), "mic starts off");
                model.updateMascot(OptionalLong.of(MascotMood.SLEEP_AFTER_MILLIS), System.nanoTime());
                check(island.root().lookup("#mascotSleepMarks").isVisible(), "idle character shows sleep marks");
                check(island.root().lookup("#mascotSleepyEyes").isVisible(), "idle character closes its eyes");
                check(!island.root().lookup("#mascotWavingArm").isVisible(), "sleeping character is not waving");
                model.noteMascotInteraction();
                check(island.root().lookup("#mascotWavingArm").isVisible(), "returning user gets a wave");
                check(!island.root().lookup("#mascotSleepMarks").isVisible(), "sleep marks disappear on return");
                check(label(island.root(), "#batteryLabel").getText().equals("--"), "mascot keeps the real battery state visible");
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
                ((Button) island.root().lookup("#testNotificationButton")).fire();
                check(notifications.size() == 1 && notifications.get(0).contains("Test notification"), "task notification test uses the real callback path");
                ((Button) island.root().lookup("#adhkarTab")).fire();
                check(label(island.root(), "#adhkarTextLabel").getText().contains("الْحَمْدُ"), "Arabic adhkar displayed");
                check(label(island.root(), "#adhkarCountLabel").getText().contains("1"), "source repetition count displayed");
                ((Button) island.root().lookup("#nextDhikrButton")).fire();
                check(label(island.root(), "#adhkarIndexLabel").getText().startsWith("2 /"), "adhkar browsing works");
                ((Button) island.root().lookup("#eveningButton")).fire();
                check(label(island.root(), "#adhkarIndexLabel").getText().startsWith("1 /"), "evening starts at the first entry");
                ((javafx.scene.control.CheckBox) island.root().lookup("#morningReminderCheck")).setSelected(true);
                ((TextField) island.root().lookup("#morningTimeField")).setText("07:15");
                ((Button) island.root().lookup("#saveAdhkarButton")).fire();
                check(companion.morningReminderProperty().get() && companion.morningTimeProperty().get().toString().equals("07:15"), "adhkar reminder settings work");
                ((Button) island.root().lookup("#avatarButton")).fire();
                check(companion.avatarStyleProperty().get() == CompanionStore.AvatarStyle.REALISTIC, "avatar selection changes and is saved");
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
                ((Button) island.root().lookup("#lyricsTab")).fire();
                stage.getScene().getRoot().applyCss(); stage.getScene().getRoot().layout();
                var lyricList = (javafx.scene.control.ListView<?>) island.root().lookup("#lyricsList");
                check(lyricList.getItems().size() == 3, "asynchronous sample lyrics appear in the actual FXML page");
                check(label(island.root(), "#lyricsStatusLabel").getText().contains("not supplied"), "missing playback position is explained");
                check(island.root().lookupAll(".lyric-line").stream().filter(node -> node instanceof Label)
                        .map(node -> (Label) node).anyMatch(node -> node.getText().contains("صَبَاح") &&
                                node.getNodeOrientation() == javafx.geometry.NodeOrientation.RIGHT_TO_LEFT), "Arabic lyrics use RTL orientation");
                ((Button) island.root().lookup("#onlineLyricsButton")).fire();
                check(!companion.onlineLyricsProperty().get() && lyricList.getItems().isEmpty(), "online lyric lookup can be disabled");
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
                model.updateMascot(OptionalLong.of(MascotMood.SLEEP_AFTER_MILLIS), System.nanoTime());
                check(island.root().lookup("#mascotSleepMarks").isVisible(), "mascot can sleep while music plays");
                check(label(island.root(), "#mediaTitleLabel").getText().equals("A YouTube video"), "sleep does not hide media metadata");
                check(!island.root().lookup("#waveform").getStyleClass().contains("muted"), "sleep does not pause the media indicator");
                javafx.event.Event.fireEvent(island.root(), new javafx.scene.input.MouseEvent(javafx.scene.input.MouseEvent.MOUSE_ENTERED,
                        12, 12, 12, 12, javafx.scene.input.MouseButton.NONE, 0,
                        false, false, false, false, false, false, false, false, false, false, null));
                check(island.root().lookup("#mascotWavingArm").isVisible(), "returning to the island wakes the mascot");
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
                devices.idleMillis = MascotMood.SLEEP_AFTER_MILLIS;
                model.startIdleMonitoring();
                check(model.mascotPoseProperty().get() == MascotMood.Pose.SLEEPING, "monitor uses the system idle reading");
                devices.idleMillis = 0;
            });
            Thread.sleep(1200);
            runOnFx(() -> {
                check(model.mascotPoseProperty().get() == MascotMood.Pose.WAVING, "idle monitor notices activity and waves");
                check(island.root().lookup("#mascotWavingArm").isVisible(), "polling changes the actual FXML view");
                island.controller().close(); companion.close(); model.close(); stage.hide();
            });
            System.out.println("FXML views, RTL lyrics, adhkar, reminders, 3D scene state, companion, commands, media, and cleanup passed.");
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
