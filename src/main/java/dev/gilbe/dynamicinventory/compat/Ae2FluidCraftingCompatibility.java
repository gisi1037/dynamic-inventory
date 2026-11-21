package dev.gilbe.dynamicinventory.compat;

import dev.gilbe.dynamicinventory.api.InventoryUi;
import dev.gilbe.dynamicinventory.api.InventoryUiAdapter;
import dev.gilbe.dynamicinventory.api.NativeUiLayout;
import dev.gilbe.dynamicinventory.compat.ae2.AppliedEnergisticsContainerAccess;

/** Exact AE2 Fluid Crafting hosts share AE2's native slots, transfers and owner locks. */
public final class Ae2FluidCraftingCompatibility {

    private static final String ROOT = "com.glodblock.github.client.gui.";
    private static final String[] HOSTS = { "FluidAutoFiller", "FluidInterface", "FluidPacketDecoder",
        "FluidPatternEncoder", "IngredientBuffer", "LargeIngredientBuffer", "LevelMaintainer", "LevelTerminal",
        "LevelWireless", "MagnetFilter", "OCPatternEditor", "SuperStockReplenisher" };
    private static final NativeUiLayout LAYOUT = NativeUiLayout.builder()
        .fixedPlayerColumns(9)
        .backgroundInset(4)
        .build();

    private Ae2FluidCraftingCompatibility() {}

    public static void registerInventoryAdapters() {
        for (String type : HOSTS) InventoryUi.register(
            InventoryUiAdapter
                .builder(
                    "dynamicinventory:ae2fc/" + type.toLowerCase(java.util.Locale.ROOT),
                    ROOT + "container.Container" + type)
                .gui(ROOT + "Gui" + type)
                .compatibilityRevision(3)
                .layout(LAYOUT)
                .additionalSlots(
                    (container, inventory, raw) -> ((AppliedEnergisticsContainerAccess) container)
                        .dynamicinventory$playerSlot(inventory, raw))
                .nativeTransfers()
                .build());
        InventoryUi.registerPresentation("dynamicinventory:ae2_level_emitter", ROOT + "GuiLevelEmitterProxy", LAYOUT);
    }

    static boolean panelHost(InventoryUiAdapter adapter, String container) {
        if (adapter.getCompatibilityRevision() < 3) return false;
        for (String type : HOSTS) if (adapter.getId()
            .equals("dynamicinventory:ae2fc/" + type.toLowerCase(java.util.Locale.ROOT))
            && container.equals(ROOT + "container.Container" + type)
            && container.equals(adapter.getContainerClassName())
            && adapter.getGuiClassName()
                .equals(ROOT + "Gui" + type))
            return true;
        return false;
    }

    static boolean alternateScreen(InventoryUiAdapter adapter, String screen) {
        return adapter.getId()
            .equals("dynamicinventory:ae2_level_emitter") && screen.equals(ROOT + "GuiLevelEmitterProxy");
    }
}
