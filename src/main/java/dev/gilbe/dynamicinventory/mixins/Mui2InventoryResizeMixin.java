package dev.gilbe.dynamicinventory.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.cleanroommc.modularui.screen.ModularScreen;

import dev.gilbe.dynamicinventory.compat.mui2.ResponsiveInventoryPanel;

@Mixin(value = ModularScreen.class, remap = false)
public abstract class Mui2InventoryResizeMixin {

    @Unique
    private final ResponsiveInventoryPanel dynamicinventory$inventory = new ResponsiveInventoryPanel();
    @Unique
    private boolean dynamicinventory$resizing;

    @Inject(method = "onResize", at = @At("RETURN"))
    private void dynamicinventory$fit(int width, int height, CallbackInfo ci) {
        dynamicinventory$fit(width, height);
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
