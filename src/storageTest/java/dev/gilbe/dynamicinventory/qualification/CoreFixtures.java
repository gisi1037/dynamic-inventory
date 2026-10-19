package dev.gilbe.dynamicinventory.qualification;

import net.minecraft.item.Item;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.registry.GameRegistry;

/** Loaded only with -PinventoryStorageTests. Never packaged in the production jar. */
@Mod(
    modid = "dinvfixtures",
    name = "Dynamic Inventory Test Fixtures",
    version = "1.0",
    dependencies = "required-after:dynamicinventory")
public final class CoreFixtures {

    public static Item probe;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        probe = new TickProbeItem().setUnlocalizedName("dinvTickProbe")
            .setTextureName("minecraft:clock")
            .setMaxStackSize(1);
        GameRegistry.registerItem(probe, "tick_probe");
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        FMLCommonHandler.instance()
            .bus()
            .register(new TickMeasurements());
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new QualificationCommand());
        event.registerServerCommand(new dev.gilbe.dynamicinventory.ContainerAdapterTestCommand());
        event.registerServerCommand(new dev.gilbe.dynamicinventory.CapacityTestCommand());
    }
}
