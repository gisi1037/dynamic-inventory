package dev.gilbe.dynamicinventory.compat;

import dev.gilbe.dynamicinventory.api.InventoryUi;
import dev.gilbe.dynamicinventory.api.InventoryUiAdapter;
import dev.gilbe.dynamicinventory.api.NativeUiLayout;
import dev.gilbe.dynamicinventory.compat.ae2.AppliedEnergisticsBackground;
import dev.gilbe.dynamicinventory.compat.ae2.AppliedEnergisticsContainerAccess;

/** Exact native machine and terminal hosts reviewed in AE2 1080/1089. */
public final class AppliedEnergisticsCompatibility {

    private static final NativeUiLayout LAYOUT = NativeUiLayout.builder()
        .fixedPlayerColumns(9)
        .backgroundInset(4)
        .build();
    private static final NativeUiLayout SIDE_LAYOUT = NativeUiLayout.builder()
        .fixedPlayerColumns(9)
        .backgroundInset(4)
        .presentationRevision(2)
        .build();

    private AppliedEnergisticsCompatibility() {}

    public static void registerInventoryAdapters() {
        machine("grinder", "Grinder");
        machine("condenser", "Condenser");
        machine("chest", "Chest");
        machine("sky_chest", "SkyChest");
        machine("drive", "Drive");
        machine("vibration_chamber", "VibrationChamber");
        machine("inscriber", "Inscriber");
        machine("advanced_inscriber", "AdvancedInscriber");
        machine("io_port", "IOPort");
        machine("crystal_growth_chamber", "CrystalGrowthChamber");
        machine("molecular_assembler", "MAC");
        machine("wireless_access_point", "Wireless");
        machine("security_terminal", "Security");
        machine("quantum_link_chamber", "QNB");
        machine("spatial_io_port", "SpatialIOPort");
        machine("spatial_link_chamber", "SpatialLinkChamber");
        machine("interface", "Interface");
        machine("cell_workbench", "CellWorkbench");
        // Core wireless access uses the same MEMonitorable host as the wired ME Terminal.
        machine("me_terminal", "MEMonitorable");
        machine("crafting_terminal", "CraftingTerm");
        machine("pattern_terminal", "PatternTerm");
        machine("extended_pattern_terminal", "PatternTermEx");
        machine("portable_cell", "MEPortableCell");
        machine("interface_terminal", "InterfaceTerminal");
        machine("crafting_diagnostic_terminal", "CraftingDiagnosticTerminal");
    }

    private static void machine(String id, String type) {
        String gui = "appeng.client.gui.implementations.Gui" + type;
        InventoryUi.register(
            InventoryUiAdapter
                .builder("dynamicinventory:ae2_" + id, "appeng.container.implementations.Container" + type)
                .gui(gui)
                .layout(AppliedEnergisticsBackground.hasSidePanels(gui) ? SIDE_LAYOUT : LAYOUT)
                // AE2 casts every real slot to AppEngSlot and routes by isPlayerSide(), not fixed ranges.
                // Its addSlotToContainer override also assigns the slot's owning container for callbacks.
                .additionalSlots(
                    (container, inventory, raw) -> ((AppliedEnergisticsContainerAccess) container)
                        .dynamicinventory$playerSlot(inventory, raw))
                // Native handling retains filters, upgrades, outputs and AE2's server-only prediction rules.
                // Never add a generic fallback: some native paths legitimately mutate and return null.
                .nativeTransfers()
                .build());
    }
}
