package dev.gilbe.dynamicinventory.container;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import dev.gilbe.dynamicinventory.api.PlayerInventoryBinding;
import dev.gilbe.dynamicinventory.api.PlayerInventorySlotFactory;
import dev.gilbe.dynamicinventory.inventory.InventoryLayout;
import dev.gilbe.dynamicinventory.inventory.InventoryShape;

/** Discovers slots by canonical backing inventory, never by a guessed machine/player boundary. */
public final class ContainerInventoryRegion implements PlayerInventoryBinding {

    public final int nativeSlotCount;
    public final InventoryPlayer inventory;
    private final InventoryShape shape;
    private final int[] ids;

    private ContainerInventoryRegion(Container container, InventoryPlayer inventory,
        PlayerInventorySlotFactory factory) {
        this.inventory = inventory;
        shape = InventoryLayout.of(inventory);
        ids = new int[shape.mainSlots()];
        nativeSlotCount = container.inventorySlots.size();
        Arrays.fill(ids, -1);
        for (Slot slot : container.inventorySlots) {
            if (slot.inventory != inventory) continue;
            int logical = shape.ordinaryIndex(slot.getSlotIndex());
            if (logical < 0) continue;
            if (ids[logical] >= 0) throw new IllegalStateException("Duplicate player slot: " + logical);
            ids[logical] = slot.slotNumber;
        }
        // Refuse incomplete/unknown layouts before modifying the wire protocol.
        for (int i = 0; i < InventoryLayout.LEGACY_MAIN_SLOTS; i++)
            if (ids[i] < 0) throw new IllegalStateException("Missing native player slot: " + i);
        ExpandedContainerAccess access = (ExpandedContainerAccess) container;
        // Construct and validate every candidate before appending any slot. A late factory failure cannot
        // leave a partly expanded container whose slot IDs disagree between the client and server.
        Slot[] prepared = prepare(container, inventory, factory);
        for (int i = InventoryLayout.LEGACY_MAIN_SLOTS; i < ids.length; i++) {
            if (ids[i] >= 0) continue;
            ids[i] = access.dynamicinventory$append(prepared[i]).slotNumber;
        }
    }

    private Slot[] prepare(Container container, InventoryPlayer inventory, PlayerInventorySlotFactory factory) {
        Slot[] prepared = new Slot[ids.length];
        List<Slot> nativeSlots = new ArrayList<>(container.inventorySlots);
        List<ItemStack> nativeStacks = new ArrayList<>(container.inventoryItemStacks);
        Set<Slot> seen = Collections.newSetFromMap(new IdentityHashMap<Slot, Boolean>());
        seen.addAll(nativeSlots);
        try {
            for (int logical = InventoryLayout.LEGACY_MAIN_SLOTS; logical < ids.length; logical++) {
                if (ids[logical] >= 0) continue;
                int raw = shape.rawIndex(logical);
                Slot slot = factory.create(container, inventory, raw);
                if (!sameReferences(nativeSlots, container.inventorySlots)
                    || !sameReferences(nativeStacks, container.inventoryItemStacks))
                    throw new IllegalArgumentException("Additional slot factory changed container lists");
                if (slot == null || slot.inventory != inventory || slot.getSlotIndex() != raw || !seen.add(slot))
                    throw new IllegalArgumentException("Additional slot factory returned an invalid or reused slot");
                prepared[logical] = slot;
            }
        } catch (RuntimeException invalid) {
            // Restore shallow list mutations by an erroneous callback. Factories must be pure: arbitrary
            // inventory/game-state side effects cannot be undone by this construction-time validation.
            container.inventorySlots.clear();
            container.inventorySlots.addAll(nativeSlots);
            container.inventoryItemStacks.clear();
            container.inventoryItemStacks.addAll(nativeStacks);
            throw new IllegalArgumentException("Could not prepare additional player slots", invalid);
        }
        return prepared;
    }

    private static boolean sameReferences(List<?> expected, List<?> actual) {
        if (expected.size() != actual.size()) return false;
        for (int i = 0; i < expected.size(); i++) if (expected.get(i) != actual.get(i)) return false;
        return true;
    }

    public static void attach(Container container) {
        ExpandedContainerAccess access = (ExpandedContainerAccess) container;
        if (access.dynamicinventory$region() != null) return;
        for (Slot slot : container.inventorySlots) {
            if (slot.inventory instanceof InventoryPlayer) {
                access.dynamicinventory$region(
                    new ContainerInventoryRegion(
                        container,
                        (InventoryPlayer) slot.inventory,
                        PlayerInventorySlotFactory.plainSlot()));
                return;
            }
        }
        throw new IllegalStateException("Supported container has no player inventory: " + container.getClass());
    }

    public static void attach(Container container, InventoryPlayer inventory) {
        attach(container, inventory, PlayerInventorySlotFactory.plainSlot());
    }

    public static void attach(Container container, InventoryPlayer inventory, PlayerInventorySlotFactory factory) {
        ExpandedContainerAccess access = (ExpandedContainerAccess) container;
        if (access.dynamicinventory$region() != null) return;
        access.dynamicinventory$region(new ContainerInventoryRegion(container, inventory, factory));
    }

    @Override
    public int getNativeSlotCount() {
        return nativeSlotCount;
    }

    @Override
    public int getOrdinarySlotCount() {
        return ids.length;
    }

    @Override
    public long getCapacityRevision() {
        return shape.revision();
    }

    @Override
    public int getContainerSlotId(int logicalIndex) {
        return id(logicalIndex);
    }

    @Override
    public int[] getHotbarSlotIds() {
        return hotbar();
    }

    @Override
    public int[] getStorageSlotIds() {
        return Arrays.copyOfRange(ids, 9, ids.length);
    }

    @Override
    public InventoryPlayer getPlayerInventory() {
        return inventory;
    }

    public static ContainerInventoryRegion of(Container container) {
        return ((ExpandedContainerAccess) container).dynamicinventory$region();
    }

    public int id(int logical) {
        return ids[logical];
    }

    public int logical(int id) {
        for (int i = 0; i < ids.length; i++) if (ids[i] == id) return i;
        return -1;
    }

    /** Translate only a wholly ordinary native destination range; leave machine ranges untouched. */
    public int[] destinations(int start, int end, boolean reverse) {
        boolean storage = false;
        boolean hotbar = false;
        if (start >= end || start < 0 || end > nativeSlotCount) return null;
        for (int id = start; id < end; id++) {
            int logical = logical(id);
            if (logical < 0) return null;
            storage |= logical >= InventoryLayout.HOTBAR_SLOTS;
            hotbar |= logical < InventoryLayout.HOTBAR_SLOTS;
        }
        if (end - start != (storage ? 27 : 0) + (hotbar ? 9 : 0)) return null;
        // A region owns this snapshot; never size from a newer process-wide capacity.
        int[] result = new int[(storage ? ids.length - InventoryLayout.HOTBAR_SLOTS : 0) + (hotbar ? 9 : 0)];
        int n = 0;
        if (storage) for (int i = 9; i < ids.length; i++) result[n++] = ids[i];
        if (hotbar) for (int i = 0; i < 9; i++) result[n++] = ids[i];
        if (reverse) for (int i = 0; i < result.length / 2; i++) {
            int other = result.length - 1 - i;
            int swap = result[i];
            result[i] = result[other];
            result[other] = swap;
        }
        return result;
    }

    public int[] hotbar() {
        return Arrays.copyOf(ids, 9);
    }
}
