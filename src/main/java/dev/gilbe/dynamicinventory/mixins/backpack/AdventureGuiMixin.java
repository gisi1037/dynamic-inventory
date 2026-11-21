package dev.gilbe.dynamicinventory.mixins.backpack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.gilbe.dynamicinventory.client.ContainerPanelAccess;
import dev.gilbe.dynamicinventory.client.NativeInventoryPanel;

@Pseudo
@Mixin(targets = "com.darkona.adventurebackpack.client.gui.GuiWithTanks", remap = false)
public abstract class AdventureGuiMixin {

    @Inject(method = "getLeft", at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$nativeLeft(CallbackInfoReturnable<Integer> cir) {
        NativeInventoryPanel panel = ((ContainerPanelAccess) this).dynamicinventory$panel();
        if (panel != null) cir.setReturnValue(panel.nativeHeaderLeft());
    }

    @Inject(method = "getTop", at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$nativeTop(CallbackInfoReturnable<Integer> cir) {
        NativeInventoryPanel panel = ((ContainerPanelAccess) this).dynamicinventory$panel();
        if (panel != null) cir.setReturnValue(panel.nativeHeaderTop());
    }
}
