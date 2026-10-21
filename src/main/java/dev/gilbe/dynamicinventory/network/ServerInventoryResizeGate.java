package dev.gilbe.dynamicinventory.network;

import java.security.MessageDigest;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S3FPacketCustomPayload;

import dev.gilbe.dynamicinventory.inventory.InventoryLayout;
import dev.gilbe.dynamicinventory.network.InventoryResizeProtocol.Message;

/** One game-thread transaction per connection. Contents and clicks stay locked until the rebuilt map agrees. */
public final class ServerInventoryResizeGate {

    private Container readyContainer, pendingContainer;
    private long readyRevision = -1, nextNonce;
    private Message offer;
    private int deadline;
    private boolean releasing;
    private boolean closed;
    private boolean needsSynchronization;

    public static ServerInventoryResizeGate of(EntityPlayerMP player) {
        return ((ServerGateAccess) player.playerNetServerHandler).dynamicinventory$resizeGate();
    }

    public boolean allowsInput(EntityPlayerMP player) {
        return !closed && offer == null
            && readyContainer == player.inventoryContainer
            && readyRevision == InventoryLayout.of(player.inventory)
                .revision();
    }

    /** Listener registration is retried after ACK. No old contents queue is reused for a new capacity. */
    public boolean beforeListener(EntityPlayerMP player, Container container) {
        if (container != player.inventoryContainer) {
            if (allowsInput(player)) return true;
            player.closeScreen(); // A host opened during capacity synchronization cannot acquire a listener.
            return false;
        }
        if (releasing || allowsInput(player)) return true;
        synchronize(player);
        return false;
    }

    public void synchronize(EntityPlayerMP player) {
        if (closed || (offer != null && pendingContainer == player.inventoryContainer)) return;
        pendingContainer = player.inventoryContainer;
        needsSynchronization = false;
        offer = new Message(
            InventoryResizeProtocol.APPLY,
            InventoryLayout.of(player.inventory),
            ++nextNonce,
            InventoryResizeProtocol.describe(player));
        deadline = player.ticksExisted + InventoryResizeProtocol.TIMEOUT_TICKS;
        send(player, offer);
    }

    public boolean filter(EntityPlayerMP player, Packet packet) {
        if (packet instanceof net.minecraft.network.play.server.S07PacketRespawn) {
            // Dimension travel can recreate only the CLIENT player while retaining the
            // server container. Invalidate the agreement even when its Java identity is unchanged.
            ((dev.gilbe.dynamicinventory.container.ExpandedContainerAccess) player.inventoryContainer)
                .dynamicinventory$removeListener(player);
            readyContainer = null;
            readyRevision = -1;
            pendingContainer = null;
            offer = null;
            needsSynchronization = true;
            // Let the respawn packet go first. The next snapshot (or tick) offers the new map.
            return false;
        }
        if (packet instanceof net.minecraft.network.play.server.S2EPacketCloseWindow) return false;
        if (!(packet instanceof WindowPacketAccess)) return false;
        if (closed) return true;
        if (releasing || allowsInput(player)) return false;
        // An early native snapshot can precede addCraftingToCrafters. Start the same agreement;
        // after ACK a fresh complete snapshot supersedes all withheld deltas, including the cursor.
        synchronize(player);
        return true;
    }

    public void receive(EntityPlayerMP player, byte[] data) {
        try {
            Message reply = Message.decode(data);
            if (offer == null || !reply.matches(offer)) return;
            if (reply.kind == InventoryResizeProtocol.REJECT) {
                reject(player, "Client refused the resized player inventory");
            } else if (reply.kind == InventoryResizeProtocol.ACCEPT) {
                if (pendingContainer != player.inventoryContainer || player.openContainer != pendingContainer
                    || !MessageDigest.isEqual(InventoryResizeProtocol.describe(player), offer.digest)) {
                    reject(player, "Player inventory changed during capacity agreement");
                    return;
                }
                // Initial native player snapshots include slot subclasses, Baubles and cursor updates.
                // Keep action guards locked while allowing this ordered release through sendPacket.
                releasing = true;
                try {
                    pendingContainer.addCraftingToCrafters(player);
                } finally {
                    releasing = false;
                }
                // Listener callbacks belong to other mods too. A callback that replaces the
                // player container invalidates the agreement we just released contents for.
                if (offer == null || pendingContainer != player.inventoryContainer
                    || player.openContainer != pendingContainer
                    || !MessageDigest.isEqual(InventoryResizeProtocol.describe(player), offer.digest)) {
                    reject(player, "Player inventory changed while releasing capacity agreement");
                    return;
                }
                readyContainer = pendingContainer;
                readyRevision = offer.shape.revision();
                Message ready = offer.reply(InventoryResizeProtocol.READY);
                offer = null;
                pendingContainer = null;
                send(player, ready);
            }
        } catch (RuntimeException invalid) {
            reject(player, "Invalid player capacity agreement");
        }
    }

    public void tick(EntityPlayerMP player) {
        if (needsSynchronization && offer == null) synchronize(player);
        if (offer != null && pendingContainer != player.inventoryContainer) synchronize(player);
        if (offer != null && player.ticksExisted - deadline >= 0) reject(player, "Player capacity agreement timed out");
    }

    private void reject(EntityPlayerMP player, String reason) {
        clear();
        // The server's committed inventory remains authoritative and saves normally on disconnect.
        // Never roll back to a detached array after pickups or other server actions may have occurred.
        player.playerNetServerHandler.kickPlayerFromServer("Dynamic Inventory: " + reason);
    }

    private static void send(EntityPlayerMP player, Message message) {
        player.playerNetServerHandler
            .sendPacket(new S3FPacketCustomPayload(InventoryResizeProtocol.CHANNEL, message.encode()));
    }

    public void clear() {
        closed = true;
        readyContainer = null;
        readyRevision = -1;
        pendingContainer = null;
        offer = null;
        releasing = false;
        needsSynchronization = false;
    }
}
