package dev.gilbe.dynamicinventory.inventory;

import java.io.File;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import net.minecraft.world.World;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.event.world.WorldEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.FMLInjectionData;
import dev.gilbe.dynamicinventory.api.InventoryUi;
import dev.gilbe.dynamicinventory.api.InventoryUiProtocol;

/** Configured once per process; a remote server supplies the effective value before play starts. */
public final class InventoryCapacity {

    public static final int DEFAULT_STORAGE = 72;
    public static final int MIN_STORAGE = 27;
    public static final int MAX_STORAGE = 216;
    private static final int CONFIGURED_STORAGE = load();
    private static volatile int storage = CONFIGURED_STORAGE;
    private static final Set<World> LIVE_WORLDS = Collections.newSetFromMap(new IdentityHashMap<World, Boolean>());
    private static boolean localServerActive;

    private static int load() {
        File home = (File) FMLInjectionData.data()[6];
        Configuration config = new Configuration(new File(home, "config/dynamicinventory.cfg"));
        int value = config.getInt(
            "storageSlots",
            "capacity",
            DEFAULT_STORAGE,
            MIN_STORAGE,
            MAX_STORAGE,
            "Server storage capacity, excluding the nine hotbar slots. Applied at startup; clients adopt it during login.");
        config.get("capacity", "storageSlots", DEFAULT_STORAGE)
            .set(value);
        config.get("capacity", "storageSlots", DEFAULT_STORAGE)
            .setRequiresMcRestart(true);
        if (config.hasChanged()) config.save();
        return value;
    }

    private InventoryCapacity() {}

    public static int storageSlots() {
        return storage;
    }

    public static int configuredStorageSlots() {
        return CONFIGURED_STORAGE;
    }

    /** Pre-play only. Existing arrays and container mappings must never change capacity underneath a live world. */
    public static synchronized void useConfiguredCapacity() {
        useServerCapacity(CONFIGURED_STORAGE);
    }

    public static synchronized void beginServerSession() {
        useConfiguredCapacity();
        localServerActive = true;
    }

    public static synchronized void endServerSession() {
        localServerActive = false;
    }

    public static String wireVersion() {
        return InventoryUiProtocol.wireVersion(storage, InventoryUi.getCompatibilityDigest());
    }

    public static int readWireStorage(String version) {
        return InventoryUiProtocol.readStorage(version, InventoryUi.getCompatibilityDigest());
    }

    /** Called by the real login handshake before world construction; read-only status probes never call this. */
    public static synchronized void useServerCapacity(int value) {
        if (value < MIN_STORAGE || value > MAX_STORAGE)
            throw new IllegalArgumentException("Unsupported inventory capacity");
        if (value != storage && (localServerActive || !LIVE_WORLDS.isEmpty()))
            throw new IllegalStateException("Inventory capacity cannot change while a world or local server is active");
        storage = value;
    }

    /** Registered on Forge's world bus on both sides, including integrated client/server worlds. */
    public static final class SessionEvents {

        @SubscribeEvent
        public void onLoad(WorldEvent.Load event) {
            synchronized (InventoryCapacity.class) {
                LIVE_WORLDS.add(event.world);
            }
        }

        @SubscribeEvent
        public void onUnload(WorldEvent.Unload event) {
            synchronized (InventoryCapacity.class) {
                LIVE_WORLDS.remove(event.world);
            }
        }
    }

}
