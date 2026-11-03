package dev.gilbe.dynamicinventory.qualification;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.cleanroommc.bogosorter.mixins.early.minecraft.SlotAccessor;

import dev.gilbe.dynamicinventory.api.InventoryUi;
import dev.gilbe.dynamicinventory.api.InventoryUiAdapter;
import dev.gilbe.dynamicinventory.compat.AppliedEnergisticsCompatibility;
import dev.gilbe.dynamicinventory.compat.PanelSortScope;
import dev.gilbe.dynamicinventory.container.PlayerInventoryTransfers;

/** Actual item merging and the shared sort boundary without a game, world or client preferences. */
public final class PanelOperationsFixtures {

    private PanelOperationsFixtures() {}

    public static void main(String[] args) {
        AppliedEnergisticsCompatibility.registerInventoryAdapters();
        for (String tool : new String[] { "NetworkTool", "AdvancedNetworkTool" }) {
            InventoryUiAdapter adapter = InventoryUi.getRegisteredAdapters()
                .stream()
                .filter(
                    value -> value.getContainerClassName()
                        .equals("appeng.container.implementations.Container" + tool))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing " + tool));
            check(
                adapter.getGuiClassName()
                    .equals("appeng.client.gui.implementations.Gui" + tool),
                "Exact GUI pair");
            check(!adapter.isIncludingSubclasses() && adapter.getCompatibilityRevision() == 3, "Exact reviewed host");
            check(
                adapter.getTransferPolicy() == null && adapter.getAdditionalSlotFactory() != null,
                "Native transfers/slot factory");
        }
        List<SlotAccessor> nativeSlots = Arrays.asList(slot(9), slot(13), slot(71), slot(99));
        check(PanelSortScope.filter(nativeSlots) == nativeSlots, "Unscoped sorter is unchanged");
        PanelSortScope.run(new int[] { 13, 99 }, () -> {
            check(
                ids(PanelSortScope.filter(nativeSlots)).equals(Arrays.asList(13, 99)),
                "Only Main membership is sorted");
            PanelSortScope.run(
                new int[] { 9 },
                () -> check(ids(PanelSortScope.filter(nativeSlots)).equals(Arrays.asList(9)), "Nested scope"));
            check(ids(PanelSortScope.filter(nativeSlots)).equals(Arrays.asList(13, 99)), "Scope restored");
        });
        try {
            PanelSortScope.run(new int[] { 9 }, () -> { throw new IllegalStateException("fixture"); });
        } catch (IllegalStateException expected) {
            check(PanelSortScope.filter(nativeSlots) == nativeSlots, "Scope removed after failure");
        }
        check(nativeSlots.size() == 4, "Cached native group never mutated");

        Item item = new Item();
        FixtureContainer container = new FixtureContainer();
        int[] destinations = { 1, 2, 3, 4 };
        container.inventory.setInventorySlotContents(0, new ItemStack(item, 12));
        container.inventory.setInventorySlotContents(1, new ItemStack(item, 64));
        container.inventory.setInventorySlotContents(3, new ItemStack(item, 60));
        PlayerInventoryTransfers.transferTo(container, null, 0, destinations, 2);
        check(
            container.getSlot(2)
                .getStack().stackSize == 12,
            "Hotbar empty slot wins before Main merge");
        check(
            container.getSlot(3)
                .getStack().stackSize == 60,
            "Main unchanged while hotbar accepts all");
        container.inventory.setInventorySlotContents(0, new ItemStack(item, 12));
        container.inventory.setInventorySlotContents(2, new ItemStack(item, 64));
        container.inventory.setInventorySlotContents(5, new ItemStack(item, 7));
        PlayerInventoryTransfers.transferTo(container, null, 0, destinations, 2);
        check(
            container.getSlot(3)
                .getStack().stackSize == 64
                && container.getSlot(4)
                    .getStack().stackSize == 8,
            "Full hotbar falls back to opposite Main half, merging before empty slots");
        check(
            container.getSlot(5)
                .getStack().stackSize == 7,
            "Satellite untouched");
        container.inventory.setInventorySlotContents(0, new ItemStack(item, 12));
        container.inventory.setInventorySlotContents(4, new ItemStack(item, 64));
        check(
            PlayerInventoryTransfers.transferTo(container, null, 0, destinations, 2) == null,
            "Full destinations no-op");
        check(
            container.getSlot(0)
                .getStack().stackSize == 12,
            "No loss when full");
        System.out.println(
            "PASS Network Tool exact registrations, isolated sorting, hotbar priority, full-hotbar Main fallback and item preservation");
    }

    private static List<Integer> ids(List<SlotAccessor> slots) {
        return slots.stream()
            .map(SlotAccessor::getSlotNumber)
            .collect(java.util.stream.Collectors.toList());
    }

    private static SlotAccessor slot(int id) {
        return (SlotAccessor) Proxy.newProxyInstance(
            SlotAccessor.class.getClassLoader(),
            new Class<?>[] { SlotAccessor.class },
            (proxy, method, args) -> {
                if (method.getName()
                    .equals("getSlotNumber")) return id;
                if (method.getName()
                    .equals("getInventory")) return null;
                throw new AssertionError("Scope should only inspect canonical IDs");
            });
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static final class FixtureContainer extends Container {

        final InventoryBasic inventory = new InventoryBasic("fixture", false, 6);

        FixtureContainer() {
            for (int i = 0; i < 6; i++) addSlotToContainer(new Slot(inventory, i, 0, 0));
        }

        @Override
        public boolean canInteractWith(EntityPlayer player) {
            return true;
        }
    }
}
