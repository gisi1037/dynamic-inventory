package dev.gilbe.dynamicinventory.network;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
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

    public InventoryLockSync() {}

    public static void initialize() {
        channel = NetworkRegistry.INSTANCE.newSimpleChannel("dinv-lock-state");
        channel.registerMessage(Handler.class, InventoryLockSync.class, 0, Side.CLIENT);
        FMLCommonHandler.instance()
            .bus()
            .register(new LoginSync());
    }

    public static byte[] request(int window, int slot, long revision) {
        return ByteBuffer.allocate(REQUEST_BYTES)
            .putInt(window)
            .putInt(slot)
            .putLong(revision)
            .array();
    }

    /** Called in the ordered play-packet queue before subsequent clicks or drops. */
    public static void toggle(EntityPlayerMP player, byte[] payload) {
        if (payload == null || payload.length != REQUEST_BYTES || player.isDead) return;
        ByteBuffer data = ByteBuffer.wrap(payload);
        int window = data.getInt();
        int slot = data.getInt();
        long revision = data.getLong();
        Container container = player.openContainer;
        if (container == null || container.windowId != window
            || slot < 0
            || slot >= container.inventorySlots.size()
            || revision != InventoryLayout.of(player.inventory)
                .revision())
            return;
        int index = InventorySlotLocks.index(player.inventory, container.getSlot(slot));
        if (index < 0) return;
        InventorySlotLocks.toggle(player, index);
        send(player);
    }

    private static void send(EntityPlayerMP player) {
        InventoryLockSync state = new InventoryLockSync();
        state.locks = InventorySlotLocks.snapshot(player);
        channel.sendTo(state, player);
    }

    @Override
    public void fromBytes(ByteBuf data) {
        if (data.readableBytes() < Short.BYTES) return;
        int count = data.readUnsignedShort();
        if (count > InventorySlotLocks.LIMIT || data.readableBytes() != count * Short.BYTES) return;
        int[] received = new int[count];
        int previous = -1;
        for (int i = 0; i < count; i++) {
            int index = data.readUnsignedShort();
            if (index <= previous || index >= InventorySlotLocks.LIMIT) return;
            received[i] = previous = index;
        }
        locks = received;
    }

    @Override
    public void toBytes(ByteBuf data) {
        data.writeShort(locks.length);
        for (int lock : locks) data.writeShort(lock);
    }

    public static final class Handler implements IMessageHandler<InventoryLockSync, IMessage> {

        @Override
        public IMessage onMessage(InventoryLockSync state, MessageContext context) {
            if (state.locks != null)
                DynamicInventory.proxy.receiveInventoryLocks(context.getClientHandler(), state.locks);
            return null;
        }
    }

    public static final class LoginSync {

        private final Set<EntityPlayerMP> sent = Collections.newSetFromMap(new WeakHashMap<>());

        @SubscribeEvent
        public void tick(TickEvent.PlayerTickEvent event) {
            if (event.phase == TickEvent.Phase.END && event.player instanceof EntityPlayerMP player
                && player.playerNetServerHandler != null
                && sent.add(player)) send(player);
        }
    }
}
