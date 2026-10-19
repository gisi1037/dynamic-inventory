package dev.gilbe.dynamicinventory.qualification;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.relauncher.Side;
import dev.gilbe.dynamicinventory.DynamicInventory;
import dev.gilbe.dynamicinventory.container.PlayerContainerLayout;
import dev.gilbe.dynamicinventory.diagnostics.BaselineDiagnostics;
import dev.gilbe.dynamicinventory.inventory.ExpandedInventoryAccess;
import dev.gilbe.dynamicinventory.inventory.InventoryCapacity;
import dev.gilbe.dynamicinventory.inventory.InventoryLayout;
import dev.gilbe.dynamicinventory.persistence.InventoryCodec;
import xonin.backhand.api.core.BackhandSlot;
import xonin.backhand.api.core.IOffhandInventory;
import xonin.backhand.utils.BackhandConfig;

/** Optional API tests, compiled only for the Backhand fixture and excluded from release jars. */
@Mod(
    modid = "dinvbackhandfixtures",
    version = "1.0",
    name = "Dynamic Inventory Backhand Tests",
    dependencies = "required-after:dinvfixtures;required-after:backhand")
public final class BackhandFixtures {

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new Command());
    }

    private static final class Command extends CommandBase {

        private JsonArray passed;

        @Override
        public String getCommandName() {
            return "dinvbackhand";
        }

        @Override
        public String getCommandUsage(ICommandSender sender) {
            return "/dinvbackhand selftest";
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
            if (!world.startsWith("DI-Baseline-") || !world.contains("Qualification")
                || args.length != 1
                || !args[0].equals("selftest")) throw new IllegalArgumentException(getCommandUsage(sender));
            passed = new JsonArray();
            JsonObject report = new JsonObject();
            try {
                selftest(player);
                report.addProperty("success", true);
            } catch (Throwable failure) {
                report.addProperty("success", false);
                report.addProperty("failure", failure.toString());
                DynamicInventory.LOG.error("Backhand qualification failed", failure);
            }
            report.add("passed", passed);
            report.add("inventory", BaselineDiagnostics.snapshot(player, "server", "backhand-selftest"));
            try {
                QualificationCommand.write(player, "server-backhand-selftest", report);
            } catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
            sender.addChatMessage(new ChatComponentText("Backhand qualification success=" + report.get("success")));
        }

        private void check(boolean condition, String message) {
            if (!condition) throw new AssertionError(message);
            passed.add(new JsonPrimitive(message));
        }

        private void selftest(EntityPlayer player) {
            InventoryPlayer inventory = new InventoryPlayer(player);
            check(
                inventory.mainInventory.length == 64 && inventory.getSizeInventory() == 68,
                "63 ordinary plus fixed offhand and four armor slots");
            check(((IOffhandInventory) inventory).backhand$getOffhandSlot() == 36, "Backhand API keeps index 36");
            check(
                player.inventoryContainer.inventorySlots.size() == PlayerContainerLayout.totalSlots(),
                "All real player-container slots constructed");
            check(
                player.inventoryContainer.getSlot(45) instanceof BackhandSlot && player.inventoryContainer.getSlot(45)
                    .getSlotIndex() == 36,
                "original BackhandSlot identity retained at container 45");
            for (int ordinary = 0; ordinary < 63; ordinary++) {
                int raw = InventoryLayout.rawIndex(ordinary);
                check(
                    raw != 36 && InventoryLayout.ordinaryIndex(raw) == ordinary
                        && player.inventoryContainer.getSlot(PlayerContainerLayout.containerId(ordinary))
                            .getSlotIndex() == raw,
                    "ordinary/container mapping " + ordinary + " -> " + raw);
            }
            ItemStack offhand = tagged(new ItemStack(Items.diamond_sword, 1, 17), "legacy-offhand");
            NBTTagList legacy = new NBTTagList();
            append(legacy, offhand, 36);
            append(legacy, tagged(new ItemStack(Items.emerald, 3), "legacy-main"), 35);
            append(legacy, new ItemStack(Items.iron_boots, 1, 12), 100);
            inventory.readFromNBT(legacy);
            check(
                ItemStack.areItemStacksEqual(offhand, ((IOffhandInventory) inventory).backhand$getOffhandItem()),
                "legacy byte Slot 36 remains offhand with complete NBT");
            check(
                inventory.mainInventory[37] == null && inventory.mainInventory[63] == null,
                "legacy migration adds empty ordinary storage");
            check(
                inventory.armorInventory[0].getItemDamage() == 12
                    && inventory.getStackInSlot(64) == inventory.armorInventory[0],
                "armor API starts at raw index 64");

            NBTTagCompound prototype = new NBTTagCompound();
            NBTTagCompound data = new NBTTagCompound();
            data.setInteger("schemaVersion", 1);
            data.setInteger("mainSlots", 63);
            NBTTagList extras = new NBTTagList();
            appendExtra(extras, tagged(new ItemStack(Items.diamond, 7), "prototype-first"), 36);
            appendExtra(extras, tagged(new ItemStack(Items.gold_ingot, 11), "prototype-last"), 62);
            data.setTag("extraItems", extras);
            prototype.setTag("dynamicinventory", data);
            InventoryCodec.readExtra(inventory, prototype);
            check(
                inventory.mainInventory[37].getItem() == Items.diamond && inventory.mainInventory[63].stackSize == 11,
                "0.3 prototype logical extras migrate to raw 37 and 63");
            check(
                ItemStack.areItemStacksEqual(offhand, inventory.mainInventory[36]),
                "legacy offhand and logical extra 36 coexist");
            NBTTagCompound saved = save(inventory);
            check(
                saved.getTagList("Inventory", 10)
                    .tagCount() == 3,
                "only legacy ordinary, offhand and armor enter legacy list");
            check(
                saved.getCompoundTag("dynamicinventory")
                    .getTagList("extraItems", 10)
                    .tagCount() == 2,
                "offhand excluded from ordinary extras");
            InventoryPlayer loaded = new InventoryPlayer(player);
            loaded.readFromNBT(saved.getTagList("Inventory", 10));
            InventoryCodec.readExtra(loaded, saved);
            check(saved.equals(save(loaded)), "stable exact NBT serialization round trip");
            check(((IOffhandInventory) loaded).backhand$getOffhandSlot() == 36, "reload leaves Backhand offset fixed");
            InventoryPlayer copy = new InventoryPlayer(player);
            copy.copyInventory(loaded);
            check(save(copy).equals(saved), "clone copies offhand, extras and armor by role");
            copy.mainInventory[36].getTagCompound()
                .setString("role", "changed-offhand");
            copy.mainInventory[37].getTagCompound()
                .setString("role", "changed-extra");
            check(
                !copy.mainInventory[36].getTagCompound()
                    .equals(loaded.mainInventory[36].getTagCompound())
                    && !copy.mainInventory[37].getTagCompound()
                        .equals(loaded.mainInventory[37].getTagCompound()),
                "clone does not alias offhand or ordinary NBT");
            InventoryPlayer old = new InventoryPlayer(player);
            old.mainInventory = Arrays.copyOf(loaded.mainInventory, 37);
            copy.copyInventory(old);
            check(
                ItemStack.areItemStacksEqual(copy.mainInventory[36], offhand) && copy.mainInventory[37] == null,
                "copy from Backhand's original 37-slot shape preserves equipment");

            NBTTagList duplicate = (NBTTagList) legacy.copy();
            append(duplicate, new ItemStack(Items.stick, 2), 36);
            loaded.readFromNBT(duplicate);
            check(
                ItemStack.areItemStacksEqual(loaded.mainInventory[36], offhand)
                    && ((ExpandedInventoryAccess) loaded).dynamicinventory$getRecovery()
                        .getTagList("items", 10)
                        .tagCount() == 1,
                "duplicate offhand quarantined without overwriting equipment");
            pickupChecks(player);
            containerChecks(player);
            Map<String, String> remote = new HashMap<>();
            remote.put(DynamicInventory.MODID, InventoryCapacity.wireVersion());
            DynamicInventory mod = new DynamicInventory();
            check(!mod.acceptsRemote(remote, Side.CLIENT), "reject remote missing Backhand layout");
            remote.put("backhand", "1.8.15");
            check(!mod.acceptsRemote(remote, Side.CLIENT), "reject unqualified Backhand version");
            remote.put("backhand", "1.8.16");
            check(mod.acceptsRemote(remote, Side.CLIENT), "accept matching Backhand layout and mod version");
        }

        private void containerChecks(EntityPlayer player) {
            InventoryPlayer inventory = new InventoryPlayer(player);
            ContainerPlayer container = new ContainerPlayer(inventory, false, player);
            ItemStack offhand = tagged(new ItemStack(Items.diamond_sword, 1, 17), "protected-equipment");
            inventory.mainInventory[36] = offhand;
            inventory.mainInventory[63] = new ItemStack(Items.gold_ingot, 11);
            container.slotClick(72, 0, 1, player);
            check(
                inventory.mainInventory[63] == null && inventory.mainInventory[0].stackSize == 11,
                "last added slot shift-transfers to hotbar");
            container.slotClick(36, 0, 1, player);
            check(
                inventory.mainInventory[0] == null && inventory.mainInventory[9].stackSize == 11,
                "hotbar shift-transfers into ordinary storage");
            inventory.mainInventory[37] = new ItemStack(Items.iron_helmet);
            container.slotClick(46, 0, 1, player);
            check(
                inventory.mainInventory[37] == null && inventory.armorInventory[3].getItem() == Items.iron_helmet,
                "first added slot shift-equips armor through raw offset 64");
            container.craftMatrix.setInventorySlotContents(0, new ItemStack(Blocks.planks, 2));
            container.craftMatrix.setInventorySlotContents(2, new ItemStack(Blocks.planks, 2));
            container.slotClick(0, 0, 1, player);
            check(
                inventory.mainInventory[8] != null && inventory.mainInventory[8].getItem() == Items.stick
                    && inventory.mainInventory[8].stackSize == 8,
                "shift crafting repeats to produce eight sticks");
            check(
                container.craftMatrix.getStackInSlot(0) == null && container.craftMatrix.getStackInSlot(2) == null,
                "shift crafting consumes all four planks exactly");
            check(
                inventory.mainInventory[36] == offhand && offhand.getItemDamage() == 17,
                "crafting and ordinary transfers leave offhand identity untouched");
        }

        private void pickupChecks(EntityPlayer player) {
            boolean oldPolicy = BackhandConfig.OffhandPickup;
            try {
                BackhandConfig.OffhandPickup = false;
                InventoryPlayer inventory = new InventoryPlayer(player);
                for (int ordinary = 0; ordinary < 63; ordinary++)
                    inventory.mainInventory[InventoryLayout.rawIndex(ordinary)] = new ItemStack(Blocks.stone, 64);
                inventory.mainInventory[37] = null;
                check(inventory.getFirstEmptyStack() == 37, "empty offhand does not hide first expanded pickup slot");
                ItemStack incoming = tagged(new ItemStack(Items.emerald, 3), "pickup");
                check(
                    inventory.addItemStackToInventory(incoming) && inventory.mainInventory[37].stackSize == 3
                        && inventory.mainInventory[36] == null,
                    "pickup enters extra storage without filling offhand");
                inventory.mainInventory[63] = null;
                check(inventory.getFirstEmptyStack() == 63, "pickup reaches final expanded slot");
                inventory.mainInventory[63] = new ItemStack(Blocks.stone, 64);
                check(inventory.getFirstEmptyStack() == -1, "full ordinary storage respects disabled offhand pickup");
                BackhandConfig.OffhandPickup = true;
                check(inventory.getFirstEmptyStack() == 36, "enabled offhand pickup retains Backhand behavior");
                check(
                    inventory.addItemStackToInventory(tagged(new ItemStack(Items.diamond, 2), "offhand-pickup"))
                        && inventory.mainInventory[36].stackSize == 2,
                    "enabled pickup fills equipment only when permitted");
            } finally {
                BackhandConfig.OffhandPickup = oldPolicy;
            }
        }

        private static ItemStack tagged(ItemStack stack, String role) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("role", role);
            NBTTagCompound nested = new NBTTagCompound();
            nested.setIntArray("values", new int[] { 17, 36, 63 });
            tag.setTag("nested", nested);
            stack.setTagCompound(tag);
            return stack;
        }

        private static void append(NBTTagList list, ItemStack stack, int slot) {
            NBTTagCompound entry = new NBTTagCompound();
            stack.writeToNBT(entry);
            entry.setByte("Slot", (byte) slot);
            list.appendTag(entry);
        }

        private static void appendExtra(NBTTagList list, ItemStack stack, int slot) {
            NBTTagCompound entry = new NBTTagCompound();
            stack.writeToNBT(entry);
            entry.setInteger("Slot", slot);
            list.appendTag(entry);
        }

        private static NBTTagCompound save(InventoryPlayer inventory) {
            NBTTagCompound result = new NBTTagCompound();
            result.setTag("Inventory", inventory.writeToNBT(new NBTTagList()));
            InventoryCodec.writeExtra(inventory, result);
            return result;
        }
    }
}
