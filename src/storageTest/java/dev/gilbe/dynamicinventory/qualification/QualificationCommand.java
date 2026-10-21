package dev.gilbe.dynamicinventory.qualification;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.DamageSource;
import net.minecraft.world.Teleporter;
import net.minecraft.world.WorldServer;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import dev.gilbe.dynamicinventory.compat.BackhandCompatibility;
import dev.gilbe.dynamicinventory.diagnostics.BaselineDiagnostics;
import dev.gilbe.dynamicinventory.inventory.InventoryLayout;

public final class QualificationCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "dinvqualify";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/dinvqualify seed|begin|end|chest|verify|kill-keep|kill-drop|verify-drops|verify-empty|nether|overworld";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 4;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        EntityPlayerMP player = (EntityPlayerMP) getCommandSenderAsPlayer(sender);
        String world = MinecraftServer.getServer()
            .worldServerForDimension(0)
            .getWorldInfo()
            .getWorldName();
        if (!world.startsWith("DI-Baseline-") || !world.contains("Qualification") || args.length < 1)
            throw new IllegalArgumentException("Requires the disposable DI-Baseline-...Qualification world");
        String action = args[0];
        JsonObject report = new JsonObject();
        report.addProperty(
            "dedicatedServer",
            MinecraftServer.getServer()
                .isDedicatedServer());
        report.addProperty(
            "onlinePlayers",
            MinecraftServer.getServer()
                .getCurrentPlayerCount());
        try {
            switch (action) {
                case "resize-check": {
                    var shape = InventoryLayout.of(player.inventory);
                    check(shape.storageSlots() == Integer.parseInt(args[1]), "server-owned capacity");
                    check(player.inventory.mainInventory.length == shape.rawMainSlots(), "server backing length");
                    check(dev.gilbe.dynamicinventory.api.InventoryUi.isOpeningReady(player), "resize gate released");
                    dev.gilbe.dynamicinventory.network.InventoryResizeProtocol.describe(player);
                    NBTTagCompound saved = new NBTTagCompound();
                    player.writeToNBT(saved);
                    // Reload into a detached actor, so this check cannot replace live arrays or listeners.
                    EntityPlayer detached = dev.gilbe.dynamicinventory.StorageTestCommand.detachedPlayer(player);
                    detached.readFromNBT(saved);
                    check(
                        InventoryLayout.of(detached.inventory)
                            .storageSlots() == shape.storageSlots(),
                        "saved override");
                    check(
                        InventoryLayout.of(detached.inventory)
                            .revision() == shape.revision(),
                        "saved revision");
                    for (int i = 0; i < shape.rawMainSlots(); i++) check(
                        ItemStack
                            .areItemStacksEqual(player.inventory.mainInventory[i], detached.inventory.mainInventory[i]),
                        "saved contents " + i);
                    break;
                }
                case "resize-refuse": {
                    var before = InventoryLayout.of(player.inventory);
                    ItemStack[] backing = player.inventory.mainInventory;
                    ItemStack[] copies = new ItemStack[backing.length];
                    for (int i = 0; i < backing.length; i++) copies[i] = ItemStack.copyItemStack(backing[i]);
                    var container = player.inventoryContainer;
                    boolean refused = false;
                    try {
                        dev.gilbe.dynamicinventory.inventory.InventoryResizing
                            .resize(player, Integer.parseInt(args[1]));
                    } catch (IllegalArgumentException | IllegalStateException expected) {
                        refused = true;
                    }
                    check(refused, "unsafe resize refused");
                    check(
                        InventoryLayout.of(player.inventory) == before && player.inventory.mainInventory == backing
                            && player.inventoryContainer == container,
                        "refusal leaves live references untouched");
                    for (int i = 0; i < backing.length; i++)
                        check(ItemStack.areItemStacksEqual(backing[i], copies[i]), "refusal preserves contents " + i);
                    break;
                }
                case "seed":
                    seed(player);
                    break;
                case "pick-seed":
                    // A full hotbar makes Forge replace the selected slot instead of
                    // choosing an empty one. Unique tags let the packet test detect
                    // accidental writes to any neighboring or expanded storage slot.
                    for (int logical = 0; logical < InventoryLayout.mainSlots(); logical++) {
                        ItemStack stack = new ItemStack(Items.stick, logical % 63 + 1);
                        stack.setTagCompound(new NBTTagCompound());
                        stack.getTagCompound()
                            .setInteger("pickBlockProbe", logical);
                        player.inventory.mainInventory[InventoryLayout.rawIndex(logical)] = stack;
                    }
                    player.inventory.markDirty();
                    player.inventoryContainer.detectAndSendChanges();
                    break;
                case "begin":
                    TickMeasurements.begin(player, args[1]);
                    break;
                case "end":
                    report.add("ticks", TickMeasurements.finish(player));
                    check(
                        report.getAsJsonObject("ticks")
                            .get("success")
                            .getAsBoolean(),
                        "exactly-once ticking");
                    break;
                case "chest":
                    player.displayGUIChest(new InventoryBasic("Qualification chest", true, 9));
                    break;
                case "verify":
                    verify(player);
                    break;
                case "kill-keep":
                    player.worldObj.getGameRules()
                        .setOrCreateGameRule("keepInventory", "true");
                    player.attackEntityFrom(DamageSource.outOfWorld, Float.MAX_VALUE);
                    break;
                case "kill-drop":
                    player.worldObj.getGameRules()
                        .setOrCreateGameRule("keepInventory", "false");
                    player.attackEntityFrom(DamageSource.outOfWorld, Float.MAX_VALUE);
                    break;
                case "verify-empty":
                    for (ItemStack stack : player.inventory.mainInventory)
                        check(stack == null, "main empty after ordinary respawn");
                    for (ItemStack stack : player.inventory.armorInventory)
                        check(stack == null, "armor empty after ordinary respawn");
                    break;
                case "verify-drops":
                    verifyDrops(player);
                    break;
                case "nether":
                    travel(player, -1);
                    break;
                case "overworld":
                    travel(player, 0);
                    break;
                default:
                    throw new IllegalArgumentException(getCommandUsage(sender));
            }
            report.addProperty("success", true);
            sender.addChatMessage(new ChatComponentText("PASS qualification: " + action));
        } catch (Throwable failure) {
            report.addProperty("success", false);
            report.addProperty("failure", failure.toString());
            sender.addChatMessage(new ChatComponentText("FAIL qualification: " + failure));
        }
        report.add("inventory", BaselineDiagnostics.snapshot(player, "server", "qualification-" + action));
        try {
            write(player, "server-" + action, report);
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    public static void write(EntityPlayer player, String name, JsonObject report) throws Exception {
        File directory = new File("diagnostics/dynamicinventory/qualification");
        Files.createDirectories(directory.toPath());
        Files.write(
            new File(directory, player.getCommandSenderName() + "-" + name + ".json").toPath(),
            new GsonBuilder().setPrettyPrinting()
                .create()
                .toJson(report)
                .getBytes(StandardCharsets.UTF_8));
    }

    private static void seed(EntityPlayer player) {
        check(
            player.openContainer == player.inventoryContainer && player.inventory.getItemStack() == null,
            "close other containers first");
        Arrays.fill(player.inventory.mainInventory, null);
        Arrays.fill(player.inventory.armorInventory, null);
        ContainerPlayer container = (ContainerPlayer) player.inventoryContainer;
        for (int i = 0; i < 4; i++) container.craftMatrix.setInventorySlotContents(i, null);
        for (int main : new int[] { 9, 36, InventoryLayout.mainSlots() - 1 })
            player.inventory.mainInventory[InventoryLayout
                .rawIndex(main)] = tagged(new ItemStack(CoreFixtures.probe), player, "probe-" + main);
        player.inventory.mainInventory[InventoryLayout
            .rawIndex(InventoryLayout.mainSlots() - 2)] = tagged(new ItemStack(Items.diamond, 7), player, "payload");
        if (BackhandCompatibility.enabled()) player.inventory.mainInventory[InventoryLayout.OFFHAND_INDEX] = tagged(
            new ItemStack(CoreFixtures.probe),
            player,
            "probe-offhand");
        player.inventory.armorInventory[3] = tagged(new ItemStack(Items.iron_helmet, 1, 12), player, "helmet");
        player.setHealth(player.getMaxHealth());
        player.inventory.markDirty();
    }

    private static void verify(EntityPlayer player) {
        for (int main = 0; main < InventoryLayout.mainSlots(); main++) {
            ItemStack expected = null;
            if (main == 9 || main == 36 || main == InventoryLayout.mainSlots() - 1)
                expected = tagged(new ItemStack(CoreFixtures.probe), player, "probe-" + main);
            if (main == InventoryLayout.mainSlots() - 2)
                expected = tagged(new ItemStack(Items.diamond, 7), player, "payload");
            check(
                ItemStack.areItemStacksEqual(expected, player.inventory.mainInventory[InventoryLayout.rawIndex(main)]),
                "exact NBT at main index " + main);
        }
        if (BackhandCompatibility.enabled()) check(
            ItemStack.areItemStacksEqual(
                tagged(new ItemStack(CoreFixtures.probe), player, "probe-offhand"),
                player.inventory.mainInventory[InventoryLayout.OFFHAND_INDEX]),
            "exact offhand NBT");
        check(
            ItemStack.areItemStacksEqual(
                player.inventory.armorInventory[3],
                tagged(new ItemStack(Items.iron_helmet, 1, 12), player, "helmet")),
            "exact armor NBT");
        for (int armor = 0; armor < 3; armor++)
            check(player.inventory.armorInventory[armor] == null, "other armor empty");
    }

    private static void verifyDrops(EntityPlayer player) {
        int probes = 0;
        int diamonds = 0;
        int helmets = 0;
        List<?> entities = player.worldObj
            .getEntitiesWithinAABB(EntityItem.class, player.boundingBox.expand(32, 32, 32));
        for (Object object : entities) {
            EntityItem entity = (EntityItem) object;
            ItemStack stack = entity.getEntityItem();
            if (!stack.hasTagCompound() || !stack.getTagCompound()
                .getString("owner")
                .equals(
                    player.getUniqueID()
                        .toString()))
                continue;
            if (stack.getItem() == CoreFixtures.probe) probes += stack.stackSize;
            if (stack.getItem() == Items.diamond) {
                check(
                    stack.getTagCompound()
                        .equals(tagged(new ItemStack(Items.diamond), player, "payload").getTagCompound()),
                    "drop payload NBT");
                diamonds += stack.stackSize;
            }
            if (stack.getItem() == Items.iron_helmet) {
                check(stack.getItemDamage() == 12, "drop armor damage");
                helmets += stack.stackSize;
            }
        }
        check(
            probes == (BackhandCompatibility.enabled() ? 4 : 3) && diamonds == 7 && helmets == 1,
            "all normal/extra/equipment drops conserved: " + probes + "/" + diamonds + "/" + helmets);
    }

    private static void travel(EntityPlayerMP player, int dimension) {
        WorldServer target = MinecraftServer.getServer()
            .worldServerForDimension(dimension);
        MinecraftServer.getServer()
            .getConfigurationManager()
            .transferPlayerToDimension(player, dimension, new Teleporter(target) {

                @Override
                public void placeInPortal(Entity entity, double x, double y, double z, float yaw) {
                    int floor = dimension == -1 ? 128 : 4;
                    for (int dx = -2; dx <= 2; dx++)
                        for (int dz = -2; dz <= 2; dz++) target.setBlock(1000 + dx, floor, 1000 + dz, Blocks.bedrock);
                    entity.setLocationAndAngles(1000.5, floor + 1, 1000.5, 0, 0);
                }
            });
    }

    private static ItemStack tagged(ItemStack stack, EntityPlayer player, String role) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString(
            "owner",
            player.getUniqueID()
                .toString());
        tag.setString("role", role);
        NBTTagCompound nested = new NBTTagCompound();
        nested.setInteger("qualification", 17017);
        tag.setTag("nested", nested);
        stack.setTagCompound(tag);
        return stack;
    }

    private static void check(boolean success, String message) {
        if (!success) throw new AssertionError(message);
    }
}
