package dev.gilbe.dynamicinventory.mixins.nei;

import java.util.function.Predicate;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import codechicken.lib.vec.Rectangle4i;

/** Existing NEI masks still own pagination and input; floating obstacles require a complete scan. */
@Pseudo
@Mixin(targets = "codechicken.nei.ItemsGrid", remap = false)
public abstract class NeiPanelOverlapMixin {

    @Unique
    private GuiContainer dynamicinventory$screen;
    @Unique
    private long dynamicinventory$generation;

    @Shadow
    protected abstract void updateGuiOverlapSlots(GuiContainer gui);

    @Shadow
    protected abstract void onGridChanged();

    private static long dynamicinventory$revision(GuiScreen gui) {
        dev.gilbe.dynamicinventory.client.DetachedChestPanel panel = dev.gilbe.dynamicinventory.client.DetachedChestPanel
            .of(gui);
        return panel == null ? 0 : panel.revision();
    }

    @WrapOperation(
        method = { "updateGuiOverlapSlots", "checkGuiOverlap" },
        at = @At(value = "INVOKE", target = "Lcodechicken/nei/NEIClientConfig;optimizeGuiOverlapComputation()Z"),
        require = 0)
    private boolean dynamicinventory$scanInterior(Operation<Boolean> original) {
        return dynamicinventory$revision(Minecraft.getMinecraft().currentScreen) == 0 && original.call();
    }

    @WrapOperation(
        method = "updateGuiOverlapSlots",
        at = @At(
            value = "INVOKE",
            target = "Lcodechicken/nei/GridOverlapScanner;scan(Ljava/util/function/Predicate;IIIIIZZ[Z)I"),
        require = 0)
    private int dynamicinventory$scanFloatingPanels(@Coerce Object scanner, Predicate<Rectangle4i> hidden, int left,
        int top, int step, int rows, int columns, boolean scanRight, boolean scanLeft, boolean[] mask,
        Operation<Integer> original) {
        if (dynamicinventory$revision(Minecraft.getMinecraft().currentScreen) == 0)
            return original.call(scanner, hidden, left, top, step, rows, columns, scanRight, scanLeft, mask);
        Rectangle4i cell = new Rectangle4i(left, top, step, step);
        int excluded = 0;
        for (int row = 0; row < rows; row++) {
            cell.y = top + row * step;
            for (int column = 0; column < columns; column++) {
                cell.x = left + column * step;
                if (mask[row * columns + column] = hidden.test(cell)) excluded++;
            }
        }
        return excluded;
    }

    @Inject(method = "refresh", at = @At("HEAD"))
    private void dynamicinventory$invalidate(GuiContainer gui, CallbackInfo ci) {
        long generation = dynamicinventory$revision(gui);
        if (generation != dynamicinventory$generation || generation != 0 && gui != dynamicinventory$screen) {
            updateGuiOverlapSlots(gui);
            onGridChanged();
        }
        dynamicinventory$generation = generation;
        dynamicinventory$screen = gui;
    }
}
