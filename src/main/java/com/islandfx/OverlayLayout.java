package com.islandfx;

/** Placement in logical screen coordinates, reserving room for the expanded island. */
public final class OverlayLayout {
    public record Position(double islandX, double islandY, double coreX, double coreY) { }
    private OverlayLayout() { }

    public static Position position(double left, double top, double width, double height,
                                    double islandWidth, double expandedWidth, double expandedHeight,
                                    double coreWidth, double coreHeight) {
        double islandX = left + Math.max(0, (width - islandWidth) / 2);
        double maxCoreX = Math.max(left, left + width - coreWidth);
        // Leave space for the widest point of the spring, not just the compact notch.
        double preferredX = left + (width + Math.max(islandWidth, expandedWidth)) / 2 + 18;
        boolean fitsBesideIsland = preferredX <= maxCoreX;
        double coreX = Math.min(maxCoreX, Math.max(left, preferredX));
        double preferredY = top + (fitsBesideIsland ? 92 : expandedHeight + 18);
        double maxCoreY = Math.max(top, top + height - coreHeight);
        double coreY = Math.min(maxCoreY, Math.max(top, preferredY));
        return new Position(islandX, top, coreX, coreY);
    }
}
