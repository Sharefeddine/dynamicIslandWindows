package com.islandfx;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Ellipse;
import javafx.stage.Stage;
import javafx.util.Duration;

/** Renders the real FXML views on a demo desktop with explicitly simulated values. */
public final class PreviewRenderer {
    private static final class DemoDevices implements DeviceServices {
        public OptionalInt batteryPercent() { return OptionalInt.empty(); }
        public boolean sendMediaKey(MediaKey key) { return true; }
        public void startVoice(Consumer<String> phrase, BiConsumer<VoiceEvent, String> state) { }
        public void stopVoice() { }
        public void close() { }
    }
    private static final CompanionModel.Page[] PAGES = CompanionModel.Page.values();
    private static final String[] NAMES = { "island-expanded.png", "island-focus.png", "island-water.png", "island-shelf.png", "island-tasks.png" };
    private PreviewRenderer() { }

    public static void main(String[] args) throws Exception {
        Path directory = Path.of(args.length == 0 ? "docs/previews" : args[0]);
        Files.createDirectories(directory);
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.startup(() -> Platform.runLater(() -> {
            AppModel model = new AppModel(new DemoDevices());
            CompanionModel companion = new CompanionModel(new CompanionStore(null), Clock.systemDefaultZone(), System::nanoTime, (title, message) -> { });
            Stage stage = new Stage();
            try {
                Pane desktop = new Pane(); desktop.setPrefSize(1440, 900);
                desktop.setStyle("-fx-background-color: linear-gradient(from 0% 0% to 100% 100%, #c5dbef 0%, #7d9bce 38%, #142f50 100%);");
                Circle glow = new Circle(1010, 520, 410, Color.web("#8db8f5", 0.24));
                Ellipse drift1 = new Ellipse(260, 850, 850, 300); drift1.setFill(Color.web("#304d84", 0.28)); drift1.setRotate(-12);
                Ellipse drift2 = new Ellipse(1170, 970, 760, 330); drift2.setFill(Color.web("#102540", 0.38)); drift2.setRotate(10);
                Label watermark = new Label("ISLAND  /  WINDOWS"); watermark.setLayoutX(54); watermark.setLayoutY(54);
                watermark.setStyle("-fx-text-fill: rgba(18,47,73,0.58); -fx-font-family: 'Segoe UI'; -fx-font-size: 11px; -fx-font-weight: bold;");
                Label caption = new Label("JAVAFX PREVIEW  ·  SAMPLE MEDIA, BATTERY, TASKS & VOICE STATES"); caption.setLayoutX(54); caption.setLayoutY(835);
                caption.setStyle("-fx-text-fill: rgba(232,242,255,0.68); -fx-font-family: 'Segoe UI'; -fx-font-size: 10px; -fx-font-weight: bold;");
                desktop.getChildren().addAll(glow, drift1, drift2, watermark, caption);
                Views.IslandView island = Views.island(); Views.JarvisView core = Views.jarvis();
                island.controller().attach(model, companion, () -> { }); core.controller().attach(model, () -> { });
                Region notch = (Region) island.root();
                notch.prefWidthProperty().addListener((unused, before, width) -> notch.setLayoutX((1440 - width.doubleValue()) / 2));
                notch.relocate((1440 - notch.getPrefWidth()) / 2, 0); core.root().relocate(1000, 110);
                desktop.getChildren().addAll(notch, core.root());
                Scene scene = new Scene(desktop, 1440, 900); stage.setScene(scene); stage.show();
                model.setPreviewSample(72, false);
                model.mediaInfoProperty().set(MediaInfo.session(MediaInfo.Playback.PLAYING, "A sample YouTube video", "Example channel", "chrome.exe", 193, 256));
                model.submitCommand("battery");
                companion.drink(); companion.drink();
                companion.addFiles(List.of(Path.of("/sample/Notes.pdf").toFile(), Path.of("/sample/Project.png").toFile()));
                companion.addTask("Finish a creative project", ""); companion.addTask("Take a short break", "");
                companion.setTaskDone(companion.tasks().get(1).id(), true);
                companion.noticeProperty().set("");
                snapshot(scene, desktop, directory.resolve("island-compact.png"), false);
                model.expandedProperty().set(true); model.setPreviewSample(72, true);
                model.inputSourceProperty().set("HEARD"); model.lastInputProperty().set("next video"); model.responseProperty().set("Sent next to Windows.");
                Runnable cleanup = () -> { island.controller().close(); companion.close(); model.close(); stage.hide(); finished.countDown(); Platform.exit(); };
                Consumer<Throwable> onError = error -> { failure.set(error); cleanup.run(); };
                capturePage(0, scene, desktop, directory, companion, cleanup, onError);
            } catch (Throwable error) { failure.set(error); companion.close(); model.close(); stage.hide(); finished.countDown(); Platform.exit(); }
        }));
        finished.await();
        if (failure.get() != null) throw new RuntimeException("Preview rendering failed", failure.get());
    }
    private static void capturePage(int index, Scene scene, Pane desktop, Path directory, CompanionModel companion,
                                    Runnable cleanup, Consumer<Throwable> onError) {
        if (index >= PAGES.length) { cleanup.run(); return; }
        companion.selectPage(PAGES[index]);
        PauseTransition wait = new PauseTransition(Duration.millis(index == 0 ? 750 : 300));
        wait.setOnFinished(event -> {
            try {
                snapshot(scene, desktop, directory.resolve(NAMES[index]), index > 0);
                if (index == 0) snapshot(scene, desktop, directory.resolve("island-music.png"), true);
                capturePage(index + 1, scene, desktop, directory, companion, cleanup, onError);
            } catch (Throwable error) { onError.accept(error); }
        });
        wait.play();
    }
    private static void snapshot(Scene scene, Pane root, Path path, boolean detail) throws Exception {
        root.applyCss(); root.layout();
        WritableImage image = scene.snapshot(null); PixelReader pixels = image.getPixelReader();
        BufferedImage output = new BufferedImage((int) image.getWidth(), (int) image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < output.getHeight(); y++) for (int x = 0; x < output.getWidth(); x++) output.setRGB(x, y, pixels.getArgb(x, y));
        // Crop only the desktop image, never redraw or mock the JavaFX controls.
        BufferedImage saved = detail ? output.getSubimage(450, 0, 540, 320) : output;
        ImageIO.write(saved, "png", path.toFile());
    }
}
