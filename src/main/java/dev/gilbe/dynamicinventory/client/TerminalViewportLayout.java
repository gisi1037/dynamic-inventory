package dev.gilbe.dynamicinventory.client;

/** Divide the usable height between the native terminal and real player storage, in 18px rows. */
public final class TerminalViewportLayout {

    private TerminalViewportLayout() {}

    public static int terminalRows(int availableHeight, int fixedHeight, int desiredPlayerRows, boolean small,
        int smallRows, int tallMinimumRows) {
        int budget = Math.max(4, (availableHeight - fixedHeight) / 18);
        int reserved = small ? smallRows : tallMinimumRows;
        int terminal = Math.max(3, Math.min(reserved, budget - 1));
        int player = Math.max(1, Math.min(desiredPlayerRows, budget - terminal));
        return small ? terminal : budget - player;
    }
}
