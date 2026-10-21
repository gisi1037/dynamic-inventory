package dev.gilbe.dynamicinventory.mixins.minecraft;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMerchant;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import dev.gilbe.dynamicinventory.api.InventoryUi;
import dev.gilbe.dynamicinventory.client.ContainerPanelAccess;
import dev.gilbe.dynamicinventory.client.NativeInventoryPanel;

/** Trade previews are drawn after the base container has restored its outer bounds. */
@Mixin(GuiMerchant.class)
public abstract class VanillaMerchantGuiMixin {

    @Inject(method = "actionPerformed", at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$waitForOpening(GuiButton button, CallbackInfo ci) {
        if (((ContainerPanelAccess) this).dynamicinventory$panel() != null
            && !InventoryUi.isOpeningReady(Minecraft.getMinecraft().thePlayer)) ci.cancel();
    }

    // Vanilla drawScreen stores the recipe list in local 4 and its native origin in locals 5/6.
    // Change only these preview coordinates; the base container still draws and hits real slots normally.
    @ModifyVariable(method = "drawScreen", at = @At("STORE"), index = 5, require = 1, allow = 1)
    private int dynamicinventory$previewLeft(int left) {
        NativeInventoryPanel panel = ((ContainerPanelAccess) this).dynamicinventory$panel();
        return panel == null ? left : panel.nativeHeaderLeft();
    }

    @ModifyVariable(method = "drawScreen", at = @At("STORE"), index = 6, require = 1, allow = 1)
    private int dynamicinventory$previewTop(int top) {
        NativeInventoryPanel panel = ((ContainerPanelAccess) this).dynamicinventory$panel();
        return panel == null ? top : panel.nativeHeaderTop();
    }

    @ModifyArgs(
        method = "drawScreen",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiMerchant;func_146978_c(IIIIII)Z"),
        require = 3,
        allow = 3)
    private void dynamicinventory$previewHover(Args args) {
        NativeInventoryPanel panel = ((ContainerPanelAccess) this).dynamicinventory$panel();
        if (panel != null) {
            // Preserve vanilla's hover padding while compensating for its outer guiLeft/guiTop subtraction.
            args.set(4, (Integer) args.get(4) - panel.headerOffsetX());
            args.set(5, (Integer) args.get(5) - panel.headerOffsetY());
        }
    }
}
