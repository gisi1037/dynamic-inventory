package dev.gilbe.dynamicinventory.mixins.appliedenergistics2;

import net.minecraft.entity.player.EntityPlayerMP;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import appeng.container.AEBaseContainer;
import dev.gilbe.dynamicinventory.api.InventoryUi;

@Mixin(
    targets = { "com.glodblock.github.client.gui.container.ContainerFluidAutoFiller",
        "com.glodblock.github.client.gui.container.ContainerFluidInterface",
        "com.glodblock.github.client.gui.container.ContainerIngredientBuffer",
        "com.glodblock.github.client.gui.container.ContainerLevelMaintainer",
        "com.glodblock.github.client.gui.container.ContainerLevelTerminal",
        "com.glodblock.github.client.gui.container.ContainerMagnetFilter",
        "com.glodblock.github.client.gui.container.ContainerSuperStockReplenisher" },
    remap = false)
public abstract class FluidContainerSyncMixin {

    @Inject(method = "detectAndSendChanges", remap = true, at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$opening(CallbackInfo ci) {
        AEBaseContainer container = (AEBaseContainer) (Object) this;
        if (InventoryUi.findAdapter(container) != null
            && container.getPlayerInv().player instanceof EntityPlayerMP player
            && player.openContainer == container
            && !InventoryUi.isOpeningReady(player)) ci.cancel();
    }
}
