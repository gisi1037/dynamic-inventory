package dev.gilbe.dynamicinventory.mixins.bogosorter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.cleanroommc.bogosorter.BogoSortAPI;
import com.cleanroommc.bogosorter.common.sort.SlotGroup;
import com.cleanroommc.bogosorter.mixins.early.minecraft.SlotAccessor;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;

import dev.gilbe.dynamicinventory.compat.PanelSortScope;
import dev.gilbe.dynamicinventory.container.ContainerInventoryRegion;
import dev.gilbe.dynamicinventory.inventory.AutomaticInventoryRouting;
import dev.gilbe.dynamicinventory.inventory.HotbarBanks;
import dev.gilbe.dynamicinventory.inventory.InventoryLayout;
import dev.gilbe.dynamicinventory.inventory.InventoryShape;

/** Feed Bogo's existing same-item shortcut the canonical player slots, without changing its sort groups. */
@Mixin(targets = "com.cleanroommc.bogosorter.ShortcutHandler", remap = false)
public abstract class BogoBulkTransferMixin {

    @Unique
    private static final String dynamicinventory$MOVE_ALL = "moveAllItems(Lnet/minecraft/entity/player/EntityPlayer;"
        + "Lnet/minecraft/inventory/Container;Lcom/cleanroommc/bogosorter/mixins/early/minecraft/SlotAccessor;Z)V";

    @ModifyExpressionValue(
        method = dynamicinventory$MOVE_ALL,
        at = @At(
            value = "INVOKE",
            target = "Lcom/cleanroommc/bogosorter/common/sort/GuiSortingContext;getSlotGroup(I)Lcom/cleanroommc/bogosorter/common/sort/SlotGroup;"))
    private static SlotGroup dynamicinventory$sourceGroup(SlotGroup original,
        @Local(argsOnly = true) Container container, @Local(argsOnly = true) SlotAccessor source,
        @Local(argsOnly = true) boolean sameItemOnly) {
        if (original != null) return original;
        ContainerInventoryRegion binding = ContainerInventoryRegion.of(container);
        if (binding == null || binding.logical(source.getSlotNumber()) < InventoryShape.HOTBAR_SLOTS) return null;
        return new SlotGroup(true, false, Collections.singletonList(source), 1);
    }

    @Inject(method = dynamicinventory$MOVE_ALL, at = @At("HEAD"), cancellable = true)
    private static void dynamicinventory$keepStoredHotbars(EntityPlayer player, Container container,
        SlotAccessor source, boolean sameItemOnly, CallbackInfo ci) {
        if (!sameItemOnly && source != null && PanelSortScope.reserved(source)) ci.cancel();
    }

    @ModifyExpressionValue(
        method = dynamicinventory$MOVE_ALL,
        at = @At(
            value = "INVOKE",
            target = "Lcom/cleanroommc/bogosorter/common/sort/SlotGroup;getSlots()Ljava/util/List;"))
    private static List<SlotAccessor> dynamicinventory$sources(List<SlotAccessor> original,
        @Local(argsOnly = true) EntityPlayer player, @Local(argsOnly = true) Container container,
        @Local(argsOnly = true) SlotAccessor source, @Local(argsOnly = true) boolean sameItemOnly) {
        ContainerInventoryRegion binding = ContainerInventoryRegion.of(container);
        if (binding == null || binding.inventory != player.inventory)
            return sameItemOnly ? original : PanelSortScope.filter(original);
        int logical = binding.logical(source.getSlotNumber());
        if (logical < InventoryShape.HOTBAR_SLOTS) return sameItemOnly ? original : PanelSortScope.filter(original);
        int raw = InventoryLayout.of(binding.inventory)
            .rawIndex(logical);
        if (HotbarBanks.isReserved(binding.inventory, raw)) return Collections.emptyList();
        List<SlotAccessor> storage = dynamicinventory$storage(container, binding, player, false);
        return sameItemOnly ? storage : PanelSortScope.filter(storage);
    }

    @ModifyExpressionValue(
        method = dynamicinventory$MOVE_ALL,
        at = @At(
            value = "INVOKE",
            target = "Lcom/cleanroommc/bogosorter/ShortcutHandler;getShortcutTargetSlots(Lcom/cleanroommc/bogosorter/mixins/early/minecraft/SlotAccessor;Lcom/cleanroommc/bogosorter/common/sort/GuiSortingContext;Ljava/util/List;)Ljava/util/List;"))
    private static List<SlotAccessor> dynamicinventory$destinations(List<SlotAccessor> original,
        @Local(argsOnly = true) EntityPlayer player, @Local(argsOnly = true) Container container,
        @Local(argsOnly = true) SlotAccessor source, @Local(argsOnly = true) boolean sameItemOnly) {
        if (original == null) return null;
        if (!sameItemOnly) return PanelSortScope.excludeReserved(original);
        if (BogoSortAPI.isPlayerSlot(source)) return original;
        ContainerInventoryRegion binding = ContainerInventoryRegion.of(container);
        return binding != null && binding.inventory == player.inventory
            ? dynamicinventory$storage(container, binding, player, true)
            : original;
    }

    @Unique
    private static List<SlotAccessor> dynamicinventory$storage(Container container, ContainerInventoryRegion binding,
        EntityPlayer player, boolean destination) {
        List<SlotAccessor> result = new ArrayList<>();
        InventoryShape shape = InventoryLayout.of(binding.inventory);
        for (int logical = InventoryShape.HOTBAR_SLOTS; logical < binding.getOrdinarySlotCount(); logical++) {
            int raw = shape.rawIndex(logical);
            if (HotbarBanks.isReserved(binding.inventory, raw)
                || (destination && !AutomaticInventoryRouting.transfer(binding.inventory, raw))) continue;
            SlotAccessor slot = BogoSortAPI.getSlot(container, binding.id(logical));
            if (destination || slot.callCanTakeStack(player)) result.add(slot);
        }
        return result;
    }
}
