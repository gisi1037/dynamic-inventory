package dev.gilbe.dynamicinventory.mixins.etfuturum;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;

import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import dev.gilbe.dynamicinventory.api.InventoryUi;
import dev.gilbe.dynamicinventory.client.ContainerPanelAccess;
import dev.gilbe.dynamicinventory.client.NativeInventoryPanel;

/** Et Futurum's independent enchantment GUI retains its native lapis, option and book behavior. */
@Pseudo
@Mixin(targets = "ganymedes01.etfuturum.client.gui.inventory.GuiEnchantment", remap = false)
public abstract class EtFuturumEnchantmentGuiMixin {

    @Inject(method = "func_73864_a(III)V", at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$waitForOpening(int mouseX, int mouseY, int button, CallbackInfo ci) {
        if (((ContainerPanelAccess) this).dynamicinventory$panel() != null
            && !InventoryUi.isOpeningReady(Minecraft.getMinecraft().thePlayer)) ci.cancel();
    }

    // The pinned 2.6.64 GUI stores its custom option origin after the base container click, in locals 4/5.
    @ModifyVariable(method = "func_73864_a(III)V", at = @At("STORE"), index = 4, require = 1, allow = 1)
    private int dynamicinventory$optionLeft(int left) {
        NativeInventoryPanel panel = ((ContainerPanelAccess) this).dynamicinventory$panel();
        return panel == null ? left : panel.nativeHeaderLeft();
    }

    @ModifyVariable(method = "func_73864_a(III)V", at = @At("STORE"), index = 5, require = 1, allow = 1)
    private int dynamicinventory$optionTop(int top) {
        NativeInventoryPanel panel = ((ContainerPanelAccess) this).dynamicinventory$panel();
        return panel == null ? top : panel.nativeHeaderTop();
    }

    @Redirect(
        method = "func_146976_a(FII)V",
        at = @At(value = "INVOKE", target = "Lganymedes01/etfuturum/client/OpenGLHelper;viewport(IIII)V", ordinal = 0),
        require = 1,
        allow = 1)
    private void dynamicinventory$bookViewport(int x, int y, int width, int height) {
        NativeInventoryPanel panel = ((ContainerPanelAccess) this).dynamicinventory$panel();
        if (panel != null) {
            Minecraft client = Minecraft.getMinecraft();
            int scale = new ScaledResolution(client, client.displayWidth, client.displayHeight).getScaleFactor();
            x += panel.nativeViewportOffsetX() * scale;
            y -= panel.nativeViewportOffsetY() * scale;
        }
        // The pinned OpenGLHelper.viewport is a direct GL11 delegate. Leave its final viewport reset untouched.
        GL11.glViewport(x, y, width, height);
    }

    @ModifyArgs(
        method = "func_73863_a(IIF)V",
        at = @At(
            value = "INVOKE",
            target = "Lganymedes01/etfuturum/client/gui/inventory/GuiEnchantment;func_146978_c(IIIIII)Z"),
        require = 1,
        allow = 1)
    private void dynamicinventory$optionTooltip(Args args) {
        NativeInventoryPanel panel = ((ContainerPanelAccess) this).dynamicinventory$panel();
        if (panel != null) {
            // Tooltip hit testing runs after the base draw restores the outer container origin.
            args.set(4, (Integer) args.get(4) - panel.headerOffsetX());
            args.set(5, (Integer) args.get(5) - panel.headerOffsetY());
        }
    }
}
