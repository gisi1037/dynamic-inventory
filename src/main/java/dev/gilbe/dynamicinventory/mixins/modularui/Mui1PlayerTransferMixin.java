package dev.gilbe.dynamicinventory.mixins.modularui;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.gtnewhorizons.modularui.common.internal.wrapper.BaseSlot;
import com.gtnewhorizons.modularui.common.internal.wrapper.ModularUIContainer;

import dev.gilbe.dynamicinventory.compat.mui1.PlayerInventorySlot;

@Mixin(value = ModularUIContainer.class, remap = false)
public abstract class Mui1PlayerTransferMixin {

    // Scrolling disables offscreen widgets for input; it must not reduce shift-click capacity.
    @Redirect(
        method = "transferItem",
        at = @At(
            value = "INVOKE",
            target = "Lcom/gtnewhorizons/modularui/common/internal/wrapper/BaseSlot;isEnabled()Z"))
    private boolean dynamicinventory$storageCapacity(BaseSlot slot) {
        return slot instanceof PlayerInventorySlot ? ((PlayerInventorySlot) slot).isTransferEnabled()
            : slot.isEnabled();
    }
}
