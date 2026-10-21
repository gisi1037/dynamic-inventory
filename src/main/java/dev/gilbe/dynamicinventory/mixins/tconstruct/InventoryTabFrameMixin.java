package dev.gilbe.dynamicinventory.mixins.tconstruct;

import net.minecraft.client.gui.GuiButton;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

import dev.gilbe.dynamicinventory.compat.InventoryTabsCompatibility;

/** TiC's registry also supplies the Galacticraft tab. Share the frame instead of two creative texture slices. */
@Pseudo
@Mixin(targets = "tconstruct.client.tabs.AbstractTab", remap = false)
public abstract class InventoryTabFrameMixin extends GuiButton {

    private InventoryTabFrameMixin() {
        super(0, 0, 0, "");
    }

    @Redirect(
        method = "drawButton(Lnet/minecraft/client/Minecraft;Lnet/minecraft/item/ItemStack;Z)V",
        at = @At(value = "INVOKE", target = "Ltconstruct/client/tabs/AbstractTab;func_73729_b(IIIIII)V"),
        require = 1,
        allow = 1)
    private void dynamicinventory$sharedFrame(@Coerce Object tab, int x, int y, int u, int v, int width, int height) {
        if (!InventoryTabsCompatibility.drawTabFrame(this, x, y, width, height))
            drawTexturedModalRect(x, y, u, v, width, height);
    }
}
