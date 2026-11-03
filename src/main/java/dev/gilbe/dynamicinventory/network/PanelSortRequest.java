package dev.gilbe.dynamicinventory.network;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;

import com.cleanroommc.bogosorter.BogoSortAPI;
import com.cleanroommc.bogosorter.common.network.CSort;
import com.cleanroommc.bogosorter.mixins.early.minecraft.SlotAccessor;

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
import dev.gilbe.dynamicinventory.compat.PanelSortScope;
import dev.gilbe.dynamicinventory.inventory.InventoryLayout;
import dev.gilbe.dynamicinventory.inventory.InventoryShape;
import io.netty.buffer.ByteBuf;

/** Carry panel membership alongside Bogo's unchanged rules, names, colors and inversion flags. */
public final class PanelSortRequest implements IMessage {

    private static final Map<EntityPlayerMP, Deque<PanelSortRequest>> PENDING = new WeakHashMap<>();
    private static SimpleNetworkWrapper channel;
    private int window;
    private long revision;
    private int[] slots = new int[0];
    private CSort nativeSort;

    public PanelSortRequest() {}

    public static void initialize() {
        channel = NetworkRegistry.INSTANCE.newSimpleChannel("dinv-panel-sort");
        channel.registerMessage(Handler.class, PanelSortRequest.class, 0, Side.SERVER);
        FMLCommonHandler.instance()
            .bus()
            .register(new Events());
    }

    public static void send(Container container, long revision, int[] slots, CSort nativeSort) {
        if (slots.length == 0) return;
        PanelSortRequest request = new PanelSortRequest();
        request.window = container.windowId;
        request.revision = revision;
        request.slots = slots.clone();
        request.nativeSort = nativeSort;
        channel.sendToServer(request);
    }

    @Override
    public void fromBytes(ByteBuf bytes) {
        if (bytes.readableBytes() < 16) return;
        window = bytes.readInt();
        revision = bytes.readLong();
        int count = bytes.readInt();
        if (count < 1 || count > InventoryShape.MAX_STORAGE || bytes.readableBytes() < count * 4) return;
        int[] candidate = new int[count];
        for (int i = 0; i < count; i++) candidate[i] = bytes.readInt();
        CSort sort = new CSort();
        sort.fromBytes(bytes);
        if (sort.isRejected()) {
            sort.acknowledge();
            return;
        }
        slots = candidate;
        nativeSort = sort;
    }

    @Override
    public void toBytes(ByteBuf bytes) {
        bytes.writeInt(window)
            .writeLong(revision)
            .writeInt(slots.length);
        for (int id : slots) bytes.writeInt(id);
        nativeSort.toBytes(bytes);
    }

    private void apply(EntityPlayerMP player) {
        Container container = player.openContainer;
        if (nativeSort == null || player.isDead
            || player.getHealth() <= 0
            || container == null
            || container.windowId != window
            || InventoryLayout.of(player.inventory)
                .revision() != revision
            || !InventoryUi.isOpeningReady(player)
            || !container.canInteractWith(player)
            || player.inventory.getItemStack() != null) return;
        Set<Integer> unique = new HashSet<>();
        for (int id : slots) {
            if (id < 0 || id >= container.inventorySlots.size()
                || !unique.add(id)
                || !BogoSortAPI.isPlayerSlot((SlotAccessor) container.getSlot(id))) return;
        }
        // Filtering happens inside Bogo's native sortable-slot policy, after its pin/lock checks.
        PanelSortScope.run(slots, () -> nativeSort.executeServer(player.playerNetServerHandler));
    }

    public static final class Handler implements IMessageHandler<PanelSortRequest, IMessage> {

        @Override
        public IMessage onMessage(PanelSortRequest request, MessageContext context) {
            if (request.nativeSort == null || request.slots.length == 0) return null;
            synchronized (PENDING) {
                Deque<PanelSortRequest> queue = PENDING
                    .computeIfAbsent(context.getServerHandler().playerEntity, ignored -> new ArrayDeque<>());
                if (queue.size() < 8) queue.addLast(request);
            }
            return null;
        }
    }

    public static final class Events {

        @SubscribeEvent
        public void tick(TickEvent.PlayerTickEvent event) {
            if (event.phase != TickEvent.Phase.END || !(event.player instanceof EntityPlayerMP player)) return;
            Deque<PanelSortRequest> queue;
            synchronized (PENDING) {
                queue = PENDING.remove(player);
            }
            if (queue != null) for (PanelSortRequest request : queue) request.apply(player);
        }
    }
}
