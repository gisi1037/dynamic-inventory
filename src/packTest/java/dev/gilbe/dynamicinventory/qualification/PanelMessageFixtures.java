package dev.gilbe.dynamicinventory.qualification;

import java.lang.reflect.Field;

import dev.gilbe.dynamicinventory.inventory.HotbarBanks;
import dev.gilbe.dynamicinventory.inventory.InventoryShape;
import dev.gilbe.dynamicinventory.network.HotbarRequest;
import dev.gilbe.dynamicinventory.network.HotbarSwapReply;
import dev.gilbe.dynamicinventory.network.PanelTransferRequest;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/** Bounded wire payloads: malformed input is refused before a queue or inventory is touched. */
public final class PanelMessageFixtures {

    private PanelMessageFixtures() {}

    public static void main(String[] args) throws Exception {
        int checks = 0;
        for (int length = 0; length <= 24; length++) {
            if (length == 16) continue;
            HotbarRequest request = new HotbarRequest();
            request.fromBytes(
                Unpooled.buffer()
                    .writeZero(length));
            check(number(request, "operation") == -1, "invalid hotbar packet length");
            check(new HotbarRequest.ServerHandler().onMessage(request, null) == null, "invalid request queued");
            checks++;
        }
        for (int op = -1; op <= 3; op++)
            for (int value : new int[] { -2, -1, 0, HotbarBanks.MAX_BANKS, Integer.MAX_VALUE }) {
                HotbarRequest request = new HotbarRequest();
                ByteBuf payload = Unpooled.buffer()
                    .writeInt(op)
                    .writeInt(value)
                    .writeLong(17);
                if ((op == 0 || op == 2) && value > 0 && value <= HotbarBanks.MAX_BANKS)
                    for (int i = 0; i < value * 9; i++) payload.writeInt(i);
                request.fromBytes(payload);
                boolean valid = op >= 0 && op <= 3 && value >= (op == 1 ? -1 : 0) && value <= HotbarBanks.MAX_BANKS;
                check(number(request, "operation") == (valid ? op : -1), "hotbar operation/value bounds");
                if (valid) {
                    ByteBuf encoded = Unpooled.buffer();
                    request.toBytes(encoded);
                    check(
                        encoded.readInt() == op && encoded.readInt() == value && encoded.readLong() == 17,
                        "hotbar wire round trip");
                }
                if (op == 2) check(
                    new HotbarRequest.ServerHandler().onMessage(request, null) == null,
                    "server accepts no client-authored count state");
                checks++;
            }
        for (int length = 0; length < 24; length++) {
            PanelTransferRequest request = new PanelTransferRequest();
            request.fromBytes(
                Unpooled.buffer()
                    .writeZero(length));
            check(number(request, "source") == -1, "truncated panel packet");
            check(new PanelTransferRequest.Handler().onMessage(request, null) == null, "truncated request queued");
            checks++;
        }
        for (int invalid : new int[] { -1, InventoryShape.MAX_STORAGE, 0 }) {
            ByteBuf payload = Unpooled.buffer()
                .writeInt(0)
                .writeInt(1)
                .writeLong(17);
            for (int i = 0; i < 9; i++) payload.writeInt(i == 8 ? invalid : i);
            HotbarRequest request = new HotbarRequest();
            request.fromBytes(payload);
            check(number(request, "operation") == -1, "invalid/duplicate reserved identity refused");
            checks++;
        }
        for (int count : new int[] { -1, 0, 1, InventoryShape.MAX_STORAGE + 9, Integer.MAX_VALUE }) {
            for (boolean complete : new boolean[] { false, true }) {
                ByteBuf bytes = Unpooled.buffer()
                    .writeInt(4)
                    .writeLong(17)
                    .writeInt(9)
                    .writeInt(count)
                    .writeInt(Math.max(0, count));
                if (complete && count > 0 && count <= InventoryShape.MAX_STORAGE + 9)
                    for (int i = 0; i < count; i++) bytes.writeInt(i + 10);
                PanelTransferRequest request = new PanelTransferRequest();
                request.fromBytes(bytes);
                boolean valid = complete && count > 0 && count <= InventoryShape.MAX_STORAGE + 9;
                check(number(request, "source") == (valid ? 9 : -1), "panel destination bounds");
                if (valid) {
                    ByteBuf encoded = Unpooled.buffer();
                    request.toBytes(encoded);
                    check(
                        encoded.readInt() == 4 && encoded.readLong() == 17
                            && encoded.readInt() == 9
                            && encoded.readInt() == count
                            && encoded.readInt() == count
                            && encoded.readableBytes() == count * 4,
                        "panel wire round trip");
                } else check(
                    new PanelTransferRequest.Handler().onMessage(request, null) == null,
                    "invalid destinations queued");
                checks++;
            }
        }
        for (long sequence : new long[] { -1, 0, 1, Long.MAX_VALUE }) {
            HotbarRequest request = new HotbarRequest();
            request.fromBytes(
                Unpooled.buffer()
                    .writeInt(1)
                    .writeInt(2)
                    .writeLong(17)
                    .writeLong(sequence));
            check(number(request, "operation") == (sequence > 0 ? 1 : -1), "Predicted swap sequence bounds");
            if (sequence > 0) {
                ByteBuf bytes = Unpooled.buffer();
                request.toBytes(bytes);
                bytes.skipBytes(16);
                check(bytes.readLong() == sequence && bytes.readableBytes() == 0, "Predicted swap wire round trip");
            }
            checks++;
        }
        for (int banks : new int[] { 0, 1, HotbarBanks.MAX_BANKS }) {
            HotbarSwapReply source = new HotbarSwapReply();
            source.sequence = 71;
            source.revision = 17;
            source.next = banks == 0 ? 0 : banks - 1;
            source.slots = java.util.stream.IntStream.range(0, banks * 9)
                .toArray();
            source.stacks = new net.minecraft.item.ItemStack[9 + source.slots.length];
            ByteBuf bytes = Unpooled.buffer();
            source.toBytes(bytes);
            HotbarSwapReply reply = new HotbarSwapReply();
            reply.fromBytes(bytes.copy());
            check(
                reply.sequence == 71 && reply.revision == 17
                    && reply.next == source.next
                    && java.util.Arrays.equals(reply.slots, source.slots)
                    && reply.stacks.length == source.stacks.length,
                "Bounded authoritative snapshot round trip");
            for (int length = 0; length < bytes.readableBytes(); length++) {
                HotbarSwapReply truncated = new HotbarSwapReply();
                truncated.fromBytes(bytes.copy(0, length));
                check(truncated.sequence == -1, "Truncated authoritative snapshot refused");
            }
            checks++;
        }
        System.out.println("PASS " + checks + " bounded hotbar/panel wire cases and spoofed-state refusal");
    }

    private static int number(Object target, String name) throws Exception {
        Field field = target.getClass()
            .getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(target);
    }

    private static void check(boolean condition, String reason) {
        if (!condition) throw new AssertionError(reason);
    }
}
