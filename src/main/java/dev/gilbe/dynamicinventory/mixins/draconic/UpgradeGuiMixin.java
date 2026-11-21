package dev.gilbe.dynamicinventory.mixins.draconic;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.brandon3055.draconicevolution.client.gui.GUIUpgradeModifier;

import dev.gilbe.dynamicinventory.client.ContainerPanelAccess;

@Mixin(targets = "com.brandon3055.draconicevolution.client.gui.GUIUpgradeModifier", remap = false)
public abstract class UpgradeGuiMixin {

    @Redirect(
        method = "drawSlots",
        at = @At(
            value = "INVOKE",
            target = "Lcom/brandon3055/draconicevolution/client/gui/GUIUpgradeModifier;drawTexturedModalRect(IIIIII)V",
            remap = true))
    private void dynamicinventory$hideOldGrid(GUIUpgradeModifier gui, int x, int y, int u, int v, int width,
        int height) {
        ContainerPanelAccess access = (ContainerPanelAccess) this;
        if (access.dynamicinventory$panel() == null || y == access.dynamicinventory$top() + 47)
            gui.drawTexturedModalRect(x, y, u, v, width, height);
    }
}
