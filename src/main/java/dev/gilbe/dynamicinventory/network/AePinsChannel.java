package dev.gilbe.dynamicinventory.network;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraftforge.common.MinecraftForge;

import appeng.api.storage.data.IAEStack;
import appeng.container.AEBaseContainer;
import appeng.container.implementations.ContainerMEMonitorable;
import appeng.items.contents.PinList;
import appeng.util.Platform;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import dev.gilbe.dynamicinventory.DynamicInventory;
import dev.gilbe.dynamicinventory.api.InventoryUi;
import dev.gilbe.dynamicinventory.compat.ae2.PinIdentity;
import dev.gilbe.dynamicinventory.compat.ae2.PinnedItemTransfer;
import dev.gilbe.dynamicinventory.compat.ae2.WirelessPinsFeed;
import dev.gilbe.dynamicinventory.inventory.InventoryLayout;
import dev.gilbe.dynamicinventory.inventory.InventoryShape;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/** Wireless snapshots for players who have enabled the Pins panel. */
public final class AePinsChannel {

    public static final int BACKGROUND_REFRESH_TICKS = 200;
    private static final int MAX_BYTES = 512 * 1024;
    private static final int OPEN = 0, RENEW = 1, CLOSE = 2, SET_PIN = 3, REMOVE_PIN = 4;
    private static final int FIRST_TRANSFER = 5;
    private static final Map<EntityPlayerMP, Request> PENDING = new ConcurrentHashMap<>();
    private static final Map<EntityPlayerMP, Deque<Request>> PENDING_EDITS = new IdentityHashMap<>();
    private static final Map<EntityPlayerMP, WirelessPinsFeed> ACTIVE = new IdentityHashMap<>();
    private static SimpleNetworkWrapper channel;

    private AePinsChannel() {}

    public static void initialize() {
        channel = NetworkRegistry.INSTANCE.newSimpleChannel("dinv-ae-pins2");
        channel.registerMessage(ReceiveRequest.class, Request.class, 0, Side.SERVER);
        channel.registerMessage(ReceiveSnapshot.class, Snapshot.class, 1, Side.CLIENT);
        channel.registerMessage(ReceiveTransferReply.class, TransferReply.class, 2, Side.CLIENT);
        Events events = new Events();
        FMLCommonHandler.instance()
            .bus()
            .register(events);
        MinecraftForge.EVENT_BUS.register(events);
    }

    public static void send(long epoch, boolean open, boolean renew, boolean background) {
        Request request = new Request();
        request.epoch = epoch;
        request.kind = open ? (renew ? RENEW : OPEN) : CLOSE;
        request.background = open && background;
        channel.sendToServer(request);
    }

    public static void edit(long epoch, int windowId, int index, boolean remove, IAEStack<?> expected) {
        Request request = new Request();
        request.epoch = epoch;
        request.kind = remove ? REMOVE_PIN : SET_PIN;
        request.windowId = windowId;
        request.index = index;
        request.expected = PinIdentity.of(expected);
        channel.sendToServer(request);
    }

    public enum Transfer {

        SCROLL_CURSOR(1),
        SCROLL_ITEMS(1),
        PICKUP(0),
        SPLIT(0),
        STACK(0),
        ALL(0),
        DEPOSIT_SLOT(2),
        DEPOSIT_ALL(2),
        DEPOSIT_TERMINAL_ALL(2),
        CRAFT(0),
        SPACE_PANEL(2),
        SCROLL_DEPOSIT_SLOT(3),
        SCROLL_WITHDRAW_SLOT(3);

        private static final Transfer[] VALUES = values();
        private final int argumentBytes;

        Transfer(int argumentBytes) {
            this.argumentBytes = argumentBytes;
        }

        private static Transfer fromKind(int kind) {
            int index = kind - FIRST_TRANSFER;
            return index >= 0 && index < VALUES.length ? VALUES[index] : null;
        }
    }

    public static int scrollSlotArgument(int slot, int amount) {
        return (slot << 8) | Math.min(64, amount);
    }

    public static int scrollSlot(int argument) {
        return argument >>> 8;
    }

    public static int scrollAmount(int argument) {
        return argument & 0xff;
    }

    public static void transfer(long epoch, int windowId, int index, Transfer action, int argument, long sequence,
        IAEStack<?> expected) {
        Request request = new Request();
        request.epoch = epoch;
        request.kind = FIRST_TRANSFER + action.ordinal();
        request.windowId = windowId;
        request.index = index;
        request.argument = action.argumentBytes == 1 ? Math.max(-64, Math.min(64, argument)) : argument;
        request.sequence = sequence;
        request.expected = PinIdentity.of(expected);
        channel.sendToServer(request);
    }

    public static void transferPanel(long epoch, int windowId, int index, int clicked, long sequence, long revision,
        int[] slots, IAEStack<?> expected) {
        if (slots.length == 0) return;
        Request request = new Request();
        request.epoch = epoch;
        request.kind = FIRST_TRANSFER + Transfer.SPACE_PANEL.ordinal();
        request.windowId = windowId;
        request.index = index;
        request.argument = clicked;
        request.sequence = sequence;
        request.revision = revision;
        request.slots = slots.clone();
        request.expected = PinIdentity.of(expected);
        channel.sendToServer(request);
    }

    public static void shutdown() {
        PENDING.clear();
        synchronized (PENDING_EDITS) {
            PENDING_EDITS.clear();
        }
        for (WirelessPinsFeed feed : ACTIVE.values()) feed.close();
        ACTIVE.clear();
    }

    public static final class Request implements IMessage {

        long epoch;
        long sequence;
        long revision;
        int kind = -1;
        int windowId, index, argument;
        int[] slots;
        byte[] expected;
        boolean background;

        @Override
        public void fromBytes(ByteBuf bytes) {
            int length = bytes.readableBytes();
            if (length < 9 || length > PinIdentity.BYTES + 35 + 2 * (InventoryShape.MAX_STORAGE + 9)) return;
            epoch = bytes.readLong();
            int action = bytes.readUnsignedByte();
            if (length == 9) {
                if (action <= CLOSE) kind = action;
                return;
            }
            if (length == 10) {
                if (action <= RENEW && bytes.readUnsignedByte() == 1) {
                    kind = action;
                    background = true;
                }
                return;
            }
            Transfer transfer = Transfer.fromKind(action);
            length -= PinIdentity.BYTES;
            int argumentBytes = transfer == null ? 0 : transfer.argumentBytes;
            if (transfer == null ? length != 15 || action != SET_PIN && action != REMOVE_PIN
                : transfer == Transfer.SPACE_PANEL ? length < 37 : length != 23 + argumentBytes) return;
            expected = new byte[PinIdentity.BYTES];
            bytes.readBytes(expected);
            windowId = bytes.readInt();
            index = bytes.readUnsignedShort();
            if (argumentBytes == 1) {
                argument = bytes.readByte();
                if (argument == 0 || Math.abs(argument) > 64) return;
            } else if (argumentBytes == 2) argument = bytes.readUnsignedShort();
            else if (argumentBytes == 3) {
                argument = bytes.readUnsignedMedium();
                int amount = scrollAmount(argument);
                if (amount == 0 || amount > 64) return;
            }
            if (transfer != null) sequence = bytes.readLong();
            if (transfer == Transfer.SPACE_PANEL) {
                revision = bytes.readLong();
                int count = bytes.readUnsignedShort();
                if (count < 1 || count > InventoryShape.MAX_STORAGE + 9 || bytes.readableBytes() != 2 * count) return;
                slots = new int[count];
                for (int i = 0; i < count; i++) slots[i] = bytes.readUnsignedShort();
            }
            if (index < PinList.TOTAL_SLOTS) kind = action;
        }

        @Override
        public void toBytes(ByteBuf bytes) {
            bytes.writeLong(epoch)
                .writeByte(kind);
            if (background) bytes.writeByte(1);
            if (kind >= SET_PIN) bytes.writeBytes(expected)
                .writeInt(windowId)
                .writeShort(index);
            Transfer transfer = Transfer.fromKind(kind);
            if (transfer != null && transfer.argumentBytes == 1) bytes.writeByte(argument);
            if (transfer != null && transfer.argumentBytes == 2) bytes.writeShort(argument);
            if (transfer != null && transfer.argumentBytes == 3) bytes.writeMedium(argument);
            if (transfer != null) bytes.writeLong(sequence);
            if (transfer == Transfer.SPACE_PANEL) {
                bytes.writeLong(revision)
                    .writeShort(slots.length);
                for (int slot : slots) bytes.writeShort(slot);
            }
        }
    }

    public static final class TransferReply implements IMessage {

        long sequence;

        public TransferReply() {}

        TransferReply(long sequence) {
            this.sequence = sequence;
        }

        @Override
        public void fromBytes(ByteBuf bytes) {
            if (bytes.readableBytes() == Long.BYTES) sequence = bytes.readLong();
        }

        @Override
        public void toBytes(ByteBuf bytes) {
            bytes.writeLong(sequence);
        }
    }

    public static final class ReceiveTransferReply implements IMessageHandler<TransferReply, IMessage> {

        @Override
        public IMessage onMessage(TransferReply reply, MessageContext context) {
            if (reply.sequence > 0) DynamicInventory.proxy.receiveAePinsTransfer(reply.sequence);
            return null;
        }
    }

    public enum WirelessStatus {

        RETRYING,
        NO_LINK,
        CHOOSE_TERMINAL,
        NO_POWER,
        NETWORK_UNAVAILABLE,
        NO_ACCESS,
        SNAPSHOT_FAILED;

        private static final WirelessStatus[] VALUES = values();

        String translationSuffix() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public static final class Snapshot implements IMessage {

        private long epoch;
        private IAEStack<?>[] stacks;
        private int craftingSlots, playerSlots;
        private WirelessStatus status = WirelessStatus.SNAPSHOT_FAILED;

        public Snapshot() {}

        public Snapshot(long epoch, IAEStack<?>[] stacks, int craftingSlots, int playerSlots) {
            this.epoch = epoch;
            this.stacks = stacks;
            this.craftingSlots = craftingSlots;
            this.playerSlots = playerSlots;
        }

        public static Snapshot unavailable(long epoch, WirelessStatus status) {
            Snapshot snapshot = new Snapshot(epoch, null, 0, 0);
            snapshot.status = status;
            return snapshot;
        }

        public String statusKey() {
            return "dynamicinventory.ae2pins." + (available() ? "empty" : status.translationSuffix());
        }

        public long epoch() {
            return epoch;
        }

        public boolean available() {
            return stacks != null;
        }

        public IAEStack<?>[] stacks() {
            return stacks;
        }

        public int pinSlots(boolean crafting) {
            return crafting ? craftingSlots : playerSlots;
        }

        @Override
        public void fromBytes(ByteBuf bytes) {
            if (bytes.readableBytes() < 9 || bytes.readableBytes() > MAX_BYTES + 11) return;
            epoch = bytes.readLong();
            if (!bytes.readBoolean()) {
                int reason = bytes.isReadable() ? bytes.readUnsignedByte() : 0;
                if (reason < WirelessStatus.VALUES.length) status = WirelessStatus.VALUES[reason];
                return;
            }
            IAEStack<?>[] decoded = new IAEStack<?>[PinList.TOTAL_SLOTS];
            try {
                int crafting = bytes.readUnsignedByte(), player = bytes.readUnsignedByte();
                if (crafting > PinList.CRAFTING_SLOTS || player > PinList.PLAYER_SLOTS
                    || crafting % 9 != 0
                    || player % 9 != 0) return;
                while (bytes.isReadable()) {
                    int index = bytes.readUnsignedShort();
                    if (index >= decoded.length || decoded[index] != null) return;
                    decoded[index] = Platform.readStackByte(bytes);
                    if (decoded[index] == null) return;
                }
                stacks = decoded;
                craftingSlots = crafting;
                playerSlots = player;
            } catch (RuntimeException ignored) {
                // Discard incomplete data; a partial snapshot cannot be rendered safely.
            }
        }

        @Override
        public void toBytes(ByteBuf bytes) {
            bytes.writeLong(epoch);
            if (stacks == null) {
                bytes.writeBoolean(false)
                    .writeByte(status.ordinal());
                return;
            }
            ByteBuf payload = Unpooled.buffer(256, MAX_BYTES);
            try {
                for (int i = 0; i < stacks.length; i++) if (stacks[i] != null) {
                    payload.writeShort(i);
                    Platform.writeStackByte(stacks[i], payload);
                }
                bytes.writeBoolean(true)
                    .writeByte(craftingSlots)
                    .writeByte(playerSlots)
                    .writeBytes(payload);
            } catch (RuntimeException invalid) {
                bytes.writeBoolean(false)
                    .writeByte(WirelessStatus.SNAPSHOT_FAILED.ordinal());
            } finally {
                payload.release();
            }
        }
    }

    public static final class ReceiveRequest implements IMessageHandler<Request, IMessage> {

        @Override
        public IMessage onMessage(Request request, MessageContext context) {
            if (request.epoch < 0
                || request.epoch == 0 && request.kind != REMOVE_PIN && Transfer.fromKind(request.kind) == null)
                return null;
            EntityPlayerMP player = context.getServerHandler().playerEntity;
            if (request.kind >= OPEN && request.kind <= CLOSE) PENDING.merge(
                player,
                request,
                (old, next) -> next.epoch < old.epoch || next.epoch == old.epoch && next.kind == RENEW ? old : next);
            else if (request.kind >= SET_PIN) {
                synchronized (PENDING_EDITS) {
                    Deque<Request> edits = PENDING_EDITS.computeIfAbsent(player, ignored -> new ArrayDeque<>());
                    if (edits.size() < 64) edits.addLast(request);
                }
            }
            return null;
        }
    }

    public static final class ReceiveSnapshot implements IMessageHandler<Snapshot, IMessage> {

        @Override
        public IMessage onMessage(Snapshot snapshot, MessageContext context) {
            if (snapshot.epoch() > 0) DynamicInventory.proxy.receiveAePins(snapshot);
            return null;
        }
    }

    public static final class Events {

        @SubscribeEvent
        public void tick(TickEvent.PlayerTickEvent event) {
            if (event.phase != TickEvent.Phase.END || !(event.player instanceof EntityPlayerMP player)) return;
            Request request = PENDING.remove(player);
            Deque<Request> edits;
            synchronized (PENDING_EDITS) {
                edits = PENDING_EDITS.remove(player);
            }
            WirelessPinsFeed feed = ACTIVE.get(player);
            if (request != null && (feed == null || request.epoch >= feed.epoch())) {
                if (request.kind == RENEW) {
                    if (feed == null)
                        channel.sendTo(Snapshot.unavailable(request.epoch, WirelessStatus.RETRYING), player);
                    else if (request.epoch == feed.epoch()) feed.renew(request.background);
                } else {
                    cleanup(player);
                    feed = request.kind == OPEN
                        ? WirelessPinsFeed
                            .open(player, request.epoch, request.background, value -> channel.sendTo(value, player))
                        : null;
                    if (feed != null) ACTIVE.put(player, feed);
                }
            }
            if (feed != null && feed.expired()) {
                channel.sendTo(Snapshot.unavailable(feed.epoch(), WirelessStatus.RETRYING), player);
                cleanup(player);
                feed = null;
            }
            long acknowledged = 0;
            if (edits != null) for (Request edit : edits) {
                apply(player, feed, edit);
                acknowledged = Math.max(acknowledged, edit.sequence);
            }
            if (acknowledged > 0 && player.openContainer != null) player.openContainer.detectAndSendChanges();
            if (feed != null && !feed.update(value -> channel.sendTo(value, player))) cleanup(player);
            if (acknowledged > 0) channel.sendTo(new TransferReply(acknowledged), player);
        }

        private static void apply(EntityPlayerMP player, WirelessPinsFeed feed, Request edit) {
            var container = player.openContainer;
            if (player.isDead || container == null
                || container.windowId != edit.windowId
                || !InventoryUi.isOpeningReady(player)
                || !container.canInteractWith(player)) return;
            Transfer transfer = Transfer.fromKind(edit.kind);
            if (transfer == Transfer.SPACE_PANEL) {
                if (!validPanel(player, container, edit)) return;
                if (edit.epoch == 0 && container instanceof AEBaseContainer ae && InventoryUi.findAdapter(ae) != null)
                    PinnedItemTransfer.moveNativePanel(player, ae, edit.slots);
                else if (feed != null && edit.epoch == feed.epoch())
                    feed.transfer(container, edit.index, transfer, edit.argument, edit.slots, edit.expected);
            } else if (edit.epoch == 0 && transfer != null && container instanceof ContainerMEMonitorable terminal) {
                PinnedItemTransfer.fromTerminal(player, terminal, edit.index, transfer, edit.argument, edit.expected);
            } else if (edit.epoch == 0 && edit.kind == REMOVE_PIN
                && container instanceof ContainerMEMonitorable terminal
                && player.inventory.getItemStack() == null
                && PinIdentity.matches(edit.expected, terminal.getPin(edit.index))) {
                    terminal.setPin(null, edit.index);
                } else if (feed != null && edit.epoch == feed.epoch() && transfer != Transfer.DEPOSIT_TERMINAL_ALL) {
                    if (transfer != null)
                        feed.transfer(container, edit.index, transfer, edit.argument, null, edit.expected);
                    else feed.edit(edit.index, edit.kind == REMOVE_PIN, edit.expected);
                }
        }

        private static boolean validPanel(EntityPlayerMP player, Container container, Request edit) {
            var shape = InventoryLayout.of(player.inventory);
            if (edit.revision != shape.revision()) return false;
            boolean[] seen = new boolean[shape.mainSlots()];
            boolean containsSource = false;
            for (int id : edit.slots) {
                if (id >= container.inventorySlots.size()) return false;
                int logical = PinnedItemTransfer.playerIndex(player, container.getSlot(id));
                if (logical < 0 || seen[logical]) return false;
                seen[logical] = true;
                if (id == edit.argument) containsSource = true;
            }
            return containsSource;
        }

        @SubscribeEvent
        public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
            if (event.player instanceof EntityPlayerMP player) releasePlayer(player);
        }

        @SubscribeEvent
        public void clone(net.minecraftforge.event.entity.player.PlayerEvent.Clone event) {
            if (event.original instanceof EntityPlayerMP player) releasePlayer(player);
        }

        private static void releasePlayer(EntityPlayerMP player) {
            PENDING.remove(player);
            synchronized (PENDING_EDITS) {
                PENDING_EDITS.remove(player);
            }
            cleanup(player);
        }

        private static void cleanup(EntityPlayerMP player) {
            WirelessPinsFeed feed = ACTIVE.remove(player);
            if (feed != null) feed.close();
        }
    }
}
