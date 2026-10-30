package dev.gilbe.dynamicinventory.network;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import dev.gilbe.dynamicinventory.api.InventoryUi;
import dev.gilbe.dynamicinventory.container.PlayerInventoryTransfers;
import dev.gilbe.dynamicinventory.inventory.InventoryLayout;
import dev.gilbe.dynamicinventory.inventory.InventoryShape;
import io.netty.buffer.ByteBuf;

/** Client layouts choose destinations; the server only permits moves within the owner's ordinary inventory. */
public final class PanelTransferRequest implements IMessage {

    private static final Map<EntityPlayerMP, Deque<PanelTransferRequest>> PENDING = new WeakHashMap<>();
    private static SimpleNetworkWrapper channel;
    private int window, source = -1;
    private long revision;
    private int[] destinations = new int[0];

    public PanelTransferRequest() {}

    private PanelTransferRequest(int window, long revision, int source, int[] destinations) {
        this.window = window;
        this.revision = revision;
        this.source = source;
        this.destinations = destinations.clone();
    }

    public static void initialize() {
        channel = NetworkRegistry.INSTANCE.newSimpleChannel("dinv-panel-move");
        channel.registerMessage(Handler.class, PanelTransferRequest.class, 0, Side.SERVER);
        FMLCommonHandler.instance()
            .bus()
            .register(new Events());
    }

    public static void send(Container container, long revision, int source, int[] destinations) {
        channel.sendToServer(new PanelTransferRequest(container.windowId, revision, source, destinations));
    }

    @Override
    public void fromBytes(ByteBuf bytes) {
        if (bytes.readableBytes() < 20) return;
        window = bytes.readInt();
        revision = bytes.readLong();
        int candidate = bytes.readInt(), count = bytes.readInt();
        if (count < 1 || count > InventoryShape.MAX_STORAGE + 9 || bytes.readableBytes() != count * 4) return;
        destinations = new int[count];
        for (int i = 0; i < count; i++) destinations[i] = bytes.readInt();
        source = candidate;
    }

    @Override
    public void toBytes(ByteBuf bytes) {
        bytes.writeInt(window)
            .writeLong(revision)
            .writeInt(source)
            .writeInt(destinations.length);
        for (int id : destinations) bytes.writeInt(id);
    }

    public static final class Handler implements IMessageHandler<PanelTransferRequest, IMessage> {

        @Override
        public IMessage onMessage(PanelTransferRequest request, MessageContext context) {
            if (request.source < 0 || request.destinations.length == 0) return null;
            synchronized (PENDING) {
                Deque<PanelTransferRequest> queue = PENDING
                    .computeIfAbsent(context.getServerHandler().playerEntity, ignored -> new ArrayDeque<>());
                if (queue.size() < 32) queue.addLast(request);
            }
            return null;
        }
    }

    private void apply(EntityPlayerMP player) {
        Container container = player.openContainer;
        if (player.isDead || player.getHealth() <= 0
            || container == null
            || container.windowId != window
            || !dev.gilbe.dynamicinventory.compat.VanillaPanelHosts.supports(container)
            || InventoryLayout.of(player.inventory)
                .revision() != revision
            || !InventoryUi.isOpeningReady(player)
            || !container.canInteractWith(player)
            || player.inventory.getItemStack() != null
            || !ordinary(container, player, source)) return;
        Set<Integer> unique = new HashSet<>();
        for (int id : destinations) if (id == source || !unique.add(id) || !ordinary(container, player, id)) return;
        PlayerInventoryTransfers.transferTo(container, player, source, destinations);
        player.inventory.markDirty();
        container.detectAndSendChanges();
    }

    private static boolean ordinary(Container container, EntityPlayerMP player, int id) {
        if (id < 0 || id >= container.inventorySlots.size()) return false;
        Slot slot = container.getSlot(id);
        return slot.inventory == player.inventory && InventoryLayout.of(player.inventory)
            .ordinaryIndex(slot.getSlotIndex()) >= 0;
    }

    public static final class Events {

        @SubscribeEvent
        public void tick(TickEvent.PlayerTickEvent event) {
            if (event.phase != TickEvent.Phase.END || !(event.player instanceof EntityPlayerMP player)) return;
            Deque<PanelTransferRequest> queue;
            synchronized (PENDING) {
                queue = PENDING.remove(player);
            }
            if (queue != null) for (PanelTransferRequest request : queue) request.apply(player);
        }
    }
}
