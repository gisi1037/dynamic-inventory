package dev.gilbe.dynamicinventory.qualification;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import dev.gilbe.dynamicinventory.compat.BackhandCompatibility;
import dev.gilbe.dynamicinventory.inventory.ExpandedInventoryAccess;
import dev.gilbe.dynamicinventory.inventory.InventoryLayout;

/** Disk-backed OpenBlocks consumer checks; excluded from production and never uses a fake player. */
public final class OpenBlocksCompatibilityFixtures {

    private OpenBlocksCompatibilityFixtures() {}

    public static JsonObject run(EntityPlayerMP player) throws Exception {
        String world = MinecraftServer.getServer()
            .worldServerForDimension(0)
            .getWorldInfo()
            .getWorldName();
        require(world.startsWith("DI-Baseline-") && world.contains("Qualification"), "Disposable world required");
        require(
            Loader.isModLoaded("OpenBlocks") && BackhandCompatibility.enabled(),
            "OpenBlocks and Backhand required");
        require(InventoryLayout.mainSlots() > 36, "This boundary fixture requires expanded ordinary storage");
        require(
            player.openContainer == player.inventoryContainer && player.inventory.getItemStack() == null,
            "Close containers and empty cursor before the backup fixture");
        require(player.isEntityAlive(), "A living online player is required");
        require(!(player instanceof FakePlayer), "An actual online player is required");
        ItemStack[] originalMain = copy(player.inventory.mainInventory);
        ItemStack[] originalArmor = copy(player.inventory.armorInventory);
        ExpandedInventoryAccess access = (ExpandedInventoryAccess) player.inventory;
        NBTTagCompound recovery = (NBTTagCompound) access.dynamicinventory$getRecovery()
            .copy();
        JsonObject report = new JsonObject();
        JsonArray passed = new JsonArray();
        report.add("passed", passed);
        report.addProperty("scope", "OpenBlocks disk restore plus actual smaller/larger dump rejection");
        report.addProperty("world", world);
        report.addProperty("rawMainSlots", originalMain.length);
        report.addProperty("inventoryApiSlots", player.inventory.getSizeInventory());
        String nonce = UUID.randomUUID()
            .toString();
        Class<?> storeClass = Class.forName("openblocks.common.PlayerInventoryStore");
        Object store = storeClass.getField("instance")
            .get(null);
        Method load = storeClass.getMethod("loadInventories", net.minecraft.world.World.class, String.class);
        LoadCounter counter = new LoadCounter();
        MinecraftForge.EVENT_BUS.register(counter);
        try {
            // Do not exercise equipment Load callbacks on somebody's equipped items as a side effect of this test.
            // The full grave/equipment lifecycle belongs to a separate fixture.
            Class<?> captureClass = Class.forName("openblocks.api.InventoryEvent$Store");
            Object capture = captureClass.getConstructor(EntityPlayer.class)
                .newInstance(player);
            MinecraftForge.EVENT_BUS.post((cpw.mods.fml.common.eventhandler.Event) capture);
            java.util.Map<?, ?> subInventories = (java.util.Map<?, ?>) captureClass.getMethod("getSubInventories")
                .invoke(capture);
            for (Object subInventory : subInventories.values()) {
                java.util.Map<?, ?> slots = (java.util.Map<?, ?>) subInventory.getClass()
                    .getMethod("asMap")
                    .invoke(subInventory);
                require(
                    slots.isEmpty(),
                    "Run with empty optional equipment inventories; baseline items were not changed");
            }
            report.addProperty("optionalEquipmentEmpty", true);
            Arrays.fill(player.inventory.mainInventory, null);
            Arrays.fill(player.inventory.armorInventory, null);
            int[] boundaries = { 35, InventoryLayout.OFFHAND_INDEX, InventoryLayout.rawIndex(36),
                originalMain.length - 1 };
            for (int i = 0; i < boundaries.length; i++) {
                player.inventory.mainInventory[boundaries[i]] = probe(
                    Items.diamond,
                    i + 2,
                    0,
                    nonce,
                    "main-" + boundaries[i]);
            }
            Item[] armor = { Items.iron_boots, Items.iron_leggings, Items.iron_chestplate, Items.iron_helmet };
            for (int i = 0; i < armor.length; i++)
                player.inventory.armorInventory[i] = probe(armor[i], 1, 7 + i, nonce, "armor-" + i);
            ItemStack[] expectedMain = copy(player.inventory.mainInventory);
            ItemStack[] expectedArmor = copy(player.inventory.armorInventory);
            File dump = (File) storeClass.getMethod("storePlayerInventory", EntityPlayer.class, String.class)
                .invoke(store, player, "dinv-fixture-" + nonce);
            require(dump.isFile() && dump.length() > 0, "OpenBlocks wrote an actual dump file");
            report.addProperty("dump", dump.getAbsolutePath());
            String id = dumpId(dump);
            Object loaded = load.invoke(store, player.worldObj, id);
            IInventory disk = mainInventory(loaded);
            requireSameCapacity(player, disk);
            for (int i = 0; i < disk.getSizeInventory(); i++) require(
                ItemStack.areItemStacksEqual(player.inventory.getStackInSlot(i), disk.getStackInSlot(i)),
                "Disk item and typed NBT at API index " + i);
            passed.add(new JsonPrimitive("Real OpenBlocks disk dump preserves every main and armor API slot"));
            Arrays.fill(player.inventory.mainInventory, null);
            Arrays.fill(player.inventory.armorInventory, null);
            // An unrelated sentinel proves restore performs writes rather than comparing an unchanged inventory.
            player.inventory.mainInventory[0] = probe(Items.stick, 19, 0, nonce, "must-be-overwritten");
            require(
                (Boolean) storeClass.getMethod("restoreInventory", EntityPlayer.class, String.class)
                    .invoke(store, player, id),
                "OpenBlocks restore reports success");
            require(equal(expectedMain, player.inventory.mainInventory), "Exact main contents restored");
            require(equal(expectedArmor, player.inventory.armorInventory), "Exact armor contents restored");
            require(recovery.equals(access.dynamicinventory$getRecovery()), "Opaque recovery remains unchanged");
            require(counter.loads == 1, "Successful restore posts exactly one actual OpenBlocks Load event");
            passed.add(
                new JsonPrimitive("Actual restore writes offhand, both extra boundaries and damaged armor exactly"));

            require(
                Arrays.stream(storeClass.getDeclaredMethods())
                    .anyMatch(
                        method -> method.getName()
                            .contains("dynamicinventory$checkBackupSize")),
                "Production guard must be transformed into OpenBlocks before attempting any mismatch restore");
            report.addProperty("productionMismatchGuard", true);
            // Model an unexpanded Backhand dump using the actual foreign serializer.
            InventoryBasic legacy = new InventoryBasic("Legacy Backhand backup", true, 41);
            for (int i = 0; i < armor.length; i++)
                legacy.setInventorySlotContents(37 + i, probe(armor[i], 1, 17 + i, nonce, "legacy-armor-" + i));
            Class<?> extrasClass = Class.forName("openblocks.common.PlayerInventoryStore$ExtrasFiller");
            Object extras = Proxy.newProxyInstance(
                extrasClass.getClassLoader(),
                new Class<?>[] { extrasClass },
                (proxy, method, args) -> null);
            File legacyDump = (File) storeClass
                .getMethod(
                    "storeInventory",
                    IInventory.class,
                    String.class,
                    String.class,
                    net.minecraft.world.World.class,
                    extrasClass)
                .invoke(store, legacy, player.getCommandSenderName(), "dinv-legacy-" + nonce, player.worldObj, extras);
            require(legacyDump.isFile() && legacyDump.length() > 0, "Legacy-shaped dump was written");
            report.addProperty("legacyDump", legacyDump.getAbsolutePath());
            IInventory legacyDisk = mainInventory(load.invoke(store, player.worldObj, dumpId(legacyDump)));
            require(legacyDisk.getSizeInventory() == 41, "Actual foreign dump retains legacy API slot count");
            require(InventoryLayout.ordinaryIndex(37) >= 36, "First legacy armor raw index now denotes added storage");
            for (int raw = 37; raw <= 40; raw++) {
                require(
                    ItemStack.areItemStacksEqual(legacy.getStackInSlot(raw), legacyDisk.getStackInSlot(raw)),
                    "Legacy armor remains serialized at raw index " + raw);
            }
            rejectMismatch(store, storeClass, player, legacyDump, 41, counter);
            passed.add(
                new JsonPrimitive(
                    "Actual restore rejects legacy armor/storage collision before items, drops or Load event"));
            report.addProperty("legacyRestoreAttempted", true);
            int largerSize = player.inventory.getSizeInventory() + 1;
            InventoryBasic larger = new InventoryBasic("Oversized backup", true, largerSize);
            larger.setInventorySlotContents(0, probe(Items.stick, 23, 0, nonce, "larger-overwrite"));
            larger.setInventorySlotContents(largerSize - 1, probe(Items.emerald, 11, 0, nonce, "larger-overflow"));
            File largerDump = (File) storeClass
                .getMethod(
                    "storeInventory",
                    IInventory.class,
                    String.class,
                    String.class,
                    net.minecraft.world.World.class,
                    extrasClass)
                .invoke(store, larger, player.getCommandSenderName(), "dinv-larger-" + nonce, player.worldObj, extras);
            require(largerDump.isFile() && largerDump.length() > 0, "Oversized dump was written");
            report.addProperty("largerDump", largerDump.getAbsolutePath());
            require(
                mainInventory(load.invoke(store, player.worldObj, dumpId(largerDump))).getSizeInventory() == largerSize,
                "Actual foreign dump retains oversized API slot count");
            rejectMismatch(store, storeClass, player, largerDump, largerSize, counter);
            passed.add(
                new JsonPrimitive("Actual restore rejects larger dump before overwrite, overflow drops or Load event"));
            report.addProperty("largerRestoreAttempted", true);
            report.addProperty("success", true);
        } catch (Throwable failure) {
            report.addProperty("success", false);
            report.addProperty("failure", failure.toString());
        } finally {
            MinecraftForge.EVENT_BUS.unregister(counter);
            System.arraycopy(originalMain, 0, player.inventory.mainInventory, 0, originalMain.length);
            System.arraycopy(originalArmor, 0, player.inventory.armorInventory, 0, originalArmor.length);
            access.dynamicinventory$setRecovery((NBTTagCompound) recovery.copy());
            player.inventory.markDirty();
            player.inventoryContainer.detectAndSendChanges();
            require(
                equal(originalMain, player.inventory.mainInventory)
                    && equal(originalArmor, player.inventory.armorInventory),
                "Original main and armor baseline restored");
            report.addProperty("baselineRestored", true);
        }
        QualificationCommand.write(player, "server-openblocks-backup", report);
        return report;
    }

    private static void rejectMismatch(Object store, Class<?> storeClass, EntityPlayerMP player, File dump,
        int storedSize, LoadCounter counter) throws Exception {
        ItemStack[] main = copy(player.inventory.mainInventory);
        ItemStack[] armor = copy(player.inventory.armorInventory);
        ExpandedInventoryAccess access = (ExpandedInventoryAccess) player.inventory;
        NBTTagCompound recovery = (NBTTagCompound) access.dynamicinventory$getRecovery()
            .copy();
        Set<Integer> drops = itemEntities(player);
        int loads = counter.loads;
        boolean rejected = false;
        try {
            storeClass.getMethod("restoreInventory", EntityPlayer.class, String.class)
                .invoke(store, player, dumpId(dump));
        } catch (InvocationTargetException wrapper) {
            Throwable failure = wrapper.getCause();
            rejected = failure instanceof IllegalArgumentException && failure.getMessage() != null
                && failure.getMessage()
                    .contains("inventory size " + storedSize + " differs from " + player.inventory.getSizeInventory());
            require(rejected, "Actual restore rejected for the expected capacity mismatch: " + failure);
        }
        require(rejected, "Actual restore must refuse mismatched capacity");
        require(
            equal(main, player.inventory.mainInventory) && equal(armor, player.inventory.armorInventory),
            "Rejected restore leaves all main and armor items unchanged");
        require(recovery.equals(access.dynamicinventory$getRecovery()), "Rejected restore leaves recovery unchanged");
        require(drops.equals(itemEntities(player)), "Rejected restore creates no item entities");
        require(counter.loads == loads, "Rejected restore posts no OpenBlocks Load event");
    }

    private static Set<Integer> itemEntities(EntityPlayer player) {
        Set<Integer> ids = new HashSet<>();
        for (Object entity : player.worldObj.loadedEntityList)
            if (entity instanceof EntityItem) ids.add(((EntityItem) entity).getEntityId());
        return ids;
    }

    public static final class LoadCounter {

        private int loads;

        @SubscribeEvent
        public void event(Event event) {
            if (event.getClass()
                .getName()
                .equals("openblocks.api.InventoryEvent$Load")) loads++;
        }
    }

    private static IInventory mainInventory(Object loaded) throws Exception {
        require(loaded != null, "OpenBlocks loaded the dump from disk");
        IInventory inventory = (IInventory) loaded.getClass()
            .getField("mainInventory")
            .get(loaded);
        require(inventory != null, "OpenBlocks loaded a main inventory");
        return inventory;
    }

    private static void requireSameCapacity(EntityPlayer player, IInventory source) {
        if (source.getSizeInventory() != player.inventory.getSizeInventory())
            throw new IllegalArgumentException("Fixture refuses raw-index restore across inventory capacities");
    }

    private static String dumpId(File dump) {
        String name = dump.getName();
        require(name.startsWith("inventory-") && name.endsWith(".dat"), "Recognized OpenBlocks dump filename");
        return name.substring("inventory-".length(), name.length() - ".dat".length());
    }

    private static ItemStack probe(Item item, int count, int damage, String nonce, String role) {
        ItemStack stack = new ItemStack(item, count, damage);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("fixture", nonce);
        tag.setString("role", role);
        NBTTagCompound nested = new NBTTagCompound();
        nested.setByte("byte", (byte) 17);
        nested.setLong("long", 0x123456789ABCDEFL);
        nested.setIntArray("array", new int[] { -1, 0, 82, Integer.MAX_VALUE });
        tag.setTag("typed", nested);
        stack.setTagCompound(tag);
        return stack;
    }

    private static ItemStack[] copy(ItemStack[] stacks) {
        return Arrays.stream(stacks)
            .map(stack -> stack == null ? null : stack.copy())
            .toArray(ItemStack[]::new);
    }

    private static boolean equal(ItemStack[] left, ItemStack[] right) {
        if (left.length != right.length) return false;
        for (int i = 0; i < left.length; i++) if (!ItemStack.areItemStacksEqual(left[i], right[i])) return false;
        return true;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
