package dev.gilbe.dynamicinventory.mixins.modularui2;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.cleanroommc.modularui.screen.ModularScreen;

import dev.gilbe.dynamicinventory.compat.mui2.Mui2DetachedPanels;
import dev.gilbe.dynamicinventory.compat.mui2.Mui2PanelAccess;
import dev.gilbe.dynamicinventory.compat.mui2.ResponsiveInventoryPanel;

@Mixin(value = ModularScreen.class, remap = false)
public abstract class Mui2InventoryResizeMixin implements Mui2PanelAccess {

    @Unique
    private final ResponsiveInventoryPanel dynamicinventory$inventory = new ResponsiveInventoryPanel();
    @Unique
    private boolean dynamicinventory$resizing;

    @Unique
    private final Mui2DetachedPanels dynamicinventory$panels = new Mui2DetachedPanels();

    @Override
    public Mui2DetachedPanels dynamicinventory$detachedPanels() {
        return dynamicinventory$panels;
    }

    @Inject(method = { "onResize", "onUpdate" }, at = @At("HEAD"))
    private void dynamicinventory$restoreLayout(CallbackInfo ci) {
        dynamicinventory$panels.beforeLayout();
    }

    @Inject(method = "onResize", at = @At("RETURN"))
    private void dynamicinventory$fit(int width, int height, CallbackInfo ci) {
        dynamicinventory$fit(width, height);
        dynamicinventory$panels.update((ModularScreen) (Object) this);
    }

    @Inject(method = "onUpdate", at = @At("RETURN"))
    private void dynamicinventory$refreshBranches(CallbackInfo ci) {
        ModularScreen screen = (ModularScreen) (Object) this;
        // Secondary-panel admission and DynamicSyncedWidget rebuilds bypass onResize.
        // Check at the native 20 Hz update rate and relayout only when inventory geometry changes.
        dynamicinventory$fit(
            screen.getContext()
                .getScreenArea().width,
            screen.getContext()
                .getScreenArea().height);
        dynamicinventory$panels.update(screen);
    }

    @Inject(
        method = "onFrameUpdate",
        at = @At(
            value = "INVOKE",
            target = "Lcom/cleanroommc/modularui/screen/viewport/ModularGuiContext;onFrameUpdate()V"))
    private void dynamicinventory$projectBeforeHover(CallbackInfo ci) {
        dynamicinventory$panels.update((ModularScreen) (Object) this);
    }

    @Inject(method = "drawScreen", at = @At("HEAD"))
    private void dynamicinventory$drawPanels(CallbackInfo ci) {
        dynamicinventory$panels.drawBackground((ModularScreen) (Object) this);
    }

    @Inject(method = "drawForeground", at = @At("RETURN"))
    private void dynamicinventory$panelControls(CallbackInfo ci) {
        dynamicinventory$panels.drawForeground((ModularScreen) (Object) this);
    }

    @Inject(method = "onClose", at = @At("HEAD"))
    private void dynamicinventory$releasePanels(CallbackInfo ci) {
        dynamicinventory$panels.close();
    }

    @Unique
    private void dynamicinventory$fit(int width, int height) {
        if (dynamicinventory$resizing || width <= 0 || height <= 0) return;
        dynamicinventory$resizing = true;
        try {
            ModularScreen screen = (ModularScreen) (Object) this;
            if (dynamicinventory$inventory.resize(screen, height)) screen.onResize(width, height);
        } finally {
            dynamicinventory$resizing = false;
        }
    }
}
