package dev.gilbe.dynamicinventory.mixins.modularui;

import java.util.ArrayDeque;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.gtnewhorizons.modularui.api.math.Size;
import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.api.widget.IWidgetParent;
import com.gtnewhorizons.modularui.api.widget.Widget;

import dev.gilbe.dynamicinventory.client.InventoryViewSettings;
import dev.gilbe.dynamicinventory.client.InventoryViewportLayout;
import dev.gilbe.dynamicinventory.compat.mui1.InventoryViewport;

@Mixin(value = ModularWindow.class, remap = false)
public abstract class Mui1InventoryResizeMixin {

    @Shadow
    private Size size;
    @Unique
    private int dynamicinventory$nativeHeight = -1;

    @Inject(method = "onResize", at = @At("HEAD"))
    private void dynamicinventory$fit(Size screen, CallbackInfo ci) {
        ModularWindow window = (ModularWindow) (Object) this;
        ArrayDeque<Widget> pending = new ArrayDeque<>(window.getChildren());
        while (!pending.isEmpty()) {
            Widget widget = pending.removeFirst();
            if (widget instanceof InventoryViewport) {
                if (dynamicinventory$nativeHeight < 0) dynamicinventory$nativeHeight = size.height;
                int rows = InventoryViewportLayout.rows(
                    ((InventoryViewport) widget).getStorageSlots(),
                    dynamicinventory$nativeHeight,
                    screen.height,
                    InventoryViewSettings.expandMachineInventory);
                size = new Size(size.width, dynamicinventory$nativeHeight + (rows - 3) * 18);
                InventoryViewport viewport = (InventoryViewport) widget;
                viewport.setVerticalScrollOffset(0);
                viewport.setSize(168, rows * 18);
                IWidgetParent group = viewport.getParent();
                int hotbar = 0;
                for (Widget child : group.getChildren())
                    if (child != viewport) child.setPos(hotbar++ * 18, rows * 18 + 4);
                ((Widget) group).setSize(168, rows * 18 + 22);
                break;
            }
            if (widget instanceof IWidgetParent) pending.addAll(((IWidgetParent) widget).getChildren());
        }
    }
}
