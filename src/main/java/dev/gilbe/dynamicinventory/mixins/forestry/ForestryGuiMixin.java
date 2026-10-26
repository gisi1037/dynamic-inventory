package dev.gilbe.dynamicinventory.mixins.forestry;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.resources.I18n;

import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.gilbe.dynamicinventory.client.ContainerPanelAccess;
import dev.gilbe.dynamicinventory.client.NativeInventoryPanel;
import dev.gilbe.dynamicinventory.compat.ForestryLedgerAccess;

/** Forestry widgets, ledgers and tooltips use these native-section getters outside the draw layers. */
@Pseudo
@Mixin(targets = "forestry.core.gui.GuiForestry", remap = false)
public abstract class ForestryGuiMixin {

    @Shadow
    public abstract FontRenderer getFontRenderer();

    @Inject(method = { "drawGuiContainerForegroundLayer(II)V", "func_146979_b(II)V" }, at = @At("RETURN"))
    private void dynamicinventory$inventoryCaption(int mouseX, int mouseY, CallbackInfo ci) {
        NativeInventoryPanel panel = ((ContainerPanelAccess) this).dynamicinventory$panel();
        // Forestry has no native player caption to relocate. The shared label hook positions this one.
        if (panel != null)
            getFontRenderer().drawString(I18n.format("container.inventory"), 8, panel.nativeUiHeight() - 94, 0x404040);
    }

    @Redirect(
        method = "drawWidgets",
        at = @At(value = "INVOKE", target = "Lforestry/core/gui/ledgers/LedgerManager;drawLedgers()V"))
    private void dynamicinventory$ledgers(@Coerce Object manager) {
        if (((ContainerPanelAccess) this).dynamicinventory$panel() == null) {
            ((ForestryLedgerAccess) manager).drawLedgers();
            return;
        }
        // The native sheet is cropped to remove the old player grid. Side ledgers live outside that sheet.
        GL11.glPushAttrib(GL11.GL_SCISSOR_BIT);
        try {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            ((ForestryLedgerAccess) manager).drawLedgers();
        } finally {
            GL11.glPopAttrib();
        }
    }

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

    @Inject(method = "getSizeX", at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$width(CallbackInfoReturnable<Integer> cir) {
        NativeInventoryPanel panel = ((ContainerPanelAccess) this).dynamicinventory$panel();
        if (panel != null) cir.setReturnValue(panel.nativeUiWidth());
    }

    @Inject(method = "getSizeY", at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$height(CallbackInfoReturnable<Integer> cir) {
        NativeInventoryPanel panel = ((ContainerPanelAccess) this).dynamicinventory$panel();
        if (panel != null) cir.setReturnValue(panel.nativeUiHeight());
    }
}
