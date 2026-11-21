package dev.gilbe.dynamicinventory.mixins.thaumcraft;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import dev.gilbe.dynamicinventory.client.ContainerPanelAccess;
import dev.gilbe.dynamicinventory.client.NativeInventoryPanel;

@Pseudo
@Mixin(targets = "thaumcraft.client.gui.GuiFocalManipulator", remap = false)
public abstract class FocalOverlayMixin {

    @ModifyVariable(method = "func_73863_a(IIF)V", at = @At(value = "STORE", ordinal = 0), index = 4)
    private int dynamicinventory$tooltipLeft(int left) {
        NativeInventoryPanel panel = ((ContainerPanelAccess) this).dynamicinventory$panel();
        return panel == null ? left : panel.nativeHeaderLeft();
    }

    @ModifyVariable(method = "func_73863_a(IIF)V", at = @At(value = "STORE", ordinal = 0), index = 5)
    private int dynamicinventory$tooltipTop(int top) {
        NativeInventoryPanel panel = ((ContainerPanelAccess) this).dynamicinventory$panel();
        return panel == null ? top : panel.nativeHeaderTop();
    }
}
