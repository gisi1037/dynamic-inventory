package dev.gilbe.dynamicinventory.mixins.minecraft;

import net.minecraft.entity.IMerchant;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerMerchant;
import net.minecraft.inventory.ICrafting;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.gilbe.dynamicinventory.api.InventoryUi;

/** Vanilla trading registers its listener before sending the open-window packet, unlike its other screens. */
@Mixin(EntityPlayerMP.class)
public abstract class VanillaMerchantOpeningMixin {

    @Redirect(
        method = "displayGUIMerchant",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/inventory/Container;addCraftingToCrafters(Lnet/minecraft/inventory/ICrafting;)V"))
    private void dynamicinventory$deferListener(Container container, ICrafting listener) {
        // A registered host must exist on the client before its opening agreement or contents arrive.
        // Leave unregistered hosts on the original path; no general packet buffering is needed here.
        if (InventoryUi.findAdapter(container) == null) container.addCraftingToCrafters(listener);
    }

    @Inject(method = "displayGUIMerchant", at = @At("RETURN"))
    private void dynamicinventory$registerAfterOpen(IMerchant merchant, String title, CallbackInfo ci) {
        EntityPlayerMP player = (EntityPlayerMP) (Object) this;
        Container container = player.openContainer;
        if (container.getClass() == ContainerMerchant.class && InventoryUi.findAdapter(container) != null)
            container.addCraftingToCrafters(player);
    }
}
