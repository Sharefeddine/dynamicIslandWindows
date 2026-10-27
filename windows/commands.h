#pragma once

#include <string>

namespace island {
enum class Intent {
    Unknown,
    ToggleMusic,
    NextTrack,
    PreviousTrack,
    Time,
    Battery,
    Help,
    StopListening,
};

// A deliberately small set of offline, English voice commands.
Intent classify_command(const std::wstring& phrase);
}
