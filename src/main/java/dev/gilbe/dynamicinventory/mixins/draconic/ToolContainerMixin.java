package dev.gilbe.dynamicinventory.mixins.draconic;

import net.minecraft.inventory.Container;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.gilbe.dynamicinventory.api.InventoryUi;
import dev.gilbe.dynamicinventory.container.ContainerInventoryRegion;
import dev.gilbe.dynamicinventory.container.ExpandedContainerAccess;

@Mixin(targets = "com.brandon3055.draconicevolution.common.container.ContainerAdvTool", remap = false)
public abstract class ToolContainerMixin extends Container {

    @Inject(method = "setSlotsActive", at = @At("RETURN"))
    private void dynamicinventory$rebind(boolean active, CallbackInfo ci) {
        // The native mode packet rebuilds both slot lists on each peer. Append the player slots
        // again after the new native slots, preserving their order and the tool inventory callbacks.
        ContainerInventoryRegion binding = ContainerInventoryRegion.of(this);
        if (binding == null) return;
        ((ExpandedContainerAccess) this).dynamicinventory$region(null);
        InventoryUi.attachRegistered(this, binding.inventory);
    }
}
