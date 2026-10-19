package dev.gilbe.dynamicinventory;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.common.MinecraftForge;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import dev.gilbe.dynamicinventory.compat.BackhandCompatibility;
import dev.gilbe.dynamicinventory.compat.BogoInventoryCompatibility;
import dev.gilbe.dynamicinventory.compat.BuiltinInventoryUiAdapters;
import dev.gilbe.dynamicinventory.compat.PackCompatibility;
import dev.gilbe.dynamicinventory.diagnostics.BaselineCommand;
import dev.gilbe.dynamicinventory.diagnostics.BaselineDiagnostics;
import dev.gilbe.dynamicinventory.diagnostics.BaselineServerEvents;
import dev.gilbe.dynamicinventory.inventory.InventoryCapacity;

public class CommonProxy {

    public boolean isClientOpeningReady(EntityPlayer player) {
        return false;
    }

    // Validate the selected compatibility profile before any player inventory can be constructed.
    public void preInit(FMLPreInitializationEvent event) {
        if (Loader.isModLoaded("backhand")
            && (!BackhandCompatibility.enabled() || !BackhandCompatibility.SUPPORTED_VERSION.equals(
                Loader.instance()
                    .getIndexedModList()
                    .get("backhand")
                    .getVersion()))) {
            throw new IllegalStateException(
                "Backhand support requires a pinned rc2-backhand or rc2-integration fixture with Backhand 1.8.16.");
        }
        if (BackhandCompatibility.enabled() && !Loader.isModLoaded("backhand")) {
            throw new IllegalStateException("The offhand inventory layout requires Backhand on both sides");
        }
        if (BackhandCompatibility.enabled() && Loader.isModLoaded("bogosorter") && !PackCompatibility.enabled())
            throw new IllegalStateException("Backhand with BogoSorter requires the rc2-integration equipment layout");
        InventoryCapacity.configuredStorageSlots();
        PackCompatibility.verifyVersions();
        BaselineDiagnostics.initialize(event.getModConfigurationDirectory());

        DynamicInventory.LOG.info(
            "Dynamic Inventory {}: configured {} storage slots; adapter registration closes after startup",
            Tags.VERSION,
            InventoryCapacity.configuredStorageSlots());
    }

    // Built-in adapters join addon registrations during startup; world events guard the effective capacity.
    public void init(FMLInitializationEvent event) {
        BuiltinInventoryUiAdapters.initialize();
        MinecraftForge.EVENT_BUS.register(new InventoryCapacity.SessionEvents());
        FMLCommonHandler.instance()
            .bus()
            .register(new BaselineServerEvents());
    }

    // Record the actual loaded dependency versions for the selected qualification profile.
    public void postInit(FMLPostInitializationEvent event) {
        // Both client and server must replace Bogo's native ranges after its default compat initialization.
        if (PackCompatibility.enabled()) BogoInventoryCompatibility.initialize();
        BaselineDiagnostics.writeRuntimeManifest();
    }

    // Commands are attached to each new server, while adapters remain fixed for the process lifetime.
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new BaselineCommand());
    }
}
