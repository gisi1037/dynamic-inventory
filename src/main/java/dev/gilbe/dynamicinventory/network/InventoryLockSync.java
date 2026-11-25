package dev.gilbe.dynamicinventory.network;

import java.nio.ByteBuffer;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import dev.gilbe.dynamicinventory.DynamicInventory;
import dev.gilbe.dynamicinventory.inventory.InventoryLayout;
import dev.gilbe.dynamicinventory.inventory.InventorySlotLocks;
import io.netty.buffer.ByteBuf;

public final class InventoryLockSync implements IMessage {

    public static final String REQUEST = "dinv-lock-toggle";
    private static final int REQUEST_BYTES = Integer.BYTES * 2 + Long.BYTES;
    private static SimpleNetworkWrapper channel;
    private int[] locks;
    private int[] types = new int[0];

    public InventoryLockSync() {}

    public static void initialize() {
        channel = NetworkRegistry.INSTANCE.newSimpleChannel("dinv-lock-state");
        channel.registerMessage(Handler.class, InventoryLockSync.class, 0, Side.CLIENT);
    }

    public static boolean refresh(EntityPlayerMP player, byte[] payload) {
        if (payload == null || payload.length != 1 || payload[0] != 0) return false;
        send(player);
        return true;
    }

    public static byte[] request(int window, int slot, long revision) {
        return request(window, slot, revision, false);
    }

    public static byte[] request(int window, int slot, long revision, boolean type) {
        ByteBuffer buffer = ByteBuffer.allocate(REQUEST_BYTES + (type ? 1 : 0));
        buffer.putInt(window)
            .putInt(slot)
            .putLong(revision);
        if (type) buffer.put((byte) 1);
        return buffer.array();
    }

    /** Called in the ordered play-packet queue before subsequent clicks or drops. */
    public static void toggle(EntityPlayerMP player, byte[] payload) {
        if (payload == null || (payload.length != REQUEST_BYTES && payload.length != REQUEST_BYTES + 1)
            || player.isDead) return;
        ByteBuffer data = ByteBuffer.wrap(payload);
        int window = data.getInt();
        int slot = data.getInt();
        long revision = data.getLong();
        boolean type = data.hasRemaining();
        if (type && data.get() != 1) return;
        Container container = player.openContainer;
        if (container == null || container.windowId != window
            || slot < 0
            || slot >= container.inventorySlots.size()
            || revision != InventoryLayout.of(player.inventory)
                .revision())
            return;
        Slot target = container.getSlot(slot);
        int index = InventorySlotLocks.index(player.inventory, target);
        if (index < 0) return;
        if (type) InventorySlotLocks
            .toggleType(player, index, target.getHasStack() ? target.getStack() : player.inventory.getItemStack());
        else InventorySlotLocks.toggle(player, index);
        send(player);
    }

    static void send(EntityPlayerMP player) {
        InventoryLockSync state = new InventoryLockSync();
        state.locks = InventorySlotLocks.snapshot(player);
        state.types = InventorySlotLocks.typeSnapshot(player);
        channel.sendTo(state, player);
    }

    @Override
    public void fromBytes(ByteBuf data) {
        int[] received = readLocks(data);
        if (received == null) return;
        if (data.isReadable()) {
            if (data.readableBytes() < Short.BYTES) return;
            int typeCount = data.readUnsignedShort();
            if (typeCount > InventorySlotLocks.LIMIT || data.readableBytes() != typeCount * 3 * Integer.BYTES) return;
            int[] filters = new int[typeCount * 3];
            int previous = -1;
            for (int i = 0; i < filters.length; i += 3) {
                int index = data.readInt(), item = data.readInt(), meta = data.readInt();
                if (index <= previous || index >= InventorySlotLocks.LIMIT || item <= 0 || item > 32767) return;
                filters[i] = previous = index;
                filters[i + 1] = item;
                filters[i + 2] = meta;
            }
            types = filters;
        }
        locks = received;
    }

    static int[] readLocks(ByteBuf data) {
        if (data.readableBytes() < Short.BYTES) return null;
        int count = data.readUnsignedShort();
        if (count > InventorySlotLocks.LIMIT || data.readableBytes() < count * Short.BYTES) return null;
        int[] received = new int[count];
        int previous = -1;
        for (int i = 0; i < count; i++) {
            int index = data.readUnsignedShort();
            if (index <= previous || index >= InventorySlotLocks.LIMIT) return null;
            received[i] = previous = index;
        }
        return received;
    }

    static void writeLocks(ByteBuf data, int[] locks) {
        data.writeShort(locks.length);
        for (int lock : locks) data.writeShort(lock);
    }

    @Override
    public void toBytes(ByteBuf data) {
        writeLocks(data, locks);
        if (types.length != 0) {
            data.writeShort(types.length / 3);
            for (int component : types) data.writeInt(component);
        }
    }

    public static final class Handler implements IMessageHandler<InventoryLockSync, IMessage> {

        @Override
        public IMessage onMessage(InventoryLockSync state, MessageContext context) {
            if (state.locks != null)
                DynamicInventory.proxy.receiveInventoryLocks(context.getClientHandler(), state.locks, state.types);
            return null;
        }
    }

}
