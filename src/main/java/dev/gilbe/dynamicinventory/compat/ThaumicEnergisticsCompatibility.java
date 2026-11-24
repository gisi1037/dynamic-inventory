package dev.gilbe.dynamicinventory.compat;

import dev.gilbe.dynamicinventory.api.InventoryUi;
import dev.gilbe.dynamicinventory.api.InventoryUiAdapter;
import dev.gilbe.dynamicinventory.api.NativeUiLayout;
import dev.gilbe.dynamicinventory.compat.ae2.AppliedEnergisticsContainerAccess;

/** Native player-inventory screens checked against the Thaumic Energistics jars in GTNH 2.9. */
public final class ThaumicEnergisticsCompatibility {

    static final String MOD_ID = "thaumicenergistics";
    private static final String GUI = "thaumicenergistics.client.gui.Gui";
    private static final String CONTAINER = "thaumicenergistics.common.container.Container";
    public static final String ASSEMBLER = GUI + "ArcaneAssembler";
    public static final String ARCANE_TERMINAL = GUI + "ArcaneCraftingTerminal";
    public static final String DISTILLATION_ENCODER = GUI + "DistillationPatternEncoder";
    private static final NativeUiLayout LAYOUT = NativeUiLayout.builder()
        .fixedPlayerColumns(9)
        .backgroundInset(4)
        .build();
    private static final Host[] HOSTS = { new Host("arcane-assembler", "ArcaneAssembler", ASSEMBLER, false),
        new Host("arcane-crafting-terminal", "PartArcaneCraftingTerminal", ARCANE_TERMINAL, true),
        new Host("distillation-pattern-encoder", "DistillationPatternEncoder", DISTILLATION_ENCODER, true),
        new Host("knowledge-inscriber", "KnowledgeInscriber", GUI + "KnowledgeInscriber", false) };

    private ThaumicEnergisticsCompatibility() {}

    static boolean supportedVersion() {
        return IntegrationProfile.selected()
            .usesRelease29Adapters() && OptionalInventoryAdapters.reviewedVersion(MOD_ID);
    }

    static void registerInventoryAdapters() {
        for (Host host : HOSTS) {
            InventoryUiAdapter.Builder adapter = InventoryUiAdapter.builder(host.id, host.container)
                .gui(host.gui)
                .compatibilityRevision(2)
                .layout(LAYOUT)
                .nativeTransfers();
            if (host.aeSlots) adapter.additionalSlots(
                (container, inventory, raw) -> ((AppliedEnergisticsContainerAccess) container)
                    .dynamicinventory$playerSlot(inventory, raw));
            InventoryUi.register(adapter.build());
        }
    }

    static boolean panelHost(InventoryUiAdapter adapter, String container) {
        for (Host host : HOSTS) if (host.id.equals(adapter.getId()) && host.container.equals(container)
            && host.container.equals(adapter.getContainerClassName())
            && host.gui.equals(adapter.getGuiClassName())) return true;
        return false;
    }

    private static final class Host {

        private final String id, container, gui;
        private final boolean aeSlots;

        private Host(String id, String container, String gui, boolean aeSlots) {
            this.id = "dynamicinventory:thaumicenergistics/" + id;
            this.container = CONTAINER + container;
            this.gui = gui;
            this.aeSlots = aeSlots;
        }
    }
}
