package dev.gilbe.dynamicinventory.client;

import java.util.List;

import dev.gilbe.dynamicinventory.client.InventoryPanelLayout.Rect;

/** Pure screen bounds; minimum native content takes priority over requested empty space. */
public final class NativeScreenMargins {

    private NativeScreenMargins() {}

    public static int availableHeight(int screenHeight, int top, int bottom) {
        return Math.max(1, screenHeight - Math.max(0, top) - Math.max(0, bottom));
    }

    public static int top(int screenHeight, int panelHeight, int offsetY, int requestedTop, int requestedBottom) {
        int free = Math.max(0, screenHeight - panelHeight);
        // Preserve the bottom tools first if the native minimum leaves too little room for both margins.
        int bottom = Math.min(Math.max(0, requestedBottom), free);
        int top = Math.min(Math.max(0, requestedTop), free - bottom);
        int maximumTop = free - bottom;
        long centered = top + (maximumTop - top) / 2L + offsetY;
        return (int) Math.max(top, Math.min(maximumTop, centered));
    }

    /** Reclaim space left by the attached layout without shifting relative panel positions. */
    public static int detachedDownwardShift(Rect viewport, int attachedHeight, List<Rect> visible,
        List<Rect> stationary) {
        if (visible.isEmpty()) return 0;
        int top = Integer.MAX_VALUE, bottom = Integer.MIN_VALUE;
        for (Rect rect : visible) {
            top = Math.min(top, rect.y);
            bottom = Math.max(bottom, rect.bottom());
        }
        int height = bottom - top;
        if (height >= attachedHeight || height >= viewport.height) return 0;
        int shift = Math.max(0, viewport.y + (viewport.height - height) / 2 - top);
        for (Rect rect : visible) for (Rect obstacle : stationary) {
            if (rect.right() <= obstacle.x || obstacle.right() <= rect.x || obstacle.bottom() <= rect.y) continue;
            shift = Math.min(shift, Math.max(0, obstacle.y - rect.bottom()));
        }
        return shift;
    }
}
