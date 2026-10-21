package dev.gilbe.dynamicinventory.compat;

import dev.gilbe.dynamicinventory.api.InventoryUi;
import dev.gilbe.dynamicinventory.api.InventoryUiAdapter;

/** Tinkers-owned integrations. Called only after the profile and loaded-mod checks. */
public final class TinkersConstructCompatibility {

    private TinkersConstructCompatibility() {}

    public static void registerInventoryAdapters() {
        // Keep native crafting callbacks and attached-chest transfers. Register other
        // Tinkers containers individually after review; this is not a mod-wide allowlist.
        InventoryUi.register(
            InventoryUiAdapter
                .builder("dynamicinventory:tinkers_station", "tconstruct.tools.inventory.CraftingStationContainer")
                .compatibilityRevision(2)
                .gui("tconstruct.tools.gui.CraftingStationGui")
                .nativeTransfers()
                .build());
        if (OptionalInventoryAdapters.reviewedVersion("TConstruct")) {
            NativeMachineAdapters.furnace(
                "dynamicinventory:tinkers/furnace",
                "tconstruct.tools.inventory.FurnaceContainer",
                "tconstruct.tools.gui.FurnaceGui");
        }
    }
}
