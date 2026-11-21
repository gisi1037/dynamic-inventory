package dev.gilbe.dynamicinventory.mixins.backpack;

import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.gilbe.dynamicinventory.container.ContainerInventoryRegion;
import dev.gilbe.dynamicinventory.container.PlayerInventoryTransfers;

@Pseudo
@Mixin(targets = "de.eydamos.backpack.inventory.container.ContainerAdvanced", remap = false)
public abstract class BackpackTransferMixin extends Container {

    // The source Slot and its stack have already been loaded. The remaining uses of this argument
    // only classify the source by its original container ranges.
    @ModifyVariable(
        method = { "transferStackInSlot", "func_82846_b" },
        at = @At(
            value = "FIELD",
            target = "Lde/eydamos/backpack/inventory/container/Boundaries;EXTRA:Lde/eydamos/backpack/inventory/container/Boundaries;",
            ordinal = 0),
        argsOnly = true,
        index = 2)
    private int dynamicinventory$expandedSource(int id) {
        ContainerInventoryRegion region = ContainerInventoryRegion.of(this);
        return region != null && id >= region.getNativeSlotCount() && region.logical(id) >= 0 ? region.id(9) : id;
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
