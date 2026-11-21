package dev.gilbe.dynamicinventory.mixins.appliedenergistics2;

import net.minecraft.client.Minecraft;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.glodblock.github.client.gui.base.FCBaseMEGui;

import dev.gilbe.dynamicinventory.api.InventoryUi;

/** Native Small/Tall changes must rebuild the decorator through the normal Forge initialization events. */
@Mixin(targets = "com.glodblock.github.client.gui.base.FCBaseMEGui", remap = false)
public abstract class FluidGuiResizeMixin {

    @Redirect(
        method = "mouseClicked",
        remap = true,
        at = @At(
            value = "INVOKE",
            target = "Lcom/glodblock/github/client/gui/base/FCBaseMEGui;initGui()V",
            remap = true))
    private void dynamicinventory$rebuild(FCBaseMEGui gui) {
        if (InventoryUi.findAdapter(gui.inventorySlots) == null) gui.initGui();
        else gui.setWorldAndResolution(Minecraft.getMinecraft(), gui.width, gui.height);
    }
}
