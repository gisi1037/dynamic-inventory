package dev.gilbe.dynamicinventory.mixins.minecraft;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiEnchantment;
import net.minecraft.client.gui.ScaledResolution;

import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

import dev.gilbe.dynamicinventory.client.ContainerPanelAccess;
import dev.gilbe.dynamicinventory.client.NativeInventoryPanel;

/** Enchantment options and the physical book viewport must follow the same native header. */
@Mixin(GuiEnchantment.class)
public abstract class VanillaEnchantmentGuiMixin {

    // These two locals belong to the custom option hit boxes, after super.mouseClicked has handled real slots.
    @ModifyVariable(method = "mouseClicked", at = @At("STORE"), index = 4, require = 1, allow = 1)
    private int dynamicinventory$optionLeft(int left) {
        NativeInventoryPanel panel = ((ContainerPanelAccess) this).dynamicinventory$panel();
        return panel == null ? left : panel.nativeHeaderLeft();
    }

    @ModifyVariable(method = "mouseClicked", at = @At("STORE"), index = 5, require = 1, allow = 1)
    private int dynamicinventory$optionTop(int top) {
        NativeInventoryPanel panel = ((ContainerPanelAccess) this).dynamicinventory$panel();
        return panel == null ? top : panel.nativeHeaderTop();
    }

    @Redirect(
        method = "drawGuiContainerBackgroundLayer",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glViewport(IIII)V", ordinal = 0, remap = false),
        require = 1,
        allow = 1)
    private void dynamicinventory$bookViewport(int x, int y, int width, int height) {
        NativeInventoryPanel panel = ((ContainerPanelAccess) this).dynamicinventory$panel();
        if (panel != null) {
            Minecraft client = Minecraft.getMinecraft();
            int scale = new ScaledResolution(client, client.displayWidth, client.displayHeight).getScaleFactor();
            x += panel.nativeViewportOffsetX() * scale;
            // OpenGL's viewport starts at the bottom of the framebuffer.
            y -= panel.nativeViewportOffsetY() * scale;
        }
        GL11.glViewport(x, y, width, height);
    }
}
