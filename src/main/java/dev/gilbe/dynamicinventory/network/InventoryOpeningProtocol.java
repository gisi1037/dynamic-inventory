package dev.gilbe.dynamicinventory.network;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.IdentityHashMap;
import java.util.Map;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;

import dev.gilbe.dynamicinventory.api.InventoryUi;
import dev.gilbe.dynamicinventory.api.InventoryUiAdapter;
import dev.gilbe.dynamicinventory.api.PlayerInventoryBinding;
import dev.gilbe.dynamicinventory.inventory.InventoryLayout;

/** Runtime identity agreement, independent of GUI coordinates, item contents, or rendering framework. */
public final class InventoryOpeningProtocol {

    // Deliberately NOT registered with Forge's channel registry. Vanilla's ordered play-packet queue dispatches
    // this channel to our NetHandler hooks on the game thread, alongside the inventory packets it protects.
    public static final String CHANNEL = "DI|Open";
    public static final int OFFER = 1, ACCEPT = 2, COMMIT = 3, REJECT = 4, READY = 5;
    public static final int TIMEOUT_TICKS = 100;

    private InventoryOpeningProtocol() {}

    /** Null means this peer cannot construct a valid registered binding. No item contents enter the digest. */
    public static byte[] describe(Container container, InventoryPlayer owner) {
        InventoryUiAdapter adapter = InventoryUi.findAdapter(container);
        if (adapter == null) return null;
        PlayerInventoryBinding binding = InventoryUi.attachRegistered(container, owner);
        if (binding == null || binding.getPlayerInventory() != owner) return null;
        if (binding.getOrdinarySlotCount() != InventoryLayout.mainSlots()
            || container.inventorySlots.size() > Short.MAX_VALUE) return null;
        // A host can modify slots after its constructor hook. A cached binding must still describe LIVE slots.
        for (int logical = 0; logical < binding.getOrdinarySlotCount(); logical++) {
            int id = binding.getContainerSlotId(logical);
            if (id < 0 || id >= container.inventorySlots.size()) return null;
            Slot slot = container.getSlot(id);
            if (slot.slotNumber != id || slot.inventory != owner
                || slot.getSlotIndex() != InventoryLayout.rawIndex(logical)) return null;
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeUTF(adapter.getId());
            out.writeInt(adapter.getCompatibilityRevision());
            out.writeUTF(
                container.getClass()
                    .getName());
            out.writeInt(binding.getNativeSlotCount());
            out.writeInt(container.inventorySlots.size());
            // The server's real chest and client's dummy chest need not have the same Java class. Compare
            // backing-inventory GROUPS by first appearance instead: aliases and raw indices must agree.
            Map<IInventory, Integer> groups = new IdentityHashMap<>();
            groups.put(owner, 0);
            for (int id = 0; id < container.inventorySlots.size(); id++) {
                Slot slot = container.getSlot(id);
                if (slot.slotNumber != id) return null;
                if (!groups.containsKey(slot.inventory)) groups.put(slot.inventory, groups.size());
                out.writeInt(slot.slotNumber);
                out.writeInt(groups.get(slot.inventory));
                out.writeInt(slot.getSlotIndex());
                out.writeUTF(
                    slot.getClass()
                        .getName());
            }
            for (int logical = 0; logical < binding.getOrdinarySlotCount(); logical++)
                out.writeInt(binding.getContainerSlotId(logical));
            return MessageDigest.getInstance("SHA-256")
                .digest(bytes.toByteArray());
        } catch (IOException | NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("Could not describe inventory opening", impossible);
        }
    }

    /** Fixed-size, bounded payload. Nonce belongs to one connection and one opening, not merely a window ID. */
    public static final class Message {

        public final int kind, window;
        public final long nonce;
        public final byte[] digest;

        public Message(int kind, int window, long nonce, byte[] digest) {
            if (kind < OFFER || kind > READY
                || window < 1
                || window > 100
                || nonce <= 0
                || digest == null
                || digest.length != 32) throw new IllegalArgumentException("Invalid opening message");
            this.kind = kind;
            this.window = window;
            this.nonce = nonce;
            this.digest = digest.clone();
        }

        public Message reply(int nextKind) {
            return new Message(nextKind, window, nonce, digest);
        }

        public boolean matches(Message other) {
            return other != null && window == other.window
                && nonce == other.nonce
                && MessageDigest.isEqual(digest, other.digest);
        }

        public byte[] encode() {
            return ByteBuffer.allocate(42)
                .put((byte) kind)
                .put((byte) window)
                .putLong(nonce)
                .put(digest)
                .array();
        }

        public static Message decode(byte[] bytes) {
            if (bytes == null || bytes.length != 42) throw new IllegalArgumentException("Invalid opening payload size");
            ByteBuffer input = ByteBuffer.wrap(bytes);
            int kind = input.get() & 255, window = input.get() & 255;
            long nonce = input.getLong();
            byte[] digest = new byte[32];
            input.get(digest);
            return new Message(kind, window, nonce, digest);
        }
    }
}
