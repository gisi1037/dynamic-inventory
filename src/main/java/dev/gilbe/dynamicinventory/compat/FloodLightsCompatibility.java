package dev.gilbe.dynamicinventory.compat;

import dev.gilbe.dynamicinventory.api.InventoryTransfers;
import dev.gilbe.dynamicinventory.api.InventoryUi;
import dev.gilbe.dynamicinventory.api.InventoryUiAdapter;
import dev.gilbe.dynamicinventory.api.NativeUiLayout;

/** Flood Lights 1.5.6 places its 36 player slots BEFORE its single filtered machine slot. */
public final class FloodLightsCompatibility {

    private FloodLightsCompatibility() {}

    public static void registerInventoryAdapters() {
        register("carbon", "Carbon");
        register("electric", "Electric");
    }

    private static void register(String id, String type) {
        InventoryUi.register(
            InventoryUiAdapter
                .builder(
                    "dynamicinventory:floodlights/" + id,
                    "de.keridos.floodlights.client.gui.container.Container" + type + "Floodlight")
                .gui("de.keridos.floodlights.client.gui.Gui" + type + "Floodlight")
                .expectedNativeSlots(37)
                .layout(
                    NativeUiLayout.builder()
                        .fixedPlayerColumns(9)
                        .backgroundInset(4)
                        .build())
                // Native >=36 means "machine source" and would misclassify our appended player slots.
                // For those sources only, target the real FuelSlot/ElectricSlot. The shared transfer
                // asks that native slot about validity and stack limits; invalid items stay put.
                .extraTransfers(InventoryTransfers.toSlots(36))
                .build());
    }
}
