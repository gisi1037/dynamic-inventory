package dev.gilbe.dynamicinventory.compat;

/** Cinder furnace 2.7.45 retains its native smelting logic, output slot, and ordinary progress properties. */
public final class TwilightForestCompatibility {

    private TwilightForestCompatibility() {}

    public static void registerInventoryAdapters() {
        NativeMachineAdapters.furnace(
            "dynamicinventory:twilightforest/cinder-furnace",
            "twilightforest.inventory.ContainerTFCinderFurnace",
            "twilightforest.client.GuiTFCinderFurnace");
    }
}
