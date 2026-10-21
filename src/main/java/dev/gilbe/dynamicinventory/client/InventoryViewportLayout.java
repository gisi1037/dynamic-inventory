package dev.gilbe.dynamicinventory.client;

/** Shared vertical fit policy for native machine panels, measured in scaled GUI pixels. */
public final class InventoryViewportLayout {

    private InventoryViewportLayout() {}

    /** Desired rows before fitting the viewport; switching mode never changes column/slot identity. */
    public static int requestedRows(int storageSlots, int columns, boolean expand) {
        int total = (storageSlots + columns - 1) / columns;
        return expand ? total : Math.min(total, 3);
    }

    public static int rows(int storageSlots, int nativeHeight, int screenHeight, boolean expand) {
        int total = requestedRows(storageSlots, 9, true);
        if (!expand) return requestedRows(storageSlots, 9, false);
        int additional = Math.max(0, (screenHeight - nativeHeight - 40) / 18);
        return Math.min(total, 3 + additional);
    }
}
