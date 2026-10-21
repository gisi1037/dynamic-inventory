package dev.gilbe.dynamicinventory;

import java.util.Arrays;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.ContainerFurnace;
import net.minecraft.inventory.ContainerWorkbench;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.util.ChatComponentText;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import dev.gilbe.dynamicinventory.container.ContainerInventoryRegion;
import dev.gilbe.dynamicinventory.inventory.InventoryLayout;
import dev.gilbe.dynamicinventory.qualification.QualificationCommand;

/** Real container click tests on detached players; user inventory is never seeded by selftest. */
public final class ContainerAdapterTestCommand extends CommandBase {

    private JsonArray passed;

    @Override
    public String getCommandName() {
        return "dinvadapters";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/dinvadapters selftest|chest|double-chest|workbench|furnace|state";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 4;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        EntityPlayerMP player = (EntityPlayerMP) getCommandSenderAsPlayer(sender);
        String world = player.worldObj.getWorldInfo()
            .getWorldName();
        if (!world.startsWith("DI-Baseline-") || !world.contains("Qualification") || args.length != 1)
            throw new IllegalArgumentException("Requires disposable qualification world");
        passed = new JsonArray();
        JsonObject report = new JsonObject();
        try {
            if (args[0].equals("selftest")) selftest(player);
            else if (args[0].equals("state")) {
                Container container = player.openContainer;
                report.addProperty(
                    "container",
                    container.getClass()
                        .getName());
                report.addProperty("slots", container.inventorySlots.size());
                ContainerInventoryRegion region = ContainerInventoryRegion.of(container);
                if (region != null) {
                    JsonArray ids = new JsonArray();
                    for (int i = 0; i < InventoryLayout.mainSlots(); i++) ids.add(new JsonPrimitive(region.id(i)));
                    report.add("ordinaryIds", ids);
                }
            } else if (args[0].equals("stress-seed") || args[0].equals("output-seed")) {
                check(player.inventory.getItemStack() == null, "stress fixture cursor empty");
                check(
                    player.openContainer.getClass()
                        .getName()
                        .contains("ModularContainer")
                        || player.openContainer.getClass()
                            .getName()
                            .contains("ModularUIContainer"),
                    "native modular container active");
                fill(player);
                player.inventory.mainInventory[InventoryLayout.rawIndex(InventoryLayout.mainSlots() - 1)] = null;
                if (args[0].equals("stress-seed")) player.inventory.mainInventory[0] = new ItemStack(Items.emerald, 5);
                else {
                    net.minecraft.inventory.Slot output = null;
                    for (net.minecraft.inventory.Slot slot : player.openContainer.inventorySlots)
                        if (!playerSlot(slot, player) && !slot.isItemValid(new ItemStack(Items.iron_ingot))) {
                            check(output == null, "fixture has exactly one machine output");
                            output = slot;
                        }
                    check(output != null, "fixture machine output found");
                    output.putStack(new ItemStack(Items.diamond, 7));
                    check(
                        player.inventory.mainInventory[InventoryLayout.rawIndex(InventoryLayout.mainSlots() - 1)]
                            == null,
                        "output fixture never seeds the target player slot");
                    report.addProperty("outputId", output.slotNumber);
                }
                player.inventory.markDirty();
                player.openContainer.detectAndSendChanges();
            } else if (args[0].equals("stress-check") || args[0].equals("output-check")) {
                boolean output = args[0].equals("output-check");
                boolean rejectedByMachine = !output && player.openContainer.getClass()
                    .getName()
                    .equals("com.cleanroommc.modularui.screen.ModularContainer");
                if (rejectedByMachine) {
                    // MUI2 has one player transfer group: hotbar quick-move targets the machine,
                    // whose input accepts iron only. An emerald must stay put, not bypass its filter
                    // by moving to another slot in the same player group. Output still tests the last slot below.
                    check(
                        item(player, 0, Items.emerald, 5),
                        "MUI2 machine filter rejects emerald without consuming source");
                    check(
                        player.inventory.mainInventory[InventoryLayout.rawIndex(InventoryLayout.mainSlots() - 1)]
                            == null,
                        "MUI2 rejected quick-move does not reroute into player storage");
                } else check(
                    item(
                        player,
                        InventoryLayout.mainSlots() - 1,
                        output ? Items.diamond : Items.emerald,
                        output ? 7 : 5),
                    "native transfer reaches last offscreen expanded slot");
                if (!output && !rejectedByMachine)
                    check(player.inventory.mainInventory[0] == null, "hotbar source consumed exactly once");
                report.add(
                    "inventory",
                    dev.gilbe.dynamicinventory.diagnostics.BaselineDiagnostics.snapshot(player, "server", args[0]));
            } else {
                check(player.inventory.getItemStack() == null, "empty cursor before opening fixture");
                player.closeScreen();
                int x = (int) Math.floor(player.posX) + 2, y = (int) Math.floor(player.posY),
                    z = (int) Math.floor(player.posZ);
                switch (args[0]) {
                    case "chest":
                    case "double-chest":
                        InventoryBasic chest = new InventoryBasic(
                            "Adapter test chest",
                            true,
                            args[0].equals("chest") ? 27 : 54);
                        chest.setInventorySlotContents(0, new ItemStack(Items.diamond, 7));
                        player.displayGUIChest(chest);
                        break;
                    case "workbench":
                        player.worldObj.setBlock(x, y, z, Blocks.crafting_table);
                        player.displayGUIWorkbench(x, y, z);
                        break;
                    case "furnace":
                        player.worldObj.setBlock(x, y, z, Blocks.furnace);
                        TileEntityFurnace furnace = (TileEntityFurnace) player.worldObj.getTileEntity(x, y, z);
                        furnace.setInventorySlotContents(0, new ItemStack(Blocks.iron_ore, 3));
                        furnace.setInventorySlotContents(1, new ItemStack(Items.coal, 2));
                        player.func_146101_a(furnace);
                        break;
                    default:
                        throw new IllegalArgumentException(getCommandUsage(sender));
                }
            }
            report.addProperty("success", true);
        } catch (Throwable failure) {
            report.addProperty("success", false);
            report.addProperty("failure", failure.toString());
            DynamicInventory.LOG.error("Container adapter tests", failure);
        }
        report.add("passed", passed);
        try {
            QualificationCommand.write(player, "adapters-" + args[0], report);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        sender.addChatMessage(new ChatComponentText("Adapters " + args[0] + ": " + report.get("success")));
    }

    /** Modular slots use a dummy Slot.inventory; inspect their handler before classifying them as machine slots. */
    private static boolean playerSlot(net.minecraft.inventory.Slot slot, EntityPlayer player) {
        if (slot.inventory == player.inventory) return true;
        try {
            Object handler = slot.getClass()
                .getMethod("getItemHandler")
                .invoke(slot);
            return handler.getClass()
                .getMethod("getInventoryPlayer")
                .invoke(handler) == player.inventory;
        } catch (ReflectiveOperationException notPlayerWrapper) {
            return false;
        }
    }

    private void selftest(EntityPlayer owner) {
        EntityPlayer player = StorageTestCommand.detachedPlayer(owner);
        for (int size : new int[] { 27, 54 }) {
            InventoryBasic chest = new InventoryBasic("test", true, size);
            ContainerChest container = new ContainerChest(player.inventory, chest);
            ContainerInventoryRegion region = verify(container, player, size + 36);
            fill(player);
            player.inventory.mainInventory[InventoryLayout.rawIndex(InventoryLayout.mainSlots() - 1)] = null;
            chest.setInventorySlotContents(0, new ItemStack(Items.diamond, 7));
            container.slotClick(0, 0, 1, player);
            check(
                chest.getStackInSlot(0) == null && item(player, InventoryLayout.mainSlots() - 1, Items.diamond, 7),
                "chest output reaches final extra " + size);
            container.slotClick(region.id(InventoryLayout.mainSlots() - 1), 0, 1, player);
            check(
                player.inventory.mainInventory[InventoryLayout.rawIndex(InventoryLayout.mainSlots() - 1)] == null
                    && chest.getStackInSlot(0).stackSize == 7,
                "extra shifts back to chest " + size);
            clear(player);
            player.inventory.mainInventory[InventoryLayout.rawIndex(InventoryLayout.mainSlots() - 1)] = new ItemStack(
                Items.emerald,
                11);
            container.slotClick(region.id(InventoryLayout.mainSlots() - 1), 0, 2, player);
            check(
                item(player, 0, Items.emerald, 11)
                    && player.inventory.mainInventory[InventoryLayout.rawIndex(InventoryLayout.mainSlots() - 1)]
                        == null,
                "hotbar key swaps final extra " + size);
            container.slotClick(region.id(0), 0, 0, player);
            container.slotClick(region.id(InventoryLayout.mainSlots() - 1), 0, 0, player);
            check(
                item(player, InventoryLayout.mainSlots() - 1, Items.emerald, 11)
                    && player.inventory.getItemStack() == null,
                "cursor clicks use canonical final extra " + size);
        }
        clear(player);
        ContainerWorkbench workbench = new ContainerWorkbench(player.inventory, player.worldObj, 0, 0, 0);
        ContainerInventoryRegion craft = verify(workbench, player, 46);
        fill(player);
        player.inventory.mainInventory[InventoryLayout.rawIndex(InventoryLayout.mainSlots() - 1)] = null;
        workbench.craftMatrix.setInventorySlotContents(0, new ItemStack(Blocks.planks, 2));
        workbench.craftMatrix.setInventorySlotContents(3, new ItemStack(Blocks.planks, 2));
        workbench.slotClick(0, 0, 1, player);
        check(
            item(player, InventoryLayout.mainSlots() - 1, Items.stick, 8)
                && workbench.craftMatrix.getStackInSlot(0) == null
                && workbench.craftMatrix.getStackInSlot(3) == null,
            "craft output repeats recipe, consumes ingredients, reaches final extra");
        clear(player);
        player.inventory.mainInventory[InventoryLayout.rawIndex(InventoryLayout.mainSlots() - 1)] = new ItemStack(
            Items.diamond,
            7);
        workbench.slotClick(craft.id(InventoryLayout.mainSlots() - 1), 0, 1, player);
        check(
            item(player, 0, Items.diamond, 7) && workbench.craftMatrix.getStackInSlot(0) == null,
            "workbench extra shifts to hotbar without filling grid");
        clear(player);
        TileEntityFurnace furnace = new TileEntityFurnace();
        furnace.setWorldObj(player.worldObj);
        ContainerFurnace container = new ContainerFurnace(player.inventory, furnace);
        ContainerInventoryRegion heat = verify(container, player, 39);
        player.inventory.mainInventory[InventoryLayout.rawIndex(InventoryLayout.mainSlots() - 1)] = new ItemStack(
            Blocks.iron_ore,
            3);
        container.slotClick(heat.id(InventoryLayout.mainSlots() - 1), 0, 1, player);
        check(
            furnace.getStackInSlot(0).stackSize == 3
                && player.inventory.mainInventory[InventoryLayout.rawIndex(InventoryLayout.mainSlots() - 1)] == null,
            "furnace extra smeltable routes to input");
        player.inventory.mainInventory[InventoryLayout.rawIndex(InventoryLayout.mainSlots() - 1)] = new ItemStack(
            Items.coal,
            2);
        container.slotClick(heat.id(InventoryLayout.mainSlots() - 1), 0, 1, player);
        check(furnace.getStackInSlot(1).stackSize == 2, "furnace extra fuel routes to fuel");
        player.inventory.mainInventory[InventoryLayout.rawIndex(InventoryLayout.mainSlots() - 1)] = new ItemStack(
            Items.diamond,
            5);
        container.slotClick(heat.id(InventoryLayout.mainSlots() - 1), 0, 1, player);
        check(
            item(player, 0, Items.diamond, 5) && furnace.getStackInSlot(2) == null,
            "furnace non-recipe extra routes to hotbar, never output");
        fill(player);
        player.inventory.mainInventory[InventoryLayout.rawIndex(InventoryLayout.mainSlots() - 1)] = null;
        furnace.setInventorySlotContents(2, new ItemStack(Items.iron_ingot, 3));
        container.slotClick(2, 0, 1, player);
        check(
            item(player, InventoryLayout.mainSlots() - 1, Items.iron_ingot, 3) && furnace.getStackInSlot(2) == null,
            "native furnace output reaches final extra with pickup callback");
        clear(player);
    }

    private ContainerInventoryRegion verify(Container container, EntityPlayer player, int nativeCount) {
        ContainerInventoryRegion region = ContainerInventoryRegion.of(container);
        check(
            region != null && region.nativeSlotCount == nativeCount
                && container.inventorySlots.size()
                    == nativeCount + InventoryLayout.mainSlots() - InventoryLayout.LEGACY_MAIN_SLOTS,
            "append-only slot count " + container.getClass()
                .getSimpleName());
        for (int i = 0; i < InventoryLayout.mainSlots(); i++) check(
            container.getSlot(region.id(i)).inventory == player.inventory && container.getSlot(region.id(i))
                .getSlotIndex() == InventoryLayout.rawIndex(i),
            "canonical ordinary " + i);
        check(
            region.destinations(region.id(9), region.id(9) + 1, false) == null,
            "partial native range is not expanded");
        return region;
    }

    private void fill(EntityPlayer player) {
        player.inventory.setItemStack(null);
        for (int i = 0; i < InventoryLayout.mainSlots(); i++)
            player.inventory.mainInventory[InventoryLayout.rawIndex(i)] = new ItemStack(Blocks.stone, 64);
    }

    private void clear(EntityPlayer player) {
        Arrays.fill(player.inventory.mainInventory, null);
        player.inventory.setItemStack(null);
    }

    private boolean item(EntityPlayer player, int logical, net.minecraft.item.Item item, int count) {
        ItemStack stack = player.inventory.mainInventory[InventoryLayout.rawIndex(logical)];
        return stack != null && stack.getItem() == item && stack.stackSize == count;
    }

    private void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        passed.add(new JsonPrimitive(message));
    }
}
