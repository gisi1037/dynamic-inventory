package dev.gilbe.dynamicinventory.mixins.minecraft;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;

import org.lwjgl.input.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.gilbe.dynamicinventory.client.ContainerPanelAccess;
import dev.gilbe.dynamicinventory.client.DetachedChestPanel;
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
    protected boolean field_147007_t;

    @Override
    public boolean dynamicinventory$draggingItems() {
        return field_147007_t;
    }

    @Shadow
    protected abstract void drawGuiContainerBackgroundLayer(float ticks, int x, int y);

    @Shadow
    protected abstract void drawGuiContainerForegroundLayer(int x, int y);

    @Unique
    private NativeInventoryPanel dynamicinventory$panel;

    @Unique
    private DetachedChestPanel dynamicinventory$floating;

    @Override
    public DetachedChestPanel dynamicinventory$floating() {
        return dynamicinventory$floating;
    }

    @Override
    public void dynamicinventory$floating(DetachedChestPanel panel) {
        dynamicinventory$floating = panel;
    }

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

    @Inject(method = "drawScreen", at = @At("HEAD"))
    private void dynamicinventory$dragFrame(int x, int y, float ticks, CallbackInfo ci) {
        DetachedChestPanel floating = DetachedChestPanel.of(this);
        if (floating != null) floating.updateDragFrame();
    }

    @Redirect(
        method = "drawScreen",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/inventory/GuiContainer;drawGuiContainerBackgroundLayer(FII)V"))
    private void dynamicinventory$background(GuiContainer gui, float ticks, int x, int y) {
        if (dynamicinventory$panel == null) drawGuiContainerBackgroundLayer(ticks, x, y);
        else dynamicinventory$panel.background(() -> drawGuiContainerBackgroundLayer(ticks, x, y));
        dev.gilbe.dynamicinventory.client.HotbarBankPanel.draw(gui);
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
        if (dev.gilbe.dynamicinventory.client.HotbarBankPanel
            .pointer((GuiContainer) (Object) this, x, y, button, true)) {
            ci.cancel();
            return;
        }
        DetachedChestPanel floating = DetachedChestPanel.of(this);
        if (floating != null && floating.pointer(x, y, button, true)) {
            ci.cancel();
            return;
        }
        if (dynamicinventory$panel != null && dynamicinventory$panel.scrollbar(x, y)) ci.cancel();
    }

    @Inject(method = "handleMouseClick", at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$panelQuickMove(net.minecraft.inventory.Slot slot, int id, int button, int mode,
        CallbackInfo ci) {
        DetachedChestPanel floating = DetachedChestPanel.of(this);
        if (mode == 1 && (button == 0 || button == 1) && floating != null && floating.quickMoveToMain(id)) ci.cancel();
    }

    @Inject(method = "mouseClickMove", at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$move(int x, int y, int button, long elapsed, CallbackInfo ci) {
        if (dev.gilbe.dynamicinventory.client.HotbarBankPanel.pointer((GuiContainer) (Object) this, x, y, -1, false)) {
            ci.cancel();
            return;
        }
        DetachedChestPanel floating = DetachedChestPanel.of(this);
        if (floating != null && floating.pointer(x, y, -1, false)) ci.cancel();
    }

    @Inject(method = "mouseMovedOrUp", at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$release(int x, int y, int button, CallbackInfo ci) {
        if (dev.gilbe.dynamicinventory.client.HotbarBankPanel
            .pointer((GuiContainer) (Object) this, x, y, button, false)) {
            ci.cancel();
            return;
        }
        DetachedChestPanel floating = DetachedChestPanel.of(this);
        if (floating != null && floating.pointer(x, y, button, false)) ci.cancel();
    }

    // Reviewed vanilla locals: outside flag after guiLeft/guiTop in press and release respectively.
    @ModifyVariable(method = "mouseClicked", at = @At("STORE"), index = 10, require = 1, allow = 1)
    private boolean dynamicinventory$outsidePress(boolean original, int x, int y, int button) {
        return dynamicinventory$outside(original, x, y);
    }

    @ModifyVariable(method = "mouseMovedOrUp", at = @At("STORE"), index = 7, require = 1, allow = 1)
    private boolean dynamicinventory$outsideRelease(boolean original, int x, int y, int button) {
        return dynamicinventory$outside(original, x, y);
    }

    private boolean dynamicinventory$outside(boolean original, int x, int y) {
        dev.gilbe.dynamicinventory.client.InventoryPanelLayout.Rect hotbars = dev.gilbe.dynamicinventory.client.HotbarBankPanel
            .bounds((GuiContainer) (Object) this);
        if (hotbars != null && hotbars.contains(x, y)) return false;
        DetachedChestPanel floating = DetachedChestPanel.of(this);
        return floating != null && floating.detached() ? !floating.inside(x, y) : original;
    }

    @Inject(method = "keyTyped", at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$page(char character, int key, CallbackInfo ci) {
        DetachedChestPanel floating = DetachedChestPanel.of(this);
        if (key == Keyboard.KEY_ESCAPE && floating != null && floating.cancel()) {
            ci.cancel();
            return;
        }
        if (dynamicinventory$panel != null && (key == Keyboard.KEY_PRIOR || key == Keyboard.KEY_NEXT)) {
            dynamicinventory$panel.scroll(key == Keyboard.KEY_NEXT ? 1 : -1);
            ci.cancel();
        }
    }
}
