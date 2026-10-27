package com.islandfx;

import java.util.OptionalInt;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** OS integration is kept separate from the FXML views so it can be replaced in previews/tests. */
public interface DeviceServices extends AutoCloseable {
    enum MediaKey {
        PREVIOUS(0xB1), PLAY_PAUSE(0xB3), NEXT(0xB0);
        final int virtualKey;
        MediaKey(int virtualKey) { this.virtualKey = virtualKey; }
    }
    enum VoiceEvent { LISTENING, PARTIAL, REJECTED, ERROR, ENDED }

    OptionalInt batteryPercent();
    boolean sendMediaKey(MediaKey key);
    default void startMedia(Consumer<MediaInfo> onChange) { onChange.accept(MediaInfo.unavailable()); }
    void startVoice(Consumer<String> phrase, BiConsumer<VoiceEvent, String> state);
    void stopVoice();
    @Override void close();
}
