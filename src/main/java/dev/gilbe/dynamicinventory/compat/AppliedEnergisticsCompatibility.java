package dev.gilbe.dynamicinventory.compat;

import dev.gilbe.dynamicinventory.api.InventoryUi;
import dev.gilbe.dynamicinventory.api.InventoryUiAdapter;
import dev.gilbe.dynamicinventory.api.NativeUiLayout;
import dev.gilbe.dynamicinventory.compat.ae2.AppliedEnergisticsBackground;
import dev.gilbe.dynamicinventory.compat.ae2.AppliedEnergisticsContainerAccess;

/** Exact native machine and terminal hosts reviewed in the pinned AE2 profiles. */
public final class AppliedEnergisticsCompatibility {

    private static final String WIRELESS_CRAFTING_ID = "dynamicinventory:ae2_wireless_crafting_terminal";
    private static final String WIRELESS_CRAFTING_CONTAINER = "net.p455w0rd.wirelesscraftingterminal.common.container.ContainerWirelessCraftingTerminal";

    private static final String[][] HOSTS = { { "grinder", "Grinder" }, { "condenser", "Condenser" },
        { "chest", "Chest" }, { "sky_chest", "SkyChest" }, { "drive", "Drive" },
        { "vibration_chamber", "VibrationChamber" }, { "inscriber", "Inscriber" },
        { "advanced_inscriber", "AdvancedInscriber" }, { "io_port", "IOPort" },
        { "crystal_growth_chamber", "CrystalGrowthChamber" }, { "molecular_assembler", "MAC" },
        { "wireless_access_point", "Wireless" }, { "security_terminal", "Security" }, { "quantum_link_chamber", "QNB" },
        { "spatial_io_port", "SpatialIOPort" }, { "spatial_link_chamber", "SpatialLinkChamber" },
        { "interface", "Interface" }, { "cell_workbench", "CellWorkbench" }, { "network_tool", "NetworkTool" },
        { "advanced_network_tool", "AdvancedNetworkTool" }, { "me_terminal", "MEMonitorable" },
        { "crafting_terminal", "CraftingTerm" }, { "pattern_terminal", "PatternTerm" },
        { "extended_pattern_terminal", "PatternTermEx" }, { "portable_cell", "MEPortableCell" },
        { "interface_terminal", "InterfaceTerminal" }, { "crafting_diagnostic_terminal", "CraftingDiagnosticTerminal" },
        { "storage_bus", "StorageBus" }, { "import_export_bus", "BusIO" }, { "level_emitter", "LevelEmitter" },
        { "formation_plane", "FormationPlane" }, { "advanced_level_emitter", "AdvancedLevelEmitter" },
        { "quartz_cutting_knife", "QuartzKnife" } };

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
        // Core wireless access uses the same MEMonitorable host as the wired ME Terminal.
        for (String[] host : HOSTS) machine(host[0], host[1]);
        if (IntegrationProfile.selected()
            .usesRelease29Adapters() && OptionalInventoryAdapters.reviewedVersion("ae2wct"))
            register(
                WIRELESS_CRAFTING_ID,
                WIRELESS_CRAFTING_CONTAINER,
                AppliedEnergisticsBackground.WIRELESS_CRAFTING_GUI);
    }

    static boolean panelHost(InventoryUiAdapter adapter, String container) {
        if (adapter.getCompatibilityRevision() < 3) return false;
        if (WIRELESS_CRAFTING_CONTAINER.equals(container) && WIRELESS_CRAFTING_ID.equals(adapter.getId()))
            return WIRELESS_CRAFTING_CONTAINER.equals(adapter.getContainerClassName())
                && AppliedEnergisticsBackground.WIRELESS_CRAFTING_GUI.equals(adapter.getGuiClassName());
        for (String[] host : HOSTS) if (adapter.getId()
            .equals("dynamicinventory:ae2_" + host[0])
            && container.equals("appeng.container.implementations.Container" + host[1])
            && container.equals(adapter.getContainerClassName())
            && adapter.getGuiClassName()
                .equals("appeng.client.gui.implementations.Gui" + host[1]))
            return true;
        return false;
    }

    private static void machine(String id, String type) {
        register(
            "dynamicinventory:ae2_" + id,
            "appeng.container.implementations.Container" + type,
            "appeng.client.gui.implementations.Gui" + type);
    }

    private static void register(String id, String containerClass, String gui) {
        InventoryUi.register(
            InventoryUiAdapter.builder(id, containerClass)
                .gui(gui)
                .compatibilityRevision(3)
                .layout(
                    AppliedEnergisticsBackground.hasSidePanels(gui)
                        && !AppliedEnergisticsBackground.isInterfaceTerminal(gui) ? SIDE_LAYOUT : LAYOUT)
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
