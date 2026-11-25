package dev.gilbe.dynamicinventory.mixins.modularui2;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cleanroommc.modularui.screen.ModularContainer;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import dev.gilbe.dynamicinventory.compat.mui2.Mui2PlayerInventory;
import dev.gilbe.dynamicinventory.container.PlayerInventoryTransfers;
import dev.gilbe.dynamicinventory.inventory.AutomaticInventoryRouting;
import dev.gilbe.dynamicinventory.inventory.InventoryLayout;

/** Resolves both framework slot groups using MUI2's canonical raw player indices. */
@Mixin(value = ModularContainer.class, remap = false)
public abstract class Mui2PlayerLookupMixin extends Container {

    @WrapMethod(method = "transferItem")
    private ItemStack dynamicinventory$preferFilteredSlots(ModularSlot source, ItemStack stack,
        Operation<ItemStack> original) {
        ModularContainer container = (ModularContainer) (Object) this;
        PlayerInventoryTransfers.mergeIntoFilters(container, container.getPlayer(), stack);
        return stack.stackSize == 0 ? stack : original.call(source, stack);
    }

    @Inject(method = "getShiftClickSlots", at = @At("RETURN"), cancellable = true)
    private void dynamicinventory$mainTargets(CallbackInfoReturnable<List<ModularSlot>> cir) {
        List<ModularSlot> targets = new ArrayList<>();
        for (ModularSlot slot : cir.getReturnValue()) {
            if (!(slot.getItemHandler() instanceof Mui2PlayerInventory player)
                || AutomaticInventoryRouting.transfer(player.getInventoryPlayer(), slot.getSlotIndex()))
                targets.add(slot);
        }
        AutomaticInventoryRouting.orderPlayerSlots(targets, Mui2PlayerLookupMixin::dynamicinventory$logical);
        cir.setReturnValue(targets);
    }

    @Redirect(
        method = "transferItem",
        at = @At(
            value = "INVOKE",
            target = "Lcom/cleanroommc/modularui/screen/ModularContainer;getShiftClickSlots()Ljava/util/List;",
            ordinal = 0))
    private List<ModularSlot> dynamicinventory$firstMerge(ModularContainer container, ModularSlot source,
        ItemStack stack) {
        List<ModularSlot> targets = container.getShiftClickSlots();
        if (dynamicinventory$logical(source) >= 0 || targets.stream()
            .noneMatch(slot -> {
                int logical = dynamicinventory$logical(slot);
                return logical >= 0 && logical < 9
                    && slot.getSlotGroup() != source.getSlotGroup()
                    && slot.func_111238_b()
                    && PlayerInventoryTransfers.hasTransferRoom(slot, stack);
            })) return targets;
        return targets.stream()
            .filter(slot -> dynamicinventory$logical(slot) < 9)
            .collect(java.util.stream.Collectors.toList());
    }

    @Unique
    private static int dynamicinventory$logical(ModularSlot slot) {
        return slot.getItemHandler() instanceof Mui2PlayerInventory player
            ? InventoryLayout.of(player.getInventoryPlayer())
                .ordinaryIndex(slot.getSlotIndex())
            : -1;
    }

    @Inject(method = "findPlayerSlot", at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$canonicalPlayerSlot(EntityPlayer player, int index,
        CallbackInfoReturnable<Slot> cir) {
        if (player == null) return;
        for (Slot slot : inventorySlots) {
            if (!(slot instanceof ModularSlot)) continue;
            Object handler = ((ModularSlot) slot).getItemHandler();
            if (!(handler instanceof Mui2PlayerInventory)) continue;
            Mui2PlayerInventory inventory = (Mui2PlayerInventory) handler;
            if (inventory.getInventoryPlayer() == player.inventory && slot.getSlotIndex() == index) {
                cir.setReturnValue(slot);
                return;
            }
        }
    }
}
