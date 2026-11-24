package dev.gilbe.dynamicinventory.compat;

import dev.gilbe.dynamicinventory.api.InventoryUi;
import dev.gilbe.dynamicinventory.api.InventoryUiAdapter;
import dev.gilbe.dynamicinventory.api.NativeUiLayout;

/** Galacticraft-owned integrations; string targets avoid linking its client API on a server. */
public final class GalacticraftCompatibility {

    private static final String EQUIPMENT_ID = "dynamicinventory:galacticraft_equipment";
    private static final String EQUIPMENT_CONTAINER = "micdoodle8.mods.galacticraft.core.inventory.ContainerExtendedInventory";
    private static final String EQUIPMENT_GUI = "micdoodle8.mods.galacticraft.core.client.gui.container.GuiExtendedInventory";

    private GalacticraftCompatibility() {}

    public static void registerInventoryAdapters() {
        InventoryUi.register(
            InventoryUiAdapter.builder(EQUIPMENT_ID, EQUIPMENT_CONTAINER)
                .gui(EQUIPMENT_GUI)
                .compatibilityRevision(2)
                .layout(
                    NativeUiLayout.builder()
                        .topPadding(InventoryTabsCompatibility.TAB_HEIGHT)
                        .backgroundInset(4) // Three pixels leave one white texel of the native upper-left bevel.
                        .build())
                .nativeTransfers()
                .build());
    }

    static boolean panelHost(InventoryUiAdapter adapter, String container) {
        return EQUIPMENT_ID.equals(adapter.getId()) && EQUIPMENT_CONTAINER.equals(container)
            && EQUIPMENT_CONTAINER.equals(adapter.getContainerClassName())
            && EQUIPMENT_GUI.equals(adapter.getGuiClassName());
    }
}
