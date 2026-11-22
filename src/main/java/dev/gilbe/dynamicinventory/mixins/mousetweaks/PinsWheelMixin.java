package dev.gilbe.dynamicinventory.mixins.mousetweaks;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.gilbe.dynamicinventory.client.AePinnedPanel;

/** Mouse Tweaks handles its wheel independently from the native GUI input event. */
@Pseudo
@Mixin(targets = "yalter.mousetweaks.handlers.WheelHandler", remap = false)
public abstract class PinsWheelMixin {

    @Inject(method = "handleWheelTweak", at = @At("HEAD"), cancellable = true, require = 0)
    private static void dynamicinventory$returnToPins(GuiScreen gui, Slot slot, ItemStack cursor, int slotCount,
        int wheel, @Coerce Object context, CallbackInfo ci) {
        if (cursor == null && AePinnedPanel.mouseTweaksWheel(gui, slot, wheel)) ci.cancel();
    }
}
