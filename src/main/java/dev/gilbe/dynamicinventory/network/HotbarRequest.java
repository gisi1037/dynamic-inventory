package dev.gilbe.dynamicinventory.network;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;

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
import dev.gilbe.dynamicinventory.inventory.HotbarBanks;
import dev.gilbe.dynamicinventory.inventory.InventoryLayout;
import io.netty.buffer.ByteBuf;

/** Network threads queue bounded requests; inventory swaps happen only on the server's player END tick. */
public final class HotbarRequest implements IMessage {

    private static final Map<EntityPlayerMP, java.util.Deque<HotbarRequest>> PENDING = new WeakHashMap<>();
    private static final Map<EntityPlayerMP, int[]> SENT = new WeakHashMap<>();
    private static final Map<EntityPlayerMP, Long> SENT_REVISION = new WeakHashMap<>();
    private static SimpleNetworkWrapper channel;
    private int operation = -1, value;
    private long revision;
    private long sequence;
    private int[] slots = new int[0];

    public HotbarRequest() {}

    private HotbarRequest(int operation, int value, long revision) {
        this.operation = operation;
        this.value = value;
        this.revision = revision;
    }

    public static void initialize() {
        channel = NetworkRegistry.INSTANCE.newSimpleChannel("dinv-hotbars");
        channel.registerMessage(ServerHandler.class, HotbarRequest.class, 0, Side.SERVER);
        channel.registerMessage(ClientHandler.class, HotbarRequest.class, 1, Side.CLIENT);
        channel.registerMessage(HotbarSwapReply.Handler.class, HotbarSwapReply.class, 2, Side.CLIENT);
        FMLCommonHandler.instance()
            .bus()
            .register(new Events());
    }

    public static void configure(int[] slots, long revision) {
        HotbarRequest request = new HotbarRequest(0, slots.length / 9, revision);
        request.slots = slots.clone();
        channel.sendToServer(request);
    }

    public static void swap(int row, long revision) {
        channel.sendToServer(new HotbarRequest(1, row, revision));
    }

    public static void swap(int row, long revision, long sequence) {
        HotbarRequest request = new HotbarRequest(1, row, revision);
        request.sequence = sequence;
        channel.sendToServer(request);
    }

    public static void capabilities(long revision) {
        channel.sendToServer(new HotbarRequest(3, 0, revision));
    }

    @Override
    public void fromBytes(ByteBuf bytes) {
        if (bytes.readableBytes() < 16) return;
        int candidate = bytes.readInt();
        value = bytes.readInt();
        revision = bytes.readLong();
        if (candidate < 0 || candidate > 3 || value < -1 || value > HotbarBanks.MAX_BANKS) return;
        // A separate capability message preserves the original configuration/state wire format.
        // Older servers never advertise this, so newer clients retain their native swap path there.
        if (candidate == 3) {
            if (value >= 0 && bytes.readableBytes() == 0) operation = candidate;
            return;
        }
        if (candidate == 1) {
            if (bytes.readableBytes() == 8) {
                sequence = bytes.readLong();
                if (sequence <= 0) return;
            }
            if (bytes.readableBytes() == 0) operation = candidate;
            return;
        }
        if (value < 0 || bytes.readableBytes() != value * 9 * 4) return;
        slots = new int[value * 9];
        java.util.Set<Integer> unique = new java.util.HashSet<>();
        for (int i = 0; i < slots.length; i++) {
            int slot = bytes.readInt();
            if (slot < 0 || slot >= dev.gilbe.dynamicinventory.inventory.InventoryShape.MAX_STORAGE
                || !unique.add(slot)) return;
            slots[i] = slot;
        }
        operation = candidate;
    }

    @Override
    public void toBytes(ByteBuf bytes) {
        bytes.writeInt(operation)
            .writeInt(value)
            .writeLong(revision);
        if (operation == 0 || operation == 2) for (int slot : slots) bytes.writeInt(slot);
        if (operation == 1 && sequence > 0) bytes.writeLong(sequence);
    }

    public static final class ServerHandler implements IMessageHandler<HotbarRequest, IMessage> {

        @Override
        public IMessage onMessage(HotbarRequest request, MessageContext context) {
            if (request.operation != 0 && request.operation != 1 && request.operation != 3) return null;
            synchronized (PENDING) {
                java.util.Deque<HotbarRequest> queue = PENDING
                    .computeIfAbsent(context.getServerHandler().playerEntity, ignored -> new java.util.ArrayDeque<>());
                if (queue.size() < 128) queue.addLast(request);
            }
            return null;
        }
    }

    public static final class ClientHandler implements IMessageHandler<HotbarRequest, IMessage> {

        @Override
        public IMessage onMessage(HotbarRequest state, MessageContext context) {
            if (state.operation == 2 && state.value >= 0) DynamicInventory.proxy.receiveHotbarSlots(state.slots);
            if (state.operation == 3) DynamicInventory.proxy.receiveHotbarCapability(state.revision, state.value);
            return null;
        }
    }

    public static final class Events {

        @SubscribeEvent
        public void tick(TickEvent.PlayerTickEvent event) {
            if (event.phase != TickEvent.Phase.END || !(event.player instanceof EntityPlayerMP player)
                || player.playerNetServerHandler == null) return;
            java.util.Deque<HotbarRequest> requests;
            synchronized (PENDING) {
                requests = PENDING.remove(player);
            }
            boolean configured = false;
            HotbarRequest acknowledgement = null;
            if (requests != null) for (HotbarRequest request : requests) {
                if (request.revision == InventoryLayout.of(player.inventory)
                    .revision()) {
                    try {
                        if (request.operation == 0) HotbarBanks.configure(player, request.slots);
                        else if (request.operation == 1) HotbarBanks.swap(player, request.value);
                    } catch (IllegalArgumentException | IllegalStateException refused) {
                        player.addChatMessage(new ChatComponentText("Hotbars: " + refused.getMessage()));
                    }
                }
                configured |= request.operation != 1;
                if (request.sequence > 0) acknowledgement = request;
            }
            // One snapshot acknowledges the entire ordered tick batch, including any refusals.
            if (acknowledgement != null)
                channel.sendTo(HotbarSwapReply.capture(player, acknowledgement.sequence), player);
            int[] slots = HotbarBanks.slots(player);
            long revision = InventoryLayout.of(player.inventory)
                .revision();
            if (configured || !java.util.Arrays.equals(slots, SENT.get(player))
                || !Long.valueOf(revision)
                    .equals(SENT_REVISION.get(player))) {
                SENT.put(player, slots.clone());
                SENT_REVISION.put(player, revision);
                HotbarBanks.receive(player, slots);
                HotbarRequest state = new HotbarRequest(
                    2,
                    slots.length / 9,
                    InventoryLayout.of(player.inventory)
                        .revision());
                state.slots = slots;
                channel.sendTo(state, player);
                channel.sendTo(new HotbarRequest(3, HotbarBanks.next(player), state.revision), player);
            }
        }
    }
}
