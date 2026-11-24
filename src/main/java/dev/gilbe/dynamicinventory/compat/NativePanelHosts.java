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
            "wanion.avaritiaddons.block.chest.compressed.GuiCompressedChest" } };

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
                || EnderIoCompatibility.alternateScreen(adapter, screen.getName())
                || ForestryCompatibility.alternateScreen(adapter, screen.getName())
                || Ae2FluidCraftingCompatibility.alternateScreen(adapter, screen.getName()));
    }

    // Existing registrations retain the owning mod's exact profile/version gate. This grants no unregistered host,
    // alternative screen, subclass or machine slot access, and never loads optional classes on a dedicated server.
    private static boolean reviewedAdapter(InventoryUiAdapter adapter, String container) {
        if (adapter == null || adapter.isIncludingSubclasses() || adapter.getCompatibilityRevision() < 2) return false;
        if (AppliedEnergisticsCompatibility.panelHost(adapter, container)
            || Ae2FluidCraftingCompatibility.panelHost(adapter, container)
            || ThaumicEnergisticsCompatibility.panelHost(adapter, container)) return true;
        if (EtFuturumCompatibility.panelHost(adapter, container) || EnderIoCompatibility.panelHost(adapter, container)
            || ExtraUtilitiesCompatibility.panelHost(adapter, container)
            || DraconicEvolutionCompatibility.panelHost(adapter, container)
            || ThaumcraftCompatibility.panelHost(adapter, container)
            || ForestryCompatibility.panelHost(adapter, container)
            || RailcraftCompatibility.panelHost(adapter, container)
            || BackpackCompatibility.panelHost(adapter, container)
            || HarvestCraftCompatibility.panelHost(adapter, container)
            || GalacticraftCompatibility.panelHost(adapter, container)) return true;
        for (String[] host : HOSTS) if (host[0].equals(adapter.getId()) && host[1].equals(container)
            && host[1].equals(adapter.getContainerClassName())
            && host[2].equals(adapter.getGuiClassName())) return true;
        return false;
    }
}
