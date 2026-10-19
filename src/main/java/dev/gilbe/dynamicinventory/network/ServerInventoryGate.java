package dev.gilbe.dynamicinventory.network;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.S2EPacketCloseWindow;
import net.minecraft.network.play.server.S3FPacketCustomPayload;
import net.minecraft.util.ChatComponentText;

import dev.gilbe.dynamicinventory.DynamicInventory;
import dev.gilbe.dynamicinventory.api.InventoryUi;
import dev.gilbe.dynamicinventory.network.InventoryOpeningProtocol.Message;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/** Holds ordinary window updates until both runtime slot maps agree. Used only on the server's game thread. */
public final class ServerInventoryGate {

    private static final int EXTRA_PACKETS = 128, MAX_BYTES = 4 * 1024 * 1024;
    private Container container;
    private int window, deadline, bufferedBytes;
    private long nextNonce;
    private Message offer;
    private boolean ready;
    private final List<Packet> buffered = new ArrayList<>();

    public static ServerInventoryGate of(EntityPlayerMP player) {
        return ((ServerGateAccess) player.playerNetServerHandler).dynamicinventory$openingGate();
    }

    /** Called before listener registration; an invalid server binding never sends its first contents packet. */
    public boolean begin(EntityPlayerMP player, Container target) {
        refresh(player);
        if (target != player.openContainer || target.windowId == 0 || InventoryUi.findAdapter(target) == null)
            return true;
        if (container == target && window == target.windowId) return true;
        clear();
        container = target;
        window = target.windowId;
        if (window < 1 || window > 100) {
            reject(player, "Inventory opening requires a standard window ID (1-100)");
            return false;
        }
        byte[] digest = InventoryOpeningProtocol.describe(target, player.inventory);
        if (digest == null) {
            reject(player, "Server refused inventory binding: " + InventoryUi.getRefusalReason(target));
            return false;
        }
        offer = new Message(InventoryOpeningProtocol.OFFER, window, ++nextNonce, digest);
        deadline = player.ticksExisted + InventoryOpeningProtocol.TIMEOUT_TICKS;
        send(player, offer);
        return true;
    }

    /** Returns true to withhold a packet. Unknown containers and persistent window 0 keep their native path. */
    public boolean filter(EntityPlayerMP player, Packet packet) {
        if (packet instanceof S2EPacketCloseWindow) {
            clear();
            return false;
        }
        refresh(player);
        if (!(packet instanceof WindowPacketAccess)) return false;
        int packetWindow = ((WindowPacketAccess) packet).dynamicinventory$windowId();
        if (packetWindow == 0) return false;
        if (packetWindow == -1) {
            if (container == null || ready) return false;
        } else {
            Container active = player.openContainer;
            if (active.windowId != packetWindow || InventoryUi.findAdapter(active) == null) return false;
            // Also protects a custom addCraftingToCrafters override which sends standard packets without super.
            if (!begin(player, active)) return true;
            if (((WindowPacketAccess) packet).dynamicinventory$contents() != null
                && ((WindowPacketAccess) packet).dynamicinventory$contents().length != active.inventorySlots.size()) {
                reject(player, "Inventory contents were built before the expanded binding was ready");
                return true;
            }
            if (ready) return false;
        }
        // Bound both count and serialized payload. A missing acknowledgement must not retain unlimited NBT.
        ByteBuf bytes = Unpooled.buffer(256, MAX_BYTES);
        try {
            packet.writePacketData(new PacketBuffer(bytes));
            // Vanilla sends a full snapshot and then one initial delta per occupied slot. Reserve space
            // for that legitimate burst even for full expanded inventories and large attached chests.
            // Slot count is bounded by the signed-short wire limit; the byte cap remains absolute.
            if (buffered.size() >= EXTRA_PACKETS + container.inventorySlots.size()
                || (long) bufferedBytes + bytes.readableBytes() > MAX_BYTES) {
                reject(player, "Inventory opening exceeded its pending update budget");
            } else {
                buffered.add(packet);
                bufferedBytes += bytes.readableBytes();
            }
        } catch (IOException | IndexOutOfBoundsException invalid) {
            reject(player, "Inventory opening update could not be buffered");
        } finally {
            bytes.release();
        }
        return true;
    }

    public boolean allowsInput(EntityPlayerMP player, int packetWindow) {
        refresh(player);
        Container active = player.openContainer;
        if (packetWindow != active.windowId) return false;
        return active.windowId == 0 || InventoryUi.findAdapter(active) == null
            || (container == active && ready && window == packetWindow);
    }

    public void receive(EntityPlayerMP player, byte[] payload) {
        refresh(player);
        try {
            Message reply = Message.decode(payload);
            if (container == null || !reply.matches(offer)) return; // Old window, nonce, or digest: never approve it.
            if (reply.kind == InventoryOpeningProtocol.REJECT) {
                reject(player, "Client refused inventory opening");
            } else if (reply.kind == InventoryOpeningProtocol.ACCEPT && !ready) {
                byte[] current = InventoryOpeningProtocol.describe(container, player.inventory);
                if (current == null || !MessageDigest.isEqual(current, offer.digest)) {
                    reject(player, "Server inventory mapping changed while opening");
                    return;
                }
                ready = true;
                // Commit travels BEFORE all withheld contents, through the same ordered play connection.
                send(player, offer.reply(InventoryOpeningProtocol.COMMIT));
                List<Packet> release = new ArrayList<>(buffered);
                buffered.clear();
                bufferedBytes = 0;
                for (Packet packet : release) player.playerNetServerHandler.sendPacket(packet);
                // Native integrations may defer their custom snapshots while pending (Tinkers uses
                // these for counts above 127). Send them AFTER the byte-sized vanilla snapshot and
                // BEFORE READY, without retaining unbounded custom packets or advancing their caches.
                Container syncing = container;
                Message agreement = offer;
                syncing.detectAndSendChanges();
                // Only unlock client prediction after ALL initial updates, including the cursor, have arrived.
                // Host sync callbacks can close or replace a window; never mark its replacement ready.
                refresh(player);
                if (container == syncing && ready && offer == agreement)
                    send(player, agreement.reply(InventoryOpeningProtocol.READY));
            }
        } catch (IllegalArgumentException invalid) {
            reject(player, "Malformed inventory opening reply");
        }
    }

    public void tick(EntityPlayerMP player) {
        refresh(player);
        if (container != null && !ready && player.ticksExisted - deadline >= 0)
            reject(player, "Inventory opening agreement timed out");
    }

    private void refresh(EntityPlayerMP player) {
        if (container != null && (container != player.openContainer || window != container.windowId)) clear();
    }

    private void reject(EntityPlayerMP player, String reason) {
        boolean current = container != null && container == player.openContainer && window == container.windowId;
        clear(); // Closing invokes host callbacks; those cleanup packets must not enter the rejected queue.
        if (!current) return;
        DynamicInventory.LOG.warn("Rejected inventory window for {}: {}", player.getCommandSenderName(), reason);
        player.addChatMessage(new ChatComponentText("Dynamic Inventory: " + reason));
        player.closeScreen();
    }

    private static void send(EntityPlayerMP player, Message message) {
        player.playerNetServerHandler
            .sendPacket(new S3FPacketCustomPayload(InventoryOpeningProtocol.CHANNEL, message.encode()));
    }

    public void clear() {
        container = null;
        offer = null;
        ready = false;
        buffered.clear();
        bufferedBytes = 0;
    }
}
