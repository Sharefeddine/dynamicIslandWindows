package com.islandfx;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;

public final class IslandController {
    @FXML private VBox root;
    @FXML private HBox header;
    @FXML private VBox details;
    @FXML private Label batteryLabel;
    @FXML private SVGPath chevron;
    private AppModel model;
    private Runnable onSizeChanged = () -> { };

    public void attach(AppModel model, Runnable onSizeChanged) {
        this.model = model;
        this.onSizeChanged = onSizeChanged;
        batteryLabel.textProperty().bind(model.batteryProperty());
        model.expandedProperty().addListener((unused, before, expanded) -> setExpanded(expanded));
        setExpanded(model.expandedProperty().get());
    }
    private void setExpanded(boolean expanded) {
        details.setVisible(expanded);
        details.setManaged(expanded);
        chevron.setRotate(expanded ? 180 : 0);
        onSizeChanged.run();
    }
    @FXML private void toggleExpanded(MouseEvent event) { if (model != null) model.toggleExpanded(); }
    @FXML private void headerKey(KeyEvent event) {
        if (model != null && (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.SPACE)) {
            model.toggleExpanded();
            event.consume();
        }
    }
    @FXML private void previous() { if (model != null) model.media(DeviceServices.MediaKey.PREVIOUS); }
    @FXML private void playPause() { if (model != null) model.media(DeviceServices.MediaKey.PLAY_PAUSE); }
    @FXML private void next() { if (model != null) model.media(DeviceServices.MediaKey.NEXT); }
}
