package dev.gilbe.dynamicinventory.container;

import dev.gilbe.dynamicinventory.compat.BackhandCompatibility;
import dev.gilbe.dynamicinventory.compat.BaublesInventoryBridge;
import dev.gilbe.dynamicinventory.compat.PackCompatibility;
import dev.gilbe.dynamicinventory.inventory.InventoryLayout;

/** Container IDs deliberately retain vanilla's hotbar IDs, including its window-zero packet special case. */
public final class PlayerContainerLayout {

    public static final int RESULT = 0;
    public static final int ARMOR_START = 5;
    public static final int LEGACY_STORAGE_START = 9;
    public static final int HOTBAR_START = 36;
    public static final int HOTBAR_END = 45;
    public static final int OFFHAND = 45;
    public static final int EXTRA_START = HOTBAR_END + (BackhandCompatibility.enabled() ? 1 : 0);
    public static final int COLUMNS = 9;
    public static final int ROW_HEIGHT = 18;
    public static final int STORAGE_Y = 84;

    private PlayerContainerLayout() {}

    public static int slotCount() {
        return EXTRA_START + InventoryLayout.mainSlots() - InventoryLayout.LEGACY_MAIN_SLOTS;
    }

    public static int storageRows() {
        return (InventoryLayout.storageSlots() + COLUMNS - 1) / COLUMNS;
    }

    public static int hotbarY() {
        return STORAGE_Y + storageRows() * ROW_HEIGHT + 4;
    }

    public static int guiHeight() {
        return hotbarY() + 24;
    }

    /** Equipment is appended after every established storage ID. */
    public static int totalSlots() {
        return slotCount() + (PackCompatibility.enabled() ? BaublesInventoryBridge.slotCount() : 0);
    }

    public static int containerId(int mainIndex) {
        if (mainIndex < 0 || mainIndex >= InventoryLayout.mainSlots()) throw new IllegalArgumentException("mainIndex");
        if (mainIndex < InventoryLayout.HOTBAR_SLOTS) return HOTBAR_START + mainIndex;
        if (mainIndex < InventoryLayout.LEGACY_MAIN_SLOTS) return mainIndex;
        return EXTRA_START + mainIndex - InventoryLayout.LEGACY_MAIN_SLOTS;
    }

    public static int mainIndex(int containerId) {
        if (containerId >= LEGACY_STORAGE_START && containerId < HOTBAR_START) return containerId;
        if (containerId >= HOTBAR_START && containerId < HOTBAR_END) return containerId - HOTBAR_START;
        if (containerId >= EXTRA_START && containerId < slotCount())
            return containerId - EXTRA_START + InventoryLayout.LEGACY_MAIN_SLOTS;
        return -1;
    }

    public static int storageX(int mainIndex) {
        return 8 + (mainIndex - InventoryLayout.HOTBAR_SLOTS) % COLUMNS * ROW_HEIGHT;
    }

    public static int storageY(int mainIndex) {
        return STORAGE_Y + (mainIndex - InventoryLayout.HOTBAR_SLOTS) / COLUMNS * ROW_HEIGHT;
    }
}
