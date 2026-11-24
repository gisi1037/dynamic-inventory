package dev.gilbe.dynamicinventory.compat;

import dev.gilbe.dynamicinventory.api.InventoryUi;
import dev.gilbe.dynamicinventory.api.InventoryUiAdapter;

/**
 * HarvestCraft 1.3.15-GTNH's ordinary processing inventories use vanilla slot synchronization and
 * progress properties. Keep their recipe/bait checks and custom result-slot callbacks in native code.
 * Market, cutting board and pot have different interaction contracts and are deliberately separate.
 */
public final class HarvestCraftCompatibility {

    private enum Host {

        CHURN("churn", "Churn", 39),
        OVEN("oven", "Oven", 39),
        QUERN("quern", "Quern", 39),
        ANIMAL_TRAP("animal-trap", "PamAnimalTrap", 55),
        FISH_TRAP("fish-trap", "PamFishTrap", 55),
        APIARY("apiary", "PamApiary", 55),
        GRINDER("grinder", "PamGrinder", 39),
        PRESSER("presser", "PamPresser", 39);

        final String id, container, gui;
        final int slots;

        Host(String id, String type, int slots) {
            this.id = "dynamicinventory:harvestcraft/" + id;
            this.container = "com.pam.harvestcraft.Container" + type;
            this.gui = "com.pam.harvestcraft.Gui" + type;
            this.slots = slots;
        }
    }

    private static final Host[] HOSTS = Host.values();

    private HarvestCraftCompatibility() {}

    public static void registerInventoryAdapters() {
        for (Host host : HOSTS) InventoryUi.register(
            InventoryUiAdapter.builder(host.id, host.container)
                .gui(host.gui)
                .expectedNativeSlots(host.slots)
                .layout(NativeMachineAdapters.LAYOUT)
                // Revision 3 also identifies the shared modular-panel integration on the peer.
                .compatibilityRevision(3)
                .extraTransfers(NativeMachineAdapters.nativeThenHotbar())
                .build());
    }

    static boolean panelHost(InventoryUiAdapter adapter, String container) {
        for (Host host : HOSTS) {
            if (host.container.equals(container) && host.container.equals(adapter.getContainerClassName())
                && host.id.equals(adapter.getId())
                && host.gui.equals(adapter.getGuiClassName())) return true;
        }
        return false;
    }
}
