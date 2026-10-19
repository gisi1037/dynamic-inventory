package dev.gilbe.dynamicinventory.client;

/** Shared vertical fit policy for native machine panels, measured in scaled GUI pixels. */
public final class InventoryViewportLayout {

    private InventoryViewportLayout() {}

    public static int rows(int storageSlots, int nativeHeight, int screenHeight, boolean expand) {
        int total = (storageSlots + 8) / 9;
        if (!expand) return Math.min(total, 3);
        int additional = Math.max(0, (screenHeight - nativeHeight - 40) / 18);
        return Math.min(total, 3 + additional);
    }
}
