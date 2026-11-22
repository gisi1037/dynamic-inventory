package dev.gilbe.dynamicinventory.mixins.enderio;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.gilbe.dynamicinventory.client.ContainerPanelAccess;
import dev.gilbe.dynamicinventory.client.NativeInventoryPanel;

@Pseudo
@Mixin(targets = "crazypants.enderio.machine.tank.GuiTank", remap = false)
public abstract class TankGuiMixin {

    @Inject(method = { "updateScreen", "func_73876_c" }, at = @At("RETURN"))
    private void dynamicinventory$voidSlot(CallbackInfo ci) {
        NativeInventoryPanel panel = ((ContainerPanelAccess) this).dynamicinventory$panel();
        if (panel != null) {
            panel.refreshNativeMachinePositions(2, 3);
            panel.captureNewNativeButtons();
        }
    }
}
