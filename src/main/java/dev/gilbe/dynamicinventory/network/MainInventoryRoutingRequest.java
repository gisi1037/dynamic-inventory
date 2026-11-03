package dev.gilbe.dynamicinventory.network;

import net.minecraft.entity.player.EntityPlayerMP;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import dev.gilbe.dynamicinventory.inventory.AutomaticInventoryRouting;
import dev.gilbe.dynamicinventory.inventory.InventoryShape;
import io.netty.buffer.ByteBuf;

/** Client layout preferences restrict destinations; they never grant slots or move items. */
public final class MainInventoryRoutingRequest implements IMessage {

    private static SimpleNetworkWrapper channel;
    private int window;
    private long revision;
    private int[] pickup, transfer;

    public MainInventoryRoutingRequest() {}

    public static void initialize() {
        channel = NetworkRegistry.INSTANCE.newSimpleChannel("dinv-main-route");
        channel.registerMessage(Handler.class, MainInventoryRoutingRequest.class, 0, Side.SERVER);
        FMLCommonHandler.instance()
            .bus()
            .register(new Events());
    }

    public static void send(int window, long revision, int[] pickup, int[] transfer) {
        MainInventoryRoutingRequest request = new MainInventoryRoutingRequest();
        request.window = window;
        request.revision = revision;
        request.pickup = pickup.clone();
        request.transfer = transfer.clone();
        channel.sendToServer(request);
    }

    @Override
    public void fromBytes(ByteBuf bytes) {
        if (bytes.readableBytes() < 20) return;
        window = bytes.readInt();
        revision = bytes.readLong();
        int[] first = read(bytes), second = read(bytes);
        if (revision >= 0 && first != null && second != null && bytes.readableBytes() == 0) {
            pickup = first;
            transfer = second;
        }
    }

    private static int[] read(ByteBuf bytes) {
        if (bytes.readableBytes() < 4) return null;
        int count = bytes.readInt();
        if (count < 0 || count > InventoryShape.MAX_STORAGE || bytes.readableBytes() < count * 4) return null;
        int[] slots = new int[count];
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (int i = 0; i < count; i++) {
            int slot = bytes.readInt();
            if (slot < 0 || slot >= InventoryShape.MAX_STORAGE || !seen.add(slot)) return null;
            slots[i] = slot;
        }
        return slots;
    }

    @Override
    public void toBytes(ByteBuf bytes) {
        bytes.writeInt(window)
            .writeLong(revision)
            .writeInt(pickup.length);
        for (int slot : pickup) bytes.writeInt(slot);
        bytes.writeInt(transfer.length);
        for (int slot : transfer) bytes.writeInt(slot);
    }

    public static final class Handler implements IMessageHandler<MainInventoryRoutingRequest, IMessage> {

        @Override
        public IMessage onMessage(MainInventoryRoutingRequest request, MessageContext context) {
            if (request.pickup == null || request.transfer == null) return null;
            EntityPlayerMP player = context.getServerHandler().playerEntity;
            if (player.openContainer == null || player.openContainer.windowId != request.window) return null;
            // Metadata only: publish before the following native click packet, preserving native transfer callbacks.
            AutomaticInventoryRouting
                .receive(player, request.revision, request.window, request.pickup, request.transfer);
            return null;
        }
    }

    public static final class Events {

        @SubscribeEvent
        public void logout(cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent event) {
            AutomaticInventoryRouting.persist(event.player);
        }

        @SubscribeEvent
        public void tick(TickEvent.PlayerTickEvent event) {
            if (event.phase == TickEvent.Phase.END && event.player instanceof EntityPlayerMP)
                AutomaticInventoryRouting.persist(event.player);
        }
    }
}
