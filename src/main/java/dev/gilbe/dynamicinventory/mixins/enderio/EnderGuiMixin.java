package dev.gilbe.dynamicinventory.mixins.enderio;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.gilbe.dynamicinventory.api.client.NativeUiAnchors;
import dev.gilbe.dynamicinventory.client.ContainerPanelAccess;
import dev.gilbe.dynamicinventory.client.NativeInventoryPanel;

/** Anchor EnderCore's real ghost controls, tooltips and text fields to the machine section. */
@Pseudo
@Mixin(targets = "com.enderio.core.client.gui.GuiContainerBase", remap = false)
public abstract class EnderGuiMixin implements NativeUiAnchors {

    @Shadow
    protected List<?> textFields;

    @Inject(method = "getGuiLeft", at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$left(CallbackInfoReturnable<Integer> cir) {
        NativeInventoryPanel panel = ((ContainerPanelAccess) this).dynamicinventory$panel();
        if (panel != null) cir.setReturnValue(panel.nativeHeaderLeft());
    }

    @Inject(method = "getGuiTop", at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$top(CallbackInfoReturnable<Integer> cir) {
        NativeInventoryPanel panel = ((ContainerPanelAccess) this).dynamicinventory$panel();
        if (panel != null) cir.setReturnValue(panel.nativeHeaderTop());
    }

    @Override
    public int[] captureHeaderOrigins() {
        return new int[0];
    }

    @Override
    public void positionHeaderOrigins(int[] ignored) {
        for (Object field : textFields) {
            try {
                field.getClass()
                    .getMethod("init", Class.forName("com.enderio.core.api.client.gui.IGuiScreen"))
                    .invoke(field, this);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Cannot position EnderCore text field", failure);
            }
        }
    }
}
