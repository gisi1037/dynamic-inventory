package dev.gilbe.dynamicinventory.mixins.draconic;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "com.brandon3055.draconicevolution.common.container.ContainerDislocatorInhibitor", remap = false)
public abstract class InhibitorContainerMixin extends Container {

    @Redirect(
        method = "transferStackInSlot",
        remap = true,
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/entity/player/InventoryPlayer;getStackInSlot(I)Lnet/minecraft/item/ItemStack;"))
    private ItemStack dynamicinventory$filterSource(InventoryPlayer inventory, int id) {
        Slot slot = getSlot(id);
        return slot.inventory == inventory ? slot.getStack() : null;
    }
}
