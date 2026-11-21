package dev.gilbe.dynamicinventory.client;

/** Divide the usable height between the native terminal and real player storage, in 18px rows. */
public final class TerminalViewportLayout {

    private TerminalViewportLayout() {}

    public static int terminalRows(int availableHeight, int fixedHeight, int requestedRows) {
        // The fixed height includes the player frame and hotbar. Leave one storage row as well.
        return Math.max(3, Math.min(requestedRows, (availableHeight - fixedHeight) / 18 - 1));
    }

    /** Leave room for the detached player's smallest usable frame, including a wrapped hotbar. */
    public static int detachedRows(int availableHeight, int nativeFixedHeight, int minimumPlayerHeight,
        int requestedRows) {
        return Math.max(3, Math.min(requestedRows, (availableHeight - nativeFixedHeight - minimumPlayerHeight) / 18));
    }
}
