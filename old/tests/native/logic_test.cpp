#include "../../windows/commands.h"
#include "../../windows/layout.h"
#include <iostream>

namespace {
int failures = 0;
void check(bool condition, const char* description) {
    if (!condition) { std::cerr << "FAIL: " << description << '\n'; ++failures; }
}
}

int main() {
    using island::Intent;
    check(island::classify_command(L"play music") == Intent::ToggleMusic, "play music");
    check(island::classify_command(L"Pause the song") == Intent::ToggleMusic, "pause song");
    check(island::classify_command(L"play next track") == Intent::NextTrack, "next track takes precedence");
    check(island::classify_command(L"previous track") == Intent::PreviousTrack, "previous track");
    check(island::classify_command(L"what time is it") == Intent::Time, "time");
    check(island::classify_command(L"battery charge") == Intent::Battery, "battery");
    check(island::classify_command(L"stop listening") == Intent::StopListening, "stop listening");
    check(island::classify_command(L"stop the music") == Intent::Unknown, "do not guess stop state");
    check(island::classify_command(L"playground") == Intent::Unknown, "word boundary");
    const auto large = island::position_overlays({0, 0, 1440, 900}, 380, 310, 8, 14, 84);
    check(large.island.x == 530 && large.island.y == 8, "center island on primary work area");
    check(large.core.x == 924 && large.core.y == 84, "core is below and right of island");
    const auto small = island::position_overlays({-200, 40, 440, 760}, 380, 310, 8, 14, 84);
    check(small.island.x == -70 && small.core.x == 130 && small.core.y == 124, "clamp core to work area");
    if (failures != 0) return 1;
    std::cout << "Native command and layout tests passed\n";
    return 0;
}
