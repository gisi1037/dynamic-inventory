package dev.gilbe.dynamicinventory.mixins.bogosorter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.cleanroommc.bogosorter.BogoSortAPI;
import com.cleanroommc.bogosorter.common.PinnedSlots;
import com.cleanroommc.bogosorter.common.sort.SlotGroup;
import com.cleanroommc.bogosorter.mixins.early.minecraft.SlotAccessor;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import dev.gilbe.dynamicinventory.compat.PanelSortScope;
import dev.gilbe.dynamicinventory.container.ContainerInventoryRegion;
import dev.gilbe.dynamicinventory.inventory.AutomaticInventoryRouting;
import dev.gilbe.dynamicinventory.inventory.HotbarBanks;
import dev.gilbe.dynamicinventory.inventory.InventoryLayout;
import dev.gilbe.dynamicinventory.inventory.InventoryShape;
import dev.gilbe.dynamicinventory.inventory.InventorySlotLocks;

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
        if (source != null && (InventorySlotLocks.locked(container, source.getSlotNumber(), player)
            || !sameItemOnly && PanelSortScope.reserved(source))) ci.cancel();
    }

    @Inject(method = "moveItemStack", at = @At("HEAD"), cancellable = true)
    private static void dynamicinventory$protectSingleSource(EntityPlayer player, Container container,
        SlotAccessor source, boolean emptyOnly, int amount, CallbackInfo ci) {
        if (source != null && InventorySlotLocks.locked(container, source.getSlotNumber(), player)) ci.cancel();
    }

    @Inject(
        method = "dropItems(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/inventory/Container;Lcom/cleanroommc/bogosorter/mixins/early/minecraft/SlotAccessor;Z)V",
        at = @At("HEAD"),
        cancellable = true)
    private static void dynamicinventory$protectDropSource(EntityPlayer player, Container container,
        SlotAccessor source, boolean sameItemOnly, CallbackInfo ci) {
        if (source != null && InventorySlotLocks.locked(container, source.getSlotNumber(), player)) ci.cancel();
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
        if (!sameItemOnly) return PanelSortScope.automaticTargets(original);
        if (BogoSortAPI.isPlayerSlot(source)) return original;
        ContainerInventoryRegion binding = ContainerInventoryRegion.of(container);
        return binding != null && binding.inventory == player.inventory
            ? dynamicinventory$storage(container, binding, player, true)
            : PanelSortScope.automaticTargets(original);
    }

    // The single-item shortcut has native sortable and fallback branches. Each removes pins before
    // both insert modes (empty-only or merge); keep its eligible slots and apply Main's routing.
    @ModifyExpressionValue(
        method = "moveItemStack",
        at = @At(
            value = "INVOKE",
            target = "Lcom/cleanroommc/bogosorter/ShortcutHandler;withoutPinned(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/inventory/Container;Ljava/util/List;[I)Ljava/util/List;"),
        require = 0)
    private static List<SlotAccessor> dynamicinventory$singleTargets(List<SlotAccessor> slots) {
        return PanelSortScope.automaticTargets(slots);
    }

    @WrapOperation(
        method = { dynamicinventory$MOVE_ALL, "moveItemStack" },
        at = @At(
            value = "INVOKE",
            target = "Lcom/cleanroommc/bogosorter/BogoSortAPI;insert(Lnet/minecraft/inventory/Container;Ljava/util/List;Lnet/minecraft/item/ItemStack;)Lnet/minecraft/item/ItemStack;"))
    private static ItemStack dynamicinventory$insert(Container container, List<SlotAccessor> slots, ItemStack stack,
        Operation<ItemStack> original, @Local(argsOnly = true) EntityPlayer player) {
        return dynamicinventory$insertIntoPlayer(
            container,
            slots,
            stack,
            player,
            (targets, remaining) -> original.call(container, targets, remaining));
    }

    @WrapOperation(
        method = "moveItemStack",
        at = @At(
            value = "INVOKE",
            target = "Lcom/cleanroommc/bogosorter/BogoSortAPI;insert(Lnet/minecraft/inventory/Container;Ljava/util/List;Lnet/minecraft/item/ItemStack;Z)Lnet/minecraft/item/ItemStack;"))
    private static ItemStack dynamicinventory$insertEmpty(Container container, List<SlotAccessor> slots,
        ItemStack stack, boolean empty, Operation<ItemStack> original, @Local(argsOnly = true) EntityPlayer player) {
        return dynamicinventory$insertIntoPlayer(
            container,
            slots,
            stack,
            player,
            (targets, remaining) -> original.call(container, targets, remaining, empty));
    }

    @Unique
    private static ItemStack dynamicinventory$insertIntoPlayer(Container container, List<SlotAccessor> slots,
        ItemStack stack, EntityPlayer player,
        java.util.function.BiFunction<List<SlotAccessor>, ItemStack, ItemStack> insert) {
        InventoryShape shape = InventoryLayout.of(player.inventory);
        java.util.function.ToIntFunction<SlotAccessor> index = slot -> InventorySlotLocks
            .index(player.inventory, container.getSlot(slot.getSlotNumber()));
        if (slots.isEmpty() || slots.stream()
            .anyMatch(slot -> {
                int logical = index.applyAsInt(slot);
                return logical < 0 || logical >= shape.mainSlots();
            })) return insert.apply(slots, stack);
        var destinations = new ArrayList<>(slots);
        var hotbar = new boolean[InventoryShape.HOTBAR_SLOTS];
        for (SlotAccessor slot : slots) {
            int logical = index.applyAsInt(slot);
            if (logical < hotbar.length) hotbar[logical] = true;
        }
        // Bogo's player sorting group can omit the active hotbar. Include its existing
        // slots for incoming transfers, retaining Bogo's pin and slot validity rules.
        for (net.minecraft.inventory.Slot slot : container.inventorySlots) {
            int logical = InventorySlotLocks.index(player.inventory, slot);
            if (logical < 0 || logical >= hotbar.length
                || hotbar[logical]
                || !AutomaticInventoryRouting.transfer(player.inventory, logical)) continue;
            SlotAccessor candidate = BogoSortAPI.getSlot(container, slot.slotNumber);
            if (!PinnedSlots.isPinned(player, candidate)) {
                destinations.add(candidate);
                hotbar[logical] = true;
            }
        }
        AutomaticInventoryRouting.orderPlayerSlots(destinations, index);
        int split = 0;
        while (split < destinations.size() && index.applyAsInt(destinations.get(split)) < hotbar.length) split++;
        ItemStack remaining = split == 0 ? stack : insert.apply(destinations.subList(0, split), stack);
        return remaining == null || split == destinations.size() ? remaining
            : insert.apply(destinations.subList(split, destinations.size()), remaining);
    }

    // Bogo's Forestry output uses a separate preflight and commit. Apply the same filter to both.
    @ModifyExpressionValue(
        method = "moveForestryWorktableOutput",
        at = @At(
            value = "INVOKE",
            target = "Lcom/cleanroommc/bogosorter/common/sort/SlotGroup;getSlots()Ljava/util/List;"),
        require = 0)
    private static List<SlotAccessor> dynamicinventory$forestryTargets(List<SlotAccessor> slots) {
        return PanelSortScope.automaticTargets(slots);
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
