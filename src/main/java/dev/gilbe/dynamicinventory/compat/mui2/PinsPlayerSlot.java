package dev.gilbe.dynamicinventory.compat.mui2;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;

import com.cleanroommc.modularui.utils.item.PlayerMainInvWrapper;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;

public final class PinsPlayerSlot {

    private PinsPlayerSlot() {}

    public static boolean belongsTo(Slot slot, InventoryPlayer inventory) {
        return slot instanceof ModularSlot modular && modular.getItemHandler() instanceof PlayerMainInvWrapper wrapper
            && wrapper.getInventoryPlayer() == inventory;
    }
}
