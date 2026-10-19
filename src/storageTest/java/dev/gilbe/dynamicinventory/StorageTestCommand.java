package dev.gilbe.dynamicinventory;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.IChatComponent;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.authlib.GameProfile;

import cpw.mods.fml.relauncher.Side;
import dev.gilbe.dynamicinventory.compat.BackhandCompatibility;
import dev.gilbe.dynamicinventory.diagnostics.BaselineDiagnostics;
import dev.gilbe.dynamicinventory.inventory.ExpandedInventoryAccess;
import dev.gilbe.dynamicinventory.inventory.InventoryCapacity;
import dev.gilbe.dynamicinventory.inventory.InventoryLayout;
import dev.gilbe.dynamicinventory.persistence.InventoryCodec;

/** Explicitly enabled runtime tests, executed on the logical server through its command queue. */
public final class StorageTestCommand extends CommandBase {

    private JsonArray passed = new JsonArray();

    @Override
    public String getCommandName() {
        return "dinvstoragetest";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/dinvstoragetest selftest|roundtrip|seed|verify";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 4;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        passed = new JsonArray();
        EntityPlayer player = getCommandSenderAsPlayer(sender);
        String world = player.worldObj.getWorldInfo()
            .getWorldName();
        if (!world.startsWith("DI-Baseline-") || !world.contains("Step2") || args.length != 1) {
            throw new IllegalArgumentException("Requires a disposable DI-Baseline-...Step2 world and one action");
        }
        JsonObject report = new JsonObject();
        try {
            switch (args[0]) {
                case "selftest":
                    selftest(player);
                    break;
                case "roundtrip":
                    // This focused suite also supports Backhand and every startup capacity.
                    inventoryOnlyRoundTrips(player);
                    break;
                case "seed":
                    seed(player);
                    verify(player);
                    break;
                case "verify":
                    verify(player);
                    break;
                default:
                    throw new IllegalArgumentException(getCommandUsage(sender));
            }
            report.addProperty("success", true);
            sender.addChatMessage(new ChatComponentText("PASS inventory storage tests: " + args[0]));
        } catch (Throwable failure) {
            report.addProperty("success", false);
            report.addProperty("failure", failure.toString());
            DynamicInventory.LOG.error("Inventory storage tests failed", failure);
            sender.addChatMessage(new ChatComponentText("FAIL inventory storage tests: " + failure));
        }
        report.add("passed", passed);
        report.add("serverInventory", BaselineDiagnostics.snapshot(player, "server", "storage-test-" + args[0]));
        try {
            File dir = new File("diagnostics/dynamicinventory");
            Files.createDirectories(dir.toPath());
            Files.write(
                new File(dir, "storage-tests-" + args[0] + ".json").toPath(),
                new GsonBuilder().setPrettyPrinting()
                    .create()
                    .toJson(report)
                    .getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void selftest(EntityPlayer owner) {
        inventoryOnlyRoundTrips(owner);
        InventoryPlayer inventory = new InventoryPlayer(owner);
        check(
            inventory.mainInventory.length == InventoryLayout.rawMainSlots()
                && inventory.getSizeInventory() == InventoryLayout.rawMainSlots() + 4,
            "construction and API size");
        for (int i = 0; i < 4; i++) {
            inventory.setInventorySlotContents(InventoryLayout.rawMainSlots() + i, new ItemStack(Items.iron_helmet));
            check(
                inventory.getStackInSlot(InventoryLayout.rawMainSlots() + i) == inventory.armorInventory[i],
                "armor API offset " + i);
            check(
                inventory.decrStackSize(InventoryLayout.rawMainSlots() + i, 1).stackSize == 1
                    && inventory.armorInventory[i] == null,
                "armor decrement " + i);
            inventory.setInventorySlotContents(InventoryLayout.rawMainSlots() + i, new ItemStack(Items.iron_boots));
            check(
                inventory.getStackInSlotOnClosing(InventoryLayout.rawMainSlots() + i) != null
                    && inventory.armorInventory[i] == null,
                "armor closing " + i);
        }

        inventory.mainInventory[0] = named(new ItemStack(Items.iron_pickaxe, 1, 7), "original");
        inventory.mainInventory[35] = new ItemStack(Items.diamond, 3);
        inventory.mainInventory[36] = named(new ItemStack(Blocks.dirt, 61), "extra-first");
        inventory.mainInventory[InventoryLayout.mainSlots() - 1] = named(new ItemStack(Items.emerald, 5), "extra-last");
        inventory.armorInventory[3] = new ItemStack(Items.iron_helmet, 1, 4);
        NBTTagCompound saved = save(inventory);
        NBTTagList legacy = saved.getTagList("Inventory", 10);
        check(legacy.tagCount() == 3, "extra items excluded from legacy list");
        check(
            saved.getCompoundTag("dynamicinventory")
                .getTagList("extraItems", 10)
                .getCompoundTagAt(0)
                .hasKey("Slot", 3),
            "extra index uses integer NBT");
        InventoryPlayer loaded = new InventoryPlayer(owner);
        load(loaded, saved);
        check(equal(inventory, loaded), "all contents, damage and nested NBT round trip");
        check(saved.equals(save(loaded)), "stable serialization round trip");

        NBTTagCompound vanilla = new NBTTagCompound();
        vanilla.setTag("Inventory", legacy.copy());
        load(loaded, vanilla);
        check(
            loaded.mainInventory.length == InventoryLayout.rawMainSlots() && loaded.mainInventory[36] == null
                && loaded.mainInventory[InventoryLayout.mainSlots() - 1] == null,
            "vanilla migration allocates empty extras");
        check(
            ItemStack.areItemStacksEqual(loaded.mainInventory[0], inventory.mainInventory[0])
                && ItemStack.areItemStacksEqual(loaded.armorInventory[3], inventory.armorInventory[3]),
            "vanilla items and armor migrate unchanged");

        InventoryPlayer copy = new InventoryPlayer(owner);
        copy.copyInventory(inventory);
        check(equal(inventory, copy), "copy includes extras and armor");
        copy.mainInventory[InventoryLayout.mainSlots() - 1].getTagCompound()
            .setString("fixture", "changed");
        check(
            !copy.mainInventory[InventoryLayout.mainSlots() - 1].getTagCompound()
                .equals(inventory.mainInventory[InventoryLayout.mainSlots() - 1].getTagCompound()),
            "copy does not alias item NBT");
        loaded.mainInventory = Arrays.copyOf(loaded.mainInventory, 36);
        copy.copyInventory(loaded);
        check(
            copy.mainInventory.length == InventoryLayout.rawMainSlots() && copy.mainInventory[0] != null
                && copy.mainInventory[InventoryLayout.mainSlots() - 1] == null,
            "copy from legacy capacity clears old extra contents");
        inventory.mainInventory = Arrays.copyOf(inventory.mainInventory, InventoryLayout.mainSlots() + 17);
        inventory.mainInventory[InventoryLayout.mainSlots() + 7] = new ItemStack(Items.diamond, 2);
        copy.copyInventory(inventory);
        check(
            recovery(copy).getTagList("items", 10)
                .tagCount() == 1,
            "copy overflow retained in recovery");

        InventoryPlayer pickup = new InventoryPlayer(owner);
        for (int i = 0; i < 36; i++) pickup.mainInventory[i] = new ItemStack(Blocks.stone, 64);
        ItemStack incoming = new ItemStack(Blocks.dirt, 7);
        pickup.addItemStackToInventory(incoming);
        check(incoming.stackSize == 0 && pickup.mainInventory[36].stackSize == 7, "pickup reaches first extra slot");
        for (int i = 37; i < InventoryLayout.mainSlots() - 1; i++)
            pickup.mainInventory[i] = new ItemStack(Blocks.stone, 64);
        pickup.mainInventory[36].stackSize = 63;
        incoming = new ItemStack(Blocks.dirt, 3);
        pickup.addItemStackToInventory(incoming);
        check(
            incoming.stackSize == 0 && pickup.mainInventory[36].stackSize == 64
                && pickup.mainInventory[InventoryLayout.mainSlots() - 1].stackSize == 2,
            "partial merge reaches last extra slot");
        pickup.mainInventory[InventoryLayout.mainSlots() - 1].stackSize = 64;
        incoming = new ItemStack(Items.diamond, 3);
        check(
            !pickup.addItemStackToInventory(incoming) && incoming.stackSize == 3,
            "full inventory preserves unaccepted items");

        NBTTagCompound invalid = (NBTTagCompound) saved.copy();
        NBTTagList extras = invalid.getCompoundTag("dynamicinventory")
            .getTagList("extraItems", 10);
        extras.appendTag(
            extras.getCompoundTagAt(0)
                .copy());
        for (int index : new int[] { -1, 0, 35, InventoryLayout.mainSlots(), InventoryLayout.mainSlots() + 36,
            InventoryLayout.mainSlots() + 37, 255, 256 }) {
            NBTTagCompound entry = (NBTTagCompound) extras.getCompoundTagAt(0)
                .copy();
            entry.setInteger("Slot", index);
            extras.appendTag(entry);
        }
        NBTTagCompound missing = (NBTTagCompound) extras.getCompoundTagAt(0)
            .copy();
        missing.removeTag("Slot");
        extras.appendTag(missing);
        NBTTagCompound wrongType = (NBTTagCompound) extras.getCompoundTagAt(0)
            .copy();
        wrongType.setByte("Slot", (byte) 40);
        extras.appendTag(wrongType);
        NBTTagCompound unknownItem = (NBTTagCompound) extras.getCompoundTagAt(0)
            .copy();
        unknownItem.setShort("id", (short) 32767);
        unknownItem.setInteger("Slot", 40);
        extras.appendTag(unknownItem);
        load(loaded, invalid);
        check(
            recovery(loaded).getTagList("items", 10)
                .tagCount() == 12,
            "duplicate, missing, mistyped, unknown-item and invalid indices quarantined");
        check(
            loaded.mainInventory[36].stackSize == 61
                && loaded.mainInventory[InventoryLayout.mainSlots() - 1].stackSize == 5
                && loaded.mainInventory[0].getItem() == Items.iron_pickaxe,
            "invalid entries never replace valid slots");
        NBTTagCompound repaired = save(loaded);
        for (int i = 0; i < 5; i++) load(loaded, save(loaded));
        check(repaired.equals(save(loaded)), "recovery stays byte-for-byte structurally stable across repeated loads");

        NBTTagCompound future = (NBTTagCompound) saved.copy();
        future.getCompoundTag("dynamicinventory")
            .setInteger("schemaVersion", 999);
        load(loaded, future);
        check(
            recovery(loaded).getTag("unreadData")
                .equals(future.getTag("dynamicinventory")) && loaded.mainInventory[36] == null,
            "unknown future schema preserved opaquely");
        future.setTag("dynamicinventory", new NBTTagString("invalid-root"));
        load(loaded, future);
        check(
            recovery(loaded).getTag("unreadData")
                .equals(future.getTag("dynamicinventory")),
            "malformed root preserved");

        NBTTagCompound malformedRecovery = (NBTTagCompound) saved.copy();
        NBTTagCompound rawRecovery = new NBTTagCompound();
        rawRecovery.setString("items", "wrong-type");
        malformedRecovery.getCompoundTag("dynamicinventory")
            .setTag("recovery", rawRecovery.copy());
        NBTTagList duplicateList = malformedRecovery.getCompoundTag("dynamicinventory")
            .getTagList("extraItems", 10);
        duplicateList.appendTag(
            duplicateList.getCompoundTagAt(0)
                .copy());
        load(loaded, malformedRecovery);
        check(
            recovery(loaded).getTag("unreadRecovery")
                .equals(rawRecovery)
                && recovery(loaded).getTagList("items", 10)
                    .tagCount() == 1,
            "malformed recovery survives a newly rejected entry");

        NBTTagCompound legacyInvalid = (NBTTagCompound) saved.copy();
        NBTTagList invalidLegacyList = legacyInvalid.getTagList("Inventory", 10);
        invalidLegacyList.appendTag(
            invalidLegacyList.getCompoundTagAt(0)
                .copy());
        NBTTagCompound foreignLegacy = (NBTTagCompound) invalidLegacyList.getCompoundTagAt(0)
            .copy();
        foreignLegacy.setByte("Slot", (byte) 36);
        invalidLegacyList.appendTag(foreignLegacy);
        load(loaded, legacyInvalid);
        check(
            recovery(loaded).getTagList("items", 10)
                .tagCount() == 2 && loaded.mainInventory[36].stackSize == 61,
            "legacy duplicate and foreign byte index quarantined without colliding with extra storage");

        EntityPlayer original = detachedPlayer(owner);
        original.readEntityFromNBT(saved);
        NBTTagCompound entityRoundTrip = new NBTTagCompound();
        original.writeEntityToNBT(entityRoundTrip);
        check(
            entityRoundTrip.getTag("dynamicinventory")
                .equals(saved.getTag("dynamicinventory")),
            "EntityPlayer read/write hooks restore exact extra NBT");
        recovery(original.inventory).setString("fixture", "must-survive-clone");
        EntityPlayer clone = detachedPlayer(owner);
        clone.clonePlayer(original, true);
        check(equal(original.inventory, clone.inventory), "EntityPlayer full clone copies all main and armor slots");
        boolean previousKeep = owner.worldObj.getGameRules()
            .getGameRuleBooleanValue("keepInventory");
        try {
            owner.worldObj.getGameRules()
                .setOrCreateGameRule("keepInventory", "true");
            clone = detachedPlayer(owner);
            clone.clonePlayer(original, false);
            check(equal(original.inventory, clone.inventory), "keepInventory death clone copies extra items");
            owner.worldObj.getGameRules()
                .setOrCreateGameRule("keepInventory", "false");
            clone = detachedPlayer(owner);
            clone.clonePlayer(original, false);
            check(
                clone.inventory.mainInventory[36] == null
                    && clone.inventory.mainInventory[InventoryLayout.mainSlots() - 1] == null
                    && recovery(clone.inventory).equals(recovery(original.inventory)),
                "ordinary death clone starts empty but preserves opaque recovery data");
        } finally {
            owner.worldObj.getGameRules()
                .setOrCreateGameRule("keepInventory", Boolean.toString(previousKeep));
        }

        DynamicInventory mod = new DynamicInventory();
        for (Side side : Side.values()) {
            check(
                mod.acceptsRemote(Collections.singletonMap("dynamicinventory", InventoryCapacity.wireVersion()), side),
                "matching remote build " + side);
            check(
                !mod.acceptsRemote(Collections.emptyMap(), side)
                    && !mod.acceptsRemote(Collections.singletonMap("dynamicinventory", "0.1.0-dev"), side),
                "missing and baseline-only mod rejected " + side);
        }
    }

    /** Exercise the public inventory serializer directly; no codec-only save helper can hide a missing hook. */
    private void inventoryOnlyRoundTrips(EntityPlayer owner) {
        InventoryPlayer source = new InventoryPlayer(owner);
        source.mainInventory[0] = named(new ItemStack(Items.iron_pickaxe, 1, 7), "inventory-hotbar");
        source.mainInventory[35] = named(new ItemStack(Items.diamond, 3), "inventory-legacy-end");
        source.armorInventory[0] = named(new ItemStack(Items.iron_boots, 1, 4), "inventory-armor");
        if (InventoryLayout.isExtra(36)) {
            source.mainInventory[InventoryLayout.rawIndex(36)] = named(
                new ItemStack(Blocks.dirt, 61),
                "inventory-first");
            source.mainInventory[InventoryLayout.rawIndex(InventoryLayout.mainSlots() - 1)] = named(
                new ItemStack(Items.emerald, 5),
                "inventory-last");
        }
        if (BackhandCompatibility.enabled()) source.mainInventory[InventoryLayout.OFFHAND_INDEX] = named(
            new ItemStack(Items.diamond_sword, 1, 17),
            "inventory-offhand");
        // Opaque recovery fields must travel with standalone inventories, even with no added items.
        recovery(source).setTag("unreadData", new NBTTagString("opaque inventory-only fixture"));
        NBTTagCompound record = new NBTTagCompound();
        record.setString("origin", "extra");
        NBTTagCompound rejected = new NBTTagCompound();
        rejected.setInteger("Slot", -1);
        rejected.setString("fixture", "raw rejected payload");
        record.setTag("entry", rejected);
        NBTTagList records = new NBTTagList();
        records.appendTag(record);
        recovery(source).setTag("items", records);

        NBTTagList list = source.writeToNBT(new NBTTagList());
        int legacyCount = InventoryCodec.writeLegacy(source, new NBTTagList())
            .tagCount();
        NBTTagCompound extension = list.getCompoundTagAt(list.tagCount() - 1);
        check(
            list.tagCount() == legacyCount + 1 && extension.hasKey("Slot", 1)
                && (extension.getByte("Slot") & 255) == 255
                && extension.hasKey(InventoryCodec.ROOT_TAG, 10)
                && !extension.hasKey("id"),
            "inventory-only serializer appends one non-item metadata record at unused byte slot 255");
        InventoryPlayer loaded = new InventoryPlayer(owner);
        loaded.readFromNBT(list);
        check(
            equal(source, loaded) && recovery(source).equals(recovery(loaded)),
            "public inventory-only read/write preserves every role, nested item NBT and opaque recovery");
        for (int i = 0; i < 4; i++) loaded.readFromNBT(loaded.writeToNBT(new NBTTagList()));
        check(
            list.equals(loaded.writeToNBT(new NBTTagList())),
            "repeated inventory-only round trips neither duplicate extras nor grow recovery");
        source.mainInventory[0].getTagCompound()
            .setString("write-alias-probe", "changed");
        boolean snapshotIndependent = !list.getCompoundTagAt(0)
            .getCompoundTag("tag")
            .hasKey("write-alias-probe");
        source.mainInventory[0].getTagCompound()
            .removeTag("write-alias-probe");
        if (InventoryLayout.isExtra(36)) {
            source.mainInventory[InventoryLayout.rawIndex(36)].getTagCompound()
                .setString("write-alias-probe", "changed");
            snapshotIndependent &= !extension.getCompoundTag(InventoryCodec.ROOT_TAG)
                .getTagList("extraItems", 10)
                .getCompoundTagAt(0)
                .getCompoundTag("tag")
                .hasKey("write-alias-probe");
            source.mainInventory[InventoryLayout.rawIndex(36)].getTagCompound()
                .removeTag("write-alias-probe");
        }
        check(snapshotIndependent, "serialized legacy and added item NBT is an independent snapshot");
        recovery(loaded).setString("alias-probe", "changed");
        loaded.mainInventory[0].getTagCompound()
            .setString("alias-probe", "changed");
        check(
            !recovery(source).hasKey("alias-probe") && !extension.getCompoundTag(InventoryCodec.ROOT_TAG)
                .getCompoundTag("recovery")
                .hasKey("alias-probe")
                && !source.mainInventory[0].getTagCompound()
                    .hasKey("alias-probe")
                && !list.getCompoundTagAt(0)
                    .getCompoundTag("tag")
                    .hasKey("alias-probe"),
            "inventory-only loading does not alias source items or saved recovery");

        // Existing byte-slot-only lists remain readable and clear all stale extra/recovery state.
        loaded.readFromNBT(InventoryCodec.writeLegacy(source, new NBTTagList()));
        check(
            recovery(loaded).hasNoTags()
                && (!InventoryLayout.isExtra(36) || loaded.mainInventory[InventoryLayout.rawIndex(36)] == null)
                && ItemStack.areItemStacksEqual(source.mainInventory[0], loaded.mainInventory[0])
                && ItemStack.areItemStacksEqual(source.armorInventory[0], loaded.armorInventory[0])
                && (!BackhandCompatibility.enabled() || ItemStack.areItemStacksEqual(
                    source.mainInventory[InventoryLayout.OFFHAND_INDEX],
                    loaded.mainInventory[InventoryLayout.OFFHAND_INDEX])),
            "legacy inventory-only lists migrate with empty extras and recovery while preserving equipment");

        NBTTagList duplicate = (NBTTagList) list.copy();
        duplicate.appendTag(extension.copy());
        loaded.readFromNBT(duplicate);
        check(
            equal(source, loaded) && recovery(loaded).getTagList("items", 10)
                .tagCount() == 2,
            "duplicate inventory metadata is quarantined without duplicating playable items");
        NBTTagList normalized = loaded.writeToNBT(new NBTTagList());
        loaded.readFromNBT(normalized);
        check(
            normalized.equals(loaded.writeToNBT(new NBTTagList())),
            "quarantined duplicate inventory metadata stays stable after normalization");

        NBTTagList foreign = (NBTTagList) list.copy();
        NBTTagCompound foreignItem = new NBTTagCompound();
        new ItemStack(Items.stick, 2).writeToNBT(foreignItem);
        foreignItem.setByte("Slot", (byte) 255);
        foreignItem.setTag(
            InventoryCodec.ROOT_TAG,
            extension.getTag(InventoryCodec.ROOT_TAG)
                .copy());
        foreign.appendTag(foreignItem);
        loaded.readFromNBT(foreign);
        check(
            equal(source, loaded) && recovery(loaded).getTagList("items", 10)
                .tagCount() == 2
                && "legacy".equals(
                    recovery(loaded).getTagList("items", 10)
                        .getCompoundTagAt(1)
                        .getString("origin")),
            "an actual item at byte slot 255 remains rejected legacy data rather than metadata");

        for (boolean future : new boolean[] { false, true }) {
            NBTTagList unsupported = (NBTTagList) list.copy();
            NBTTagCompound inline = unsupported.getCompoundTagAt(unsupported.tagCount() - 1);
            if (future) inline.getCompoundTag(InventoryCodec.ROOT_TAG)
                .setInteger("schemaVersion", 999);
            else inline.setString(InventoryCodec.ROOT_TAG, "malformed inventory-only schema");
            loaded.readFromNBT(unsupported);
            check(
                recovery(loaded).getTag("unreadData")
                    .equals(inline.getTag(InventoryCodec.ROOT_TAG))
                    && (!InventoryLayout.isExtra(36) || loaded.mainInventory[InventoryLayout.rawIndex(36)] == null),
                "unsupported inventory-only metadata preserved opaquely: future=" + future);
            normalized = loaded.writeToNBT(new NBTTagList());
            loaded.readFromNBT(normalized);
            check(
                normalized.equals(loaded.writeToNBT(new NBTTagList())),
                "unsupported inventory-only metadata survives another standalone round trip: future=" + future);
        }

        EntityPlayer player = detachedPlayer(owner);
        player.inventory.copyInventory(source);
        NBTTagCompound entity = new NBTTagCompound();
        player.writeEntityToNBT(entity);
        check(
            entity.getTagList("Inventory", 10)
                .tagCount() == legacyCount && entity.getTag(InventoryCodec.ROOT_TAG)
                    .equals(extension.getTag(InventoryCodec.ROOT_TAG)),
            "full player saves keep the established root schema with no duplicate inline metadata");
        for (int i = 0; i < 4; i++) {
            player.readEntityFromNBT(entity);
            NBTTagCompound next = new NBTTagCompound();
            player.writeEntityToNBT(next);
            check(
                equal(source, player.inventory) && recovery(source).equals(recovery(player.inventory))
                    && entity.getTag(InventoryCodec.ROOT_TAG)
                        .equals(next.getTag(InventoryCodec.ROOT_TAG))
                    && next.getTagList("Inventory", 10)
                        .tagCount() == legacyCount,
                "full player reload preserves inventory and recovery exactly once: " + i);
            entity = next;
        }
        // Importers may put a standalone list into a player compound. Both representations are supported.
        NBTTagCompound imported = (NBTTagCompound) entity.copy();
        imported.setTag("Inventory", list.copy());
        player.readEntityFromNBT(imported);
        check(
            equal(source, player.inventory) && recovery(source).equals(recovery(player.inventory)),
            "matching root and inline player schemas are decoded once without recovery duplication");
        imported.removeTag(InventoryCodec.ROOT_TAG);
        player.readEntityFromNBT(imported);
        check(
            equal(source, player.inventory) && recovery(source).equals(recovery(player.inventory)),
            "player import falls back to standalone inventory metadata when its root schema is absent");
        imported.setTag(
            InventoryCodec.ROOT_TAG,
            entity.getTag(InventoryCodec.ROOT_TAG)
                .copy());
        imported.getTagList("Inventory", 10)
            .getCompoundTagAt(list.tagCount() - 1)
            .getCompoundTag(InventoryCodec.ROOT_TAG)
            .getCompoundTag("recovery")
            .setString("conflict", "preserve me");
        player.readEntityFromNBT(imported);
        check(
            equal(source, player.inventory) && recovery(player.inventory).getTagList("items", 10)
                .tagCount() == 2
                && "inventory".equals(
                    recovery(player.inventory).getTagList("items", 10)
                        .getCompoundTagAt(1)
                        .getString("origin")),
            "a conflicting inline player schema is retained in quarantine while the player root wins");
    }

    static EntityPlayer detachedPlayer(EntityPlayer owner) {
        return new EntityPlayer(owner.worldObj, new GameProfile(UUID.randomUUID(), "DIStorageTest")) {

            @Override
            public void addChatMessage(IChatComponent message) {}

            @Override
            public boolean canCommandSenderUseCommand(int level, String command) {
                return false;
            }

            @Override
            public ChunkCoordinates getPlayerCoordinates() {
                return new ChunkCoordinates(0, 0, 0);
            }
        };
    }

    private void seed(EntityPlayer player) {
        check(player.inventory.getItemStack() == null, "empty cursor before fixture");
        for (int i = 0; i < InventoryLayout.mainSlots(); i++)
            player.inventory.mainInventory[i] = new ItemStack(Blocks.stone, 64);
        Arrays.fill(player.inventory.armorInventory, null);
        player.inventory.mainInventory[0] = named(new ItemStack(Items.iron_pickaxe, 1, 7), "original");
        player.inventory.mainInventory[35] = new ItemStack(Items.diamond, 3);
        player.inventory.mainInventory[36] = named(new ItemStack(Blocks.dirt, 61), "extra-first");
        player.inventory.mainInventory[InventoryLayout.mainSlots() - 1] = named(
            new ItemStack(Items.emerald, 5),
            "extra-last");
        player.inventory.armorInventory[3] = new ItemStack(Items.iron_helmet, 1, 4);
        player.inventory.markDirty();
        player.inventoryContainer.detectAndSendChanges();
    }

    private void verify(EntityPlayer player) {
        InventoryPlayer expected = new InventoryPlayer(player);
        for (int i = 0; i < InventoryLayout.mainSlots(); i++)
            expected.mainInventory[i] = new ItemStack(Blocks.stone, 64);
        expected.mainInventory[0] = named(new ItemStack(Items.iron_pickaxe, 1, 7), "original");
        expected.mainInventory[35] = new ItemStack(Items.diamond, 3);
        expected.mainInventory[36] = named(new ItemStack(Blocks.dirt, 61), "extra-first");
        expected.mainInventory[InventoryLayout.mainSlots() - 1] = named(new ItemStack(Items.emerald, 5), "extra-last");
        expected.armorInventory[3] = new ItemStack(Items.iron_helmet, 1, 4);
        check(
            equal(expected, player.inventory),
            "server fixture all configured main slots and armor match exact expected NBT");
        NBTTagCompound root = new NBTTagCompound();
        player.writeEntityToNBT(root);
        check(
            root.getCompoundTag("dynamicinventory")
                .getTagList("extraItems", 10)
                .tagCount() == 27,
            "EntityPlayer hook writes all 27 extra entries");
        check(
            root.getTagList("Inventory", 10)
                .tagCount() == 37,
            "EntityPlayer legacy list contains only 36 main and one armor entry");
    }

    private static ItemStack named(ItemStack stack, String name) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("fixture", name);
        NBTTagCompound nested = new NBTTagCompound();
        nested.setInteger("value", 17017);
        tag.setTag("nested", nested);
        stack.setTagCompound(tag);
        return stack;
    }

    private static NBTTagCompound save(InventoryPlayer inventory) {
        NBTTagCompound root = new NBTTagCompound();
        root.setTag("Inventory", inventory.writeToNBT(new NBTTagList()));
        InventoryCodec.writeExtra(inventory, root);
        return root;
    }

    private static void load(InventoryPlayer inventory, NBTTagCompound root) {
        inventory.readFromNBT(root.getTagList("Inventory", 10));
        InventoryCodec.readExtra(inventory, root);
    }

    private static NBTTagCompound recovery(InventoryPlayer inventory) {
        return ((ExpandedInventoryAccess) inventory).dynamicinventory$getRecovery();
    }

    private static boolean equal(InventoryPlayer first, InventoryPlayer second) {
        if (first.mainInventory.length != second.mainInventory.length
            || first.armorInventory.length != second.armorInventory.length) return false;
        for (int i = 0; i < first.mainInventory.length; i++)
            if (!ItemStack.areItemStacksEqual(first.mainInventory[i], second.mainInventory[i])) return false;
        for (int i = 0; i < first.armorInventory.length; i++)
            if (!ItemStack.areItemStacksEqual(first.armorInventory[i], second.armorInventory[i])) return false;
        return true;
    }

    private void check(boolean success, String message) {
        if (!success) throw new AssertionError(message);
        passed.add(new JsonPrimitive(message));
    }
}
