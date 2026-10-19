package dev.gilbe.dynamicinventory;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentText;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import dev.gilbe.dynamicinventory.container.PlayerContainerLayout;
import dev.gilbe.dynamicinventory.diagnostics.BaselineDiagnostics;
import dev.gilbe.dynamicinventory.inventory.InventoryLayout;

/** Server-thread fixtures for real window-click and synchronization tests. Never included in the mod jar. */
public final class ContainerTestCommand extends CommandBase {

    private JsonArray passed;

    @Override
    public String getCommandName() {
        return "dinvcontainertest";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/dinvcontainertest selftest|seed|verify|sync|clear-sync|verify-drop|verify-close|fill|verify-fill";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 4;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        EntityPlayer player = getCommandSenderAsPlayer(sender);
        String world = player.worldObj.getWorldInfo()
            .getWorldName();
        if (!world.startsWith("DI-Baseline-") || !world.contains("Step3") || args.length != 1)
            throw new IllegalArgumentException("Requires a disposable DI-Baseline-...Step3 world");
        passed = new JsonArray();
        JsonObject report = new JsonObject();
        try {
            switch (args[0]) {
                case "selftest":
                    selftest(player);
                    break;
                case "seed":
                    seed(player);
                    break;
                case "verify":
                    verify(player);
                    break;
                case "sync":
                    player.inventory.mainInventory[61] = tagged(new ItemStack(Items.redstone, 11), "closed-sync");
                    break;
                case "clear-sync":
                    player.inventory.mainInventory[61] = null;
                    break;
                case "verify-drop":
                    recoverDrops(player, false);
                    verify(player);
                    break;
                case "verify-close":
                    recoverDrops(player, true);
                    verify(player);
                    break;
                case "fill":
                    clear(player);
                    Arrays.fill(player.inventory.mainInventory, null);
                    for (int i = 0; i < InventoryLayout.mainSlots(); i++)
                        player.inventory.mainInventory[i] = new ItemStack(Blocks.stone, 64);
                    player.inventory.mainInventory[36] = new ItemStack(Blocks.dirt, 63);
                    player.inventory.mainInventory[InventoryLayout.mainSlots() - 1] = null;
                    break;
                case "verify-fill":
                    check(
                        player.inventory.mainInventory[36].stackSize == 64
                            && player.inventory.mainInventory[InventoryLayout.mainSlots() - 1].stackSize == 2,
                        "server pickup merges into first extra and inserts into final extra");
                    break;
                default:
                    throw new IllegalArgumentException(getCommandUsage(sender));
            }
            player.inventory.markDirty();
            report.addProperty("success", true);
            sender.addChatMessage(new ChatComponentText("PASS container tests: " + args[0]));
        } catch (Throwable failure) {
            report.addProperty("success", false);
            report.addProperty("failure", failure.toString());
            DynamicInventory.LOG.error("Container tests failed", failure);
            sender.addChatMessage(new ChatComponentText("FAIL container tests: " + failure));
        }
        report.add("passed", passed);
        report.add("serverInventory", BaselineDiagnostics.snapshot(player, "server", "container-test-" + args[0]));
        try {
            File directory = new File("diagnostics/dynamicinventory");
            Files.createDirectories(directory.toPath());
            Files.write(
                new File(directory, "container-tests-" + args[0] + ".json").toPath(),
                new GsonBuilder().setPrettyPrinting()
                    .create()
                    .toJson(report)
                    .getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void selftest(EntityPlayer owner) {
        EntityPlayer player = StorageTestCommand.detachedPlayer(owner);
        ContainerPlayer container = (ContainerPlayer) player.inventoryContainer;
        check(
            container.inventorySlots.size() == PlayerContainerLayout.totalSlots(),
            "Configured real slots constructed");
        boolean[] seen = new boolean[InventoryLayout.mainSlots()];
        for (Object entry : container.inventorySlots) {
            Slot slot = (Slot) entry;
            int main = PlayerContainerLayout.mainIndex(slot.slotNumber);
            if (main < 0) continue;
            check(
                slot.inventory == player.inventory && slot.getSlotIndex() == main && !seen[main],
                "canonical unique backing index " + main);
            seen[main] = true;
        }
        for (boolean found : seen) check(found, "main slot exposed");

        player.inventory.mainInventory[0] = new ItemStack(Blocks.stone, 4);
        player.inventory.mainInventory[36] = new ItemStack(Blocks.stone, 60);
        container.slotClick(36, 0, 1, player);
        check(
            player.inventory.mainInventory[36].stackSize == 64 && player.inventory.mainInventory[9] == null,
            "shift merge crosses legacy/extra boundary before taking an empty legacy slot");
        clear(player);
        for (int i = 9; i < InventoryLayout.mainSlots() - 1; i++)
            player.inventory.mainInventory[i] = new ItemStack(Blocks.stone, 64);
        player.inventory.mainInventory[0] = new ItemStack(Blocks.dirt, 8);
        container.slotClick(36, 0, 1, player);
        check(
            player.inventory.mainInventory[InventoryLayout.mainSlots() - 1].stackSize == 8
                && player.inventory.mainInventory[0] == null,
            "shift hotbar reaches final extra slot");
        clear(player);
        player.inventory.mainInventory[InventoryLayout.mainSlots() - 1] = new ItemStack(Items.iron_helmet);
        container.slotClick(PlayerContainerLayout.containerId(InventoryLayout.mainSlots() - 1), 0, 1, player);
        check(
            player.inventory.armorInventory[3] != null
                && player.inventory.mainInventory[InventoryLayout.mainSlots() - 1] == null,
            "shift extra armor equips helmet");
        clear(player);
        player.inventory.mainInventory[InventoryLayout.mainSlots() - 1] = new ItemStack(Blocks.stone, 8);
        for (int i = 0; i < 9; i++) player.inventory.mainInventory[i] = new ItemStack(Blocks.dirt, 64);
        container.slotClick(PlayerContainerLayout.containerId(InventoryLayout.mainSlots() - 1), 0, 1, player);
        check(
            player.inventory.mainInventory[InventoryLayout.mainSlots() - 1].stackSize == 8,
            "full destination leaves source intact");
        clear(player);
        container.craftMatrix.setInventorySlotContents(0, new ItemStack(Blocks.planks, 2));
        container.craftMatrix.setInventorySlotContents(2, new ItemStack(Blocks.planks, 2));
        container.slotClick(0, 0, 1, player);
        check(
            player.inventory.mainInventory[8].getItem() == Items.stick
                && player.inventory.mainInventory[8].stackSize == 8
                && container.craftMatrix.getStackInSlot(0) == null
                && container.craftMatrix.getStackInSlot(2) == null,
            "shift crafting repeats recipe and consumes exactly four planks for eight sticks");
    }

    private void seed(EntityPlayer player) {
        check(player.openContainer == player.inventoryContainer, "player container active for fixture");
        clear(player);
        player.inventory.mainInventory[0] = new ItemStack(Blocks.stone, 16);
        player.inventory.mainInventory[1] = new ItemStack(Blocks.dirt, 4);
        player.inventory.mainInventory[2] = new ItemStack(Items.iron_helmet);
        player.inventory.mainInventory[3] = new ItemStack(Blocks.planks, 4);
        player.inventory.mainInventory[36] = tagged(new ItemStack(Items.diamond, 7), "first-extra");
        player.inventory.mainInventory[InventoryLayout.mainSlots() - 1] = tagged(
            new ItemStack(Items.emerald, 9),
            "last-extra");
    }

    private void verify(EntityPlayer player) {
        ItemStack[] expected = new ItemStack[InventoryLayout.mainSlots()];
        expected[8] = new ItemStack(Blocks.crafting_table);
        expected[36] = new ItemStack(Blocks.dirt, 4);
        expected[37] = new ItemStack(Blocks.stone, 8);
        expected[39] = tagged(new ItemStack(Items.diamond, 7), "first-extra");
        expected[40] = tagged(new ItemStack(Items.emerald, 9), "last-extra");
        expected[InventoryLayout.mainSlots() - 1] = new ItemStack(Blocks.stone, 8);
        for (int main = 0; main < InventoryLayout.mainSlots(); main++) check(
            ItemStack.areItemStacksEqual(expected[main], player.inventory.mainInventory[main]),
            "exact final main slot " + main);
        check(
            ItemStack.areItemStacksEqual(player.inventory.armorInventory[3], new ItemStack(Items.iron_helmet)),
            "expected equipped helmet");
        for (int armor = 0; armor < 3; armor++)
            check(player.inventory.armorInventory[armor] == null, "other armor empty");
        check(player.inventory.getItemStack() == null, "cursor empty");
        ContainerPlayer container = (ContainerPlayer) player.inventoryContainer;
        for (int i = 0; i < 4; i++) check(container.craftMatrix.getStackInSlot(i) == null, "crafting input empty");
    }

    private void recoverDrops(EntityPlayer player, boolean closed) {
        if (closed) {
            check(player.inventory.getItemStack() == null, "cursor cleared on close");
            ContainerPlayer container = (ContainerPlayer) player.inventoryContainer;
            for (int i = 0; i < 4; i++)
                check(container.craftMatrix.getStackInSlot(i) == null, "crafting cleared on close");
            check(
                ItemStack.areItemStacksEqual(
                    player.inventory.mainInventory[0],
                    tagged(new ItemStack(Items.diamond, 7), "first-extra"))
                    && ItemStack.areItemStacksEqual(player.inventory.mainInventory[37], new ItemStack(Blocks.stone, 16))
                    && player.inventory.mainInventory[39] == null
                    && player.inventory.mainInventory[InventoryLayout.mainSlots() - 1] == null,
                "Hodgepodge close policy returns exact cursor NBT and merges crafting items without duplication");
            player.inventory.mainInventory[0] = null;
            player.inventory.mainInventory[37].stackSize = 8;
            player.inventory.mainInventory[InventoryLayout.mainSlots() - 1] = new ItemStack(Blocks.stone, 8);
            player.inventory.mainInventory[39] = tagged(new ItemStack(Items.diamond, 7), "first-extra");
            return;
        }
        int stone = 0;
        int diamonds = 0;
        List<?> entities = player.worldObj
            .getEntitiesWithinAABB(EntityItem.class, player.boundingBox.expand(16, 16, 16));
        for (Object entry : entities) {
            EntityItem entity = (EntityItem) entry;
            ItemStack stack = entity.getEntityItem();
            if (stack.getItem() == Item.getItemFromBlock(Blocks.stone)) {
                stone += stack.stackSize;
                entity.setDead();
            } else if (stack.getItem() == Items.diamond && stack.hasTagCompound()
                && stack.getTagCompound()
                    .equals(tagged(new ItemStack(Items.diamond), "first-extra").getTagCompound())) {
                        diamonds += stack.stackSize;
                        entity.setDead();
                    }
        }
        check(stone == 1 && diamonds == 0, "server drops conserve expected items and NBT");
        check(
            player.inventory.mainInventory[InventoryLayout.mainSlots() - 1].stackSize == 7,
            "drop reduces final extra slot by one");
        player.inventory.mainInventory[InventoryLayout.mainSlots() - 1].stackSize++;
        // Restore the known fixture after counting/removing its drops, avoiding later pickups contaminating tests.
    }

    private static void clear(EntityPlayer player) {
        Arrays.fill(player.inventory.mainInventory, null);
        Arrays.fill(player.inventory.armorInventory, null);
        player.inventory.setItemStack(null);
        ContainerPlayer container = (ContainerPlayer) player.inventoryContainer;
        for (int i = 0; i < 4; i++) container.craftMatrix.setInventorySlotContents(i, null);
    }

    private static ItemStack tagged(ItemStack stack, String marker) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("fixture", marker);
        NBTTagCompound nested = new NBTTagCompound();
        nested.setInteger("value", 17017);
        tag.setTag("nested", nested);
        stack.setTagCompound(tag);
        return stack;
    }

    private void check(boolean result, String message) {
        if (!result) throw new AssertionError(message);
        passed.add(new JsonPrimitive(message));
    }
}
