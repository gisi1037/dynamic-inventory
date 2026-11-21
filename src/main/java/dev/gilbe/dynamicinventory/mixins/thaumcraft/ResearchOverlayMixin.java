package dev.gilbe.dynamicinventory.mixins.thaumcraft;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import dev.gilbe.dynamicinventory.client.ContainerPanelAccess;
import dev.gilbe.dynamicinventory.client.NativeInventoryPanel;

/** Research tooltips and hex overlays are drawn after the native background scope has ended. */
@Pseudo
@Mixin(targets = "thaumcraft.client.gui.GuiResearchTable", remap = false)
public abstract class ResearchOverlayMixin {

    @ModifyVariable(method = "func_73863_a(IIF)V", at = @At(value = "STORE", ordinal = 0), index = 4)
    private int dynamicinventory$guiLeft(int left) {
        return dynamicinventory$overlayLeft(left);
    }

    @ModifyVariable(method = "func_73863_a(IIF)V", at = @At(value = "STORE", ordinal = 0), index = 5)
    private int dynamicinventory$guiTop(int top) {
        return dynamicinventory$overlayTop(top);
    }

    @ModifyVariable(method = "func_73863_a(IIF)V", at = @At(value = "STORE", ordinal = 0), index = 6)
    private int dynamicinventory$overlayLeft(int left) {
        NativeInventoryPanel panel = ((ContainerPanelAccess) this).dynamicinventory$panel();
        return panel == null ? left : panel.nativeHeaderLeft();
    }

    @ModifyVariable(method = "func_73863_a(IIF)V", at = @At(value = "STORE", ordinal = 0), index = 7)
    private int dynamicinventory$overlayTop(int top) {
        NativeInventoryPanel panel = ((ContainerPanelAccess) this).dynamicinventory$panel();
        return panel == null ? top : panel.nativeHeaderTop();
    }
}
