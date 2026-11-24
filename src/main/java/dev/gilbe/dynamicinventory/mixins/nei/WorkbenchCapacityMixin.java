package dev.gilbe.dynamicinventory.mixins.nei;

import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerWorkbench;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

import codechicken.lib.inventory.InventoryRange;
import codechicken.lib.inventory.InventoryUtils;
import dev.gilbe.dynamicinventory.container.ContainerInventoryRegion;
import dev.gilbe.dynamicinventory.container.PlayerInventoryTransfers;

/** NEI adds an atomic recipe-output capacity check before vanilla's merge. Expand that check too. */
@Mixin(ContainerWorkbench.class)
public abstract class WorkbenchCapacityMixin extends Container {

    @Redirect(
        method = "transferStackInSlot",
        require = 0,
        at = @At(
            value = "INVOKE",
            target = "Lcodechicken/lib/inventory/InventoryUtils;insertItem(Lcodechicken/lib/inventory/InventoryRange;Lnet/minecraft/item/ItemStack;Z)I",
            remap = false))
    private int dynamicinventory$capacity(@Coerce Object range, ItemStack stack, boolean simulate) {
        ContainerInventoryRegion region = ContainerInventoryRegion.of(this);
        // ContainerWorkbench quick-moves its result into native slots [10, 46) in reverse order.
        int[] destinations = region != null && simulate ? region.destinations(10, 46, true) : null;
        return destinations == null ? InventoryUtils.insertItem((InventoryRange) range, stack, simulate)
            : PlayerInventoryTransfers.remainingAfterMerge(this, stack, destinations);
    }
}
