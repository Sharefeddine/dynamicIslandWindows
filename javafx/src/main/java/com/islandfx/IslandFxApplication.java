package com.islandfx;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/** Two transparent, always-on-top JavaFX stages: the island and the JARVIS core. */
public final class IslandFxApplication extends Application {
    private final SingleInstance instance = new SingleInstance();
    private final TraySupport tray = new TraySupport();
    private final GlobalHotkey hotkey = new GlobalHotkey();
    private Stage islandStage;
    private Stage coreStage;
    private AppModel model;
    private boolean stopping;

    @Override public void start(Stage primary) throws Exception {
        if (!instance.acquire()) {
            new Alert(Alert.AlertType.INFORMATION, "Island is already running. Use its tray icon to show JARVIS or exit.").showAndWait();
            Platform.exit();
            return;
        }
        Platform.setImplicitExit(false);
        model = new AppModel(new WindowsDeviceServices());
        islandStage = primary;
        coreStage = new Stage();
        configure(islandStage, "Island for Windows");
        configure(coreStage, "JARVIS");
        coreStage.initOwner(islandStage);

        Views.IslandView island = Views.island();
        Views.JarvisView core = Views.jarvis();
        island.controller().attach(model, () -> Platform.runLater(this::resizeIsland));
        core.controller().attach(model, this::hideCore);
        core.controller().enableDragging(coreStage);
        islandStage.setScene(new Scene(island.root(), Color.TRANSPARENT));
        coreStage.setScene(new Scene(core.root(), Color.TRANSPARENT));
        islandStage.setOnCloseRequest(event -> { event.consume(); shutdown(); });
        coreStage.setOnCloseRequest(event -> { event.consume(); hideCore(); });
        installContextMenu(island.root());
        installContextMenu(core.root());

        islandStage.show();
        coreStage.show();
        placeWindows();
        model.startBatteryPolling();
        tray.install(this::toggleCore, this::toggleVoice, this::shutdown);
        hotkey.start(() -> Platform.runLater(() -> { if (!stopping) toggleVoice(); }));
    }

    private static void configure(Stage stage, String title) {
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setTitle(title);
        stage.setAlwaysOnTop(true);
        stage.setResizable(false);
    }
    private void placeWindows() {
        Rectangle2D screen = Screen.getPrimary().getVisualBounds();
        OverlayLayout.Position pos = OverlayLayout.position(screen.getMinX(), screen.getMinY(), screen.getWidth(),
                islandStage.getWidth(), coreStage.getWidth());
        islandStage.setX(pos.islandX()); islandStage.setY(pos.islandY());
        coreStage.setX(pos.coreX()); coreStage.setY(pos.coreY());
    }
    private void resizeIsland() {
        if (islandStage == null || !islandStage.isShowing()) return;
        islandStage.sizeToScene();
        Rectangle2D screen = Screen.getPrimary().getVisualBounds();
        islandStage.setX(screen.getMinX() + Math.max(0, (screen.getWidth() - islandStage.getWidth()) / 2));
    }
    private void hideCore() {
        if (model != null) model.stopListening();
        if (coreStage != null) coreStage.hide();
    }
    private void showCore() { if (coreStage != null && !coreStage.isShowing()) coreStage.show(); }
    private void toggleCore() { if (coreStage.isShowing()) hideCore(); else showCore(); }
    private void toggleVoice() { showCore(); model.toggleListening(); }

    private void installContextMenu(javafx.scene.Parent root) {
        MenuItem core = new MenuItem("Show / hide JARVIS");
        core.setOnAction(event -> toggleCore());
        MenuItem voice = new MenuItem("Start / stop listening");
        voice.setOnAction(event -> toggleVoice());
        MenuItem exit = new MenuItem("Exit Island");
        exit.setOnAction(event -> shutdown());
        ContextMenu menu = new ContextMenu(core, voice, exit);
        root.setOnContextMenuRequested(event -> { menu.show(root, event.getScreenX(), event.getScreenY()); event.consume(); });
    }

    private void shutdown() {
        if (stopping) return;
        stopping = true;
        hotkey.close();
        tray.close();
        if (model != null) model.close();
        instance.close();
        Platform.exit();
    }
    @Override public void stop() { shutdown(); }
}
