package dev.gilbe.dynamicinventory.compat;

import cpw.mods.fml.common.Loader;
import dev.gilbe.dynamicinventory.api.InventoryUiAdapter;

/** Thaumcraft hosts with ordinary player slots and a native machine transfer implementation. */
public final class ThaumcraftCompatibility {

    public static final String TWEAKS_GUI = "elan.tweaks.common.gui.ComposableContainerGui";
    private static final String TWEAKS_CONTAINER = "elan.tweaks.common.container.SpecializedContainer";
    private static final String TWEAKS_ID = "dynamicinventory:thaumcraft/research-tweaks-table";

    private static final Host[] HOSTS = { new Host("alchemical-furnace", "AlchemyFurnace", 38),
        new Host("arcane-bore", "ArcaneBore", 38), new Host("arcane-workbench", "ArcaneWorkbench", 47),
        new Host("deconstruction-table", "DeconstructionTable", 37),
        new Host("focal-manipulator", "FocalManipulator", 37), new Host("research-table", "ResearchTable", 38),
        new Host("spa", "Spa", 37), new Host("thaumatorium", "Thaumatorium", 37),
        new Host("traveling-trunk", "TravelingTrunk", "entities.golems", 0), new Host("pech", "Pech", "entities", 41) };

    private ThaumcraftCompatibility() {}

    public static void registerInventoryAdapters() {
        for (Host host : HOSTS) {
            NativeMachineAdapters.machine(host.id, host.container, host.gui, host.slots);
        }
    }

    public static void registerResearchTweaksAdapter() {
        NativeMachineAdapters.machine(TWEAKS_ID, TWEAKS_CONTAINER, TWEAKS_GUI, 38);
    }

    static boolean researchTweaks() {
        return Loader.isModLoaded("ThaumcraftResearchTweaks")
            && OptionalInventoryAdapters.reviewedVersion("ThaumcraftResearchTweaks");
    }

    static boolean panelHost(InventoryUiAdapter adapter, String container) {
        if (container.equals(TWEAKS_CONTAINER) && TWEAKS_CONTAINER.equals(adapter.getContainerClassName())
            && TWEAKS_GUI.equals(adapter.getGuiClassName())
            && TWEAKS_ID.equals(adapter.getId())) return true;
        for (Host host : HOSTS) {
            if (host.container.equals(container) && host.container.equals(adapter.getContainerClassName())
                && host.gui.equals(adapter.getGuiClassName())
                && host.id.equals(adapter.getId())) return true;
        }
        return false;
    }

    private static final class Host {

        final String id;
        final String container;
        final String gui;
        final int slots;

        Host(String id, String name, int slots) {
            this(id, name, "container", slots);
        }

        Host(String id, String name, String containerPackage, int slots) {
            this.id = "dynamicinventory:thaumcraft/" + id;
            this.container = "thaumcraft.common." + containerPackage + ".Container" + name;
            this.gui = "thaumcraft.client.gui.Gui" + name;
            this.slots = slots;
        }
    }
}
