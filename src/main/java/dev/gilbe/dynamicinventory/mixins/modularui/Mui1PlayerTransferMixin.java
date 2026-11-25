package dev.gilbe.dynamicinventory.mixins.modularui;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.gtnewhorizons.modularui.common.internal.wrapper.BaseSlot;
import com.gtnewhorizons.modularui.common.internal.wrapper.ModularUIContainer;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import dev.gilbe.dynamicinventory.compat.mui1.PlayerInventorySlot;
import dev.gilbe.dynamicinventory.container.PlayerInventoryTransfers;

@Mixin(value = ModularUIContainer.class, remap = false)
public abstract class Mui1PlayerTransferMixin {

    @WrapMethod(method = "transferItem")
    private ItemStack dynamicinventory$preferFilteredSlots(BaseSlot source, ItemStack stack,
        Operation<ItemStack> original) {
        ModularUIContainer container = (ModularUIContainer) (Object) this;
        PlayerInventoryTransfers.mergeIntoFilters(
            container,
            container.getContext()
                .getPlayer(),
            stack);
        return stack.stackSize == 0 ? stack : original.call(source, stack);
    }

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
