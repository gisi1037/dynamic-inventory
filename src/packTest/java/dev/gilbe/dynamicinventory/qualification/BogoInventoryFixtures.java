package dev.gilbe.dynamicinventory.qualification;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import com.cleanroommc.bogosorter.api.IBogoSortAPI;
import com.cleanroommc.bogosorter.common.sort.GuiSortingContext;
import com.cleanroommc.bogosorter.common.sort.SlotGroup;
import com.cleanroommc.bogosorter.mixins.early.minecraft.SlotAccessor;

import dev.gilbe.dynamicinventory.compat.BogoInventoryCompatibility;
import dev.gilbe.dynamicinventory.container.ContainerInventoryRegion;
import dev.gilbe.dynamicinventory.inventory.InventoryLayout;
import tconstruct.tools.gui.ChestSlot;

/** Exercises native Bogo contexts with the station's slot order, without sorting items or changing world blocks. */
public final class BogoInventoryFixtures {

    private BogoInventoryFixtures() {}

    public static List<String> run(EntityPlayer player) {
        List<String> passed = new ArrayList<>();
        ItemStack[] before = new ItemStack[player.inventory.mainInventory.length];
        for (int raw = 0; raw < before.length; raw++)
            before[raw] = ItemStack.copyItemStack(player.inventory.mainInventory[raw]);
        ItemStack cursor = ItemStack.copyItemStack(player.inventory.getItemStack());
        IBogoSortAPI.getInstance()
            .addCompat(FixtureContainer.class, BogoInventoryCompatibility::addChestGroup);
        for (int chestSize : new int[] { 0, 27, 243 }) {
            FixtureContainer container = new FixtureContainer(player.inventory, chestSize);
            ContainerInventoryRegion binding = ContainerInventoryRegion.of(container);
            check(binding != null && binding.nativeSlotCount == 46 + chestSize, "native station binding " + chestSize);
            // create() avoids replacing the context cached for an actually open player GUI.
            GuiSortingContext context = GuiSortingContext.create(container);
            Set<Integer> seen = new HashSet<>();
            Set<Integer> storage = new HashSet<>();
            int chest = 0, hotbar = 0, machineGroups = 0;
            for (SlotGroup group : context.getSlotGroups()) {
                if (!group.isPlayerInventory()) machineGroups++;
                for (SlotAccessor slot : group.getSlots()) {
                    check(seen.add(slot.getSlotNumber()), "groups are disjoint at slot " + slot.getSlotNumber());
                    if (group.isPlayerInventory()) {
                        check(slot.getInventory() == player.inventory, "player group keeps canonical backing");
                        int ordinary = InventoryLayout.ordinaryIndex(slot.callGetSlotIndex());
                        check(ordinary >= 0, "equipment stays outside ordinary sorting groups");
                        if (group.isHotbar()) {
                            check(ordinary < 9, "hotbar group contains only hotbar slots");
                            hotbar++;
                        } else {
                            check(ordinary >= 9, "storage group excludes hotbar");
                            storage.add(slot.getSlotNumber());
                        }
                    } else {
                        check(
                            slot instanceof ChestSlot && slot.getInventory() == container.side,
                            "machine group contains only native attached-chest slots");
                        check(
                            slot.getSlotNumber() < binding.nativeSlotCount,
                            "machine group excludes appended player storage");
                        chest++;
                    }
                }
            }
            for (int id : binding.getStorageSlotIds()) check(storage.contains(id), "all canonical storage is sortable");
            check(
                storage.size() == InventoryLayout.storageSlots() && hotbar == 9,
                "native player groups cover storage and hotbar exactly once");
            check(
                chest == chestSize && machineGroups == (chestSize == 0 ? 0 : 1),
                "attached chest has its own exact group, including no-chest station");
            passed.add(
                "Bogo station-layout chest=" + chestSize
                    + ": canonical storage="
                    + storage.size()
                    + ", hotbar=9, machine="
                    + chest
                    + ", no overlapping groups or equipment");
        }
        for (int raw = 0; raw < before.length; raw++) check(
            ItemStack.areItemStacksEqual(before[raw], player.inventory.mainInventory[raw]),
            "context creation preserves player contents " + raw);
        check(
            ItemStack.areItemStacksEqual(cursor, player.inventory.getItemStack()),
            "context creation preserves cursor");
        passed.add("Bogo context fixtures preserve all player contents and cursor without sorting");
        return passed;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError("Bogo inventory: " + message);
    }

    private static final class FixtureContainer extends Container {

        private final IInventory side;

        private FixtureContainer(InventoryPlayer player, int size) {
            IInventory crafting = new InventoryBasic("Bogo fixture crafting", false, 10);
            for (int index = 0; index < 10; index++) addSlotToContainer(new Slot(crafting, index, 0, 0));
            for (int raw = 9; raw < 36; raw++) addSlotToContainer(new Slot(player, raw, 0, 0));
            for (int raw = 0; raw < 9; raw++) addSlotToContainer(new Slot(player, raw, 0, 0));
            side = new InventoryBasic("Bogo fixture chest", false, size);
            for (int index = 0; index < size; index++)
                addSlotToContainer(new ChestSlot(side, index, index, index % 6 * 18, index / 6 * 18, 2));
            ContainerInventoryRegion.attach(this, player);
        }

        @Override
        public boolean canInteractWith(EntityPlayer player) {
            return true;
        }
    }
}
