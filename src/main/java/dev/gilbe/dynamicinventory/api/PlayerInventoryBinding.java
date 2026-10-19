package dev.gilbe.dynamicinventory.api;

import net.minecraft.entity.player.InventoryPlayer;

/**
 * Experimental API v1 mapping for one container and one canonical player inventory.
 * Logical ordinary indices start at hotbar 0-8, continue through storage, and exclude equipment. Raw inventory
 * indices may differ (for example Backhand's offhand occupies raw 36). Container slot IDs are absolute positions
 * in {@code Container.inventorySlots}; use this binding rather than treating either index as a container ID.
 *
 * <p>
 * The canonical inventory and its live Slot objects are shared with the native container. Never copy them into
 * a second backing store. Transfer policies must verify that their player owns this same inventory and retain the
 * live slots' insertion, extraction, stack-limit and callback rules. This mapping does not replace native callbacks.
 * </p>
 */
public interface PlayerInventoryBinding {

    /** Number of slots before missing added player slots were appended; existing native IDs remain unchanged. */
    int getNativeSlotCount();

    /** Total logical ordinary slots, including the hotbar; excludes armor and optional equipment. */
    int getOrdinarySlotCount();

    /** Maps a logical ordinary index to an absolute container slot ID, never to a raw inventory index. */
    int getContainerSlotId(int logicalIndex);

    /** Returns a defensive copy of the nine absolute hotbar container slot IDs. */
    int[] getHotbarSlotIds();

    /** Returns a defensive copy of absolute storage container slot IDs, excluding the hotbar and equipment. */
    int[] getStorageSlotIds();

    /** Returns the live canonical owner, not a copy; a transfer's player.inventory must be this exact object. */
    InventoryPlayer getPlayerInventory();

    /** Returns the logical player slot for a container id, or -1 when it is not bound. */
    default int logicalFromContainerId(int containerSlotId) {
        for (int logical = 0; logical < getOrdinarySlotCount(); logical++) {
            if (getContainerSlotId(logical) == containerSlotId) return logical;
        }
        return -1;
    }
}
