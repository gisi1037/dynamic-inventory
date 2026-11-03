package dev.gilbe.dynamicinventory.compat;

import net.minecraft.inventory.Slot;

import dev.gilbe.dynamicinventory.api.InventoryUi;
import dev.gilbe.dynamicinventory.api.InventoryUiAdapter;
import dev.gilbe.dynamicinventory.api.NativeUiLayout;

/** Exact Extra Utilities 1.2.12 hosts, including item-backed contents and dynamic generator widgets. */
public final class ExtraUtilitiesCompatibility {

    private static final String[][] HOSTS = {
        { "filing-cabinet", "com.rwtema.extrautils.gui.ContainerFilingCabinet",
            "com.rwtema.extrautils.gui.GuiFilingCabinet" },
        { "filter", "com.rwtema.extrautils.gui.ContainerFilter", "com.rwtema.extrautils.gui.GuiFilter" },
        { "sorting-pipe", "com.rwtema.extrautils.gui.ContainerFilterPipe", "com.rwtema.extrautils.gui.GuiFilterPipe" },
        { "golden-bag", "com.rwtema.extrautils.gui.ContainerHoldingBag", "com.rwtema.extrautils.gui.GuiHoldingBag" },
        { "transfer-node", "com.rwtema.extrautils.gui.ContainerTransferNode",
            "com.rwtema.extrautils.gui.GuiTransferNode" },
        { "trash-can", "com.rwtema.extrautils.gui.ContainerTrashCan", "com.rwtema.extrautils.gui.GuiTrashCan" },
        { "mini-chest", "com.rwtema.extrautils.tileentity.chests.DynamicContainerMiniChest",
            "com.rwtema.extrautils.dynamicgui.DynamicGui" },
        { "generator", "com.rwtema.extrautils.tileentity.generators.DynamicContainerGenerator",
            "com.rwtema.extrautils.dynamicgui.DynamicGui" },
        { "ender-constructor", "com.rwtema.extrautils.tileentity.enderconstructor.DynamicContainerEnderConstructor",
            "com.rwtema.extrautils.tileentity.enderconstructor.DynamicGuiEnderConstructor" } };

    private ExtraUtilitiesCompatibility() {}

    public static void registerInventoryAdapters() {
        for (String[] host : HOSTS) InventoryUi.register(
            InventoryUiAdapter.builder("dynamicinventory:extrautils/" + host[0], host[1])
                .gui(host[2])
                .compatibilityRevision(2)
                .layout(
                    NativeUiLayout.builder()
                        .fixedPlayerColumns(9)
                        .backgroundInset(4)
                        .minimumHeaderHeight(host[0].equals("filing-cabinet") ? 142 : 0)
                        .build())
                // Ghost filters can mutate their settings and return null; retain native handling exactly once.
                .nativeTransfers()
                .additionalSlots((container, inventory, raw) -> {
                    if (!(container instanceof dev.gilbe.dynamicinventory.compat.extrautils.ExtraUtilitiesItemAccess item)
                        || item.dynamicinventory$ownerSlot() != raw) return new Slot(inventory, raw, 0, 0);
                    return new Slot(inventory, raw, 0, 0) {

                        @Override
                        public boolean canTakeStack(net.minecraft.entity.player.EntityPlayer player) {
                            return false;
                        }

                        @Override
                        public boolean isItemValid(net.minecraft.item.ItemStack stack) {
                            return false;
                        }
                    };
                })
                .build());
    }

    static boolean panelHost(InventoryUiAdapter adapter, String container) {
        if (adapter.getCompatibilityRevision() < 2) return false;
        for (String[] host : HOSTS) if (adapter.getId()
            .equals("dynamicinventory:extrautils/" + host[0]) && container.equals(host[1])
            && host[2].equals(adapter.getGuiClassName())) return true;
        return false;
    }
}
