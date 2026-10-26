package dev.gilbe.dynamicinventory.mixins.forestry;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import dev.gilbe.dynamicinventory.api.InventoryUi;
import dev.gilbe.dynamicinventory.api.PlayerInventoryBinding;
import dev.gilbe.dynamicinventory.compat.ForestryCompatibility;

/** Change only routing; the enclosing native method still owns extraction and crafting callbacks. */
@Pseudo
@Mixin(targets = "forestry.core.utils.SlotUtil", remap = false)
public abstract class ForestrySlotUtilMixin {

    @Shadow
    private static boolean shiftItemStack(List<Slot> slots, ItemStack stack, int index, int count, boolean crafting) {
        throw new AssertionError();
    }

    @Shadow
    private static boolean shiftToMachineInventory(List<Slot> slots, ItemStack stack, int count) {
        throw new AssertionError();
    }

    @Shadow
    private static boolean shiftItemStackToRangeMerge(List<Slot> slots, ItemStack stack, int start, int count) {
        throw new AssertionError();
    }

    @Shadow
    private static boolean shiftItemStackToRangeOpenSlots(List<Slot> slots, ItemStack stack, int start, int count) {
        throw new AssertionError();
    }

    @Redirect(
        method = "transferStackInSlot",
        at = @At(
            value = "INVOKE",
            target = "Lforestry/core/utils/SlotUtil;shiftItemStack(Ljava/util/List;Lnet/minecraft/item/ItemStack;IIZ)Z"))
    private static boolean dynamicinventory$route(List<Slot> slots, ItemStack stack, int index, int count,
        boolean crafting, List<Slot> originalSlots, EntityPlayer player, int originalIndex) {
        Container container = player.openContainer;
        PlayerInventoryBinding binding = InventoryUi.getBinding(container);
        if (container.inventorySlots != slots || !ForestryCompatibility.handles(container)
            || binding == null
            || binding.getPlayerInventory() != player.inventory)
            return shiftItemStack(slots, stack, index, count, crafting);

        int logical = binding.logicalFromContainerId(index);
        // Forestry's original 36 slots remain first. Do not let its machine loop consume appended player slots.
        if ((logical >= 0 || crafting) && shiftToMachineInventory(slots, stack, binding.getNativeSlotCount()))
            return true;

        List<Slot> storage = new ArrayList<>();
        for (int id : binding.getStorageSlotIds()) storage.add(container.getSlot(id));
        List<Slot> hotbar = new ArrayList<>();
        for (int id : binding.getHotbarSlotIds()) hotbar.add(container.getSlot(id));
        if (logical >= 0) {
            List<Slot> destination = logical < 9 ? storage : hotbar;
            boolean changed = shiftItemStackToRangeMerge(destination, stack, 0, destination.size());
            return shiftItemStackToRangeOpenSlots(destination, stack, 0, destination.size()) | changed;
        }
        // Preserve native order: merge both regions before using any empty slot, hotbar first.
        boolean changed = shiftItemStackToRangeMerge(hotbar, stack, 0, hotbar.size());
        changed |= shiftItemStackToRangeMerge(storage, stack, 0, storage.size());
        changed |= shiftItemStackToRangeOpenSlots(hotbar, stack, 0, hotbar.size());
        changed |= shiftItemStackToRangeOpenSlots(storage, stack, 0, storage.size());
        return changed;
    }
}
