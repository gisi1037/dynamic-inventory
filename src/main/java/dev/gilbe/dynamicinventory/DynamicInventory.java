package dev.gilbe.dynamicinventory;

import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLLoadCompleteEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerAboutToStartEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppedEvent;
import cpw.mods.fml.common.network.NetworkCheckHandler;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.relauncher.Side;
import dev.gilbe.dynamicinventory.api.InventoryUi;
import dev.gilbe.dynamicinventory.compat.BackhandCompatibility;
import dev.gilbe.dynamicinventory.compat.PackCompatibility;
import dev.gilbe.dynamicinventory.inventory.InventoryCapacity;
import dev.gilbe.dynamicinventory.network.InventoryOpeningProtocol;

@Mod(
    modid = DynamicInventory.MODID,
    version = Tags.VERSION,
    name = "DynamicInventory",
    acceptedMinecraftVersions = "[1.7.10]")
public class DynamicInventory {

    public static final String MODID = "dynamicinventory";
    public static final Logger LOG = LogManager.getLogger(MODID);

    @SidedProxy(
        clientSide = "dev.gilbe.dynamicinventory.ClientProxy",
        serverSide = "dev.gilbe.dynamicinventory.CommonProxy")
    public static CommonProxy proxy;

    @NetworkCheckHandler
    public boolean acceptsRemote(Map<String, String> versions, Side remoteSide) {
        String advertised = versions.get(MODID);
        // Forge probes an empty mod list while constructing this mod, before adapter
        // registration can close. Status probes also use a bare version. Neither is
        // a capacity negotiation, so neither may request the frozen adapter digest.
        if (advertised == null) return false;
        boolean statusVersion = Tags.VERSION.equals(advertised);
        int remoteStorage = statusVersion ? -1 : InventoryCapacity.readWireStorage(advertised);
        // Forge also calls this for server-list status probes, which contain ordinary mod versions.
        // Bare versions are not negotiable: CapacityLoginMixin requires protocol + digest on the real handshake.
        boolean compatible = (remoteStorage >= 0 || statusVersion)
            && (!BackhandCompatibility.enabled() || PackCompatibility.enabled() == versions.containsKey("bogosorter"))
            && (BackhandCompatibility.enabled() ? BackhandCompatibility.supportedVersion()
                .equals(versions.get("backhand")) : !versions.containsKey("backhand"));
        return compatible;
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        proxy.serverStarting(event);
    }

    @Mod.EventHandler
    public void serverAboutToStart(FMLServerAboutToStartEvent event) {
        // Apply the local server's configured capacity before worlds, players and containers exist.
        InventoryCapacity.beginServerSession();
    }

    @Mod.EventHandler
    public void serverStopped(FMLServerStoppedEvent event) {
        // A later connection may adopt a different capacity once the last old world has also unloaded.
        InventoryCapacity.endServerSession();
    }

    @Mod.EventHandler
    public void loadComplete(FMLLoadCompleteEvent event) {
        // Registering this channel with Forge would change its dispatch order/thread. Fail early on a collision.
        for (Side side : Side.values()) if (NetworkRegistry.INSTANCE.hasChannel(InventoryOpeningProtocol.CHANNEL, side))
            throw new IllegalStateException("Inventory opening channel is unexpectedly registered with Forge");
        for (Side side : Side.values()) if (NetworkRegistry.INSTANCE
            .hasChannel(dev.gilbe.dynamicinventory.network.InventoryResizeProtocol.CHANNEL, side))
            throw new IllegalStateException("Inventory resize channel is unexpectedly registered with Forge");
        // Addons may register through postInit. Every subsequent handshake uses this fixed declaration digest.
        InventoryUi.closeRegistration();
    }
}
