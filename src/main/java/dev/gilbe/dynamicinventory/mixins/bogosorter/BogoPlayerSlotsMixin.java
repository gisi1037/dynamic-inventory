package dev.gilbe.dynamicinventory.mixins.bogosorter;

import net.minecraft.entity.player.InventoryPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cleanroommc.bogosorter.mixins.early.minecraft.SlotAccessor;
import com.cleanroommc.modularui.utils.item.SlotItemHandler;

import dev.gilbe.dynamicinventory.inventory.InventoryLayout;

/** Classify canonical ordinary slots by role, including extra storage and excluding equipment. */
@Mixin(targets = "com.cleanroommc.bogosorter.BogoSortAPI", remap = false)
public abstract class BogoPlayerSlotsMixin {

    @Inject(
        method = "isPlayerSlot(Lcom/cleanroommc/bogosorter/mixins/early/minecraft/SlotAccessor;)Z",
        at = @At("HEAD"),
        cancellable = true)
    private static void dynamicinventory$ordinary(SlotAccessor slot, CallbackInfoReturnable<Boolean> result) {
        if (slot == null) return;
        if (slot instanceof com.gtnewhorizons.modularui.common.internal.wrapper.BaseSlot legacy && legacy
            .getItemHandler() instanceof dev.gilbe.dynamicinventory.compat.mui1.Mui1PlayerInventory inventory) {
            result.setReturnValue(slot.callGetSlotIndex() >= 0 && slot.callGetSlotIndex() < inventory.getSlots());
            return;
        }
        if (slot instanceof SlotItemHandler handler && handler
            .getItemHandler() instanceof dev.gilbe.dynamicinventory.compat.mui2.Mui2PlayerInventory inventory) {
            result.setReturnValue(
                inventory.getInventoryShape()
                    .ordinaryIndex(slot.callGetSlotIndex()) >= 0);
            return;
        }
        if (slot.getInventory() instanceof InventoryPlayer inventory) {
            result.setReturnValue(
                InventoryLayout.of(inventory)
                    .ordinaryIndex(slot.callGetSlotIndex()) >= 0);
        } else if (slot instanceof SlotItemHandler handler && handler
            .getItemHandler() instanceof com.cleanroommc.modularui.utils.item.PlayerMainInvWrapper inventory) {
                result.setReturnValue(
                    InventoryLayout.of(inventory.getInventoryPlayer())
                        .ordinaryIndex(slot.callGetSlotIndex()) >= 0);
            }
    }
}
