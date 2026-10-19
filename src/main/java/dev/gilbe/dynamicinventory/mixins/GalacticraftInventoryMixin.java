package dev.gilbe.dynamicinventory.mixins;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.gilbe.dynamicinventory.api.InventoryUi;
import dev.gilbe.dynamicinventory.container.ContainerInventoryRegion;
import dev.gilbe.dynamicinventory.container.PlayerInventoryTransfers;
import dev.gilbe.dynamicinventory.inventory.InventoryLayout;

@Mixin(targets = "micdoodle8.mods.galacticraft.core.inventory.ContainerExtendedInventory", remap = false)
public abstract class GalacticraftInventoryMixin extends Container {

    @ModifyArg(
        method = "<init>",
        at = @At(
            value = "INVOKE",
            target = "Lmicdoodle8/mods/galacticraft/core/inventory/SlotArmorGC;<init>(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/inventory/IInventory;IIII)V"),
        index = 2)
    private int dynamicinventory$armor(int oldIndex) {
        return InventoryLayout.rawMainSlots() + oldIndex - 36;
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void dynamicinventory$append(CallbackInfo ci) {
        InventoryUi.attachRegistered(this);
    }

    @Inject(method = "transferStackInSlot", at = @At("HEAD"), cancellable = true, remap = true)
    private void dynamicinventory$extra(EntityPlayer player, int id, CallbackInfoReturnable<ItemStack> cir) {
        ContainerInventoryRegion region = ContainerInventoryRegion.of(this);
        // Unsupported or malformed layouts should use the container's original transfer logic.
        if (region == null) return;
        if (id < region.nativeSlotCount || id >= inventorySlots.size()) return;
        int[] equipment = new int[14];
        for (int i = 0; i < equipment.length; i++) equipment[i] = 36 + i;
        ItemStack moved = PlayerInventoryTransfers.transferTo(this, player, id, equipment);
        if (moved == null) moved = PlayerInventoryTransfers.transferTo(this, player, id, region.hotbar());
        cir.setReturnValue(moved);
    }
}
