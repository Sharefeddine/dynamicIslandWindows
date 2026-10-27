package com.islandfx;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;

public final class JarvisController {
    @FXML private VBox root;
    @FXML private HBox dragHandle;
    @FXML private Circle statusDot;
    @FXML private Label statusLabel;
    @FXML private Label voiceLabel;
    @FXML private Label messageLabel;
    @FXML private Button micButton;
    private AppModel model;
    private Runnable onHide = () -> { };
    private double dragX;
    private double dragY;

    public void attach(AppModel model, Runnable onHide) {
        this.model = model;
        this.onHide = onHide;
        messageLabel.textProperty().bind(model.messageProperty());
        model.voiceProperty().addListener((unused, before, state) -> renderVoice(state));
        renderVoice(model.voiceProperty().get());
    }

    public void enableDragging(Stage stage) {
        dragHandle.addEventHandler(MouseEvent.MOUSE_PRESSED, event -> {
            if (event.isPrimaryButtonDown() && !isFromButton(event)) {
                dragX = event.getScreenX() - stage.getX();
                dragY = event.getScreenY() - stage.getY();
            }
        });
        dragHandle.addEventHandler(MouseEvent.MOUSE_DRAGGED, event -> {
            if (event.isPrimaryButtonDown() && !isFromButton(event)) {
                stage.setX(event.getScreenX() - dragX);
                stage.setY(event.getScreenY() - dragY);
            }
        });
    }
    private boolean isFromButton(MouseEvent event) {
        Object node = event.getTarget();
        while (node instanceof javafx.scene.Node current) {
            if (current instanceof Button) return true;
            node = current.getParent();
        }
        return false;
    }

    private void renderVoice(AppModel.VoiceState state) {
        boolean active = state == AppModel.VoiceState.LISTENING;
        statusDot.getStyleClass().remove("active");
        micButton.getStyleClass().remove("active");
        if (active) { statusDot.getStyleClass().add("active"); micButton.getStyleClass().add("active"); }
        statusLabel.setText(active ? "ACTIVE" : state == AppModel.VoiceState.STARTING ? "STARTING" : "STANDBY");
        voiceLabel.setText(switch (state) {
            case LISTENING -> "Listening...";
            case STARTING -> "Starting…";
            case ERROR -> "Voice unavailable";
            case OFF -> "Ready to listen";
        });
        micButton.setAccessibleText(active ? "Stop listening" : "Start listening");
    }
    @FXML private void toggleListening() { if (model != null) model.toggleListening(); }
    @FXML private void hide() { onHide.run(); }
}
