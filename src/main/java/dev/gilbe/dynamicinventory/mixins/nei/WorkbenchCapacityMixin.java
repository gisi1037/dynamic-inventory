package dev.gilbe.dynamicinventory.mixins.nei;

import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerWorkbench;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

import codechicken.lib.inventory.InventoryRange;
import codechicken.lib.inventory.InventoryUtils;
import dev.gilbe.dynamicinventory.container.ContainerInventoryRegion;

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
        if (region == null || !simulate) return InventoryUtils.insertItem((InventoryRange) range, stack, simulate);
        int remaining = stack.stackSize;
        for (int i = 0; i < region.getOrdinarySlotCount() && remaining > 0; i++) {
            Slot slot = getSlot(region.id(i));
            if (!slot.isItemValid(stack)) continue;
            ItemStack existing = slot.getStack();
            int limit = Math.min(slot.getSlotStackLimit(), stack.getMaxStackSize());
            if (existing == null) remaining -= limit;
            else if (existing.getItem() == stack.getItem()
                && (!stack.getHasSubtypes() || existing.getItemDamage() == stack.getItemDamage())
                && ItemStack.areItemStackTagsEqual(existing, stack))
                remaining -= Math.max(0, limit - existing.stackSize);
        }
        return Math.max(0, remaining);
    }
}
