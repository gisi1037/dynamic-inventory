package dev.gilbe.dynamicinventory.compat;

import dev.gilbe.dynamicinventory.api.InventoryUi;
import dev.gilbe.dynamicinventory.api.InventoryUiAdapter;
import dev.gilbe.dynamicinventory.api.NativeUiLayout;

/** All eleven block tiers share this exact native container and GUI, including the filtered dirt chest. */
public final class IronChestCompatibility {

    private IronChestCompatibility() {}

    public static void registerInventoryAdapters() {
        InventoryUi.register(
            InventoryUiAdapter.builder("dynamicinventory:ironchest", "cpw.mods.ironchest.ContainerIronChest")
                .gui("cpw.mods.ironchest.client.GUIChest")
                .layout(
                    NativeUiLayout.builder()
                        .topPadding(0)
                        .minimumHeaderHeight(0)
                        .backgroundInset(0)
                        .build())
                // Native ranges end at inventorySlots.size(), and native ValidatingSlots retain dirt filtering.
                .nativeTransfers()
                .build());
    }
}
