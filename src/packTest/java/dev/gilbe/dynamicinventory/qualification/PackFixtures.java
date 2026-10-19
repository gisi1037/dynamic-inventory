package dev.gilbe.dynamicinventory.qualification;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;

import com.cleanroommc.bogosorter.BogoSortAPI;
import com.cleanroommc.bogosorter.common.PinnedSlots;
import com.cleanroommc.bogosorter.common.refill.RefillHandler;
import com.cleanroommc.bogosorter.common.sort.GuiSortingContext;
import com.cleanroommc.bogosorter.common.sort.SortHandler;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import baubles.api.BaubleType;
import baubles.api.BaublesApi;
import baubles.api.IBauble;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.registry.GameRegistry;
import dev.gilbe.dynamicinventory.DynamicInventory;
import dev.gilbe.dynamicinventory.diagnostics.BaselineDiagnostics;
import dev.gilbe.dynamicinventory.inventory.InventoryLayout;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

/** Real optional-mod behavior in disposable worlds; never shipped in the mod jar. */
@Mod(
    modid = "dinvpackfixtures",
    version = "1.0",
    name = "Dynamic Inventory Pack Tests",
    dependencies = "required-after:dinvfixtures;required-after:bogosorter;required-after:Baubles")
public final class PackFixtures {

    private static final Map<String, int[]> CALLBACKS = new java.util.HashMap<>();

    private static int[] callbacks(EntityLivingBase player) {
        return CALLBACKS.computeIfAbsent(
            player.getUniqueID()
                .toString(),
            key -> new int[2]);
    }

    public static final Item RING = new TestRing().setUnlocalizedName("dinv_test_ring")
        .setTextureName("minecraft:diamond");

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        GameRegistry.registerItem(RING, "test_ring");
        baubles.api.expanded.BaubleExpandedSlots.tryAssignSlotsUpToMinimum("ring", 18);
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new Command());
    }

    public static final class TestRing extends Item implements IBauble {

        public TestRing() {
            setMaxStackSize(1);
        }

        @Override
        public BaubleType getBaubleType(ItemStack stack) {
            return BaubleType.RING;
        }

        @Override
        public boolean canEquip(ItemStack stack, EntityLivingBase player) {
            return true;
        }

        @Override
        public boolean canUnequip(ItemStack stack, EntityLivingBase player) {
            return !stack.getTagCompound()
                .getBoolean("locked");
        }

        @Override
        public void onEquipped(ItemStack stack, EntityLivingBase player) {
            if (!player.worldObj.isRemote) {
                callbacks(player)[0]++;
                NBTTagCompound tag = stack.getTagCompound();
                tag.setInteger("equips", tag.getInteger("equips") + 1);
            }
        }

        @Override
        public void onUnequipped(ItemStack stack, EntityLivingBase player) {
            if (!player.worldObj.isRemote) {
                callbacks(player)[1]++;
                NBTTagCompound tag = stack.getTagCompound();
                tag.setInteger("unequips", tag.getInteger("unequips") + 1);
            }
        }

        @Override
        public void onWornTick(ItemStack stack, EntityLivingBase player) {
            if (!player.worldObj.isRemote) {
                NBTTagCompound tag = stack.getTagCompound();
                tag.setInteger("wornTicks", tag.getInteger("wornTicks") + 1);
            }
        }
    }

    public static ItemStack tagged(Item item, int count, String role) {
        ItemStack stack = new ItemStack(item, count);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("packTestRole", role);
        stack.setTagCompound(tag);
        return stack;
    }

    private static final class Command extends CommandBase {

        private JsonArray passed;

        @Override
        public String getCommandName() {
            return "dinvpack";
        }

        @Override
        public String getCommandUsage(ICommandSender sender) {
            return "/dinvpack selftest|bogogroups|seed|state|lock|unlock|rebind";
        }

        @Override
        public int getRequiredPermissionLevel() {
            return 4;
        }

        @Override
        public void processCommand(ICommandSender sender, String[] args) {
            EntityPlayer player = getCommandSenderAsPlayer(sender);
            String world = MinecraftServer.getServer()
                .worldServerForDimension(0)
                .getWorldInfo()
                .getWorldName();
            if (!world.startsWith("DI-Baseline-") || !world.contains("Qualification") || args.length != 1)
                throw new IllegalArgumentException("Requires a disposable DI-Baseline-...Qualification world");
            passed = new JsonArray();
            JsonObject report = new JsonObject();
            try {
                if (args[0].equals("selftest")) selftest(player);
                else if (args[0].equals("bogogroups")) {
                    if (!cpw.mods.fml.common.Loader.isModLoaded("TConstruct")) throw new IllegalStateException(
                        "Requires TConstruct at runtime; the minimal source profile only provides its compile API");
                    for (String check : BogoInventoryFixtures.run(player)) passed.add(new JsonPrimitive(check));
                } else if (args[0].equals("seed")) seed(player);
                else if (args[0].equals("rebind")) {
                    baubles.common.container.InventoryBaubles current = baubles.common.lib.PlayerHandler
                        .getPlayerBaubles(player);
                    NBTTagCompound saved = new NBTTagCompound();
                    current.saveNBT(saved);
                    baubles.common.container.InventoryBaubles replacement = new baubles.common.container.InventoryBaubles(
                        player);
                    replacement.readNBT(saved);
                    baubles.common.lib.PlayerHandler.setPlayerBaubles(player, replacement);
                    baubles.common.network.PacketHandler.INSTANCE.sendTo(
                        new baubles.common.network.PacketSyncAllBauble(player),
                        (net.minecraft.entity.player.EntityPlayerMP) player);
                    for (Slot slot : player.inventoryContainer.inventorySlots) {
                        if (!(slot instanceof baubles.common.container.SlotBauble)) continue;
                        slot.getStack();
                        check(
                            slot.inventory == replacement,
                            "Native slot follows replacement canonical Baubles inventory");
                    }
                } else if (args[0].equals("lock") || args[0].equals("unlock")) {
                    boolean found = false;
                    for (int i = 0; i < BaublesApi.getBaubles(player)
                        .getSizeInventory(); i++) {
                        ItemStack stack = BaublesApi.getBaubles(player)
                            .getStackInSlot(i);
                        if (stack != null && stack.getItem() == RING) {
                            stack.getTagCompound()
                                .setBoolean("locked", args[0].equals("lock"));
                            found = true;
                        }
                    }
                    check(found, "Equipped test ring exists for permission change");
                    BaublesApi.getBaubles(player)
                        .markDirty();
                } else if (!args[0].equals("state")) throw new IllegalArgumentException(getCommandUsage(sender));
                report.addProperty("success", true);
            } catch (Throwable failure) {
                report.addProperty("success", false);
                report.addProperty("failure", failure.toString());
                DynamicInventory.LOG.error("Pack qualification failed", failure);
            }
            report.add("passed", passed);
            JsonObject callbacks = new JsonObject();
            callbacks.addProperty("equips", callbacks(player)[0]);
            callbacks.addProperty("unequips", callbacks(player)[1]);
            report.add("callbacks", callbacks);
            report.add("inventory", BaselineDiagnostics.snapshot(player, "server", "pack-" + args[0]));
            JsonArray baubles = new JsonArray();
            for (int i = 0; i < BaublesApi.getBaubles(player)
                .getSizeInventory(); i++) {
                ItemStack stack = BaublesApi.getBaubles(player)
                    .getStackInSlot(i);
                JsonObject entry = new JsonObject();
                entry.addProperty("index", i);
                if (stack != null) entry.addProperty(
                    "nbt",
                    stack.writeToNBT(new NBTTagCompound())
                        .toString());
                baubles.add(entry);
            }
            report.add("baubles", baubles);
            report.addProperty(
                "dedicatedServer",
                MinecraftServer.getServer()
                    .isDedicatedServer());
            try {
                QualificationCommand.write(player, "server-pack-" + args[0], report);
            } catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
            sender.addChatMessage(new ChatComponentText("Pack " + args[0] + " success=" + report.get("success")));
        }

        private void check(boolean condition, String description) {
            if (!condition) throw new AssertionError(description);
            passed.add(new JsonPrimitive(description));
        }

        private void clearOrdinary(EntityPlayer player) {
            for (int logical = 0; logical < InventoryLayout.mainSlots(); logical++)
                player.inventory.mainInventory[InventoryLayout.rawIndex(logical)] = null;
        }

        private void seed(EntityPlayer player) {
            check(player.inventory.getItemStack() == null, "Cursor is empty before seed");
            clearOrdinary(player);
            Arrays.fill(player.inventory.armorInventory, null);
            for (int i = 0; i < BaublesApi.getBaubles(player)
                .getSizeInventory(); i++)
                BaublesApi.getBaubles(player)
                    .setInventorySlotContents(i, null);
            CALLBACKS.put(
                player.getUniqueID()
                    .toString(),
                new int[2]);
            player.inventory.mainInventory[36] = tagged(Item.getItemFromBlock(Blocks.planks), 13, "protected-offhand");
            player.inventory.armorInventory[0] = tagged(Items.leather_boots, 1, "protected-armor");
            player.inventory.mainInventory[9] = tagged(RING, 1, "test-ring");
            player.inventory.mainInventory[37] = tagged(Items.diamond, 7, "first-extra");
            // All crafting materials start beyond the original storage, in the final ordinary slot.
            player.inventory.mainInventory[63] = new ItemStack(Blocks.planks, 4);
            player.inventory.markDirty();
            player.inventoryContainer.detectAndSendChanges();
        }

        private void selftest(EntityPlayer player) {
            check(
                player.openContainer == player.inventoryContainer,
                "Selftest starts in the persistent player container");
            check(player.inventory.getItemStack() == null, "Selftest has no cursor stack");
            ItemStack[] main = Arrays.stream(player.inventory.mainInventory)
                .map(stack -> stack == null ? null : stack.copy())
                .toArray(ItemStack[]::new);
            ItemStack[] armor = Arrays.stream(player.inventory.armorInventory)
                .map(stack -> stack == null ? null : stack.copy())
                .toArray(ItemStack[]::new);
            NBTTagCompound entityData = (NBTTagCompound) player.getEntityData()
                .copy();
            try {
                GuiSortingContext.cleanup();
                GuiSortingContext context = GuiSortingContext.create(player.inventoryContainer);
                check(
                    context.getPlayerSlotGroup()
                        .getSlots()
                        .size() == 54,
                    "BogoSorter groups all 54 ordinary storage slots");
                for (Slot slot : player.inventoryContainer.inventorySlots) {
                    boolean expected = slot.inventory == player.inventory
                        && InventoryLayout.ordinaryIndex(slot.getSlotIndex()) >= 0;
                    check(
                        BogoSortAPI.isPlayerSlot(slot) == expected,
                        "BogoSorter role classification for container slot " + slot.slotNumber);
                }
                check(context.getSlotGroup(45) == null, "Offhand is outside sorting groups");
                for (int source : new int[] { 9, 35, 37, 40, 41, 63 }) {
                    clearOrdinary(player);
                    player.inventory.mainInventory[36] = tagged(
                        Item.getItemFromBlock(Blocks.dirt),
                        11,
                        "protected-offhand");
                    player.inventory.mainInventory[source] = tagged(
                        Item.getItemFromBlock(Blocks.dirt),
                        5,
                        "refill-" + source);
                    check(
                        new RefillHandler(0, new ItemStack(Blocks.dirt), player, false).handleRefill(),
                        "Refill finds raw source " + source);
                    check(
                        player.inventory.mainInventory[source] == null
                            && player.inventory.mainInventory[0].stackSize == 5,
                        "Refill removes only raw source " + source + " and fills hotbar");
                    check(
                        player.inventory.mainInventory[0].getTagCompound()
                            .getString("packTestRole")
                            .equals("refill-" + source),
                        "Refill retains complete source NBT at " + source);
                    check(
                        player.inventory.mainInventory[36].stackSize == 11,
                        "Refill preserves offhand at source " + source);
                    for (int i = 0; i < 4; i++) check(
                        ItemStack.areItemStacksEqual(armor[i], player.inventory.armorInventory[i]),
                        "Refill preserves armor " + i + " at source " + source);
                }
                clearOrdinary(player);
                player.inventory.mainInventory[36] = tagged(
                    Item.getItemFromBlock(Blocks.dirt),
                    11,
                    "protected-offhand");
                check(
                    !new RefillHandler(0, new ItemStack(Blocks.dirt), player, false).handleRefill(),
                    "Refill never uses offhand as an ordinary candidate");
                clearOrdinary(player);
                player.inventory.mainInventory[0] = tagged(Items.emerald, 1, "hotbar-sentinel");
                for (int logical = 9; logical < 63; logical++)
                    player.inventory.mainInventory[InventoryLayout.rawIndex(logical)] = tagged(
                        logical % 2 == 0 ? Items.diamond : Items.iron_ingot,
                        logical % 7 + 1,
                        "sort-" + logical);
                if (PinnedSlots.isPinned(player, 9)) PinnedSlots.toggle(player, 9);
                PinnedSlots.toggle(player, 9);
                ItemStack pinned = player.inventory.mainInventory[9].copy();
                Map<String, Integer> before = totals(player);
                new SortHandler(
                    player,
                    player.inventoryContainer,
                    Collections.emptyList(),
                    Collections.emptyList(),
                    new Int2ObjectOpenHashMap<>()).sort(72);
                check(
                    before.equals(totals(player)),
                    "Sorting from the final extra conserves item type, damage, NBT and total counts");
                check(
                    ItemStack.areItemStacksEqual(pinned, player.inventory.mainInventory[9]),
                    "Original-slot Bogo pin remains fixed");
                check(
                    player.inventory.mainInventory[0].getTagCompound()
                        .getString("packTestRole")
                        .equals("hotbar-sentinel"),
                    "Storage sorting leaves the hotbar unchanged");
                check(player.inventory.mainInventory[36].stackSize == 11, "Sorting leaves offhand unchanged");
                for (int i = 0; i < 4; i++) check(
                    ItemStack.areItemStacksEqual(armor[i], player.inventory.armorInventory[i]),
                    "Sorting preserves armor " + i);
            } finally {
                System.arraycopy(main, 0, player.inventory.mainInventory, 0, main.length);
                System.arraycopy(armor, 0, player.inventory.armorInventory, 0, armor.length);
                player.getEntityData()
                    .setTag(EntityPlayer.PERSISTED_NBT_TAG, entityData.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG));
                player.inventory.markDirty();
                player.inventoryContainer.detectAndSendChanges();
                GuiSortingContext.cleanup();
            }
        }

        private Map<String, Integer> totals(EntityPlayer player) {
            Map<String, Integer> counts = new TreeMap<>();
            for (ItemStack stack : player.inventory.mainInventory) {
                if (stack == null) continue;
                ItemStack unit = stack.copy();
                unit.stackSize = 1;
                counts.merge(
                    unit.writeToNBT(new NBTTagCompound())
                        .toString(),
                    stack.stackSize,
                    Integer::sum);
            }
            return counts;
        }
    }
}
