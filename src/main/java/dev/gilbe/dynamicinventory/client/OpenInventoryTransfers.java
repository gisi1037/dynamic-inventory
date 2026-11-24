package dev.gilbe.dynamicinventory.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.inventory.SlotCrafting;
import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.screen.ModularContainer;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.gtnewhorizons.modularui.api.forge.PlayerMainInvWrapper;
import com.gtnewhorizons.modularui.common.internal.wrapper.BaseSlot;

import appeng.container.slot.SlotFake;
import codechicken.lib.inventory.SlotDummy;
import cpw.mods.fml.common.Loader;
import dev.gilbe.dynamicinventory.inventory.InventorySlotLocks;

/** Read-only check before AE2 Pins takes over a native inventory shortcut. */
final class OpenInventoryTransfers {

    private OpenInventoryTransfers() {}

    static boolean canDeposit(EntityPlayer player, Container container, Slot source, ItemStack stack, boolean quickMove,
        int[] panel, IntFunction<ItemStack> panelStack) {
        if (container == player.inventoryContainer) return false;
        boolean mui1 = Loader.isModLoaded("modularui"), mui2 = Loader.isModLoaded("modularui2");
        List<? extends Slot> candidates = quickMove && mui2 ? Mui2.quickMoveSlots(container) : container.inventorySlots;
        List<Slot> targets = panel == null ? null : new ArrayList<>();
        for (Slot slot : candidates) {
            if (!external(slot, player, mui1, mui2)) continue;
            if (quickMove && mui2 && Mui2.sameGroup(source, slot)) continue;
            if (accepts(slot, stack)) return true;
            if (targets != null) targets.add(slot);
        }
        if (targets == null || targets.isEmpty()) return false;
        for (int id : panel) {
            Slot member = container.getSlot(id);
            if (member == source || InventorySlotLocks.locked(member, player)) continue;
            ItemStack item = panelStack.apply(id);
            if (item == null) continue;
            for (Slot target : targets) if (accepts(target, item)) return true;
        }
        return false;
    }

    static boolean canScroll(EntityPlayer player, Container container, Slot source, ItemStack stack, boolean pushAbove,
        boolean pushBelow) {
        if (container == player.inventoryContainer) return false;
        boolean mui1 = Loader.isModLoaded("modularui"), mui2 = Loader.isModLoaded("modularui2");
        boolean canPull = accepts(source, stack);
        for (Slot target : container.inventorySlots) {
            if (!external(target, player, mui1, mui2)) continue;
            boolean push = target.yDisplayPosition < source.yDisplayPosition ? pushAbove : pushBelow;
            if (push && accepts(target, stack)) return true;
            if (!push && canPull && matches(stack, target.getStack()) && target.canTakeStack(player)) return true;
        }
        return false;
    }

    private static boolean external(Slot slot, EntityPlayer player, boolean mui1, boolean mui2) {
        return slot.func_111238_b() && !(slot.inventory instanceof InventoryPlayer)
            && !(slot instanceof SlotCrafting)
            && !(slot instanceof SlotDummy)
            && !(slot instanceof SlotFake)
            && !InventorySlotLocks.locked(slot, player)
            && (!mui2 || Mui2.external(slot))
            && (!mui1 || Mui1.external(slot));
    }

    private static boolean accepts(Slot slot, ItemStack stack) {
        if (!slot.isItemValid(stack)) return false;
        ItemStack existing = slot.getStack();
        int limit = Math.min(slot.getSlotStackLimit(), stack.getMaxStackSize());
        return existing == null ? limit > 0
            : existing.stackSize < limit && stack.isStackable() && matches(stack, existing);
    }

    private static boolean matches(ItemStack stack, ItemStack existing) {
        return existing != null && existing.stackSize > 0
            && stack.isItemEqual(existing)
            && ItemStack.areItemStackTagsEqual(stack, existing);
    }

    private static final class Mui2 {

        static boolean external(Slot slot) {
            return !(slot instanceof ModularSlot modular)
                || !modular.isPhantom() && ModularSlot.getPlayerSlotPlayer(modular) == null;
        }

        static List<? extends Slot> quickMoveSlots(Container container) {
            return container instanceof ModularContainer modular ? modular.getShiftClickSlots()
                : container.inventorySlots;
        }

        static boolean sameGroup(Slot source, Slot target) {
            return source instanceof ModularSlot from && target instanceof ModularSlot to
                && from.getSlotGroup() == to.getSlotGroup();
        }
    }

    private static final class Mui1 {

        static boolean external(Slot slot) {
            return !(slot instanceof BaseSlot legacy)
                || !legacy.isPhantom() && !(legacy.getItemHandler() instanceof PlayerMainInvWrapper);
        }
    }
}
