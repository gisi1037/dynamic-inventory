package dev.gilbe.dynamicinventory.mixins;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cleanroommc.modularui.screen.ModularContainer;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;

import dev.gilbe.dynamicinventory.compat.mui2.Mui2PlayerInventory;

/** Resolves both framework slot groups using MUI2's canonical raw player indices. */
@Mixin(value = ModularContainer.class, remap = false)
public abstract class Mui2PlayerLookupMixin extends Container {

    @Inject(method = "findPlayerSlot", at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$canonicalPlayerSlot(EntityPlayer player, int index,
        CallbackInfoReturnable<Slot> cir) {
        if (player == null) return;
        for (Slot slot : inventorySlots) {
            if (!(slot instanceof ModularSlot)) continue;
            Object handler = ((ModularSlot) slot).getItemHandler();
            if (!(handler instanceof Mui2PlayerInventory)) continue;
            Mui2PlayerInventory inventory = (Mui2PlayerInventory) handler;
            if (inventory.getInventoryPlayer() == player.inventory && slot.getSlotIndex() == index) {
                cir.setReturnValue(slot);
                return;
            }
        }
    }
}
