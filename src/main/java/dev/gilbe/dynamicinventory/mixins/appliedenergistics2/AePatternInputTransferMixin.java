package dev.gilbe.dynamicinventory.mixins.appliedenergistics2;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import appeng.container.implementations.ContainerPatternTerm;
import appeng.container.slot.SlotRestrictedInput;
import dev.gilbe.dynamicinventory.compat.ae2.AppliedEnergisticsPatternTransfers;

@Mixin(value = ContainerPatternTerm.class, remap = false)
public abstract class AePatternInputTransferMixin {

    @Shadow
    @Final
    private SlotRestrictedInput patternSlotIN;

    // AE2 already validated the source and identified a blank pattern at this call site.
    @WrapOperation(
        method = { "transferStackInSlot", "func_82846_b" },
        at = @At(
            value = "INVOKE",
            target = "Lappeng/container/implementations/ContainerPatternTerm;shiftStoreItem(Lnet/minecraft/item/ItemStack;)Lnet/minecraft/item/ItemStack;"))
    private ItemStack dynamicinventory$loadBlankPatternsFirst(ContainerPatternTerm terminal, ItemStack source,
        Operation<ItemStack> store) {
        ItemStack remainder = AppliedEnergisticsPatternTransfers.loadBlankPatterns(patternSlotIN, source);
        return remainder == null ? null : store.call(terminal, remainder);
    }
}
