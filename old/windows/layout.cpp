#include "layout.h"

#include <algorithm>

namespace island {
OverlayLayout position_overlays(WorkArea area, int island_width, int core_width, int top_gap, int core_gap, int core_y) {
    const int available = std::max(0, area.right - area.left);
    const int island_x = area.left + std::max(0, (available - island_width) / 2);
    const int rightmost_core = std::max(area.left, area.right - core_width);
    const int core_x = std::clamp(island_x + island_width + core_gap, area.left, rightmost_core);
    return {{island_x, area.top + top_gap}, {core_x, area.top + core_y}};
}
}
