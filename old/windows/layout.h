#pragma once

namespace island {
struct WorkArea { int left; int top; int right; int bottom; };
struct Point { int x; int y; };
struct OverlayLayout { Point island; Point core; };

// The caller supplies actual window sizes and gap in physical pixels.
OverlayLayout position_overlays(WorkArea area, int island_width, int core_width, int top_gap, int core_gap, int core_y);
}
