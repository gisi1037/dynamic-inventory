package dev.gilbe.dynamicinventory.mixins.appliedenergistics2;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Redirect;

import dev.gilbe.dynamicinventory.client.ContainerPanelAccess;
import dev.gilbe.dynamicinventory.client.NativeInventoryPanel;

/** These custom terminals also read their origin outside AEBaseGui's normal drawing/input layers. */
@Mixin(
    targets = { "com.glodblock.github.client.gui.GuiLevelTerminal", "com.glodblock.github.client.gui.GuiLevelWireless",
        "com.glodblock.github.client.gui.GuiLevelMaintainer", "com.glodblock.github.client.gui.GuiMagnetFilter",
        "com.glodblock.github.inventory.gui.MouseRegionManager" },
    remap = false)
public abstract class FluidGuiOriginMixin {

    // Owner-free selectors cover inherited-field references made through each concrete class.
    // Both reviewed runtime names are explicit: development fixtures use MCP, installed packs use SRG.
    @Group(name = "dynamicinventory$geometry", min = 1)
    @Redirect(method = "*", at = @At(value = "FIELD", target = "guiLeft:I", opcode = Opcodes.GETFIELD), require = 0)
    private static int dynamicinventory$leftMcp(@Coerce Object gui) {
        return dynamicinventory$left(gui);
    }

    @Group(name = "dynamicinventory$geometry", min = 1)
    @Redirect(
        method = "*",
        at = @At(value = "FIELD", target = "field_147003_i:I", opcode = Opcodes.GETFIELD),
        require = 0)
    private static int dynamicinventory$leftSrg(@Coerce Object gui) {
        return dynamicinventory$left(gui);
    }

    private static int dynamicinventory$left(Object gui) {
        ContainerPanelAccess access = (ContainerPanelAccess) gui;
        NativeInventoryPanel panel = access.dynamicinventory$panel();
        return panel == null ? access.dynamicinventory$left() : panel.nativeHeaderLeft();
    }

    @Group(name = "dynamicinventory$geometry", min = 1)
    @Redirect(method = "*", at = @At(value = "FIELD", target = "guiTop:I", opcode = Opcodes.GETFIELD), require = 0)
    private static int dynamicinventory$topMcp(@Coerce Object gui) {
        return dynamicinventory$top(gui);
    }

    @Group(name = "dynamicinventory$geometry", min = 1)
    @Redirect(
        method = "*",
        at = @At(value = "FIELD", target = "field_147009_r:I", opcode = Opcodes.GETFIELD),
        require = 0)
    private static int dynamicinventory$topSrg(@Coerce Object gui) {
        return dynamicinventory$top(gui);
    }

    private static int dynamicinventory$top(Object gui) {
        ContainerPanelAccess access = (ContainerPanelAccess) gui;
        NativeInventoryPanel panel = access.dynamicinventory$panel();
        return panel == null ? access.dynamicinventory$top() : panel.nativeHeaderTop();
    }

    @Group(name = "dynamicinventory$geometry", min = 1)
    @Redirect(method = "*", at = @At(value = "FIELD", target = "ySize:I", opcode = Opcodes.GETFIELD), require = 0)
    private static int dynamicinventory$heightMcp(@Coerce Object gui) {
        return dynamicinventory$height(gui);
    }

    @Group(name = "dynamicinventory$geometry", min = 1)
    @Redirect(
        method = "*",
        at = @At(value = "FIELD", target = "field_147000_g:I", opcode = Opcodes.GETFIELD),
        require = 0)
    private static int dynamicinventory$heightSrg(@Coerce Object gui) {
        return dynamicinventory$height(gui);
    }

    private static int dynamicinventory$height(Object gui) {
        ContainerPanelAccess access = (ContainerPanelAccess) gui;
        NativeInventoryPanel panel = access.dynamicinventory$panel();
        return panel == null ? access.dynamicinventory$height() : panel.nativeUiHeight();
    }
}
