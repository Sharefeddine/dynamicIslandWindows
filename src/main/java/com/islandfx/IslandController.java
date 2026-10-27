package com.islandfx;

import java.util.Locale;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
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
    @FXML private HBox waveform;
    @FXML private Label batteryLabel;
    @FXML private Label mediaTitleLabel;
    @FXML private Label mediaSubtitleLabel;
    @FXML private Label detailTitleLabel;
    @FXML private Label detailArtistLabel;
    @FXML private Label playbackLabel;
    @FXML private SVGPath chevron;
    private AppModel model;
    private Runnable onSizeChanged = () -> { };

    public void attach(AppModel model, Runnable onSizeChanged) {
        this.model = model;
        this.onSizeChanged = onSizeChanged;
        batteryLabel.textProperty().bind(model.batteryProperty());
        model.expandedProperty().addListener((unused, before, expanded) -> setExpanded(expanded));
        model.mediaInfoProperty().addListener((unused, before, info) -> renderMedia(info));
        setExpanded(model.expandedProperty().get());
        renderMedia(model.mediaInfoProperty().get());
    }
    private void setExpanded(boolean expanded) {
        details.setVisible(expanded);
        details.setManaged(expanded);
        chevron.setRotate(expanded ? 180 : 0);
        onSizeChanged.run();
    }
    private void renderMedia(MediaInfo info) {
        if (info == null) info = MediaInfo.unavailable();
        String title;
        String subtitle;
        String detail;
        String tag;
        boolean playing = info.availability() == MediaInfo.Availability.AVAILABLE && info.playback() == MediaInfo.Playback.PLAYING;
        switch (info.availability()) {
            case LOADING -> {
                title = "Music"; subtitle = "CONNECTING"; detail = "Checking Windows media…"; tag = "CHECKING";
            }
            case UNAVAILABLE -> {
                title = "Music"; subtitle = "MEDIA UNAVAILABLE";
                detail = info.detail().isBlank() ? "Windows media is unavailable" : info.detail();
                tag = "UNAVAILABLE";
            }
            case NO_SESSION -> {
                title = "Music"; subtitle = "NO MEDIA DETECTED"; detail = "Play audio or video to see it here"; tag = "NO MEDIA";
            }
            case AVAILABLE -> {
                title = info.title().isBlank() ? "Untitled media" : info.title();
                tag = switch (info.playback()) {
                    case PLAYING -> "PLAYING";
                    case PAUSED -> "PAUSED";
                    case STOPPED -> "STOPPED";
                    case UNKNOWN -> "MEDIA";
                };
                subtitle = tag + " · " + info.sourceLabel().toUpperCase(Locale.ROOT);
                detail = info.artist().isBlank() ? info.sourceLabel() : info.artist() + " · " + info.sourceLabel();
            }
            default -> throw new IllegalStateException("Unexpected media state");
        }
        setText(mediaTitleLabel, title);
        setText(mediaSubtitleLabel, subtitle);
        setText(detailTitleLabel, info.availability() == MediaInfo.Availability.AVAILABLE ? title : detail);
        setText(detailArtistLabel, info.availability() == MediaInfo.Availability.AVAILABLE ? detail : "Windows media session");
        playbackLabel.setText(tag);
        waveform.getStyleClass().remove("muted");
        if (!playing) waveform.getStyleClass().add("muted");
    }
    private static void setText(Label label, String text) {
        label.setText(text);
        Tooltip tooltip = label.getTooltip();
        if (tooltip == null) { tooltip = new Tooltip(); label.setTooltip(tooltip); }
        tooltip.setText(text);
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
