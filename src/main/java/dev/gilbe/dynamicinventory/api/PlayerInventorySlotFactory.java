package dev.gilbe.dynamicinventory.api;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;

/**
 * Experimental API v1 factory for missing added player slots. Register the same factory on both sides.
 * Return a fresh, unattached slot backed by exactly {@code canonical} and {@code rawIndex}; subclasses can
 * preserve the native container's insertion, extraction, stack-limit and callback rules.
 * The factory must only construct a slot: do not mutate the container, inventories or other game state.
 * Existing native slots are retained and never passed to this factory.
 */
@FunctionalInterface
public interface PlayerInventorySlotFactory {

    Slot create(Container container, InventoryPlayer canonical, int rawIndex);

    /** Default for containers whose additional ordinary player slots have no special rules. */
    static PlayerInventorySlotFactory plainSlot() {
        return (container, canonical, rawIndex) -> new Slot(canonical, rawIndex, -10000, -10000);
    }
}
