package dev.gilbe.dynamicinventory.client;

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
}
