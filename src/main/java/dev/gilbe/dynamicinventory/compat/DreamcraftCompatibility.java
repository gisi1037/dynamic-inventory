package dev.gilbe.dynamicinventory.compat;

import dev.gilbe.dynamicinventory.api.InventoryTransfers;
import dev.gilbe.dynamicinventory.api.InventoryUi;
import dev.gilbe.dynamicinventory.api.InventoryUiAdapter;
import dev.gilbe.dynamicinventory.api.NativeUiLayout;

/** GTNH Core/Dreamcraft-owned integrations. The baby chest uses ordinary storage transfers. */
public final class DreamcraftCompatibility {

    private DreamcraftCompatibility() {}

    public static void registerInventoryAdapters() {
        InventoryUi.register(
            InventoryUiAdapter
                .builder("dynamicinventory:discovered/guibabychest", "com.dreammaster.modbabychest.ContainerBabyChest")
                .gui("com.dreammaster.modbabychest.GuiBabyChest")
                .layout(
                    NativeUiLayout.builder()
                        .topPadding(0)
                        .minimumHeaderHeight(0)
                        .backgroundInset(0)
                        .build())
                .extraTransfers(InventoryTransfers.toNativeSlots())
                .build());
    }
}
