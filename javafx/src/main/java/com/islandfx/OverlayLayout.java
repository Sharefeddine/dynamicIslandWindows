package com.islandfx;

/** Placement in logical screen coordinates. */
public final class OverlayLayout {
    public record Position(double islandX, double islandY, double coreX, double coreY) { }
    private OverlayLayout() { }

    public static Position position(double left, double top, double width, double islandWidth, double coreWidth) {
        double islandX = left + Math.max(0, (width - islandWidth) / 2);
        double maxCore = Math.max(left, left + width - coreWidth);
        double coreX = Math.max(left, Math.min(maxCore, islandX + islandWidth + 18));
        return new Position(islandX, top + 8, coreX, top + 92);
    }
}
