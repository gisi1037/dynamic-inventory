package dev.gilbe.dynamicinventory.mixins.backpack;

import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.gilbe.dynamicinventory.container.ContainerInventoryRegion;
import dev.gilbe.dynamicinventory.container.PlayerInventoryTransfers;

@Pseudo
@Mixin(targets = "com.darkona.adventurebackpack.inventory.ContainerAdventure", remap = false)
public abstract class AdventureTransferMixin extends Container {

    @Shadow
    protected abstract boolean transferStackToPack(ItemStack stack);

    @Inject(method = "transferFromSlot", at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$expandedSource(int id, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        ContainerInventoryRegion region = ContainerInventoryRegion.of(this);
        if (region != null && id >= region.getNativeSlotCount() && region.logical(id) >= 0) {
            cir.setReturnValue(transferStackToPack(stack));
        }
    }

    @Inject(method = { "mergeItemStack", "func_75135_a" }, at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$expandedDestination(ItemStack stack, int start, int end, boolean reverse,
        CallbackInfoReturnable<Boolean> cir) {
        ContainerInventoryRegion region = ContainerInventoryRegion.of(this);
        if (region == null) return;
        int[] ids = region.destinations(start, end, reverse);
        if (ids != null) cir.setReturnValue(PlayerInventoryTransfers.merge(this, stack, ids));
    }
}
