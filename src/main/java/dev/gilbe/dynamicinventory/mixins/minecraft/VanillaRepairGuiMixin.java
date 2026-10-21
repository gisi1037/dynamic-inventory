package dev.gilbe.dynamicinventory.mixins.minecraft;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiRepair;
import net.minecraft.client.gui.GuiTextField;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.gilbe.dynamicinventory.api.InventoryUi;
import dev.gilbe.dynamicinventory.api.client.NativeUiAnchors;
import dev.gilbe.dynamicinventory.client.ContainerPanelAccess;

/** Move the existing name field without resetting its text, selection or keyboard focus. */
@Mixin(GuiRepair.class)
public abstract class VanillaRepairGuiMixin implements NativeUiAnchors {

    @Shadow
    private GuiTextField field_147091_w;

    @Inject(method = "func_147090_g", at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$waitForOpening(CallbackInfo ci) {
        // Passive field text synchronization still runs; only local output prediction and rename packets wait.
        if (((ContainerPanelAccess) this).dynamicinventory$panel() != null
            && !InventoryUi.isOpeningReady(Minecraft.getMinecraft().thePlayer)) ci.cancel();
    }

    @Override
    public int[] captureHeaderOrigins() {
        return field_147091_w == null ? new int[0] : new int[] { field_147091_w.xPosition, field_147091_w.yPosition };
    }

    @Override
    public void positionHeaderOrigins(int[] origins) {
        if (field_147091_w != null && origins.length == 2) {
            field_147091_w.xPosition = origins[0];
            field_147091_w.yPosition = origins[1];
        }
    }
}
