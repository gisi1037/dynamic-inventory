package dev.gilbe.dynamicinventory.mixins.enderio;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.gilbe.dynamicinventory.api.InventoryTransfers;
import dev.gilbe.dynamicinventory.container.ContainerInventoryRegion;

/** The base host subtracts raw inventory length (including offhand) from the slot count. Use real ownership. */
@Pseudo
@Mixin(targets = "com.enderio.core.common.ContainerEnder", remap = false)
public abstract class BaseTransferMixin extends Container {

    @Inject(method = { "transferStackInSlot", "func_82846_b" }, at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$transfer(EntityPlayer player, int id, CallbackInfoReturnable<ItemStack> cir) {
        ContainerInventoryRegion region = ContainerInventoryRegion.of(this);
        if (region == null || id < 0 || id >= inventorySlots.size()) return;
        cir.setReturnValue(
            (region.logical(id) >= 0 ? InventoryTransfers.toNativeSlots() : InventoryTransfers.toPlayerInventory())
                .transfer(this, player, id, region));
    }
}
