package dev.gilbe.dynamicinventory.client;

import java.security.MessageDigest;
import java.util.Arrays;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.client.C17PacketCustomPayload;
import net.minecraft.util.ChatComponentText;

import dev.gilbe.dynamicinventory.compat.BackhandCompatibility;
import dev.gilbe.dynamicinventory.inventory.ExpandedInventoryAccess;
import dev.gilbe.dynamicinventory.inventory.InventoryResizing;
import dev.gilbe.dynamicinventory.network.InventoryResizeProtocol;
import dev.gilbe.dynamicinventory.network.InventoryResizeProtocol.Message;

/** Client prediction is locked through the authoritative resize snapshot, including persistent window zero. */
public final class ClientInventoryResizeGate {

    private net.minecraft.inventory.Container readyContainer;
    private Message offer;
    private long lastNonce;
    private boolean accepted;
    private int deadline;

    public boolean allowsInput(EntityPlayer player) {
        return player != null && offer == null && readyContainer == player.inventoryContainer;
    }

    public boolean filter(EntityPlayer player) {
        return !allowsInput(player) && !accepted;
    }

    public void receive(Minecraft client, byte[] data) {
        EntityPlayer player = client.thePlayer;
        if (player == null) {
            disconnect(client, "Capacity agreement arrived before player construction");
            return;
        }
        try {
            Message message = Message.decode(data);
            if (message.kind == InventoryResizeProtocol.APPLY) {
                if (message.nonce <= lastNonce) return;
                lastNonce = message.nonce;
                offer = message;
                accepted = false;
                readyContainer = null;
                deadline = player.ticksExisted + InventoryResizeProtocol.TIMEOUT_TICKS;
                if (message.shape.hasOffhand() != BackhandCompatibility.enabled())
                    throw new IllegalArgumentException("Equipment mode differs from server");
                // Close old GUI/editor callbacks BEFORE replacing the array or its container.
                client.displayGuiScreen(null);
                player.openContainer = player.inventoryContainer;
                player.inventory.mainInventory = Arrays
                    .copyOf(player.inventory.mainInventory, message.shape.rawMainSlots());
                ((ExpandedInventoryAccess) player.inventory).dynamicinventory$setShape(message.shape);
                InventoryResizing.rebuild(player);
                if (!MessageDigest.isEqual(InventoryResizeProtocol.describe(player), message.digest))
                    throw new IllegalArgumentException("Rebuilt player map differs from server");
                accepted = true; // Contents may now apply, but prediction stays locked until READY.
                client.getNetHandler()
                    .addToSendQueue(
                        new C17PacketCustomPayload(
                            InventoryResizeProtocol.CHANNEL,
                            message.reply(InventoryResizeProtocol.ACCEPT)
                                .encode()));
            } else if (offer != null && message.matches(offer)
                && accepted
                && message.kind == InventoryResizeProtocol.READY) {
                    if (!MessageDigest.isEqual(InventoryResizeProtocol.describe(player), message.digest))
                        throw new IllegalArgumentException("Player map changed during capacity synchronization");
                    readyContainer = player.inventoryContainer;
                    offer = null;
                    accepted = false;
                }
        } catch (RuntimeException invalid) {
            disconnect(client, "Could not rebuild player inventory: " + invalid.getMessage());
        }
    }

    public void tick(Minecraft client) {
        if (offer != null && client.thePlayer != null && client.thePlayer.ticksExisted - deadline >= 0)
            disconnect(client, "Player capacity agreement timed out");
    }

    private void disconnect(Minecraft client, String reason) {
        clear();
        if (client.getNetHandler() != null) client.getNetHandler()
            .getNetworkManager()
            .closeChannel(new ChatComponentText("Dynamic Inventory: " + reason));
    }

    public void clear() {
        readyContainer = null;
        offer = null;
        accepted = false;
        // Preserve lastNonce for this connection, just like the native-window agreement.
    }
}
