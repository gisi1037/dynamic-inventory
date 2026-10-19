package dev.gilbe.dynamicinventory.client;

import java.security.MessageDigest;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.network.play.client.C17PacketCustomPayload;

import dev.gilbe.dynamicinventory.DynamicInventory;
import dev.gilbe.dynamicinventory.api.InventoryUi;
import dev.gilbe.dynamicinventory.network.InventoryOpeningProtocol;
import dev.gilbe.dynamicinventory.network.InventoryOpeningProtocol.Message;

/** Client half of one play connection. Never applies items or predicts clicks before server commit. */
public final class ClientInventoryGate {

    private Container container;
    private int window, deadline;
    private long lastNonce;
    private Message offer;
    private boolean accepted, committed, ready;

    public boolean allowsInput(EntityPlayer player, int packetWindow) {
        if (player == null || packetWindow != player.openContainer.windowId) return false;
        refresh(player);
        Container active = player.openContainer;
        if (active.windowId == 0) return active == player.inventoryContainer || InventoryUi.findAdapter(active) == null;
        if (container == active && offer != null) return ready;
        return InventoryUi.findAdapter(active) == null;
    }

    public void receive(Minecraft client, byte[] payload) {
        EntityPlayer player = client.thePlayer;
        if (player == null) return;
        refresh(player);
        try {
            Message message = Message.decode(payload);
            if (message.kind == InventoryOpeningProtocol.OFFER) {
                if (message.nonce <= lastNonce) return; // Replayed offer cannot reset a newer approved opening.
                lastNonce = message.nonce;
                if (player.openContainer.windowId != message.window) {
                    send(client, message.reply(InventoryOpeningProtocol.REJECT));
                    return; // Never close a replacement screen in response to an old window's offer.
                }
                container = player.openContainer;
                window = message.window;
                offer = message;
                accepted = false;
                committed = false;
                ready = false;
                deadline = player.ticksExisted + InventoryOpeningProtocol.TIMEOUT_TICKS;
                byte[] local = InventoryOpeningProtocol.describe(container, player.inventory);
                if (local == null || !MessageDigest.isEqual(local, message.digest)) {
                    refuse(client, "Client inventory binding or runtime slot map differs from server");
                } else {
                    accepted = true;
                    send(client, message.reply(InventoryOpeningProtocol.ACCEPT));
                }
                return;
            }
            if (container != player.openContainer || !message.matches(offer)) return;
            if (message.kind == InventoryOpeningProtocol.COMMIT && accepted) {
                byte[] local = InventoryOpeningProtocol.describe(container, player.inventory);
                if (local == null || !MessageDigest.isEqual(local, offer.digest))
                    refuse(client, "Client inventory mapping changed while opening");
                else committed = true;
            }
            if (message.kind == InventoryOpeningProtocol.READY && committed) {
                // COMMIT allows the snapshot to apply; READY follows its final cursor/delta packet. Until then
                // prediction stays locked, so an initial empty-cursor update cannot undo a new user click.
                byte[] local = InventoryOpeningProtocol.describe(container, player.inventory);
                if (local == null || !MessageDigest.isEqual(local, offer.digest))
                    refuse(client, "Client inventory mapping changed during initial synchronization");
                else ready = true;
            }
        } catch (IllegalArgumentException invalid) {
            close(client, "Malformed inventory opening message");
        }
    }

    /** Defensive receive guard, including a peer that unexpectedly sends contents without any offer. */
    public boolean filter(Minecraft client, int packetWindow) {
        EntityPlayer player = client.thePlayer;
        if (player == null || packetWindow == 0) return false;
        if (packetWindow != -1 && packetWindow != player.openContainer.windowId) return false;
        refresh(player);
        if (container == player.openContainer && committed) return false;
        if (allowsInput(player, player.openContainer.windowId)) return false;
        if (offer == null) close(client, "Inventory contents arrived before opening agreement");
        else refuse(client, "Inventory contents arrived before opening commit");
        return true;
    }

    public void tick(Minecraft client) {
        EntityPlayer player = client.thePlayer;
        if (player == null) {
            clear();
            return;
        }
        refresh(player);
        Container active = player.openContainer;
        // A known client screen starts locked, even before the offer arrives. Construction/init may still use
        // window 0; wait for the open handler to assign its real ID before beginning this deadline.
        if (container == null && active.windowId != 0 && InventoryUi.findAdapter(active) != null) {
            container = active;
            window = active.windowId;
            deadline = player.ticksExisted + InventoryOpeningProtocol.TIMEOUT_TICKS;
        }
        if (container != null && !ready && player.ticksExisted - deadline >= 0)
            close(client, "Inventory opening agreement timed out");
    }

    public void clear() {
        container = null;
        offer = null;
        accepted = false;
        committed = false;
        ready = false;
        // Keep lastNonce for this connection. Window IDs are reused, but an old offer must stay obsolete.
    }

    private void refresh(EntityPlayer player) {
        if (container != null && (container != player.openContainer || window != container.windowId)) clear();
    }

    private void refuse(Minecraft client, String reason) {
        accepted = false;
        committed = false;
        ready = false;
        DynamicInventory.LOG.warn("Rejected client inventory window: {}", reason);
        send(client, offer.reply(InventoryOpeningProtocol.REJECT));
        // The server closes the same opening after receiving REJECT. Until then both contents and input stay locked.
    }

    private void close(Minecraft client, String reason) {
        EntityPlayer player = client.thePlayer;
        if (player == null) return;
        refresh(player);
        boolean current = container != null && container == player.openContainer && window == container.windowId;
        // An unsolicited contents packet can precede the first tick/offer; the registered active screen is locked too.
        current |= container == null && player.openContainer.windowId != 0
            && InventoryUi.findAdapter(player.openContainer) != null;
        clear();
        if (current) {
            DynamicInventory.LOG.warn("Closed client inventory window: {}", reason);
            player.closeScreen(); // Sends the current window ID, never a stale offer's ID.
        }
    }

    private static void send(Minecraft client, Message message) {
        client.getNetHandler()
            .addToSendQueue(new C17PacketCustomPayload(InventoryOpeningProtocol.CHANNEL, message.encode()));
    }
}
