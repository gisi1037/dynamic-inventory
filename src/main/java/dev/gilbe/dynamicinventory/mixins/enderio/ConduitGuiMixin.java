package dev.gilbe.dynamicinventory.mixins.enderio;

import java.util.List;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.Tessellator;

import org.lwjgl.opengl.GL11;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.gilbe.dynamicinventory.client.ContainerPanelAccess;
import dev.gilbe.dynamicinventory.client.InventoryPanelLayout.Rect;
import dev.gilbe.dynamicinventory.client.NativeInventoryPanel;
import dev.gilbe.dynamicinventory.compat.enderio.ConduitLayout;

/** Keep the native tab strip and conduit controls aligned to the machine section. */
@Pseudo
@Mixin(targets = "crazypants.enderio.conduit.gui.GuiExternalConnection", remap = false)
public abstract class ConduitGuiMixin implements ConduitLayout {

    @Unique
    private static final int TAB_INSET = 3;
    @Unique
    private static final int TAB_WIDTH = 19;

    @Shadow
    @Final
    private List<?> tabs;

    @Shadow
    private int activeTab;

    @Unique
    private boolean dynamicinventory$rebuilding;

    @Unique
    private ContainerPanelAccess dynamicinventory$access() {
        return (ContainerPanelAccess) this;
    }

    @Override
    public Rect dynamicinventory$tabBounds(int left, int top, int width) {
        return new Rect(left + width - TAB_INSET, top + 4, TAB_WIDTH, tabs.size() * 24);
    }

    // Native tab changes reinitialize directly, outside Forge's normal init events.
    @Inject(method = { "initGui", "func_73866_w_" }, at = @At("HEAD"))
    private void dynamicinventory$before(CallbackInfo ci) {
        ContainerPanelAccess access = dynamicinventory$access();
        NativeInventoryPanel old = access.dynamicinventory$panel();
        dynamicinventory$rebuilding = old != null;
        if (old != null) {
            old.restore();
            access.dynamicinventory$panel(null);
        }
    }

    @Inject(method = { "initGui", "func_73866_w_" }, at = @At("RETURN"))
    private void dynamicinventory$after(CallbackInfo ci) {
        if (dynamicinventory$rebuilding) {
            dynamicinventory$rebuilding = false;
            dynamicinventory$access().dynamicinventory$panel(new NativeInventoryPanel((GuiContainer) (Object) this));
        }
    }

    @Inject(method = { "drawGuiContainerBackgroundLayer", "func_146976_a" }, at = @At("HEAD"))
    private void dynamicinventory$positionTab(float ticks, int x, int y, CallbackInfo ci) {
        NativeInventoryPanel panel = dynamicinventory$access().dynamicinventory$panel();
        if (panel != null && !tabs.isEmpty() && tabs.get(activeTab) instanceof ConduitTabOriginMixin tab) {
            // The tab caches these coordinates at init, before our panel moves the machine.
            tab.dynamicinventory$setTabLeft(panel.nativeHeaderLeft() + 10);
            tab.dynamicinventory$setTabTop(panel.nativeHeaderTop());
        }
    }

    @Redirect(
        method = { "drawGuiContainerBackgroundLayer", "func_146976_a" },
        at = @At(value = "INVOKE", target = "func_78381_a()I"))
    private int dynamicinventory$drawTabs(Tessellator tessellator) {
        if (dynamicinventory$access().dynamicinventory$panel() == null) return tessellator.draw();
        // The native tab strip protrudes outside the scissor used for the main texture.
        GL11.glPushAttrib(GL11.GL_SCISSOR_BIT);
        try {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            return tessellator.draw();
        } finally {
            GL11.glPopAttrib();
        }
    }

    // Only conduit hit-testing uses the machine origin; superclass slot clicks still use the full GUI.
    @Redirect(
        method = { "mouseClicked", "func_73864_a" },
        at = @At(value = "FIELD", target = "field_147003_i:I", opcode = Opcodes.GETFIELD))
    private int dynamicinventory$clickLeft(@Coerce Object gui) {
        return dynamicinventory$left(dynamicinventory$access().dynamicinventory$left());
    }

    @Redirect(
        method = { "mouseClicked", "func_73864_a" },
        at = @At(value = "FIELD", target = "field_147009_r:I", opcode = Opcodes.GETFIELD))
    private int dynamicinventory$clickTop(@Coerce Object gui) {
        return dynamicinventory$top(dynamicinventory$access().dynamicinventory$top());
    }

    @Redirect(
        method = { "mouseClicked", "func_73864_a" },
        at = @At(value = "FIELD", target = "field_146999_f:I", opcode = Opcodes.GETFIELD))
    private int dynamicinventory$tabClickLeft(@Coerce Object gui) {
        ContainerPanelAccess access = dynamicinventory$access();
        NativeInventoryPanel panel = access.dynamicinventory$panel();
        return panel == null ? access.dynamicinventory$width() : panel.nativeUiWidth() - TAB_INSET - 1;
    }

    @ModifyVariable(method = { "mouseClicked", "func_73864_a" }, at = @At("STORE"), index = 5)
    private int dynamicinventory$tabClickRight(int original) {
        NativeInventoryPanel panel = dynamicinventory$access().dynamicinventory$panel();
        // The native check adds 24 to this local. Limit clicks to the painted tab strip beside our panels.
        return panel == null ? original : panel.nativeUiWidth() - TAB_INSET + TAB_WIDTH - 24;
    }

    @Redirect(
        method = "hideItemPanelSlot",
        at = @At(value = "FIELD", target = "field_146999_f:I", opcode = Opcodes.GETFIELD),
        require = 0)
    private int dynamicinventory$neiWidth(@Coerce Object gui) {
        ContainerPanelAccess access = dynamicinventory$access();
        NativeInventoryPanel panel = access.dynamicinventory$panel();
        return panel == null ? access.dynamicinventory$width() : panel.nativeUiWidth();
    }

    @ModifyConstant(method = "hideItemPanelSlot", constant = @Constant(intValue = 14), require = 0)
    private int dynamicinventory$neiTabWidth(int original) {
        return dynamicinventory$access().dynamicinventory$panel() == null ? original : TAB_WIDTH;
    }

    @ModifyVariable(method = "hideItemPanelSlot", at = @At("STORE"), index = 6, require = 0)
    private int dynamicinventory$neiLeft(int centered) {
        return dynamicinventory$left(centered);
    }

    @ModifyVariable(method = "hideItemPanelSlot", at = @At("STORE"), index = 7, require = 0)
    private int dynamicinventory$neiTop(int centered) {
        return dynamicinventory$top(centered);
    }

    @Unique
    private int dynamicinventory$left(int original) {
        NativeInventoryPanel panel = dynamicinventory$access().dynamicinventory$panel();
        return panel == null ? original : panel.nativeHeaderLeft();
    }

    @Unique
    private int dynamicinventory$top(int original) {
        NativeInventoryPanel panel = dynamicinventory$access().dynamicinventory$panel();
        return panel == null ? original : panel.nativeHeaderTop();
    }

}
