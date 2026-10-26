package dev.gilbe.dynamicinventory.mixins.forestry;

import net.minecraft.client.gui.inventory.GuiContainer;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Redirect;

import dev.gilbe.dynamicinventory.client.ContainerPanelAccess;
import dev.gilbe.dynamicinventory.client.NativeInventoryPanel;
import dev.gilbe.dynamicinventory.compat.ForestryLedgerAccess;

/** Ledger hit testing recomputes a centered origin instead of using GuiForestry's origin getters. */
@Pseudo
@Mixin(targets = "forestry.core.gui.ledgers.LedgerManager", remap = false)
public abstract class ForestryLedgerMixin implements ForestryLedgerAccess {

    @Group(name = "dynamicinventory$width", min = 1)
    @Redirect(
        method = "getAtPosition",
        at = @At(value = "FIELD", target = "width:I", opcode = Opcodes.GETFIELD),
        require = 0)
    private int dynamicinventory$widthMcp(@Coerce Object gui) {
        return dynamicinventory$width(gui);
    }

    @Group(name = "dynamicinventory$width", min = 1)
    @Redirect(
        method = "getAtPosition",
        at = @At(value = "FIELD", target = "field_146294_l:I", opcode = Opcodes.GETFIELD),
        require = 0)
    private int dynamicinventory$widthSrg(@Coerce Object gui) {
        return dynamicinventory$width(gui);
    }

    private static int dynamicinventory$width(Object gui) {
        NativeInventoryPanel panel = ((ContainerPanelAccess) gui).dynamicinventory$panel();
        return panel == null ? ((GuiContainer) gui).width : 2 * panel.nativeHeaderLeft() + panel.nativeUiWidth();
    }

    @Group(name = "dynamicinventory$height", min = 1)
    @Redirect(
        method = "getAtPosition",
        at = @At(value = "FIELD", target = "height:I", opcode = Opcodes.GETFIELD),
        require = 0)
    private int dynamicinventory$heightMcp(@Coerce Object gui) {
        return dynamicinventory$height(gui);
    }

    @Group(name = "dynamicinventory$height", min = 1)
    @Redirect(
        method = "getAtPosition",
        at = @At(value = "FIELD", target = "field_146295_m:I", opcode = Opcodes.GETFIELD),
        require = 0)
    private int dynamicinventory$heightSrg(@Coerce Object gui) {
        return dynamicinventory$height(gui);
    }

    private static int dynamicinventory$height(Object gui) {
        NativeInventoryPanel panel = ((ContainerPanelAccess) gui).dynamicinventory$panel();
        return panel == null ? ((GuiContainer) gui).height : 2 * panel.nativeHeaderTop() + panel.nativeUiHeight();
    }
}
