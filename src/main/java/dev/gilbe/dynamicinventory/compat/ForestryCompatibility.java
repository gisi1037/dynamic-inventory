package dev.gilbe.dynamicinventory.compat;

import net.minecraft.inventory.Container;

import dev.gilbe.dynamicinventory.api.InventoryUi;
import dev.gilbe.dynamicinventory.api.InventoryUiAdapter;
import dev.gilbe.dynamicinventory.api.NativeUiLayout;

/** Reviewed stationary Forestry hosts. Item-backed and paged inventories require separate contracts. */
public final class ForestryCompatibility {

    private ForestryCompatibility() {}

    public static void registerInventoryAdapters() {
        machine("core", "Analyzer", 90);
        for (String name : new String[] { "BeeHousing", "Alveary", "AlvearyHygroregulator", "AlvearySieve",
            "AlvearySwarmer" }) machine("apiculture", name, 0);
        for (String name : new String[] { "EngineBiogas", "EngineElectric", "EnginePeat", "Generator" })
            machine("energy", name, 80);
        for (String name : new String[] { "Bottler", "Carpenter", "Centrifuge", "Fabricator", "Fermenter", "Moistener",
            "Raintank", "Squeezer", "Still", "Worktable" }) machine("factory", name, 0);
        machine("farming", "Farm", 0);
    }

    private static void machine(String module, String name, int minimumHeader) {
        String root = "forestry." + module + ".gui.";
        InventoryUi.register(
            InventoryUiAdapter
                .builder(
                    "dynamicinventory:forestry/" + name.toLowerCase(java.util.Locale.ROOT),
                    root + "Container" + name)
                .gui(root + "Gui" + name)
                .layout(
                    NativeUiLayout.builder()
                        .backgroundInset(4)
                        .fixedPlayerColumns(9)
                        .minimumHeaderHeight(minimumHeader)
                        .build())
                // SlotUtil's shared bridge extends its ranges, retaining Forestry's native callbacks and filters.
                .nativeTransfers()
                .build());
    }

    public static boolean handles(Container container) {
        InventoryUiAdapter adapter = InventoryUi.findAdapter(container);
        return adapter != null && adapter.getId()
            .startsWith("dynamicinventory:forestry/");
    }
}
