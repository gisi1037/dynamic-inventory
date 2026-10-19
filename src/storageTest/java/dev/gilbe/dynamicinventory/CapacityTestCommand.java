package dev.gilbe.dynamicinventory;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import dev.gilbe.dynamicinventory.api.InventoryUi;
import dev.gilbe.dynamicinventory.api.InventoryUiProtocol;
import dev.gilbe.dynamicinventory.container.PlayerContainerLayout;
import dev.gilbe.dynamicinventory.inventory.ExpandedInventoryAccess;
import dev.gilbe.dynamicinventory.inventory.InventoryCapacity;
import dev.gilbe.dynamicinventory.inventory.InventoryLayout;
import dev.gilbe.dynamicinventory.persistence.InventoryCodec;
import dev.gilbe.dynamicinventory.qualification.QualificationCommand;

/** Exercises migration with detached inventories; never changes capacity while players are present. */
public final class CapacityTestCommand extends CommandBase {

    private final JsonArray passed = new JsonArray();

    @Override
    public String getCommandName() {
        return "dinvcapacitytest";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/dinvcapacitytest";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 4;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        EntityPlayerMP owner = getCommandSenderAsPlayer(sender);
        if (!owner.worldObj.getWorldInfo()
            .getWorldName()
            .startsWith("DI-Baseline-")
            || !owner.worldObj.getWorldInfo()
                .getWorldName()
                .contains("Qualification"))
            throw new IllegalArgumentException("Disposable qualification world required");
        JsonObject report = new JsonObject();
        try {
            if (args.length == 1 && args[0].equals("seed")) {
                if (!owner.worldObj.getWorldInfo()
                    .getWorldName()
                    .equals("DI-Baseline-Capacity-Matrix-Qualification") || InventoryLayout.mainSlots() < 81
                    || owner.inventory.getItemStack() != null
                    || owner.inventory.mainInventory[InventoryLayout.rawIndex(80)] != null)
                    throw new IllegalArgumentException("Empty capacity-matrix fixture slot required");
                ItemStack seed = new ItemStack(Items.diamond, 7);
                seed.setTagCompound(new NBTTagCompound());
                seed.getTagCompound()
                    .setString("capacity-matrix", "persistent final slot");
                owner.inventory.mainInventory[InventoryLayout.rawIndex(80)] = seed;
                owner.inventory.markDirty();
                owner.openContainer.detectAndSendChanges();
            }
            test(owner);
            report.addProperty("success", true);
        } catch (Throwable error) {
            report.addProperty("success", false);
            report.addProperty("failure", error.toString());
        }
        report.add("passed", passed);
        report.addProperty("ordinarySlots", InventoryLayout.mainSlots());
        report.addProperty("storageSlots", InventoryLayout.storageSlots());
        report.addProperty("configuredStorageSlots", InventoryCapacity.configuredStorageSlots());
        report.addProperty("playerContainerSlots", PlayerContainerLayout.totalSlots());
        report.addProperty("overflowItems", records(owner).tagCount());
        report.add(
            "inventory",
            dev.gilbe.dynamicinventory.diagnostics.BaselineDiagnostics.snapshot(owner, "server", "capacity"));
        try {
            QualificationCommand.write(owner, "capacity", report);
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    private void test(EntityPlayer owner) {
        check(
            InventoryCapacity.readWireStorage(InventoryCapacity.wireVersion()) == InventoryLayout.storageSlots(),
            "handshake round trip");
        for (String bad : new String[] { Tags.VERSION, "0.7.0-dev;storage=72", Tags.VERSION + ";storage=26",
            Tags.VERSION + ";storage=217", Tags.VERSION + ";storage=bad" })
            check(InventoryCapacity.readWireStorage(bad) == -1, "reject incompatible handshake " + bad);
        String digest = InventoryUi.getCompatibilityDigest();
        int currentCapacity = InventoryLayout.storageSlots();
        for (int capacity : new int[] { 27, 72, 216 }) check(
            InventoryCapacity.readWireStorage(InventoryUiProtocol.wireVersion(capacity, digest)) == capacity
                && InventoryLayout.storageSlots() == currentCapacity,
            "matching protocol parses server capacity without mutating a status probe: " + capacity);
        String differentDigest = (digest.charAt(0) == '0' ? "1" : "0") + digest.substring(1);
        for (String bad : new String[] { InventoryUiProtocol.wireVersion(currentCapacity, differentDigest),
            InventoryCapacity.wireVersion()
                .replace(";protocol=3;", ";protocol=2;"),
            InventoryUiProtocol.wireVersion(26, digest), InventoryUiProtocol.wireVersion(217, digest),
            InventoryCapacity.wireVersion() + ";storage=72", InventoryCapacity.wireVersion() + ";",
            InventoryCapacity.wireVersion()
                .replace(";storage=", ";storage=+") })
            check(InventoryCapacity.readWireStorage(bad) == -1, "reject malformed or mismatched protocol: " + bad);
        int previousArrayLength = owner.inventory.mainInventory.length;
        int previousContainerCount = owner.inventoryContainer.inventorySlots.size();
        boolean liveRefused = false;
        try {
            InventoryCapacity.useServerCapacity(currentCapacity == 27 ? 216 : 27);
        } catch (IllegalStateException expected) {
            liveRefused = true;
        }
        check(
            liveRefused && InventoryLayout.storageSlots() == currentCapacity
                && owner.inventory.mainInventory.length == previousArrayLength
                && owner.inventoryContainer.inventorySlots.size() == previousContainerCount,
            "live capacity mutation is refused without invalidating inventory arrays or container IDs");
        InventoryCapacity.useServerCapacity(currentCapacity);
        check(
            InventoryLayout.storageSlots() == currentCapacity,
            "same-capacity integrated handshake is a harmless no-op during a live session");
        EntityPlayer player = StorageTestCommand.detachedPlayer(owner);
        check(player.inventory.mainInventory.length == InventoryLayout.rawMainSlots(), "startup allocation");
        check(
            player.inventoryContainer.inventorySlots.size() == PlayerContainerLayout.totalSlots(),
            "startup container allocation");
        int last = InventoryLayout.mainSlots() - 1;
        ItemStack diamond = new ItemStack(Items.diamond, 7);
        diamond.setTagCompound(new NBTTagCompound());
        diamond.getTagCompound()
            .setString("capacity-test", "exact NBT");

        // Simulate loading an older 63-slot save into the current process.
        NBTTagCompound old = data(63);
        old.getCompoundTag(InventoryCodec.ROOT_TAG)
            .getTagList("extraItems", 10)
            .appendTag(entry(diamond, 62));
        read(player, old);
        if (last >= 62) check(
            ItemStack.areItemStacksEqual(diamond, player.inventory.mainInventory[InventoryLayout.rawIndex(62)]),
            "old save keeps canonical item index");
        else check(records(player).tagCount() == 1, "smaller capacity preserves old save overflow");

        // A larger save has an occupied slot beyond the configured capacity.
        NBTTagCompound large = data(InventoryLayout.mainSlots() + 5);
        large.getCompoundTag(InventoryCodec.ROOT_TAG)
            .getTagList("extraItems", 10)
            .appendTag(entry(diamond, last + 3));
        read(player, large);
        check(
            records(player).tagCount() == 1 && records(player).getCompoundTagAt(0)
                .getString("origin")
                .equals("capacity"),
            "shrink preserves capacity overflow");
        NBTTagCompound shrunk = new NBTTagCompound();
        InventoryCodec.writeExtra(player.inventory, shrunk);
        read(player, shrunk);
        check(records(player).tagCount() == 1, "overflow survives another save and reload");
        inventoryOnlyRoundTrip(owner, player.inventory, "shrunken capacity preserves raw overflow");

        // Simulate reopening a previously shrunken save after increasing capacity again.
        NBTTagCompound growth = data(36);
        NBTTagCompound recovery = new NBTTagCompound();
        NBTTagList records = new NBTTagList();
        NBTTagCompound record = new NBTTagCompound();
        record.setString("origin", "capacity");
        record.setTag("entry", entry(diamond, last));
        records.appendTag(record);
        recovery.setTag("items", records);
        growth.getCompoundTag(InventoryCodec.ROOT_TAG)
            .setTag("recovery", recovery);
        read(player, growth);
        // Feed that same pre-growth recovery through the public inventory-only API as a separate import.
        // This exercises restoration from the list extension, rather than the player-root codec helper.
        InventoryPlayer imported = new InventoryPlayer(owner);
        NBTTagCompound inline = new NBTTagCompound();
        inline.setByte("Slot", (byte) 255);
        inline.setTag(
            InventoryCodec.ROOT_TAG,
            growth.getTag(InventoryCodec.ROOT_TAG)
                .copy());
        NBTTagList standalone = new NBTTagList();
        standalone.appendTag(inline);
        imported.readFromNBT(standalone);
        if (last >= 36) {
            check(
                ItemStack.areItemStacksEqual(diamond, player.inventory.mainInventory[InventoryLayout.rawIndex(last)]),
                "growth restores preserved final-slot item");
            check(records(player).tagCount() == 0, "restored overflow is removed exactly once");
            check(
                ItemStack.areItemStacksEqual(diamond, imported.mainInventory[InventoryLayout.rawIndex(last)])
                    && ((ExpandedInventoryAccess) imported).dynamicinventory$getRecovery()
                        .hasNoTags(),
                "inventory-only import restores recovered capacity item exactly once");
            inventoryOnlyRoundTrip(
                owner,
                imported,
                "regrown capacity keeps restored item without recovery duplication");
            NBTTagCompound saved = new NBTTagCompound();
            InventoryCodec.writeExtra(player.inventory, saved);
            read(player, saved);
            check(
                ItemStack.areItemStacksEqual(diamond, player.inventory.mainInventory[InventoryLayout.rawIndex(last)])
                    && records(player).tagCount() == 0,
                "growth reload does not duplicate items");
            growth.getCompoundTag(InventoryCodec.ROOT_TAG)
                .getTagList("extraItems", 10)
                .appendTag(entry(new ItemStack(Items.emerald, 5), last));
            growth.getCompoundTag(InventoryCodec.ROOT_TAG)
                .setInteger("mainSlots", InventoryLayout.mainSlots());
            read(player, growth);
            check(
                player.inventory.mainInventory[InventoryLayout.rawIndex(last)].getItem() == Items.emerald
                    && records(player).tagCount() == 1,
                "growth never overwrites an occupied slot");
        } else {
            check(records(player).tagCount() == 1, "vanilla-sized capacity retains unavailable extra-slot recovery");
            check(
                ((ExpandedInventoryAccess) imported).dynamicinventory$getRecovery()
                    .getTagList("items", 10)
                    .tagCount() == 1,
                "inventory-only import retains unavailable extra-slot recovery at vanilla capacity");
            inventoryOnlyRoundTrip(
                owner,
                imported,
                "vanilla capacity keeps unavailable recovery in inventory-only saves");
        }
    }

    private void inventoryOnlyRoundTrip(EntityPlayer owner, InventoryPlayer source, String scenario) {
        NBTTagList saved = source.writeToNBT(new NBTTagList());
        InventoryPlayer loaded = new InventoryPlayer(owner);
        loaded.readFromNBT(saved);
        boolean same = ((ExpandedInventoryAccess) source).dynamicinventory$getRecovery()
            .equals(((ExpandedInventoryAccess) loaded).dynamicinventory$getRecovery());
        for (int i = 0; i < source.mainInventory.length; i++)
            same &= ItemStack.areItemStacksEqual(source.mainInventory[i], loaded.mainInventory[i]);
        for (int i = 0; i < source.armorInventory.length; i++)
            same &= ItemStack.areItemStacksEqual(source.armorInventory[i], loaded.armorInventory[i]);
        check(same, "inventory-only round trip: " + scenario);
        for (int i = 0; i < 3; i++) loaded.readFromNBT(loaded.writeToNBT(new NBTTagList()));
        check(saved.equals(loaded.writeToNBT(new NBTTagList())), "stable repeated inventory-only saves: " + scenario);
    }

    private static NBTTagCompound data(int main) {
        NBTTagCompound root = new NBTTagCompound(), extra = new NBTTagCompound();
        extra.setInteger("schemaVersion", InventoryCodec.SCHEMA_VERSION);
        extra.setInteger("mainSlots", main);
        extra.setTag("extraItems", new NBTTagList());
        root.setTag(InventoryCodec.ROOT_TAG, extra);
        return root;
    }

    private static NBTTagCompound entry(ItemStack stack, int index) {
        NBTTagCompound value = new NBTTagCompound();
        stack.writeToNBT(value);
        value.setInteger("Slot", index);
        return value;
    }

    private static void read(EntityPlayer player, NBTTagCompound root) {
        InventoryCodec.readLegacy(player.inventory, new NBTTagList());
        InventoryCodec.readExtra(player.inventory, root);
    }

    private static NBTTagList records(EntityPlayer player) {
        return ((ExpandedInventoryAccess) player.inventory).dynamicinventory$getRecovery()
            .getTagList("items", 10);
    }

    private void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        passed.add(new JsonPrimitive(message));
    }
}
