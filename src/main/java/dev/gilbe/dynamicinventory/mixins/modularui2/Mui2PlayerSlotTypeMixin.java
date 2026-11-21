package dev.gilbe.dynamicinventory.mixins.modularui2;

import net.minecraft.inventory.Slot;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cleanroommc.modularui.utils.item.SlotItemHandler;
import com.cleanroommc.modularui.widgets.slot.PlayerSlotType;

import dev.gilbe.dynamicinventory.compat.mui2.Mui2PlayerInventory;
import dev.gilbe.dynamicinventory.inventory.InventoryShape;

@Mixin(value = PlayerSlotType.class, remap = false)
public abstract class Mui2PlayerSlotTypeMixin {

    /** Use the player inventory theme even past MUI2's vanilla index ceiling. */
    @Inject(method = "getPlayerSlotType", at = @At("HEAD"), cancellable = true)
    private static void dynamicinventory$expandedStorageTheme(Slot slot, CallbackInfoReturnable<PlayerSlotType> cir) {
        if (!(slot instanceof SlotItemHandler itemSlot)
            || !(itemSlot.getItemHandler() instanceof Mui2PlayerInventory player)) return;
        InventoryShape shape = player.getInventoryShape();
        if (shape.isExtra(shape.ordinaryIndex(slot.getSlotIndex()))) cir.setReturnValue(PlayerSlotType.MAIN_INVENTORY);
    }
}
