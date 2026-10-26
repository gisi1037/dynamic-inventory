package dev.gilbe.dynamicinventory.mixins.forestry;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.gilbe.dynamicinventory.api.InventoryUi;
import dev.gilbe.dynamicinventory.api.PlayerInventoryBinding;
import dev.gilbe.dynamicinventory.compat.ForestryCompatibility;

/** Gate every reviewed overriding sync owner, before it advances custom packet caches. */
@Pseudo
@Mixin(
    targets = { "forestry.core.gui.ContainerTile", "forestry.core.gui.ContainerLiquidTanks",
        "forestry.core.gui.ContainerLiquidTanksSocketed", "forestry.apiculture.gui.ContainerAlveary",
        "forestry.apiculture.gui.ContainerBeeHousing", "forestry.energy.gui.ContainerEngineBiogas",
        "forestry.energy.gui.ContainerEngineElectric", "forestry.energy.gui.ContainerEnginePeat",
        "forestry.energy.gui.ContainerGenerator", "forestry.factory.gui.ContainerCarpenter",
        "forestry.factory.gui.ContainerFabricator", "forestry.factory.gui.ContainerFermenter",
        "forestry.factory.gui.ContainerMoistener", "forestry.factory.gui.ContainerRaintank",
        "forestry.factory.gui.ContainerWorktable", "forestry.farming.gui.ContainerFarm" },
    remap = false)
public abstract class ForestrySyncMixin extends Container {

    // The reviewed production jar uses SRG; development fixtures use MCP. Each target declares one of these.
    @Inject(method = { "detectAndSendChanges()V", "func_75142_b()V" }, at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$pending(CallbackInfo ci) {
        PlayerInventoryBinding binding = InventoryUi.getBinding(this);
        if (binding != null && ForestryCompatibility.handles(this)
            && binding.getPlayerInventory().player instanceof EntityPlayerMP player
            && player.openContainer == this
            && !InventoryUi.isOpeningReady(player)) ci.cancel();
    }
}
