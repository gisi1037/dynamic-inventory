package dev.gilbe.dynamicinventory.compat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.entity.player.InventoryPlayer;

import com.cleanroommc.bogosorter.mixins.early.minecraft.SlotAccessor;
import com.cleanroommc.modularui.utils.item.SlotItemHandler;

import dev.gilbe.dynamicinventory.inventory.HotbarBanks;
import dev.gilbe.dynamicinventory.inventory.InventoryLayout;

/** A single validated server sort; never changes Bogo's cached inventory groups or pin policy. */
public final class PanelSortScope {

    private static final ThreadLocal<Set<Integer>> ACTIVE = new ThreadLocal<>();

    private PanelSortScope() {}

    public static void run(int[] ids, Runnable action) {
        Set<Integer> previous = ACTIVE.get();
        Set<Integer> selected = new HashSet<>();
        for (int id : ids) selected.add(id);
        ACTIVE.set(selected);
        try {
            action.run();
        } finally {
            if (previous == null) ACTIVE.remove();
            else ACTIVE.set(previous);
        }
    }

    public static List<SlotAccessor> filter(List<SlotAccessor> slots) {
        Set<Integer> selected = ACTIVE.get();
        List<SlotAccessor> result = new ArrayList<>();
        for (SlotAccessor slot : slots) {
            if ((selected == null || selected.contains(slot.getSlotNumber())) && !reserved(slot)) result.add(slot);
        }
        return result.size() == slots.size() ? slots : result;
    }

    /** Native sort buttons and hotkeys must protect stored rows even outside a panel sort scope. */
    private static boolean reserved(SlotAccessor slot) {
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
