package com.islandfx;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.OptionalInt;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Ellipse;
import javafx.stage.Stage;

/** Renders the real FXML views on a demo desktop; no microphone or battery is accessed. */
public final class PreviewRenderer {
    private static final class DemoDevices implements DeviceServices {
        public OptionalInt batteryPercent() { return OptionalInt.empty(); }
        public boolean sendMediaKey(MediaKey key) { return true; }
        public void startVoice(Consumer<String> phrase, BiConsumer<VoiceEvent, String> state) { }
        public void stopVoice() { }
        public void close() { }
    }
    private PreviewRenderer() { }

    public static void main(String[] args) throws Exception {
        Path directory = Path.of(args.length == 0 ? "docs/previews" : args[0]);
        Files.createDirectories(directory);
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.startup(() -> Platform.runLater(() -> {
            AppModel model = new AppModel(new DemoDevices());
            try {
                Pane desktop = new Pane();
                desktop.setPrefSize(1440, 900);
                desktop.setStyle("-fx-background-color: linear-gradient(from 0% 0% to 100% 100%, #102735 0%, #172e3a 48%, #091722 100%);");
                Circle glow = new Circle(1040, 630, 400, Color.web("#345b69", 0.15));
                Ellipse drift1 = new Ellipse(280, 890, 880, 300); drift1.setFill(Color.web("#244453", 0.26)); drift1.setRotate(-12);
                Ellipse drift2 = new Ellipse(1160, 970, 760, 330); drift2.setFill(Color.web("#07141e", 0.36)); drift2.setRotate(10);
                Label watermark = new Label("ISLAND  /  WINDOWS");
                watermark.setLayoutX(54); watermark.setLayoutY(54);
                watermark.setStyle("-fx-text-fill: rgba(196,225,232,0.30); -fx-font-family: 'Segoe UI'; -fx-font-size: 11px; -fx-font-weight: bold;");
                Label caption = new Label("JAVAFX PREVIEW  ·  SAMPLE MEDIA, BATTERY & VOICE STATES");
                caption.setLayoutX(54); caption.setLayoutY(835);
                caption.setStyle("-fx-text-fill: rgba(190,221,229,0.56); -fx-font-family: 'Segoe UI'; -fx-font-size: 10px; -fx-font-weight: bold;");
                desktop.getChildren().addAll(glow, drift1, drift2, watermark, caption);

                Views.IslandView island = Views.island();
                Views.JarvisView core = Views.jarvis();
                island.controller().attach(model, () -> { });
                core.controller().attach(model, () -> { });
                island.root().relocate(525, 32);
                core.root().relocate(950, 121);
                desktop.getChildren().addAll(island.root(), core.root());
                Scene scene = new Scene(desktop, 1440, 900);
                Stage stage = new Stage(); stage.setScene(scene); stage.show();

                model.setPreviewSample(72, false);
                model.mediaInfoProperty().set(MediaInfo.session(MediaInfo.Playback.PLAYING,
                        "A sample YouTube video", "Example channel", "chrome.exe"));
                model.submitCommand("battery");
                snapshot(scene, desktop, directory.resolve("island-compact.png"));
                model.expandedProperty().set(true);
                model.setPreviewSample(72, true);
                model.inputSourceProperty().set("HEARD");
                model.lastInputProperty().set("next video");
                model.responseProperty().set("Sent next to Windows.");
                snapshot(scene, desktop, directory.resolve("island-expanded.png"));
                stage.hide();
                System.out.println("Rendered JavaFX FXML previews in " + directory.toAbsolutePath());
            } catch (Throwable error) { failure.set(error); }
            finally { model.close(); finished.countDown(); Platform.exit(); }
        }));
        finished.await();
        if (failure.get() != null) throw new RuntimeException("Preview rendering failed", failure.get());
    }

    private static void snapshot(Scene scene, Pane root, Path path) throws Exception {
        root.applyCss(); root.layout();
        WritableImage image = scene.snapshot(null);
        PixelReader pixels = image.getPixelReader();
        BufferedImage output = new BufferedImage((int) image.getWidth(), (int) image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < output.getHeight(); y++) {
            for (int x = 0; x < output.getWidth(); x++) output.setRGB(x, y, pixels.getArgb(x, y));
        }
        ImageIO.write(output, "png", path.toFile());
    }
}
