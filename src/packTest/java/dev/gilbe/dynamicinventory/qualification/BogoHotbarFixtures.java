package dev.gilbe.dynamicinventory.qualification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import com.cleanroommc.bogosorter.mixins.early.minecraft.SlotAccessor;

import dev.gilbe.dynamicinventory.compat.PanelSortScope;
import dev.gilbe.dynamicinventory.compat.mui1.Mui1PlayerInventory;
import dev.gilbe.dynamicinventory.compat.mui2.Mui2PlayerInventory;
import dev.gilbe.dynamicinventory.inventory.HotbarBanks;
import dev.gilbe.dynamicinventory.inventory.InventoryLayout;

/** Native Bogo groups must exclude saved hotbars before both gathering and writing items. */
final class BogoHotbarFixtures {

    private BogoHotbarFixtures() {}

    static void check(EntityPlayer player, InventoryPlayer inventory) {
        int capacity = InventoryLayout.of(inventory)
            .storageSlots();
        int[] bank = java.util.stream.IntStream.range(capacity - 9, capacity)
            .toArray();
        HotbarBanks.receive(player, bank);
        for (int framework = 0; framework < 3; framework++) {
            List<SlotAccessor> group = new ArrayList<>();
            for (int logical = 0; logical < capacity + 9; logical++) {
                Slot slot = switch (framework) {
                    case 1 -> new Legacy(new Mui1PlayerInventory(inventory), logical);
                    case 2 -> new Modern(
                        new Mui2PlayerInventory(inventory),
                        InventoryLayout.of(inventory)
                            .rawIndex(logical));
                    default -> new Vanilla(
                        inventory,
                        InventoryLayout.of(inventory)
                            .rawIndex(logical));
                };
                slot.slotNumber = logical + 17; // IDs differ from raw indices and logical storage membership.
                group.add((SlotAccessor) slot);
            }
            List<SlotAccessor> available = PanelSortScope.filter(group);
            require(available.size() == capacity, "Unscoped native sort protects nine reserved cells");
            require(group.size() == capacity + 9, "Bogo's cached group is untouched");
            for (int i = 0; i < group.size(); i++)
                require(available.contains(group.get(i)) == (i < capacity), "Scattered canonical bank membership");
            PanelSortScope.run(new int[] { 17, 18, capacity + 25 }, () -> {
                require(
                    PanelSortScope.filter(group)
                        .equals(Arrays.asList(group.get(0), group.get(1))),
                    "Explicit panel scope cannot reintroduce a protected row");
            });
            HotbarBanks.receive(player, new int[0]);
            require(PanelSortScope.filter(group) == group, "Releasing a row restores native sorting");
            HotbarBanks.receive(player, bank);
        }
        HotbarBanks.receive(player, new int[0]);
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private interface Access extends SlotAccessor {

        default Slot slot() {
            return (Slot) this;
        }

        @Override
        default int bogo$getX() {
            return slot().xDisplayPosition;
        }

        @Override
        default int bogo$getY() {
            return slot().yDisplayPosition;
        }

        @Override
        default int getSlotNumber() {
            return slot().slotNumber;
        }

        @Override
        default int callGetSlotIndex() {
            return slot().getSlotIndex();
        }

        @Override
        default IInventory getInventory() {
            return slot().inventory;
        }

        @Override
        default void callPutStack(ItemStack stack) {
            slot().putStack(stack);
        }

        @Override
        default ItemStack callGetStack() {
            return slot().getStack();
        }

        @Override
        default int callGetSlotStackLimit() {
            return slot().getSlotStackLimit();
        }

        @Override
        default boolean callIsEnabled() {
            return slot().func_111238_b();
        }

        @Override
        default boolean callIsItemValid(ItemStack stack) {
            return slot().isItemValid(stack);
        }

        @Override
        default boolean callCanTakeStack(EntityPlayer player) {
            return slot().canTakeStack(player);
        }

        @Override
        default void callOnSlotChanged() {
            slot().onSlotChanged();
        }

        @Override
        default void callOnSlotChange(ItemStack oldStack, ItemStack newStack) {
            slot().onSlotChange(oldStack, newStack);
        }

        @Override
        default void callOnPickupFromSlot(EntityPlayer player, ItemStack stack) {
            slot().onPickupFromSlot(player, stack);
        }
    }

    private static final class Vanilla extends Slot implements Access {

        Vanilla(InventoryPlayer inventory, int raw) {
            super(inventory, raw, 0, 0);
        }
    }

    private static final class Legacy extends com.gtnewhorizons.modularui.common.internal.wrapper.BaseSlot
        implements Access {

        Legacy(Mui1PlayerInventory inventory, int logical) {
            super(inventory, logical);
        }
    }

    private static final class Modern extends com.cleanroommc.modularui.widgets.slot.ModularSlot implements Access {

        Modern(Mui2PlayerInventory inventory, int raw) {
            super(inventory, raw);
        }
    }
}
