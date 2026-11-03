package dev.gilbe.dynamicinventory.compat;

import net.minecraft.inventory.Container;

import dev.gilbe.dynamicinventory.api.InventoryUi;
import dev.gilbe.dynamicinventory.api.InventoryUiAdapter;

/** Exact reviewed native hosts for the shared modular inventory presentation. */
public final class NativePanelHosts {

    private static final String[][] HOSTS = {
        { "dynamicinventory:ironchest", "cpw.mods.ironchest.ContainerIronChest", "cpw.mods.ironchest.client.GUIChest" },
        { "dynamicinventory:discovered/guibabychest", "com.dreammaster.modbabychest.ContainerBabyChest",
            "com.dreammaster.modbabychest.GuiBabyChest" },
        { "dynamicinventory:discovered/guicompressedchest",
            "wanion.avaritiaddons.block.chest.compressed.ContainerCompressedChest",
            "wanion.avaritiaddons.block.chest.compressed.GuiCompressedChest" },
        { "dynamicinventory:harvestcraft/churn", "com.pam.harvestcraft.ContainerChurn",
            "com.pam.harvestcraft.GuiChurn" },
        { "dynamicinventory:harvestcraft/oven", "com.pam.harvestcraft.ContainerOven", "com.pam.harvestcraft.GuiOven" },
        { "dynamicinventory:harvestcraft/quern", "com.pam.harvestcraft.ContainerQuern",
            "com.pam.harvestcraft.GuiQuern" },
        { "dynamicinventory:harvestcraft/animal-trap", "com.pam.harvestcraft.ContainerPamAnimalTrap",
            "com.pam.harvestcraft.GuiPamAnimalTrap" },
        { "dynamicinventory:harvestcraft/fish-trap", "com.pam.harvestcraft.ContainerPamFishTrap",
            "com.pam.harvestcraft.GuiPamFishTrap" },
        { "dynamicinventory:harvestcraft/apiary", "com.pam.harvestcraft.ContainerPamApiary",
            "com.pam.harvestcraft.GuiPamApiary" },
        { "dynamicinventory:harvestcraft/grinder", "com.pam.harvestcraft.ContainerPamGrinder",
            "com.pam.harvestcraft.GuiPamGrinder" },
        { "dynamicinventory:harvestcraft/presser", "com.pam.harvestcraft.ContainerPamPresser",
            "com.pam.harvestcraft.GuiPamPresser" } };

    private NativePanelHosts() {}

    public static boolean supports(Container container) {
        if (VanillaPanelHosts.supports(container)) return true;
        return container != null && reviewedAdapter(
            InventoryUi.findAdapter(container),
            container.getClass()
                .getName());
    }

    public static boolean supports(Container container, Class<?> screen) {
        if (VanillaPanelHosts.supports(container, screen)) return true;
        if (container == null || screen == null) return false;
        InventoryUiAdapter adapter = InventoryUi.findAdapter(container);
        return reviewedAdapter(
            adapter,
            container.getClass()
                .getName())
            && (adapter.matchesGuiClass(screen) || EtFuturumCompatibility.alternateScreen(adapter, screen.getName())
                || EnderIoCompatibility.alternateScreen(adapter, screen.getName()));
    }

    // Existing registrations retain the owning mod's exact profile/version gate. This grants no unregistered host,
    // alternative screen, subclass or machine slot access, and never loads optional classes on a dedicated server.
    private static boolean reviewedAdapter(InventoryUiAdapter adapter, String container) {
        if (adapter == null || adapter.isIncludingSubclasses() || adapter.getCompatibilityRevision() < 2) return false;
        if (AppliedEnergisticsCompatibility.panelHost(adapter, container)) return true;
        if (EtFuturumCompatibility.panelHost(adapter, container) || EnderIoCompatibility.panelHost(adapter, container)
            || ExtraUtilitiesCompatibility.panelHost(adapter, container)) return true;
        for (String[] host : HOSTS) if (host[0].equals(adapter.getId()) && host[1].equals(container)
            && host[1].equals(adapter.getContainerClassName())
            && host[2].equals(adapter.getGuiClassName())) return true;
        return false;
    }
}
