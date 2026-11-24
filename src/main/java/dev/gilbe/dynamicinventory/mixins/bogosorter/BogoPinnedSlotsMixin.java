package dev.gilbe.dynamicinventory.mixins.bogosorter;

import net.minecraft.entity.player.EntityPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cleanroommc.bogosorter.common.PinnedSlots;
import com.cleanroommc.bogosorter.mixins.early.minecraft.SlotAccessor;

import dev.gilbe.dynamicinventory.compat.BogoExpandedPins;
import dev.gilbe.dynamicinventory.inventory.InventorySlotLocks;

@Mixin(value = PinnedSlots.class, remap = false)
public abstract class BogoPinnedSlotsMixin {

    @Inject(
        method = "isPinnable(Lcom/cleanroommc/bogosorter/mixins/early/minecraft/SlotAccessor;)Z",
        at = @At("HEAD"),
        cancellable = true)
    private static void dynamicinventory$pinnable(SlotAccessor slot, CallbackInfoReturnable<Boolean> result) {
        if (BogoExpandedPins.index(slot, null) >= 0) result.setReturnValue(true);
    }

    @Inject(
        method = "isPinned(Lnet/minecraft/entity/player/EntityPlayer;Lcom/cleanroommc/bogosorter/mixins/early/minecraft/SlotAccessor;)Z",
        at = @At("HEAD"),
        cancellable = true)
    private static void dynamicinventory$pinnedSlot(EntityPlayer player, SlotAccessor slot,
        CallbackInfoReturnable<Boolean> result) {
        if (slot instanceof net.minecraft.inventory.Slot actual && InventorySlotLocks.locked(actual, player)) {
            result.setReturnValue(true);
            return;
        }
        int index = BogoExpandedPins.index(slot, player.inventory);
        if (index >= 0) result.setReturnValue(BogoExpandedPins.isPinned(player, index));
    }

    @Inject(method = "isPinned(Lnet/minecraft/entity/player/EntityPlayer;I)Z", at = @At("HEAD"), cancellable = true)
    private static void dynamicinventory$pinnedRaw(EntityPlayer player, int raw,
        CallbackInfoReturnable<Boolean> result) {
        if (InventorySlotLocks.locked(player.inventory, raw)) {
            result.setReturnValue(true);
            return;
        }
        int index = BogoExpandedPins.indexFromRaw(player.inventory, raw);
        if (index >= 0) result.setReturnValue(BogoExpandedPins.isPinned(player, index));
    }
}
