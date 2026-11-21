package dev.gilbe.dynamicinventory.compat;

import dev.gilbe.dynamicinventory.api.InventoryUi;
import dev.gilbe.dynamicinventory.api.InventoryUiAdapter;
import dev.gilbe.dynamicinventory.api.NativeUiLayout;

/** Exact item-backed hosts with their native slot restrictions and transfer callbacks. */
public final class BackpackCompatibility {

    private static final String[][] HOSTS = {
        { "adventurebackpack", "dynamicinventory:adventurebackpack/backpack",
            "com.darkona.adventurebackpack.inventory.ContainerBackpack",
            "com.darkona.adventurebackpack.client.gui.GuiAdvBackpack" },
        { "Backpack", "dynamicinventory:backpack/storage", "de.eydamos.backpack.inventory.container.ContainerAdvanced",
            "de.eydamos.backpack.gui.GuiBackpack" },
        { "Backpack", "dynamicinventory:backpack/workbench",
            "de.eydamos.backpack.inventory.container.ContainerWorkbenchBackpack",
            "de.eydamos.backpack.gui.GuiWorkbenchBackpack" } };

    private BackpackCompatibility() {}

    static void registerInventoryAdapters() {
        boolean adventure = OptionalInventoryAdapters.reviewedVersion("adventurebackpack");
        boolean backpack = OptionalInventoryAdapters.reviewedVersion("Backpack");
        for (String[] host : HOSTS) {
            if (host[0].equals("Backpack") ? backpack : adventure) InventoryUi.register(
                InventoryUiAdapter.builder(host[1], host[2])
                    .gui(host[3])
                    .layout(
                        NativeUiLayout.builder()
                            .fixedPlayerColumns(9)
                            .backgroundInset(4)
                            .build())
                    .compatibilityRevision(2)
                    .nativeTransfers()
                    .build());
        }
    }

    static boolean panelHost(InventoryUiAdapter adapter, String container) {
        for (String[] host : HOSTS) {
            if (host[1].equals(adapter.getId()) && host[2].equals(container)
                && host[2].equals(adapter.getContainerClassName())
                && host[3].equals(adapter.getGuiClassName())) return true;
        }
        return false;
    }
}
