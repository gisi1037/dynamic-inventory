package dev.gilbe.dynamicinventory.mixins.draconic;

import net.minecraft.client.gui.Gui;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.gilbe.dynamicinventory.client.ContainerPanelAccess;
import dev.gilbe.dynamicinventory.client.InventoryGuiTheme;

/** Remove obsolete player-slot art where the native sheet must extend down to machine controls. */
@Mixin(
    targets = { "com.brandon3055.draconicevolution.client.gui.GUIDraconiumChest",
        "com.brandon3055.draconicevolution.client.gui.componentguis.GUIReactor",
        "com.brandon3055.draconicevolution.client.gui.componentguis.GUIToolInventory" },
    remap = false)
public abstract class EmbeddedInventoryArtworkMixin {

    @Inject(method = "drawGuiContainerBackgroundLayer", at = @At("RETURN"), remap = true)
    private void dynamicinventory$clearOldGrid(float ticks, int x, int y, CallbackInfo ci) {
        ContainerPanelAccess access = (ContainerPanelAccess) this;
        if (access.dynamicinventory$panel() == null) return;
        int left = access.dynamicinventory$left();
        int top = access.dynamicinventory$top();
        int fill = InventoryGuiTheme.get().panelFill;
        switch (getClass().getSimpleName()) {
            case "GUIDraconiumChest" -> Gui.drawRect(left + 160, top + 178, left + 322, top + 252, fill);
            case "GUIReactor" -> Gui.drawRect(left + 43, top + 139, left + 205, top + 215, fill);
            case "GUIToolInventory" -> Gui.drawRect(left + 7, top + 44, left + 169, top + 120, fill);
        }
    }
}
