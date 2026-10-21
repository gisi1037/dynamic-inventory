package dev.gilbe.dynamicinventory.compat;

/** Ordinary Alchemical Furnace only; research, ghost slots and paired Magic Box hosts need separate review. */
public final class ThaumcraftCompatibility {

    private ThaumcraftCompatibility() {}

    public static void registerInventoryAdapters() {
        // Two machine slots and 36 original player slots. Native transfers keep
        // Thaumcraft's aspect lookup and its preference for fuel before smelting.
        NativeMachineAdapters.machine(
            "dynamicinventory:thaumcraft/alchemical-furnace",
            "thaumcraft.common.container.ContainerAlchemyFurnace",
            "thaumcraft.client.gui.GuiAlchemyFurnace",
            38);
    }
}
