package dev.gilbe.dynamicinventory.compat;

import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.tileentity.TileEntityFurnace;

import dev.gilbe.dynamicinventory.api.InventoryTransfers;
import dev.gilbe.dynamicinventory.api.InventoryUi;
import dev.gilbe.dynamicinventory.api.InventoryUiAdapter;
import dev.gilbe.dynamicinventory.api.NativeUiLayout;

/** Vanilla keeps its containers, slot rules and callbacks; these declarations add our shared player panel. */
public final class VanillaInventoryCompatibility {

    private VanillaInventoryCompatibility() {}

    public static void registerInventoryAdapters() {
        // Keep existing IDs/revisions: saved chest/workbench/furnace editor layouts still apply.
        register(
            "chest",
            "net.minecraft.inventory.ContainerChest",
            "net.minecraft.client.gui.inventory.GuiChest",
            null);
        register(
            "workbench",
            "net.minecraft.inventory.ContainerWorkbench",
            "net.minecraft.client.gui.inventory.GuiCrafting",
            InventoryTransfers.toHotbar());
        register(
            "furnace",
            "net.minecraft.inventory.ContainerFurnace",
            "net.minecraft.client.gui.inventory.GuiFurnace",
            (container, player, id, binding) -> {
                ItemStack stack = container.getSlot(id)
                    .getStack();
                if (stack == null) return null;
                if (FurnaceRecipes.smelting()
                    .getSmeltingResult(stack) != null)
                    return InventoryTransfers.toSlots(0)
                        .transfer(container, player, id, binding);
                if (TileEntityFurnace.isItemFuel(stack)) return InventoryTransfers.toSlots(1)
                    .transfer(container, player, id, binding);
                return InventoryTransfers.toHotbar()
                    .transfer(container, player, id, binding);
            });

        // Dropper uses the dispenser classes; ender/double/minecart chests use ContainerChest.
        // Hopper minecarts also reuse the ordinary hopper container and screen.
        register(
            "dispenser",
            "net.minecraft.inventory.ContainerDispenser",
            "net.minecraft.client.gui.inventory.GuiDispenser",
            null);
        register("hopper", "net.minecraft.inventory.ContainerHopper", "net.minecraft.client.gui.GuiHopper", null);
        register(
            "brewing-stand",
            "net.minecraft.inventory.ContainerBrewingStand",
            "net.minecraft.client.gui.inventory.GuiBrewingStand",
            null);
        // Native enchanting inserts ONE item, brewing limits bottles, beacon checks valid payment,
        // and horses prioritize armor/saddle before chest storage. Do not replace these native rules.
        register(
            "enchantment",
            "net.minecraft.inventory.ContainerEnchantment",
            "net.minecraft.client.gui.GuiEnchantment",
            null);
        register(
            "beacon",
            "net.minecraft.inventory.ContainerBeacon",
            "net.minecraft.client.gui.inventory.GuiBeacon",
            null);
        register(
            "horse",
            "net.minecraft.inventory.ContainerHorseInventory",
            "net.minecraft.client.gui.inventory.GuiScreenHorseInventory",
            null);

        // Only these two native methods exclude appended source IDs with a fixed upper bound.
        // Preserve native output pickup: it owns repair costs/consumption and merchant trade callbacks.
        register(
            "anvil",
            "net.minecraft.inventory.ContainerRepair",
            "net.minecraft.client.gui.GuiRepair",
            InventoryTransfers.toSlots(0, 1));
        // Vanilla shift-click moves player items between storage/hotbar; it does not autofill trades.
        register(
            "merchant",
            "net.minecraft.inventory.ContainerMerchant",
            "net.minecraft.client.gui.GuiMerchant",
            InventoryTransfers.toHotbar());
    }

    /** Null policy explicitly selects the container's native transfer implementation. */
    private static void register(String id, String container, String gui,
        dev.gilbe.dynamicinventory.api.InventoryTransferPolicy extras) {
        InventoryUiAdapter.Builder adapter = InventoryUiAdapter.builder("dynamicinventory:" + id, container)
            .gui(gui);
        // Keep these vanilla-width machines vertical, independent of old saved wide layouts.
        if ("workbench".equals(id) || "furnace".equals(id)) adapter.layout(
            NativeUiLayout.builder()
                .backgroundInset(4)
                .fixedPlayerColumns(9)
                .followMachineMode(true)
                .build());
        if (extras == null) adapter.nativeTransfers();
        else adapter.extraTransfers(extras);
        InventoryUi.register(adapter.build());
    }
}
