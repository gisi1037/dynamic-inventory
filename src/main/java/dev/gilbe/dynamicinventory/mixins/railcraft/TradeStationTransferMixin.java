package dev.gilbe.dynamicinventory.mixins.railcraft;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.gilbe.dynamicinventory.api.InventoryUi;
import dev.gilbe.dynamicinventory.compat.RailcraftCompatibility;

/** Railcraft's transfer assumes the final 36 IDs are player slots. Preserve all native IDs and phantom handling. */
@Pseudo
@Mixin(targets = "mods.railcraft.common.gui.containers.RailcraftContainer", remap = false)
public abstract class TradeStationTransferMixin extends Container {

    @Inject(method = { "func_82846_b", "transferStackInSlot" }, at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$tradeStation(EntityPlayer player, int id, CallbackInfoReturnable<ItemStack> result) {
        if (!RailcraftCompatibility.tradeStation(this)) return;
        var binding = InventoryUi.getBinding(this);
        if (binding != null) result.setReturnValue(RailcraftCompatibility.transfer(this, player, id, binding));
    }
}
