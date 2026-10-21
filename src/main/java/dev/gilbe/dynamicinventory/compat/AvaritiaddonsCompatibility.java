package dev.gilbe.dynamicinventory.compat;

import dev.gilbe.dynamicinventory.api.InventoryTransfers;
import dev.gilbe.dynamicinventory.api.InventoryUi;
import dev.gilbe.dynamicinventory.api.InventoryUiAdapter;
import dev.gilbe.dynamicinventory.api.NativeUiLayout;

/** Avaritiaddons-owned integrations. Keep compressed/infinity chest behavior distinct. */
public final class AvaritiaddonsCompatibility {

    private AvaritiaddonsCompatibility() {}

    public static void registerInventoryAdapters() {
        InventoryUi.register(
            InventoryUiAdapter
                .builder(
                    "dynamicinventory:discovered/guicompressedchest",
                    "wanion.avaritiaddons.block.chest.compressed.ContainerCompressedChest")
                .gui("wanion.avaritiaddons.block.chest.compressed.GuiCompressedChest")
                .layout(
                    NativeUiLayout.builder()
                        .topPadding(0)
                        .minimumHeaderHeight(0)
                        .backgroundInset(0) // Keep the native frame intact; the editor can override it.
                        .build())
                .extraTransfers(InventoryTransfers.toNativeSlots())
                .build());
    }
}
