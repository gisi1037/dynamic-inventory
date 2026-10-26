package dev.gilbe.dynamicinventory.mixins.nei;

import net.minecraft.client.gui.inventory.GuiContainer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import codechicken.lib.vec.Rectangle4i;
import dev.gilbe.dynamicinventory.client.DetachedChestPanel;
import dev.gilbe.dynamicinventory.client.InventoryPanelLayout;
import dev.gilbe.dynamicinventory.client.InventoryPanelLayout.Rect;

/** Resize the one native bookmark grid, preserving recipes, gutters, pagination and input ownership. */
@Pseudo
@Mixin(targets = "codechicken.nei.BookmarkPanel", remap = false)
public abstract class NeiBookmarkBoundsMixin {

    @Inject(method = "calculateBounds", at = @At("RETURN"), cancellable = true)
    private void dynamicinventory$fitBookmarks(GuiContainer gui, CallbackInfoReturnable<Rectangle4i> cir) {
        DetachedChestPanel panel = DetachedChestPanel.of(gui);
        if (panel == null || !panel.detached()) return;
        Rectangle4i original = cir.getReturnValue();
        Rect fitted = InventoryPanelLayout
            .freeRegion(new Rect(original.x, original.y, original.w, original.h), panel.occupiedBounds(), 40, 72);
        if (fitted == null) {
            java.util.List<Rect> obstacles = new java.util.ArrayList<>(panel.occupiedBounds());
            fitted = InventoryPanelLayout.freeRegion(
                new Rect(original.x, original.y, Math.max(original.w, gui.width / 2 - original.x - 2), original.h),
                obstacles,
                40,
                72);
        }
        if (fitted != null) cir.setReturnValue(new Rectangle4i(fitted.x, fitted.y, fitted.width, fitted.height));
    }
}
