package dev.gilbe.dynamicinventory.compat;

import dev.gilbe.dynamicinventory.api.InventoryUi;
import dev.gilbe.dynamicinventory.api.InventoryUiAdapter;
import dev.gilbe.dynamicinventory.api.NativeUiLayout;

/** Alchemical Grate 1.3.2-GTNH uses plain storage slots and the standard container opening lifecycle. */
public final class AlchemicalGrateCompatibility {

    private AlchemicalGrateCompatibility() {}

    public static void registerInventoryAdapters() {
        InventoryUi.register(
            InventoryUiAdapter.builder("dynamicinventory:alchgrate", "com.technicianlp.chestgrate.ContainerChestGrate")
                .gui("com.technicianlp.chestgrate.GuiContainerChestGrate")
                .layout(
                    NativeUiLayout.builder()
                        .fixedPlayerColumns(9)
                        .backgroundInset(4)
                        .build())
                // Native routing treats every source after the tile slots as player inventory,
                // and its player destination ends at inventorySlots.size(), including added slots.
                .nativeTransfers()
                .build());
    }
}
