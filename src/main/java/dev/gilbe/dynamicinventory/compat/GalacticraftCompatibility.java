package dev.gilbe.dynamicinventory.compat;

import dev.gilbe.dynamicinventory.api.InventoryUi;
import dev.gilbe.dynamicinventory.api.InventoryUiAdapter;
import dev.gilbe.dynamicinventory.api.NativeUiLayout;

/** Galacticraft-owned integrations; string targets avoid linking its client API on a server. */
public final class GalacticraftCompatibility {

    private GalacticraftCompatibility() {}

    public static void registerInventoryAdapters() {
        InventoryUi.register(
            InventoryUiAdapter
                .builder(
                    "dynamicinventory:galacticraft_equipment",
                    "micdoodle8.mods.galacticraft.core.inventory.ContainerExtendedInventory")
                .gui("micdoodle8.mods.galacticraft.core.client.gui.container.GuiExtendedInventory")
                .layout(
                    NativeUiLayout.builder()
                        .topPadding(28) // Reserve the native equipment-tab row.
                        .backgroundInset(4) // Three pixels leave one white texel of the native upper-left bevel.
                        .build())
                .nativeTransfers()
                .build());
    }
}
