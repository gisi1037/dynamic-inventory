package dev.gilbe.dynamicinventory.network;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.IdentityHashMap;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;

import dev.gilbe.dynamicinventory.container.PlayerContainerLayout;
import dev.gilbe.dynamicinventory.inventory.InventoryLayout;
import dev.gilbe.dynamicinventory.inventory.InventoryShape;

/** Ordered play-channel agreement for the persistent player container, including window zero. */
public final class InventoryResizeProtocol {

    public static final String CHANNEL = "DI|Size";
    public static final int APPLY = 1, ACCEPT = 2, READY = 3, REJECT = 4;
    public static final int TIMEOUT_TICKS = 200;

    private InventoryResizeProtocol() {}

    /** No item contents enter this fingerprint. Native ID, owner, index, alias and slot-class identities do. */
    public static byte[] describe(EntityPlayer player) {
        Container container = player.inventoryContainer;
        InventoryShape shape = InventoryLayout.of(player.inventory);
        if (container.getClass() != ContainerPlayer.class || container.windowId != 0
            || player.inventory.mainInventory.length != shape.rawMainSlots()
            || container.inventorySlots.size() != PlayerContainerLayout.totalSlots(player.inventory))
            throw new IllegalStateException("Unsupported persistent player container");
        for (int logical = 0; logical < shape.mainSlots(); logical++) {
            int id = PlayerContainerLayout.containerId(player.inventory, logical);
            Slot slot = container.getSlot(id);
            if (slot.slotNumber != id || slot.inventory != player.inventory
                || slot.getSlotIndex() != shape.rawIndex(logical))
                throw new IllegalStateException("Persistent player slot differs from inventory shape");
        }
        for (int id = 5; id < 9; id++) {
            Slot armor = container.getSlot(id);
            if (armor.inventory != player.inventory || armor.getSlotIndex() != shape.rawMainSlots() + 8 - id)
                throw new IllegalStateException("Armor does not follow the current backing array");
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(shape.storageSlots());
            out.writeBoolean(shape.hasOffhand());
            out.writeLong(shape.revision());
            Map<IInventory, Integer> groups = new IdentityHashMap<>();
            groups.put(player.inventory, 0);
            for (int id = 0; id < container.inventorySlots.size(); id++) {
                Slot slot = container.getSlot(id);
                // Baubles slots start with separate placeholders during EntityPlayer construction.
                // Resolve the reviewed bridge without reading item contents or invoking arbitrary slots.
                if (dev.gilbe.dynamicinventory.compat.PackCompatibility.enabled())
                    dev.gilbe.dynamicinventory.compat.BaublesInventoryBridge.bind(slot);
                if (slot.slotNumber != id) throw new IllegalStateException("Unstable player slot ID");
                if (!groups.containsKey(slot.inventory)) groups.put(slot.inventory, groups.size());
                out.writeInt(id);
                out.writeInt(groups.get(slot.inventory));
                out.writeInt(slot.getSlotIndex());
                out.writeUTF(
                    slot.getClass()
                        .getName());
            }
            return MessageDigest.getInstance("SHA-256")
                .digest(bytes.toByteArray());
        } catch (java.io.IOException | java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("Could not describe player inventory", impossible);
        }
    }

    public static final class Message {

        public final int kind;
        public final InventoryShape shape;
        public final long nonce;
        public final byte[] digest;

        public Message(int kind, InventoryShape shape, long nonce, byte[] digest) {
            if (kind < APPLY || kind > REJECT || shape == null || nonce <= 0 || digest == null || digest.length != 32)
                throw new IllegalArgumentException("Invalid capacity agreement");
            this.kind = kind;
            this.shape = shape;
            this.nonce = nonce;
            this.digest = digest.clone();
        }

        public Message reply(int kind) {
            return new Message(kind, shape, nonce, digest);
        }

        public boolean matches(Message other) {
            return other != null && nonce == other.nonce
                && shape.storageSlots() == other.shape.storageSlots()
                && shape.hasOffhand() == other.shape.hasOffhand()
                && shape.revision() == other.shape.revision()
                && MessageDigest.isEqual(digest, other.digest);
        }

        public byte[] encode() {
            return ByteBuffer.allocate(54)
                .put((byte) kind)
                .putInt(shape.storageSlots())
                .put((byte) (shape.hasOffhand() ? 1 : 0))
                .putLong(shape.revision())
                .putLong(nonce)
                .put(digest)
                .array();
        }

        public static Message decode(byte[] data) {
            if (data == null || data.length != 54) throw new IllegalArgumentException("Invalid capacity payload size");
            ByteBuffer input = ByteBuffer.wrap(data);
            int kind = input.get() & 255, storage = input.getInt(), offhand = input.get() & 255;
            if (offhand > 1) throw new IllegalArgumentException("Invalid equipment mode");
            InventoryShape shape = new InventoryShape(storage, offhand == 1, input.getLong());
            long nonce = input.getLong();
            byte[] digest = new byte[32];
            input.get(digest);
            return new Message(kind, shape, nonce, digest);
        }
    }
}
