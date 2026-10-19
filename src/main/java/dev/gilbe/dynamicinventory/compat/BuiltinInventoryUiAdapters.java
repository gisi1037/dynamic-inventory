package dev.gilbe.dynamicinventory.compat;

import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.tileentity.TileEntityFurnace;

import dev.gilbe.dynamicinventory.api.InventoryTransfers;
import dev.gilbe.dynamicinventory.api.InventoryUi;
import dev.gilbe.dynamicinventory.api.InventoryUiAdapter;
import dev.gilbe.dynamicinventory.api.NativeUiLayout;

/** Built-in integrations use the same registration contract available to other mods. */
public final class BuiltinInventoryUiAdapters {

    private BuiltinInventoryUiAdapters() {}

    public static void initialize() {
        InventoryUi.register(
            InventoryUiAdapter.builder("dynamicinventory:chest", "net.minecraft.inventory.ContainerChest")
                .gui("net.minecraft.client.gui.inventory.GuiChest")
                .nativeTransfers()
                .build());
        InventoryUi.register(
            InventoryUiAdapter.builder("dynamicinventory:workbench", "net.minecraft.inventory.ContainerWorkbench")
                .gui("net.minecraft.client.gui.inventory.GuiCrafting")
                .extraTransfers(InventoryTransfers.toHotbar())
                .build());
        InventoryUi.register(
            InventoryUiAdapter.builder("dynamicinventory:furnace", "net.minecraft.inventory.ContainerFurnace")
                .gui("net.minecraft.client.gui.inventory.GuiFurnace")
                .extraTransfers((container, player, id, binding) -> {
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
                })
                .build());
        if (!PackCompatibility.enabled()) return;
        InventoryUi.register(
            InventoryUiAdapter
                .builder("dynamicinventory:tinkers_station", "tconstruct.tools.inventory.CraftingStationContainer")
                .compatibilityRevision(2)
                .gui("tconstruct.tools.gui.CraftingStationGui")
                .nativeTransfers()
                .build());
        InventoryUi.register(
            InventoryUiAdapter
                .builder(
                    "dynamicinventory:galacticraft_equipment",
                    "micdoodle8.mods.galacticraft.core.inventory.ContainerExtendedInventory")
                .gui("micdoodle8.mods.galacticraft.core.client.gui.container.GuiExtendedInventory")
                .layout(
                    NativeUiLayout.builder()
                        .topPadding(28)
                        .build())
                .nativeTransfers()
                .build());
        InventoryUi.register(
            InventoryUiAdapter
                .builder(
                    "dynamicinventory:discovered/guicompressedchest",
                    "wanion.avaritiaddons.block.chest.compressed.ContainerCompressedChest")
                .gui("wanion.avaritiaddons.block.chest.compressed.GuiCompressedChest")
                .layout(
                    NativeUiLayout.builder()
                        .topPadding(0) // TODO measure tab/header padding.
                        .minimumHeaderHeight(0) // TODO measure native header geometry.
                        .backgroundInset(0) // TODO choose per-frame clipping after inspection.
                        .build())
                .extraTransfers(InventoryTransfers.toNativeSlots())
                .build());

    }
}
