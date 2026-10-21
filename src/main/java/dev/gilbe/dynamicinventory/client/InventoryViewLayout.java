package dev.gilbe.dynamicinventory.client;

import dev.gilbe.dynamicinventory.inventory.InventoryLayout;

/** Pure viewport calculation in Minecraft's scaled GUI pixels. */
public final class InventoryViewLayout {

    public final int columns;
    public final int visibleRows;
    public final int totalRows;
    public final int width;
    public final int height;
    public final int gridX;
    public final int hotbarY;

    public InventoryViewLayout(int screenWidth, int screenHeight, int preferredColumns, int preferredRows) {
        this(screenWidth, screenHeight, preferredColumns, preferredRows, InventoryLayout.storageSlots());
    }

    /** Capacity is supplied by the actual owner; layout preferences never change it. */
    public InventoryViewLayout(int screenWidth, int screenHeight, int preferredColumns, int preferredRows,
        int storageSlots) {
        if (storageSlots < 1) throw new IllegalArgumentException("storage slots");
        columns = Math.max(3, Math.min(Math.min(18, preferredColumns), (screenWidth - 42) / 18));
        totalRows = (storageSlots + columns - 1) / columns;
        visibleRows = Math.max(1, Math.min(Math.min(totalRows, preferredRows), (screenHeight - 132) / 18));
        boolean scroll = totalRows > visibleRows;
        width = Math.max(176, columns * 18 + 16 + (scroll ? 10 : 0));
        gridX = (width - columns * 18 - (scroll ? 10 : 0)) / 2;
        hotbarY = 84 + visibleRows * 18 + 4;
        height = hotbarY + 24;
    }

    public int maxScroll() {
        return totalRows - visibleRows;
    }
}
