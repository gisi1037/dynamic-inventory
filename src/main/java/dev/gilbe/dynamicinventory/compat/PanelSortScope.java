package dev.gilbe.dynamicinventory.compat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.entity.player.InventoryPlayer;

import com.cleanroommc.bogosorter.mixins.early.minecraft.SlotAccessor;
import com.cleanroommc.modularui.utils.item.SlotItemHandler;

import dev.gilbe.dynamicinventory.inventory.HotbarBanks;
import dev.gilbe.dynamicinventory.inventory.InventoryLayout;

/** A single validated server sort; never changes Bogo's cached inventory groups or pin policy. */
public final class PanelSortScope {

    private static final ThreadLocal<int[]> ACTIVE = new ThreadLocal<>();

    private PanelSortScope() {}

    public static void run(int[] ids, Runnable action) {
        int[] previous = ACTIVE.get();
        ACTIVE.set(ids);
        try {
            action.run();
        } finally {
            if (previous == null) ACTIVE.remove();
            else ACTIVE.set(previous);
        }
    }

    public static List<SlotAccessor> filter(List<SlotAccessor> slots) {
        int[] selected = ACTIVE.get();
        if (selected == null) return excludeReserved(slots);
        List<SlotAccessor> result = new ArrayList<>();
        // Bogo already removed pinned and locked cells. Visit the others in the panel's visible order.
        Map<Integer, SlotAccessor> eligible = new HashMap<>();
        for (SlotAccessor slot : slots) if (!reserved(slot)) eligible.put(slot.getSlotNumber(), slot);
        for (int id : selected) {
            SlotAccessor slot = eligible.get(id);
            if (slot != null) result.add(slot);
        }
        return result;
    }

    public static List<SlotAccessor> excludeReserved(List<SlotAccessor> slots) {
        List<SlotAccessor> result = new ArrayList<>();
        for (SlotAccessor slot : slots) if (!reserved(slot)) result.add(slot);
        return result.size() == slots.size() ? slots : result;
    }

    /** Native sort buttons and hotkeys must protect stored rows even outside a panel sort scope. */
    public static boolean reserved(SlotAccessor slot) {
        if (slot instanceof com.gtnewhorizons.modularui.common.internal.wrapper.BaseSlot legacy && legacy
            .getItemHandler() instanceof dev.gilbe.dynamicinventory.compat.mui1.Mui1PlayerInventory inventory) {
            InventoryPlayer player = inventory.getInventoryPlayer();
            return HotbarBanks.isReserved(
                player,
                InventoryLayout.of(player)
                    .rawIndex(slot.callGetSlotIndex()));
        }
        if (slot instanceof SlotItemHandler handler
            && handler.getItemHandler() instanceof com.cleanroommc.modularui.utils.item.PlayerMainInvWrapper inventory)
            return HotbarBanks.isReserved(inventory.getInventoryPlayer(), slot.callGetSlotIndex());
        return slot.getInventory() instanceof InventoryPlayer inventory
            && HotbarBanks.isReserved(inventory, slot.callGetSlotIndex());
    }
}
