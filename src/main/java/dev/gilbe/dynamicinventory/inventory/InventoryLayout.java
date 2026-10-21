package dev.gilbe.dynamicinventory.inventory;

import net.minecraft.entity.player.InventoryPlayer;

import dev.gilbe.dynamicinventory.compat.BackhandCompatibility;

/** Logical main indices are stable; armor API indices follow the main array. */
public final class InventoryLayout {

    public static final int HOTBAR_SLOTS = InventoryShape.HOTBAR_SLOTS;
    public static final int LEGACY_MAIN_SLOTS = InventoryShape.LEGACY_MAIN_SLOTS;
    public static final int ARMOR_SLOTS = InventoryShape.ARMOR_SLOTS;
    public static final int OFFHAND_INDEX = InventoryShape.OFFHAND_INDEX;

    private InventoryLayout() {}

    /** Startup defaults construct a NEW inventory. Runtime code must use of(theActualInventory). */
    public static InventoryShape initialShape() {
        return new InventoryShape(InventoryCapacity.storageSlots(), BackhandCompatibility.enabled(), 0);
    }

    public static InventoryShape of(InventoryPlayer inventory) {
        if (inventory == null) throw new NullPointerException("inventory");
        InventoryShape shape = ((ExpandedInventoryAccess) inventory).dynamicinventory$getShape();
        if (shape == null) throw new IllegalStateException("Inventory shape is not initialized");
        return shape;
    }

    // Compatibility helpers for startup fixtures and callers without an inventory.
    // They describe the startup DEFAULT, not a particular player's live capacity.

    public static int mainSlots() {
        return InventoryCapacity.storageSlots() + HOTBAR_SLOTS;
    }

    public static int storageSlots() {
        return InventoryCapacity.storageSlots();
    }

    public static int rawMainSlots() {
        return mainSlots() + (BackhandCompatibility.enabled() ? 1 : 0);
    }

    /** Ordinary logical indices exclude equipment, and remain stable in saves and screens. */
    public static int rawIndex(int ordinary) {
        if (ordinary < 0 || ordinary >= mainSlots()) throw new IllegalArgumentException("ordinary index");
        return ordinary + (BackhandCompatibility.enabled() && ordinary >= OFFHAND_INDEX ? 1 : 0);
    }

    public static int ordinaryIndex(int raw) {
        if (raw < 0 || raw >= rawMainSlots() || (BackhandCompatibility.enabled() && raw == OFFHAND_INDEX)) return -1;
        return raw - (BackhandCompatibility.enabled() && raw > OFFHAND_INDEX ? 1 : 0);
    }

    public static boolean isExtra(int index) {
        return index >= LEGACY_MAIN_SLOTS && index < mainSlots();
    }
}
