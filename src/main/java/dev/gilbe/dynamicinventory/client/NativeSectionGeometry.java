package dev.gilbe.dynamicinventory.client;

/** Pure positioning for independently movable native and player sections, in scaled pixels. */
public final class NativeSectionGeometry {

    public final int headerX, headerY, playerX, playerY, width, height, shiftX, shiftY;

    public NativeSectionGeometry(int baseWidth, int minimumHeight, int headerX, int headerY, int nativeLeft,
        int nativeRight, int nativeTop, int nativeBottom, int playerX, int playerY, int playerWidth, int playerHeight,
        int nativeOffsetX, int nativeOffsetY, int playerOffsetX, int playerOffsetY) {
        int hx = headerX + nativeOffsetX, hy = headerY + nativeOffsetY;
        int px = playerX + playerOffsetX, py = playerY + playerOffsetY;
        // Normalize negative coordinates into the outer GUI bounds. Slot IDs and backing inventories never change.
        shiftX = -Math.min(0, Math.min(hx + nativeLeft, px));
        shiftY = -Math.min(0, Math.min(hy + nativeTop, py));
        this.headerX = hx + shiftX;
        this.headerY = hy + shiftY;
        this.playerX = px + shiftX;
        this.playerY = py + shiftY;
        width = Math.max(baseWidth, Math.max(hx + nativeRight, px + playerWidth)) + shiftX;
        height = Math.max(minimumHeight, Math.max(hy + nativeBottom, py + playerHeight)) + shiftY;
    }

    /** -1 retains the adapter's original spacing. Explicit spacing starts after the last native slot's bevel. */
    public static int playerBoundary(int originalBoundary, int minimumHeader, int machineBottom, int gap) {
        int requested = gap < 0 ? Math.max(originalBoundary, machineBottom + 8) : machineBottom + 2 + gap;
        return Math.max(minimumHeader, requested);
    }
}
