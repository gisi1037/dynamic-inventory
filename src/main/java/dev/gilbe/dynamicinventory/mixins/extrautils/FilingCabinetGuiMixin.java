package dev.gilbe.dynamicinventory.mixins.extrautils;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.gilbe.dynamicinventory.client.ContainerPanelAccess;
import dev.gilbe.dynamicinventory.client.NativeInventoryPanel;

@Pseudo
@Mixin(targets = "com.rwtema.extrautils.gui.GuiFilingCabinet", remap = false)
public abstract class FilingCabinetGuiMixin {

    @Inject(method = "sortItems", at = @At("RETURN"))
    private void dynamicinventory$slots(CallbackInfo ci) {
        NativeInventoryPanel panel = ((ContainerPanelAccess) this).dynamicinventory$panel();
        if (panel != null) panel.refreshNativeMachinePositions();
    }
}
