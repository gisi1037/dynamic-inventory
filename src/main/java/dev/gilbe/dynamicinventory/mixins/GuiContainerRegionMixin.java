package dev.gilbe.dynamicinventory.mixins;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;

import org.lwjgl.input.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.gilbe.dynamicinventory.client.ContainerPanelAccess;
import dev.gilbe.dynamicinventory.client.NativeInventoryPanel;

@Mixin(GuiContainer.class)
public abstract class GuiContainerRegionMixin extends GuiScreen implements ContainerPanelAccess {

    @Shadow
    protected int xSize;
    @Shadow
    protected int ySize;
    @Shadow
    protected int guiLeft;
    @Shadow
    protected int guiTop;

    @Shadow
    protected abstract void drawGuiContainerBackgroundLayer(float ticks, int x, int y);

    @Shadow
    protected abstract void drawGuiContainerForegroundLayer(int x, int y);

    @Unique
    private NativeInventoryPanel dynamicinventory$panel;

    @Override
    public java.util.List<net.minecraft.client.gui.GuiButton> dynamicinventory$buttons() {
        return buttonList;
    }

    @Override
    public NativeInventoryPanel dynamicinventory$panel() {
        return dynamicinventory$panel;
    }

    @Override
    public void dynamicinventory$panel(NativeInventoryPanel panel) {
        dynamicinventory$panel = panel;
    }

    @Override
    public int dynamicinventory$width() {
        return xSize;
    }

    @Override
    public int dynamicinventory$height() {
        return ySize;
    }

    @Override
    public int dynamicinventory$left() {
        return guiLeft;
    }

    @Override
    public int dynamicinventory$top() {
        return guiTop;
    }

    @Override
    public void dynamicinventory$bounds(int w, int h, int x, int y) {
        xSize = w;
        ySize = h;
        guiLeft = x;
        guiTop = y;
    }

    @Redirect(
        method = "drawScreen",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/inventory/GuiContainer;drawGuiContainerBackgroundLayer(FII)V"))
    private void dynamicinventory$background(GuiContainer gui, float ticks, int x, int y) {
        if (dynamicinventory$panel == null) drawGuiContainerBackgroundLayer(ticks, x, y);
        else dynamicinventory$panel.background(() -> drawGuiContainerBackgroundLayer(ticks, x, y));
    }

    @Redirect(
        method = "drawScreen",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/inventory/GuiContainer;drawGuiContainerForegroundLayer(II)V"))
    private void dynamicinventory$foreground(GuiContainer gui, int x, int y) {
        if (dynamicinventory$panel == null) drawGuiContainerForegroundLayer(x, y);
        else dynamicinventory$panel.foreground(() -> drawGuiContainerForegroundLayer(x, y));
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$bar(int x, int y, int button, CallbackInfo ci) {
        if (dynamicinventory$panel != null && dynamicinventory$panel.scrollbar(x, y)) ci.cancel();
    }

    @Inject(method = "keyTyped", at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$page(char character, int key, CallbackInfo ci) {
        if (dynamicinventory$panel != null && (key == Keyboard.KEY_PRIOR || key == Keyboard.KEY_NEXT)) {
            dynamicinventory$panel.scroll(key == Keyboard.KEY_NEXT ? 1 : -1);
            ci.cancel();
        }
    }
}
