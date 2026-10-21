package dev.gilbe.dynamicinventory.compat;

import dev.gilbe.dynamicinventory.api.InventoryTransfers;
import dev.gilbe.dynamicinventory.api.InventoryUi;
import dev.gilbe.dynamicinventory.api.InventoryUiAdapter;
import dev.gilbe.dynamicinventory.api.NativeUiLayout;

/** Preserves the owner's food-container integration; held-item runtime qualification is still pending. */
public final class SpiceOfLifeCompatibility {

    private SpiceOfLifeCompatibility() {}

    public static void registerInventoryAdapters() {
        InventoryUi.register(
            InventoryUiAdapter
                // Retain the existing ID so previously saved layouts still match.
                .builder(
                    "dynamicinventory:discovered/guifoodcontainer",
                    "squeek.spiceoflife.inventory.ContainerFoodContainer")
                .gui("squeek.spiceoflife.gui.GuiFoodContainer")
                // Preserve the existing presentation and policy while moving ownership out of F9 scaffolds.
                .layout(
                    NativeUiLayout.builder()
                        .backgroundInset(0)
                        .build())
                // Bag size varies by item, so a fixed expectedNativeSlots would reject valid variants.
                // Native slotClick owns held-slot/UUID handling; filtered bag slots retain food checks.
                .extraTransfers(InventoryTransfers.toNativeSlots())
                .build());
    }
}
