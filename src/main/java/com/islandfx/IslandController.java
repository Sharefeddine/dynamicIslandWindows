package com.islandfx;

import java.awt.Desktop;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.geometry.NodeOrientation;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.Tooltip;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Arc;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.stage.FileChooser;
import javafx.util.Duration;

/** Controller for the animated notch and its local companion pages. */
public final class IslandController implements AutoCloseable {
    private static final double COMPACT_WIDTH = 310, COMPACT_HEIGHT = 44;
    private static final double OPEN_WIDTH = 480, OPEN_HEIGHT = 344, MAX_MORPH = 1.04;
    private static final Duration MORPH_DURATION = Duration.millis(520);

    static double maximumWidth() { return COMPACT_WIDTH + (OPEN_WIDTH - COMPACT_WIDTH) * MAX_MORPH; }
    static double maximumHeight() { return COMPACT_HEIGHT + (OPEN_HEIGHT - COMPACT_HEIGHT) * MAX_MORPH; }
    // A gentle, damped overshoot; the window stays centered as its bounds change.
    private static final Interpolator SPRING = new Interpolator() {
        @Override protected double curve(double t) {
            double x = t - 1;
            return 1 + 2.1 * x * x * x + 1.1 * x * x;
        }
    };
    @FXML private VBox root;
    @FXML private HBox header;
    @FXML private VBox details;
    @FXML private HBox waveform;
    @FXML private MascotView mascotView;
    @FXML private Label batteryLabel, mediaTitleLabel, mediaSubtitleLabel, detailTitleLabel, detailArtistLabel, playbackLabel;
    @FXML private Label positionLabel, durationLabel, pageTitleLabel, clockLabel, noticeLabel;
    @FXML private Label focusTimeLabel, focusStatusLabel, waterTotalLabel, waterGoalLabel;
    @FXML private Label lyricsStatusLabel, lyricsMatchLabel, adhkarIndexLabel, adhkarTextLabel, adhkarCountLabel, adhkarSourceLabel;
    @FXML private SVGPath chevron, playPauseIcon;
    @FXML private Arc focusArc;
    @FXML private ProgressBar mediaProgress, waterProgress;
    @FXML private StackPane pageStack;
    @FXML private VBox musicPage, waterPage, shelfPage, tasksPage, lyricsPage, adhkarPage;
    @FXML private HBox focusPage;
    @FXML private Button musicTab, focusTab, waterTab, shelfTab, tasksTab, lyricsTab, adhkarTab;
    @FXML private Button focusStartButton, waterReminderButton, drinkButton, avatarButton, morningButton, eveningButton, onlineLyricsButton;
    @FXML private TextField waterGoalField, glassField, taskField, taskTimeField, morningTimeField, eveningTimeField;
    @FXML private CheckBox morningReminderCheck, eveningReminderCheck;
    @FXML private ScrollPane adhkarScroll;
    @FXML private ListView<String> shelfList;
    @FXML private ListView<CompanionStore.Task> taskList;
    @FXML private ListView<LyricsService.Line> lyricsList;
    private AppModel model;
    private CompanionModel companion;
    private LyricsModel lyrics;
    private AdhkarCatalog adhkar;
    private AdhkarCatalog.Period adhkarPeriod = AdhkarCatalog.Period.MORNING;
    private int adhkarIndex;
    private Runnable onSizeChanged = () -> { };
    private final DoubleProperty reveal = new SimpleDoubleProperty(0);
    private final PauseTransition closeDelay = new PauseTransition(Duration.millis(450));
    private final List<ScaleTransition> bars = new ArrayList<>();
    private Timeline morph, pageFade;
    private boolean pinned, suppressHover, playing, disposed;

    @FXML private void initialize() {
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(root.widthProperty());
        clip.heightProperty().bind(root.heightProperty());
        root.setClip(clip);
        Tooltip.install(header, new Tooltip("Hover to open. Click the header to pin or close."));
        reveal.addListener((unused, before, value) -> applyReveal(value.doubleValue()));
        closeDelay.setOnFinished(event -> { if (!pinned && model != null) model.expandedProperty().set(false); });
        root.setOnMouseEntered(event -> {
            closeDelay.stop();
            if (model != null) {
                model.noteMascotInteraction();
                if (!suppressHover) model.expandedProperty().set(true);
            }
        });
        root.setOnMouseExited(event -> {
            suppressHover = false;
            if (!pinned) closeDelay.playFromStart();
        });
        details.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> { pinned = true; closeDelay.stop(); });
        root.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> { if (model != null) model.noteMascotInteraction(); });
        root.addEventFilter(KeyEvent.KEY_PRESSED, this::navigationKey);
        root.setOnDragEntered(event -> {
            if (event.getDragboard().hasFiles() && model != null) {
                model.noteMascotInteraction();
                pinned = true; closeDelay.stop();
                companion.selectPage(CompanionModel.Page.SHELF);
                model.expandedProperty().set(true);
                if (!root.getStyleClass().contains("dragging")) root.getStyleClass().add("dragging");
            }
        });
        root.setOnDragOver(event -> {
            if (event.getDragboard().hasFiles()) { event.acceptTransferModes(TransferMode.COPY); event.consume(); }
        });
        root.setOnDragExited(event -> root.getStyleClass().remove("dragging"));
        root.setOnDragDropped(event -> {
            Dragboard board = event.getDragboard();
            boolean accepted = board.hasFiles() && companion != null;
            if (accepted) companion.addFiles(board.getFiles());
            event.setDropCompleted(accepted);
            root.getStyleClass().remove("dragging");
            event.consume();
        });
        int i = 0;
        for (Node bar : waveform.getChildren()) {
            ScaleTransition pulse = new ScaleTransition(Duration.millis(340 + (i++ * 85)), bar);
            pulse.setFromY(0.35); pulse.setToY(1); pulse.setAutoReverse(true); pulse.setCycleCount(Animation.INDEFINITE);
            bars.add(pulse);
        }
        shelfList.setPlaceholder(placeholder("Drop files here, or choose + Add files"));
        taskList.setPlaceholder(placeholder("No tasks yet"));
    }

    public void attach(AppModel model, CompanionModel companion, Runnable onSizeChanged) {
        attach(model, companion, onSizeChanged, LyricsModel.create());
    }
    // Allows deterministic offline UI tests and previews without contacting a lyrics service.
    void attach(AppModel model, CompanionModel companion, Runnable onSizeChanged, LyricsModel lyrics) {
        this.model = model;
        this.companion = companion;
        this.lyrics = lyrics;
        this.onSizeChanged = onSizeChanged;
        lyrics.setEnabled(companion.onlineLyricsProperty().get());
        try { adhkar = AdhkarCatalog.load(); }
        catch (java.io.IOException failure) { companion.noticeProperty().set("Arabic adhkar data could not be loaded."); }
        batteryLabel.textProperty().bind(model.batteryProperty());
        clockLabel.textProperty().bind(companion.clockTextProperty());
        model.expandedProperty().addListener((unused, before, expanded) -> animateExpanded(expanded));
        model.mediaInfoProperty().addListener((unused, before, info) -> renderMedia(info));
        model.mascotPoseProperty().addListener((unused, before, pose) -> mascotView.showPose(pose));
        companion.pageProperty().addListener((unused, before, page) -> renderPage(page, true));
        companion.focusModeProperty().addListener((unused, before, value) -> renderFocus());
        companion.focusRemainingProperty().addListener((unused, before, value) -> renderFocus());
        companion.focusRunningProperty().addListener((unused, before, value) -> renderFocus());
        companion.focusStatusProperty().addListener((unused, before, value) -> renderFocus());
        companion.waterMlProperty().addListener((unused, before, value) -> renderWater());
        companion.waterGoalProperty().addListener((unused, before, value) -> renderWater());
        companion.glassMlProperty().addListener((unused, before, value) -> renderWater());
        companion.waterReminderProperty().addListener((unused, before, value) -> renderWater());
        companion.avatarStyleProperty().addListener((unused, before, value) -> renderAvatar());
        companion.onlineLyricsProperty().addListener((unused, before, value) -> renderOnlineLyrics());
        lyricsStatusLabel.textProperty().bind(lyrics.statusProperty());
        lyricsMatchLabel.textProperty().bind(lyrics.matchProperty());
        lyrics.activeIndexProperty().addListener((unused, before, value) -> {
            lyricsList.refresh();
            if (value.intValue() >= 0) lyricsList.scrollTo(value.intValue());
        });
        companion.noticeProperty().addListener((unused, before, value) -> renderNotice());
        companion.storageWarningProperty().addListener((unused, before, value) -> renderNotice());
        configureLists();
        waterGoalField.setText(Integer.toString(companion.waterGoalProperty().get()));
        glassField.setText(Integer.toString(companion.glassMlProperty().get()));
        morningTimeField.setText(companion.morningTimeProperty().get().format(DateTimeFormatter.ofPattern("HH:mm")));
        eveningTimeField.setText(companion.eveningTimeProperty().get().format(DateTimeFormatter.ofPattern("HH:mm")));
        morningReminderCheck.setSelected(companion.morningReminderProperty().get());
        eveningReminderCheck.setSelected(companion.eveningReminderProperty().get());
        for (Label label : List.of(adhkarTextLabel, adhkarCountLabel, adhkarSourceLabel)) {
            // JavaFX mirrors alignment in an RTL node: logical LEFT is the visual right edge.
            label.setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT); label.setAlignment(Pos.CENTER_LEFT);
            label.prefWidthProperty().bind(adhkarScroll.widthProperty().subtract(42));
        }
        boolean expanded = model.expandedProperty().get();
        details.setVisible(expanded); details.setManaged(expanded); details.setMouseTransparent(!expanded);
        reveal.set(expanded ? 1 : 0);
        applyReveal(reveal.get());
        renderPage(companion.pageProperty().get(), false);
        mascotView.showPose(model.mascotPoseProperty().get());
        renderAvatar(); renderDhikr(); renderOnlineLyrics();
        renderFocus(); renderWater(); renderNotice(); renderMedia(model.mediaInfoProperty().get());
    }

    private void animateExpanded(boolean expanded) {
        if (disposed) return;
        closeDelay.stop();
        if (morph != null) morph.stop();
        if (expanded) { details.setManaged(true); details.setVisible(true); details.setMouseTransparent(false); }
        else { details.setMouseTransparent(true); }
        morph = new Timeline(new KeyFrame(MORPH_DURATION, new KeyValue(reveal, expanded ? 1 : 0, SPRING)));
        morph.setOnFinished(event -> {
            if (!model.expandedProperty().get()) { details.setVisible(false); details.setManaged(false); }
        });
        morph.play();
    }
    private void applyReveal(double amount) {
        double shape = Math.max(0, Math.min(MAX_MORPH, amount));
        double width = COMPACT_WIDTH + (OPEN_WIDTH - COMPACT_WIDTH) * shape;
        double height = COMPACT_HEIGHT + (OPEN_HEIGHT - COMPACT_HEIGHT) * shape;
        root.setMinSize(width, height); root.setPrefSize(width, height); root.setMaxSize(width, height);
        double fade = Math.max(0, Math.min(1, (amount - 0.24) / 0.58));
        details.setOpacity(fade);
        details.setTranslateY(-7 * (1 - fade));
        chevron.setRotate(180 * Math.max(0, Math.min(1, amount)));
        onSizeChanged.run();
    }
    private void pinOrClose() {
        if (model == null) return;
        if (model.expandedProperty().get() && pinned) {
            pinned = false; suppressHover = true; model.expandedProperty().set(false);
        } else {
            pinned = true; closeDelay.stop(); model.expandedProperty().set(true);
            companion.noticeProperty().set("Pinned open · click the header to close.");
        }
    }
    @FXML private void toggleExpanded(MouseEvent event) {
        if (event.getButton() == MouseButton.PRIMARY) { pinOrClose(); event.consume(); }
    }
    @FXML private void headerKey(KeyEvent event) {
        if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.SPACE) { pinOrClose(); event.consume(); }
    }
    private void navigationKey(KeyEvent event) {
        if (model == null) return;
        model.noteMascotInteraction();
        if (event.getCode() == KeyCode.ESCAPE && model.expandedProperty().get()) {
            pinned = false; suppressHover = true; model.expandedProperty().set(false); event.consume();
        } else if (!(event.getTarget() instanceof TextInputControl) && model.expandedProperty().get()) {
            if (event.getCode() == KeyCode.LEFT) { companion.movePage(-1); event.consume(); }
            else if (event.getCode() == KeyCode.RIGHT) { companion.movePage(1); event.consume(); }
        }
    }

    private void renderPage(CompanionModel.Page page, boolean animate) {
        Node[] pages = { musicPage, focusPage, waterPage, shelfPage, tasksPage, lyricsPage, adhkarPage };
        Button[] tabs = { musicTab, focusTab, waterTab, shelfTab, tasksTab, lyricsTab, adhkarTab };
        String[] titles = { "Music", "Focus timer", "Water reminder", "File shelf", "To-do", "Lyrics", "أذكار الصباح والمساء" };
        if (pageFade != null) pageFade.stop();
        for (int i = 0; i < pages.length; i++) {
            boolean selected = i == page.ordinal();
            pages[i].setVisible(selected); pages[i].setManaged(selected);
            pages[i].setOpacity(1); pages[i].setTranslateY(0);
            tabs[i].getStyleClass().remove("active");
            if (selected) tabs[i].getStyleClass().add("active");
        }
        pageTitleLabel.setText(titles[page.ordinal()]);
        if (animate && model.expandedProperty().get()) {
            Node selected = pages[page.ordinal()];
            selected.setOpacity(0); selected.setTranslateY(6);
            pageFade = new Timeline(new KeyFrame(Duration.millis(220),
                    new KeyValue(selected.opacityProperty(), 1, Interpolator.EASE_OUT),
                    new KeyValue(selected.translateYProperty(), 0, Interpolator.EASE_OUT)));
            pageFade.play();
        }
    }
    @FXML private void previousPage() { companion.movePage(-1); }
    @FXML private void nextPage() { companion.movePage(1); }
    @FXML private void showMusic() { companion.selectPage(CompanionModel.Page.MUSIC); }
    @FXML private void showFocus() { companion.selectPage(CompanionModel.Page.FOCUS); }
    @FXML private void showWater() { companion.selectPage(CompanionModel.Page.WATER); }
    @FXML private void showShelf() { companion.selectPage(CompanionModel.Page.SHELF); }
    @FXML private void showTasks() { companion.selectPage(CompanionModel.Page.TASKS); }
    @FXML private void showLyrics() { companion.selectPage(CompanionModel.Page.LYRICS); }
    @FXML private void showAdhkar() { companion.selectPage(CompanionModel.Page.ADHKAR); }
    @FXML private void retryLyrics() { lyrics.retry(); }
    @FXML private void toggleOnlineLyrics() { companion.toggleOnlineLyrics(); }
    private void renderOnlineLyrics() {
        boolean enabled = companion.onlineLyricsProperty().get();
        onlineLyricsButton.setText(enabled ? "Online on" : "Online off");
        lyrics.setEnabled(enabled);
    }

    private void renderMedia(MediaInfo info) {
        if (info == null) info = MediaInfo.unavailable();
        if (lyrics != null) lyrics.update(info);
        String title, subtitle, detail, tag;
        boolean isPlaying = info.availability() == MediaInfo.Availability.AVAILABLE && info.playback() == MediaInfo.Playback.PLAYING;
        switch (info.availability()) {
            case LOADING -> { title = "Music"; subtitle = "CONNECTING"; detail = "Checking Windows media…"; tag = "CHECKING"; }
            case UNAVAILABLE -> {
                title = "Music"; subtitle = "MEDIA UNAVAILABLE";
                detail = info.detail().isBlank() ? "Windows media is unavailable" : info.detail(); tag = "UNAVAILABLE";
            }
            case NO_SESSION -> { title = "Music"; subtitle = "NO MEDIA DETECTED"; detail = "Play audio or video to see it here"; tag = "NO MEDIA"; }
            case AVAILABLE -> {
                title = info.title().isBlank() ? "Untitled media" : info.title();
                tag = switch (info.playback()) { case PLAYING -> "PLAYING"; case PAUSED -> "PAUSED"; case STOPPED -> "STOPPED"; case UNKNOWN -> "MEDIA"; };
                subtitle = tag + " · " + info.sourceLabel().toUpperCase(Locale.ROOT);
                detail = info.artist().isBlank() ? info.sourceLabel() : info.artist() + " · " + info.sourceLabel();
            }
            default -> throw new IllegalStateException("Unexpected media state");
        }
        setText(mediaTitleLabel, title); setText(mediaSubtitleLabel, subtitle);
        setText(detailTitleLabel, info.availability() == MediaInfo.Availability.AVAILABLE ? title : detail);
        setText(detailArtistLabel, info.availability() == MediaInfo.Availability.AVAILABLE ? detail : "Windows media session");
        playbackLabel.setText(tag);
        positionLabel.setText(info.hasTimeline() ? formatMediaTime(info.positionSeconds()) : "--:--");
        durationLabel.setText(info.hasTimeline() ? formatMediaTime(info.durationSeconds()) : "--:--");
        mediaProgress.setProgress(info.hasTimeline() ? (double) info.positionSeconds() / info.durationSeconds() : 0);
        playPauseIcon.setContent(isPlaying ? "M 3 1 H 7 V 15 H 3 Z M 11 1 H 15 V 15 H 11 Z" : "M 2 0 L 14 8 L 2 16 Z");
        waveform.getStyleClass().remove("muted");
        if (!isPlaying) waveform.getStyleClass().add("muted");
        if (playing != isPlaying) {
            playing = isPlaying;
            for (ScaleTransition bar : bars) {
                if (playing) bar.play(); else { bar.stop(); bar.getNode().setScaleY(0.45); }
            }
        }
    }
    private static String formatMediaTime(long seconds) {
        if (seconds >= 3600) return String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }
    private static void setText(Label label, String text) {
        label.setText(text);
        Tooltip tooltip = label.getTooltip();
        if (tooltip == null) { tooltip = new Tooltip(); label.setTooltip(tooltip); }
        tooltip.setText(text);
    }
    @FXML private void previous() { model.media(DeviceServices.MediaKey.PREVIOUS); }
    @FXML private void playPause() { model.media(DeviceServices.MediaKey.PLAY_PAUSE); }
    @FXML private void next() { model.media(DeviceServices.MediaKey.NEXT); }

    private void renderAvatar() {
        CompanionStore.AvatarStyle style = companion.avatarStyleProperty().get();
        mascotView.setAvatarStyle(style);
        String name = style == CompanionStore.AvatarStyle.REALISTIC ? "real" : "soft";
        avatarButton.setText(mascotView.has3d() ? "3D: " + name : "2D fallback");
        avatarButton.setTooltip(new Tooltip(mascotView.has3d()
                ? "Switch 3D companion style (stylized / anatomical head)"
                : "3D is unavailable here. Click to save a style for a 3D-capable system. Selected: " + name));
    }
    @FXML private void toggleAvatar() {
        companion.selectAvatarStyle(companion.avatarStyleProperty().get() == CompanionStore.AvatarStyle.STYLIZED
                ? CompanionStore.AvatarStyle.REALISTIC : CompanionStore.AvatarStyle.STYLIZED);
        if (!mascotView.has3d()) companion.noticeProperty().set("Style saved · using a 2D fallback because 3D is unavailable.");
    }

    private void renderDhikr() {
        morningButton.getStyleClass().remove("active"); eveningButton.getStyleClass().remove("active");
        (adhkarPeriod == AdhkarCatalog.Period.MORNING ? morningButton : eveningButton).getStyleClass().add("active");
        List<AdhkarCatalog.Entry> entries = adhkar == null ? List.of() : adhkar.forPeriod(adhkarPeriod);
        if (entries.isEmpty()) {
            adhkarIndexLabel.setText("—"); adhkarTextLabel.setText("تعذر تحميل الأذكار");
            adhkarCountLabel.setText(""); adhkarSourceLabel.setText(""); return;
        }
        adhkarIndex = Math.floorMod(adhkarIndex, entries.size());
        AdhkarCatalog.Entry entry = entries.get(adhkarIndex);
        adhkarIndexLabel.setText((adhkarIndex + 1) + " / " + entries.size());
        adhkarTextLabel.setText(wrapArabic(entry.text(), 65));
        String description = entry.countDescription().isBlank() ? "" : " · " + entry.countDescription();
        adhkarCountLabel.setText("التكرار: " + entry.count() + description);
        adhkarSourceLabel.setText(entry.source().isBlank() ? "" : wrapArabic("المصدر: " + entry.source(), 65));
        adhkarScroll.setVvalue(0);
    }
    /** Add word-boundary breaks for long Arabic passages in the compact, fixed-width reader. */
    static String wrapArabic(String text, int limit) {
        StringBuilder wrapped = new StringBuilder();
        for (String paragraph : text.split("\\R", -1)) {
            if (wrapped.length() > 0) wrapped.append('\n');
            int width = 0;
            for (String word : paragraph.trim().split("\\s+")) {
                if (word.isEmpty()) continue;
                int length = word.codePointCount(0, word.length());
                if (width > 0 && width + length + 1 > limit) { wrapped.append('\n'); width = 0; }
                else if (width > 0) { wrapped.append(' '); width++; }
                wrapped.append(word); width += length;
            }
        }
        return wrapped.toString();
    }
    @FXML private void showMorning() { adhkarPeriod = AdhkarCatalog.Period.MORNING; adhkarIndex = 0; renderDhikr(); }
    @FXML private void showEvening() { adhkarPeriod = AdhkarCatalog.Period.EVENING; adhkarIndex = 0; renderDhikr(); }
    @FXML private void previousDhikr() { adhkarIndex--; renderDhikr(); }
    @FXML private void nextDhikr() { adhkarIndex++; renderDhikr(); }
    @FXML private void saveAdhkar() {
        companion.applyAdhkarSettings(morningTimeField.getText(), morningReminderCheck.isSelected(),
                eveningTimeField.getText(), eveningReminderCheck.isSelected());
    }
    @FXML private void testNotification() { companion.testNotification(); }

    private void renderFocus() {
        int remaining = companion.focusRemainingProperty().get();
        int total = companion.focusModeProperty().get().seconds;
        focusTimeLabel.setText(String.format(Locale.ROOT, "%02d:%02d", remaining / 60, remaining % 60));
        focusStatusLabel.setText(companion.focusModeProperty().get().label + " · " + companion.focusStatusProperty().get());
        focusStartButton.setText(companion.focusRunningProperty().get() ? "Pause" : "Start");
        focusArc.setLength(-360.0 * remaining / total);
    }
    @FXML private void selectFocus() { companion.selectFocusMode(CompanionModel.FocusMode.FOCUS); }
    @FXML private void selectBreak() { companion.selectFocusMode(CompanionModel.FocusMode.BREAK); }
    @FXML private void toggleFocus() { companion.toggleFocus(); }
    @FXML private void resetFocus() { companion.resetFocus(); }

    private void renderWater() {
        int total = companion.waterMlProperty().get(), goal = companion.waterGoalProperty().get();
        waterTotalLabel.setText(Integer.toString(total));
        waterGoalLabel.setText("/ " + goal + " ml today");
        waterProgress.setProgress(Math.min(1, (double) total / goal));
        drinkButton.setText("+ " + companion.glassMlProperty().get() + " ml");
        boolean on = companion.waterReminderProperty().get();
        waterReminderButton.setText(on ? "Reminders on" : "Reminders off");
        waterReminderButton.getStyleClass().remove("active");
        if (on) waterReminderButton.getStyleClass().add("active");
    }
    @FXML private void drink() { companion.drink(); }
    @FXML private void resetWater() { companion.resetWater(); }
    @FXML private void toggleWaterReminder() { companion.toggleWaterReminder(); }
    @FXML private void saveWaterSettings() { companion.applyWaterSettings(waterGoalField.getText(), glassField.getText()); }
    private void renderNotice() {
        String warning = companion.storageWarningProperty().get();
        String text = warning.isBlank() ? companion.noticeProperty().get() : warning;
        if (text.isBlank()) text = "Hover to open · click the header to pin";
        setText(noticeLabel, text);
    }

    private void configureLists() {
        shelfList.setItems(companion.shelf());
        shelfList.setCellFactory(list -> {
            ListCell<String> cell = new ListCell<>() {
                @Override protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : CompanionModel.fileName(item));
                    setTooltip(empty || item == null ? null : new Tooltip(item));
                }
            };
            cell.setOnMouseClicked(event -> { if (!cell.isEmpty() && event.getClickCount() == 2 && event.getButton() == MouseButton.PRIMARY) openFile(); });
            cell.setOnDragDetected(event -> {
                if (cell.isEmpty()) return;
                try {
                    File file = Path.of(cell.getItem()).toFile();
                    if (!file.exists()) { companion.noticeProperty().set("The original file is missing."); return; }
                    Dragboard board = cell.startDragAndDrop(TransferMode.COPY);
                    ClipboardContent content = new ClipboardContent(); content.putFiles(List.of(file)); board.setContent(content);
                    event.consume();
                } catch (InvalidPathException invalid) { companion.noticeProperty().set("This shortcut has an invalid path."); }
            });
            return cell;
        });
        taskList.setItems(companion.tasks());
        taskList.setCellFactory(list -> new TaskCell());
        lyricsList.setItems(lyrics.lines());
        lyricsList.setPlaceholder(placeholder("Lyrics appear here when available online."));
        lyricsList.setCellFactory(list -> new ListCell<>() {
            private final Label line = new Label();
            {
                line.setWrapText(true); line.getStyleClass().add("lyric-line");
                line.prefWidthProperty().bind(list.widthProperty().subtract(35));
                line.setMaxWidth(Double.MAX_VALUE);
            }
            @Override protected void updateItem(LyricsService.Line item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().remove("current"); line.getStyleClass().remove("current");
                if (empty || item == null) { setGraphic(null); setText(null); return; }
                boolean active = getIndex() == lyrics.activeIndexProperty().get();
                if (active) { getStyleClass().add("current"); line.getStyleClass().add("current"); }
                boolean rtl = LyricsModel.isRtl(item.text());
                line.setNodeOrientation(rtl ? NodeOrientation.RIGHT_TO_LEFT : NodeOrientation.LEFT_TO_RIGHT);
                // LEFT is mirrored to the visual right edge inside an RTL JavaFX node.
                line.setAlignment(Pos.CENTER_LEFT);
                line.setText(item.text().isEmpty() ? " " : item.text());
                setGraphic(line); setText(null);
            }
        });
    }
    private static Label placeholder(String text) { Label label = new Label(text); label.getStyleClass().add("list-placeholder"); return label; }
    @FXML private void chooseFiles() {
        pinned = true;
        FileChooser chooser = new FileChooser(); chooser.setTitle("Add files to the Island shelf");
        List<File> files = chooser.showOpenMultipleDialog(root.getScene().getWindow());
        if (files != null) companion.addFiles(files);
    }
    @FXML private void removeFile() {
        String selected = shelfList.getSelectionModel().getSelectedItem();
        if (selected == null) companion.noticeProperty().set("Select a shortcut to remove.");
        else companion.removeFile(selected);
    }
    @FXML private void openFile() {
        String selected = shelfList.getSelectionModel().getSelectedItem();
        if (selected == null) { companion.noticeProperty().set("Select a shortcut to open."); return; }
        try {
            File file = Path.of(selected).toFile();
            if (!Files.exists(file.toPath())) { companion.noticeProperty().set("The original file is missing."); return; }
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                companion.noticeProperty().set("Opening files is unavailable on this system."); return;
            }
            companion.noticeProperty().set("Opening " + CompanionModel.fileName(selected) + "…");
            Thread worker = new Thread(() -> {
                try { Desktop.getDesktop().open(file); }
                catch (Exception failure) { Platform.runLater(() -> companion.noticeProperty().set("Could not open the selected file.")); }
            }, "island-open-file");
            worker.setDaemon(true); worker.start();
        } catch (InvalidPathException | SecurityException invalid) { companion.noticeProperty().set("Could not open this shortcut."); }
    }
    @FXML private void addTask() {
        if (companion.addTask(taskField.getText(), taskTimeField.getText())) { taskField.clear(); taskTimeField.clear(); }
    }
    private final class TaskCell extends ListCell<CompanionStore.Task> {
        private final CheckBox done = new CheckBox();
        private final Label title = new Label();
        private final Label due = new Label();
        private final Button remove = new Button("×");
        private final HBox row = new HBox(7, done, title, due, remove);
        TaskCell() {
            row.setAlignment(Pos.CENTER_LEFT); title.setMinWidth(0); title.setMaxWidth(Double.MAX_VALUE); HBox.setHgrow(title, Priority.ALWAYS);
            done.getStyleClass().add("task-check"); title.getStyleClass().add("task-text"); due.getStyleClass().add("task-due"); remove.getStyleClass().add("task-delete");
            done.setAccessibleText("Mark task complete"); remove.setAccessibleText("Remove task");
            done.setOnAction(event -> { if (getItem() != null) companion.setTaskDone(getItem().id(), done.isSelected()); });
            remove.setOnAction(event -> { if (getItem() != null) companion.removeTask(getItem().id()); });
        }
        @Override protected void updateItem(CompanionStore.Task item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) { setGraphic(null); setText(null); return; }
            title.setText(item.title()); title.setTooltip(new Tooltip(item.title())); done.setSelected(item.done());
            title.getStyleClass().remove("task-done"); if (item.done()) title.getStyleClass().add("task-done");
            String dueTime = item.dueMillis() > 0 ? DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(item.dueMillis())) : "";
            due.setText(item.done() || dueTime.isBlank() ? "" : item.notified() ? "alerted" : dueTime + " · alert");
            due.setTooltip(dueTime.isBlank() ? null : new Tooltip("Local reminder " +
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(item.dueMillis())) +
                    (item.notified() ? " · notification requested" : " · while Island is open")));
            setGraphic(row); setText(null);
        }
    }
    @Override public void close() {
        disposed = true; closeDelay.stop();
        if (morph != null) morph.stop();
        if (pageFade != null) pageFade.stop();
        for (ScaleTransition bar : bars) bar.stop();
        if (lyrics != null) lyrics.close();
        mascotView.close();
    }
}
