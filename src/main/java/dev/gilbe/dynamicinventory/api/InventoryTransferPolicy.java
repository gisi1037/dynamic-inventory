package dev.gilbe.dynamicinventory.api;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/**
 * Experimental API v1 strategy for moving a stack from a container slot.
 *
 * <p>
 * {@code sourceId} and destination IDs are absolute indices in
 * {@link Container#inventorySlots}; they are not raw player-inventory indices or logical player indices.
 * A {@link PlayerInventoryBinding} describes which of those container slots belong to the player's canonical
 * inventory. The player passed to the policy must own that same inventory.
 * </p>
 *
 * <p>
 * The built-in policies return a copy of the original source stack when anything moved, or {@code null}
 * when the source is invalid or no item moved. They update the source slot and call
 * {@link Slot#onPickupFromSlot(EntityPlayer, ItemStack)} after a successful move. They do not reproduce
 * subclass-specific transfer logic. In particular, they do not call
 * {@link Slot#onSlotChange(ItemStack, ItemStack)}, which crafting-result slots may require to consume
 * ingredients or update recipes. Keep such source slots on the container's native transfer path.
 * </p>
 */
public interface InventoryTransferPolicy {

    /**
     * Moves from one absolute container slot ID to the destinations selected by this policy.
     *
     * @param container the live container whose slot IDs are used for source and destinations
     * @param player    the player performing the transfer; must own the bound canonical inventory
     * @param sourceId  absolute source index in {@code container.inventorySlots}
     * @param binding   mapping from logical player slots to their absolute container slot IDs
     * @return a copy of the original source stack when items moved, or {@code null} when nothing moved
     */
    ItemStack transfer(Container container, EntityPlayer player, int sourceId, PlayerInventoryBinding binding);
}
