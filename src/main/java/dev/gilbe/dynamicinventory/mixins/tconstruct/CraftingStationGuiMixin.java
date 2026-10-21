package dev.gilbe.dynamicinventory.mixins.tconstruct;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.gilbe.dynamicinventory.api.InventoryUi;
import dev.gilbe.dynamicinventory.client.AttachedChestAccess;
import dev.gilbe.dynamicinventory.client.AttachedChestLayout;
import dev.gilbe.dynamicinventory.client.ContainerPanelAccess;
import dev.gilbe.dynamicinventory.client.InventoryGuiTheme;
import dev.gilbe.dynamicinventory.client.NativeInventoryPanel;
import dev.gilbe.dynamicinventory.client.editor.NativeLayoutPatch;
import dev.gilbe.dynamicinventory.client.editor.NativeLayoutPatches;
import dev.gilbe.dynamicinventory.inventory.InventoryLayout;
import tconstruct.tools.gui.ChestSlot;
import tconstruct.tools.gui.GuiBorderWidget;
import tconstruct.tools.gui.GuiSliderWidget;
import tconstruct.tools.logic.CraftingStationLogic;
import tconstruct.util.config.PHConstruct;

@Mixin(targets = "tconstruct.tools.gui.CraftingStationGui", remap = false)
public abstract class CraftingStationGuiMixin implements AttachedChestAccess {

    @Unique
    private static final net.minecraft.util.ResourceLocation dynamicinventory$frameTexture = new net.minecraft.util.ResourceLocation(
        "tinker",
        "textures/gui/generic.png");

    @Unique
    private int dynamicinventory$layoutHeight;
    @Unique
    private int dynamicinventory$layoutWidth;
    @Unique
    private AttachedChestLayout dynamicinventory$chestLayout;

    @Shadow
    CraftingStationLogic logic;

    @Inject(method = "selectChestLayout", at = @At("HEAD"))
    private void dynamicinventory$reservePlayerPanel(CallbackInfoReturnable<Object> ci) {
        GuiContainer gui = (GuiContainer) (Object) this;
        dynamicinventory$layoutHeight = gui.height;
        dynamicinventory$layoutWidth = gui.width;
        dynamicinventory$chestLayout = null;
        if (dynamicinventory$splitChest()) {
            NativeLayoutPatch patch = NativeLayoutPatches.resolve(gui);
            dynamicinventory$chestLayout = new AttachedChestLayout(
                gui.width,
                gui.height,
                InventoryLayout.of(gui.mc.thePlayer.inventory)
                    .storageSlots(),
                patch.value(0),
                NativeLayoutPatches.requestedRows(gui, patch, 1),
                logic.tinkerTable,
                0,
                0,
                logic.slotCount,
                patch.value(11) < 0 ? AttachedChestLayout.GAP : patch.value(11),
                patch.value(13),
                patch.value(14),
                patch.value(15));
            // Native selection assumes the chest is left of a centered crafting panel. Supply an
            // equivalent virtual viewport so its real rectangular/scrolling algorithm budgets our top band.
            gui.width = 2 * (dynamicinventory$chestLayout.chestColumns * 18 + 26 + 81) + 176;
            gui.height = dynamicinventory$chestLayout.chestHeightBudget + 44;
        } else {
            gui.height = Math.max(1, gui.height - NativeInventoryPanel.minimumPanelHeight());
        }
    }

    @Inject(method = "selectChestLayout", at = @At("RETURN"))
    private void dynamicinventory$restoreLayoutHeight(CallbackInfoReturnable<Object> ci) {
        ((GuiContainer) (Object) this).height = dynamicinventory$layoutHeight;
        ((GuiContainer) (Object) this).width = dynamicinventory$layoutWidth;
    }

    @ModifyConstant(method = "selectChestLayout", constant = @Constant(intValue = 7))
    private int dynamicinventory$rectangularTopBand(int overhangRows) {
        // Both native L-shape loops reserve an elbow for crafting; our crafting pane is below the chest.
        return dynamicinventory$chestLayout == null ? overhangRows : 0;
    }

    @ModifyConstant(method = "selectChestLayout", constant = @Constant(intValue = 5))
    private int dynamicinventory$topBandColumns(int minimumColumns) {
        return dynamicinventory$chestLayout == null ? minimumColumns : dynamicinventory$chestLayout.chestColumns;
    }

    @ModifyConstant(method = "selectChestLayout", constant = @Constant(intValue = 60))
    private int dynamicinventory$responsiveSmallChests(int fixedLayoutLimit) {
        // Tinkers returns a fixed six-column, non-scrolling layout early for <= 60 slots.
        // Use the same budgeted rectangular path for small chests as for compressed chests.
        return dynamicinventory$chestLayout == null ? fixedLayoutLimit : 0;
    }

    @ModifyArg(
        method = "drawGuiContainerForegroundLayer",
        remap = true,
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/FontRenderer;drawString(Ljava/lang/String;III)I",
            remap = true),
        index = 0)
    private String dynamicinventory$hideOldInventoryLabel(String label) {
        return dynamicinventory$splitChest() && net.minecraft.util.StatCollector.translateToLocal("container.inventory")
            .equals(label) ? "" : label;
    }

    @Override
    public boolean dynamicinventory$splitChest() {
        GuiContainer gui = (GuiContainer) (Object) this;
        // Classic mode has fixed chest dimensions and cannot consume the responsive top-band budget.
        return !PHConstruct.classicCraftingStationLayout && logic.chest != null
            && logic.slotCount > 0
            && InventoryUi.findLayout(gui.inventorySlots, gui.getClass()) != null;
    }

    @Override
    public boolean dynamicinventory$hasDescription() {
        return logic.tinkerTable;
    }

    @Override
    public int dynamicinventory$chestWidth() {
        return border.width;
    }

    @Override
    public int dynamicinventory$chestHeight() {
        return border.height;
    }

    @Shadow
    public abstract void updateChest();

    @Override
    public void dynamicinventory$refreshChestLayout() {
        // updateChest changes native positions/visibility and slider bounds, not the slot list.
        // Calling initGui here would recreate buttons and can disrupt a live editor session.
        if (dynamicinventory$splitChest()) updateChest();
    }

    @Override
    public boolean dynamicinventory$isChestSlot(Slot slot) {
        return slot instanceof ChestSlot;
    }

    @Override
    public void dynamicinventory$positionText(int craftingOriginX) {
        craftingTextLeft = craftingLeft - craftingOriginX;
        descTextLeft = descLeft - craftingOriginX;
    }

    @Redirect(
        method = "drawChest",
        at = @At(value = "INVOKE", target = "Ltconstruct/tools/gui/GuiBorderWidget;draw()V"))
    private void dynamicinventory$alignedChestFrame(GuiBorderWidget widget) {
        ContainerPanelAccess access = (ContainerPanelAccess) this;
        if (dynamicinventory$splitChest() && access.dynamicinventory$panel() != null) {
            // Widen only the drawn frame. The native chest grid, slider and hit testing keep
            // their original dimensions, and the lower panel uses this exact same border.
            access.dynamicinventory$panel()
                .drawAttachedChestFrame();
        } else widget.draw();
    }

    @Override
    public void dynamicinventory$drawFrame(int left, int top, int width, int height) {
        int oldX = border.xPos, oldY = border.yPos, oldWidth = border.width, oldHeight = border.height;
        try {
            net.minecraft.client.gui.Gui.drawRect(
                left + border.w,
                top + border.h,
                left + width - border.w,
                top + height - border.h,
                InventoryGuiTheme.get().panelFill);
            net.minecraft.client.Minecraft.getMinecraft()
                .getTextureManager()
                .bindTexture(dynamicinventory$frameTexture);
            org.lwjgl.opengl.GL11.glColor4f(1, 1, 1, 1);
            border.setPosition(left, top);
            border.setSize(width, height);
            border.draw();
        } finally {
            // Rendering must never leak temporary bounds into native layout or input code.
            border.setPosition(oldX, oldY);
            border.setSize(oldWidth, oldHeight);
        }
    }

    @Shadow
    private int craftingLeft;
    @Shadow
    private int craftingTop;
    @Shadow
    private int craftingTextLeft;
    @Shadow
    private int chestLeft;
    @Shadow
    private int chestTop;
    @Shadow
    private int descLeft;
    @Shadow
    private int descTop;
    @Shadow
    private int descTextLeft;
    @Shadow
    private GuiBorderWidget border;
    @Shadow
    private GuiSliderWidget slider;

    @Override
    public int[] dynamicinventory$headerOrigins() {
        return new int[] { craftingLeft, craftingTop, chestLeft, chestTop, descLeft, descTop, border.xPos, border.yPos,
            slider.xPos, slider.yPos };
    }

    @Override
    public void dynamicinventory$headerOrigins(int[] values) {
        craftingLeft = values[0];
        craftingTop = values[1];
        chestLeft = values[2];
        chestTop = values[3];
        descLeft = values[4];
        descTop = values[5];
        border.setPosition(values[6], values[7]);
        slider.setPosition(values[8], values[9]);
    }
}
