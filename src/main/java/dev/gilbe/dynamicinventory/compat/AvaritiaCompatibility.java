package dev.gilbe.dynamicinventory.compat;

import dev.gilbe.dynamicinventory.api.InventoryTransfers;
import dev.gilbe.dynamicinventory.api.InventoryUi;
import dev.gilbe.dynamicinventory.api.InventoryUiAdapter;
import dev.gilbe.dynamicinventory.api.NativeUiLayout;

/** Ordinary Avaritia 1.100 machine inventories; extreme/custom crafting stays separate. */
public final class AvaritiaCompatibility {

    private AvaritiaCompatibility() {}

    public static void registerInventoryAdapters() {
        NativeUiLayout layout = NativeUiLayout.builder()
            .fixedPlayerColumns(9)
            .backgroundInset(4)
            .build();
        InventoryUi.register(
            InventoryUiAdapter
                .builder("dynamicinventory:avaritia/compressor", "fox.spiteful.avaritia.gui.ContainerCompressor")
                .gui("fox.spiteful.avaritia.gui.GUICompressor")
                .expectedNativeSlots(38)
                .layout(layout)
                // Reuse CompressorManager's native recipe check; its plain Slot alone accepts junk.
                .compatibilityRevision(NativeMachineAdapters.TRANSFER_REVISION)
                .extraTransfers(NativeMachineAdapters.nativeThenHotbar())
                .build());
        InventoryUi.register(
            InventoryUiAdapter
                .builder("dynamicinventory:avaritia/neutron", "fox.spiteful.avaritia.gui.ContainerNeutron")
                .gui("fox.spiteful.avaritia.gui.GUINeutron")
                .expectedNativeSlots(37)
                .layout(layout)
                // Output only: never insert into its SlotFurnace. Native output pickup owns its callbacks;
                // this policy handles only newly appended PLAYER sources, moving them to the hotbar.
                .extraTransfers(InventoryTransfers.toHotbar())
                .build());
    }
}
