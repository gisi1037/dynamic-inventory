package dev.gilbe.dynamicinventory.compat;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import dev.gilbe.dynamicinventory.api.InventoryTransfers;
import dev.gilbe.dynamicinventory.api.InventoryUi;
import dev.gilbe.dynamicinventory.api.InventoryUiAdapter;

/** Exact replacements for vanilla screens enabled by the pack; no optional API classes are loaded here. */
public final class EtFuturumCompatibility {

    private EtFuturumCompatibility() {}

    public static void registerInventoryAdapters() {
        // Also checked by the late loader before applying the enchantment GUI hook.
        PackCompatibility.require("etfuturum", "EtFuturum");
        InventoryUi.register(
            InventoryUiAdapter
                .builder(
                    "dynamicinventory:etfuturum/brewing-stand",
                    "ganymedes01.etfuturum.inventory.ContainerNewBrewingStand")
                .gui("ganymedes01.etfuturum.client.gui.inventory.GuiNewBrewingStand")
                // Native transfer also handles blaze-powder fuel and single-bottle slots.
                .nativeTransfers()
                .build());
        if (OptionalInventoryAdapters.reviewedVersion("etfuturum")) {
            // Both hosts own vanilla-style transfers, but use different recipe registries.
            // Delegate their input/fuel checks and retain their native output/XP slots.
            NativeMachineAdapters.furnace(
                "dynamicinventory:etfuturum/blast-furnace",
                "ganymedes01.etfuturum.inventory.ContainerBlastFurnace",
                "ganymedes01.etfuturum.client.gui.inventory.GuiBlastFurnace");
            NativeMachineAdapters.furnace(
                "dynamicinventory:etfuturum/smoker",
                "ganymedes01.etfuturum.inventory.ContainerSmoker",
                "ganymedes01.etfuturum.client.gui.inventory.GuiSmoker");
        }
        InventoryUi.register(
            InventoryUiAdapter
                .builder("dynamicinventory:etfuturum/anvil", "ganymedes01.etfuturum.inventory.ContainerAnvil")
                .gui("ganymedes01.etfuturum.client.gui.inventory.GuiAnvil")
                // Inherits vanilla's bounded source transfer; preserve its own repair/result callbacks.
                .extraTransfers(InventoryTransfers.toSlots(0, 1))
                .build());
        InventoryUi.register(
            InventoryUiAdapter
                .builder(
                    "dynamicinventory:etfuturum/enchantment",
                    "ganymedes01.etfuturum.inventory.ContainerEnchantment")
                .gui("ganymedes01.etfuturum.client.gui.inventory.GuiEnchantment")
                .extraTransfers((container, player, id, binding) -> {
                    ItemStack stack = container.getSlot(id)
                        .getStack();
                    if (stack == null) return null;
                    // Ask the mod's fuel slot, preserving custom registered fuels. In noFuel mode
                    // slot 1 is already player storage. The shared transfer respects slot 0's
                    // one-item limit, unlike the native method's direct putStack of a copied stack.
                    Slot fuel = container.getSlot(1);
                    int destination = !(fuel.inventory instanceof InventoryPlayer) && fuel.isItemValid(stack) ? 1 : 0;
                    return InventoryTransfers.toSlots(destination)
                        .transfer(container, player, id, binding);
                })
                .build());
    }
}
