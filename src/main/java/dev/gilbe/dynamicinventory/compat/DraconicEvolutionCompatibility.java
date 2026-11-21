package dev.gilbe.dynamicinventory.compat;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import com.brandon3055.draconicevolution.common.container.ContainerAdvTool;

import dev.gilbe.dynamicinventory.api.InventoryUi;
import dev.gilbe.dynamicinventory.api.InventoryUiAdapter;
import dev.gilbe.dynamicinventory.api.NativeUiLayout;

/** Exact inventory hosts in Draconic Evolution 1.5.35-GTNH. */
final class DraconicEvolutionCompatibility {

    private static final String ID = "dynamicinventory:draconic/";
    private static final String ROOT = "com.brandon3055.draconicevolution.";
    private static final String[][] HOSTS = { { "weather-controller", "WeatherController", "GUIWeatherController" },
        { "grinder", "Grinder", "GUIGrinder" }, { "player-detector", "PlayerDetector", "GUIPlayerDetector" },
        { "energy-infuser", "EnergyInfuser", "GUIEnergyInfuser" }, { "generator", "Generator", "GUIGenerator" },
        { "diss-enchanter", "DissEnchanter", "GUIDissEnchanter" },
        { "draconium-chest", "DraconiumChest", "GUIDraconiumChest" },
        { "reactor", "Reactor", "componentguis.GUIReactor" },
        { "upgrade-modifier", "UpgradeModifier", "GUIUpgradeModifier" },
        { "dislocator-inhibitor", "DislocatorInhibitor", "GUIDislocatorInhibitor" },
        { "tool-inventory", "AdvTool", "componentguis.GUIToolInventory" } };

    private DraconicEvolutionCompatibility() {}

    static void registerInventoryAdapters() {
        for (String[] host : HOSTS) {
            NativeUiLayout layout = NativeUiLayout.builder()
                .fixedPlayerColumns(9)
                .backgroundInset(4)
                .minimumHeaderHeight(switch (host[0]) {
                case "draconium-chest" -> 256;
                case "reactor" -> 222;
                case "upgrade-modifier" -> 190;
                case "tool-inventory" -> 130;
                default -> 0;
                })
                .build();
            InventoryUiAdapter.Builder adapter = InventoryUiAdapter.builder(ID + host[0], container(host))
                .gui(screen(host))
                .compatibilityRevision(2)
                .layout(layout)
                .nativeTransfers();
            if (host[0].equals("tool-inventory"))
                adapter.additionalSlots((container, inventory, raw) -> new Slot(inventory, raw, 0, 0) {

                    @Override
                    public boolean canTakeStack(EntityPlayer player) {
                        return ((ContainerAdvTool) container).inventoryItemSlot != raw;
                    }

                    @Override
                    public boolean isItemValid(ItemStack stack) {
                        return ((ContainerAdvTool) container).inventoryItemSlot != raw;
                    }
                });
            InventoryUi.register(adapter.build());
        }
    }

    static boolean panelHost(InventoryUiAdapter adapter, String container) {
        for (String[] host : HOSTS) if (adapter.getId()
            .equals(ID + host[0]) && container.equals(container(host))
            && adapter.getContainerClassName()
                .equals(container)
            && screen(host).equals(adapter.getGuiClassName())) return true;
        return false;
    }

    private static String container(String[] host) {
        return ROOT + "common.container.Container" + host[1];
    }

    private static String screen(String[] host) {
        return ROOT + "client.gui." + host[2];
    }
}
