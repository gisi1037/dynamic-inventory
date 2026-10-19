package dev.gilbe.dynamicinventory.api;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import dev.gilbe.dynamicinventory.container.PlayerInventoryTransfers;

/** Experimental API v1 transfer policy factories for GUI adapters. */
public final class InventoryTransfers {

    private InventoryTransfers() {}

    /** Sends a native machine stack to storage first, then the hotbar, including offscreen slots. */
    public static InventoryTransferPolicy toPlayerInventory() {
        return new InventoryTransferPolicy() {

            @Override
            public ItemStack transfer(Container container, EntityPlayer player, int sourceId,
                PlayerInventoryBinding binding) {
                if (binding == null) return null;
                int[] storage = binding.getStorageSlotIds();
                int[] hotbar = binding.getHotbarSlotIds();
                int[] destinations = java.util.Arrays.copyOf(storage, storage.length + hotbar.length);
                System.arraycopy(hotbar, 0, destinations, storage.length, hotbar.length);
                return transferTo(container, player, sourceId, binding, destinations);
            }
        };
    }

    /** Sends a source stack to every bound storage slot, excluding the hotbar. */
    public static InventoryTransferPolicy toStorage() {
        return new InventoryTransferPolicy() {

            @Override
            public ItemStack transfer(Container container, EntityPlayer player, int sourceId,
                PlayerInventoryBinding binding) {
                return transferTo(
                    container,
                    player,
                    sourceId,
                    binding,
                    binding == null ? null : binding.getStorageSlotIds());
            }
        };
    }

    /** Sends a source stack to the bound hotbar slots. */
    public static InventoryTransferPolicy toHotbar() {
        return new InventoryTransferPolicy() {

            @Override
            public ItemStack transfer(Container container, EntityPlayer player, int sourceId,
                PlayerInventoryBinding binding) {
                int[] ids = binding == null ? null : binding.getHotbarSlotIds();
                return transferTo(container, player, sourceId, binding, ids);
            }
        };
    }

    /** Sends a source stack to native, non-player slots from the original container. */
    public static InventoryTransferPolicy toNativeSlots() {
        return new InventoryTransferPolicy() {

            @Override
            public ItemStack transfer(Container container, EntityPlayer player, int sourceId,
                PlayerInventoryBinding binding) {
                if (!validSource(container, player, sourceId) || !validBinding(player, binding)) return null;
                int nativeCount = binding.getNativeSlotCount();
                if (nativeCount < 0 || nativeCount > container.inventorySlots.size()) return null;
                InventoryPlayer canonical = binding.getPlayerInventory();
                List<Integer> destinations = new ArrayList<Integer>();
                for (int id = 0; id < nativeCount; id++) {
                    if (id == sourceId || binding.logicalFromContainerId(id) >= 0) continue;
                    Slot slot = container.getSlot(id);
                    if (slot == null || slot.inventory instanceof InventoryPlayer
                        || (canonical != null && slot.inventory == canonical)) continue;
                    destinations.add(id);
                }
                return PlayerInventoryTransfers.transferTo(container, player, sourceId, toArray(destinations));
            }
        };
    }

    /** Sends a source stack to the listed container slot ids, preserving their order. */
    public static InventoryTransferPolicy toSlots(final int... destinationIds) {
        if (destinationIds == null) throw new IllegalArgumentException("destinationIds must not be null");
        final int[] copy = destinationIds.clone();
        return new InventoryTransferPolicy() {

            @Override
            public ItemStack transfer(Container container, EntityPlayer player, int sourceId,
                PlayerInventoryBinding binding) {
                if (!validSource(container, player, sourceId) || (binding != null && !validBinding(player, binding)))
                    return null;
                for (int id : copy) if (id < 0 || id >= container.inventorySlots.size() || id == sourceId) return null;
                return PlayerInventoryTransfers.transferTo(container, player, sourceId, copy);
            }
        };
    }

    private static ItemStack transferTo(Container container, EntityPlayer player, int sourceId,
        PlayerInventoryBinding binding, int[] destinations) {
        if (!validSource(container, player, sourceId) || !validBinding(player, binding) || destinations == null)
            return null;
        int[] safe = destinations.clone();
        for (int id : safe) {
            if (id < 0 || id >= container.inventorySlots.size() || id == sourceId) return null;
        }
        return PlayerInventoryTransfers.transferTo(container, player, sourceId, safe);
    }

    private static boolean validSource(Container container, EntityPlayer player, int sourceId) {
        return container != null && player != null && sourceId >= 0 && sourceId < container.inventorySlots.size();
    }

    private static boolean validBinding(EntityPlayer player, PlayerInventoryBinding binding) {
        return player != null && binding != null && binding.getPlayerInventory() == player.inventory;
    }

    private static int[] toArray(List<Integer> values) {
        int[] result = new int[values.size()];
        for (int i = 0; i < values.size(); i++) result[i] = values.get(i);
        return result;
    }
}
