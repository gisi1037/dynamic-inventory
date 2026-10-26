package dev.gilbe.dynamicinventory.mixins.nei;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import codechicken.nei.NEIClientConfig;

/** Existing NEI masks still own pagination and input; floating obstacles require a complete scan. */
@Pseudo
@Mixin(targets = "codechicken.nei.ItemsGrid", remap = false)
public abstract class NeiPanelOverlapMixin {

    @Unique
    private GuiContainer dynamicinventory$screen;
    @Unique
    private long dynamicinventory$generation;

    @Shadow
    protected abstract void updateGuiOverlapSlots(GuiContainer gui);

    @Shadow
    protected abstract void onGridChanged();

    private static long dynamicinventory$revision(GuiScreen gui) {
        dev.gilbe.dynamicinventory.client.DetachedChestPanel panel = dev.gilbe.dynamicinventory.client.DetachedChestPanel
            .of(gui);
        return panel == null ? 0 : panel.revision();
    }

    @Redirect(
        method = { "updateGuiOverlapSlots", "checkGuiOverlap" },
        at = @At(value = "INVOKE", target = "Lcodechicken/nei/NEIClientConfig;optimizeGuiOverlapComputation()Z"))
    private boolean dynamicinventory$scanInterior() {
        return dynamicinventory$revision(Minecraft.getMinecraft().currentScreen) == 0
            && NEIClientConfig.optimizeGuiOverlapComputation();
    }

    @Inject(method = "refresh", at = @At("HEAD"))
    private void dynamicinventory$invalidate(GuiContainer gui, CallbackInfo ci) {
        long generation = dynamicinventory$revision(gui);
        if (generation != dynamicinventory$generation || generation != 0 && gui != dynamicinventory$screen) {
            updateGuiOverlapSlots(gui);
            onGridChanged();
        }
        dynamicinventory$generation = generation;
        dynamicinventory$screen = gui;
    }
}
