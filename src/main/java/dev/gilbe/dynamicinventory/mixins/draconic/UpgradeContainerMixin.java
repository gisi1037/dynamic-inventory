package dev.gilbe.dynamicinventory.mixins.draconic;

import net.minecraft.inventory.Container;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.gilbe.dynamicinventory.api.InventoryUi;

@Mixin(targets = "com.brandon3055.draconicevolution.common.container.ContainerUpgradeModifier", remap = false)
public abstract class UpgradeContainerMixin extends Container {

    @Inject(method = "updateSlotState", at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$keepPlayerSlots(CallbackInfo ci) {
        // The expanded inventory has its own section, outside the controls that replace the old grid.
        if (InventoryUi.getBinding(this) != null) ci.cancel();
    }
}
