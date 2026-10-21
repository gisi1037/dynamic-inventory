package dev.gilbe.dynamicinventory.qualification;

import java.nio.ByteBuffer;
import java.util.Arrays;

import dev.gilbe.dynamicinventory.inventory.InventoryShape;
import dev.gilbe.dynamicinventory.network.InventoryResizeProtocol;
import dev.gilbe.dynamicinventory.network.InventoryResizeProtocol.Message;

/** Headless wire validation; this does not replace the two-peer runtime transaction checks. */
public final class InventoryResizeProtocolFixtures {

    public static void main(String[] args) {
        int cases = 0;
        for (boolean offhand : new boolean[] { false, true }) for (int storage = 27; storage <= 216; storage++) {
            byte[] digest = new byte[32];
            digest[0] = 42;
            Message original = new Message(
                InventoryResizeProtocol.APPLY,
                new InventoryShape(storage, offhand, 7),
                12,
                digest);
            digest[0] = 0;
            check(original.digest[0] == 42, "owned digest copy");
            Message decoded = Message.decode(original.encode());
            check(decoded.matches(original) && decoded.kind == InventoryResizeProtocol.APPLY, "wire round trip");
            check(
                original.reply(InventoryResizeProtocol.ACCEPT)
                    .matches(original),
                "kind-independent agreement identity");
            check(!new Message(2, original.shape, 13, original.digest).matches(original), "different nonce rejected");
            check(
                !new Message(2, original.shape.resized(storage), 12, original.digest).matches(original),
                "different revision rejected");
            byte[] changed = original.digest.clone();
            changed[0]++;
            check(!new Message(2, original.shape, 12, changed).matches(original), "different map rejected");
            byte[] packet = original.encode();
            rejects(() -> Message.decode(Arrays.copyOf(packet, 53)));
            rejects(() -> Message.decode(Arrays.copyOf(packet, 55)));
            for (int bad : new int[] { -1, 0, 5, 255 }) {
                byte[] bytes = packet.clone();
                bytes[0] = (byte) bad;
                rejects(() -> Message.decode(bytes));
            }
            byte[] badEquipment = packet.clone();
            badEquipment[5] = 2;
            rejects(() -> Message.decode(badEquipment));
            byte[] badStorage = packet.clone();
            ByteBuffer.wrap(badStorage)
                .putInt(1, 217);
            rejects(() -> Message.decode(badStorage));
            byte[] badRevision = packet.clone();
            ByteBuffer.wrap(badRevision)
                .putLong(6, -1);
            rejects(() -> Message.decode(badRevision));
            byte[] badNonce = packet.clone();
            ByteBuffer.wrap(badNonce)
                .putLong(14, 0);
            rejects(() -> Message.decode(badNonce));
            cases++;
        }
        System.out
            .println("PASS capacity protocol: " + cases + " shape cases, replay/mapping and bounded-payload guards");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void rejects(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Malformed resize payload accepted");
    }
}
