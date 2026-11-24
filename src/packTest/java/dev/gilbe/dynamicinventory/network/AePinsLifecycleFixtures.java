package dev.gilbe.dynamicinventory.network;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.entity.player.PlayerEvent;

import appeng.api.config.SecurityPermissions;
import appeng.api.features.ILocatable;
import appeng.api.features.ILocatableRegistry;
import appeng.api.features.IWirelessTermHandler;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.security.ISecurityGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.items.contents.PinList;
import appeng.items.contents.PinsHandler;
import appeng.util.AEStackTypeFilter;
import appeng.util.TerminalSettings;
import baubles.api.BaublesApi;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import dev.gilbe.dynamicinventory.compat.ae2.PinIdentity;
import dev.gilbe.dynamicinventory.compat.ae2.WirelessPinsFeed;
import dev.gilbe.dynamicinventory.network.AePinsChannel.Transfer;

/**
 * Drives feed actions, server ticks and Forge clone cleanup with a movable station and a reachable old access point.
 */
public final class AePinsLifecycleFixtures {

    private static IInventory worn;

    private AePinsLifecycleFixtures() {}

    public static IInventory equipment(EntityPlayer player) {
        return worn;
    }

    private static final class AccessPoint extends WirelessTerminalGuiObject {

        private IGridNode node;

        private AccessPoint() {
            super(null, null, null, null, 0, 0, 0);
        }

        @Override
        public boolean rangeCheck() {
            return true;
        }

        @Override
        public IGridNode getActionableNode() {
            return node;
        }
    }

    private static final class Replies extends SimpleNetworkWrapper {

        private AePinsChannel.Snapshot last;

        private Replies() {
            super("unused");
        }

        @Override
        public void sendTo(IMessage message, EntityPlayerMP player) {
            last = (AePinsChannel.Snapshot) message;
        }
    }

    // The default AE2 type registry initializes ore recipes in Forge. Substitute its NBT reader;
    // use the real filter map and the real feed's tag-change detection and action gate.
    private static final class Settings extends TerminalSettings {

        private AEStackTypeFilter filter;
        private IAEStackType<?> item;
        private EntityPlayer owner;

        @Override
        public void readFromNBT(NBTTagCompound tag) {
            filter.setEnabled(
                item,
                tag.getCompoundTag("terminalSettings")
                    .getBoolean("items"));
        }

        @Override
        public AEStackTypeFilter getFilters(EntityPlayer player) {
            check(owner == player, "filter belongs to a different player");
            return filter;
        }
    }

    public static void main(String[] args) throws Exception {
        field(cpw.mods.fml.relauncher.FMLInjectionData.class, "minecraftHome").set(null, new java.io.File(".setup"));
        var allocator = (sun.misc.Unsafe) field(sun.misc.Unsafe.class, "theUnsafe").get(null);
        EntityPlayerMP player = (EntityPlayerMP) allocator.allocateInstance(EntityPlayerMP.class);
        EntityPlayerMP replacement = (EntityPlayerMP) allocator.allocateInstance(EntityPlayerMP.class);
        player.setEntityId(10);
        replacement.setEntityId(11);
        player.inventory = new InventoryPlayer(player);
        var device = new ItemStack(new Item());
        worn = new InventoryBasic("equipment", false, 1);
        worn.setInventorySlotContents(0, device);
        Field baubles = field(BaublesApi.class, "getBaubles");
        Object oldBaubles = baubles.get(null);
        baubles.set(null, AePinsLifecycleFixtures.class.getMethod("equipment", EntityPlayer.class));
        var replies = (Replies) allocator.allocateInstance(Replies.class);
        Field channel = field(AePinsChannel.class, "channel");
        Object oldChannel = channel.get(null);
        channel.set(null, replies);
        try {
            var permissions = new ArrayList<SecurityPermissions>();
            var security = mock(ISecurityGrid.class, (p, m, a) -> {
                var permission = (SecurityPermissions) a[1];
                permissions.add(permission);
                return permission == SecurityPermissions.CRAFT;
            });
            var power = mock(IEnergyGrid.class, (p, m, a) -> true);
            InvocationHandler caches = (p, m, a) -> a[0] == IEnergyGrid.class ? power : security;
            IGrid first = mock(IGrid.class, caches), second = mock(IGrid.class, caches);
            var current = new AtomicReference<>(first);
            IGridNode stationNode = mock(IGridNode.class, (p, m, a) -> current.get());
            var station = (ILocatable) Proxy.newProxyInstance(
                ILocatable.class.getClassLoader(),
                new Class<?>[] { ILocatable.class, IGridHost.class },
                (p, m, a) -> {
                    check(a[0] == ForgeDirection.UNKNOWN, "station side");
                    return stationNode;
                });
            var stations = mock(ILocatableRegistry.class, (p, m, a) -> {
                check((long) a[0] == 42L, "wrong wireless link");
                return station;
            });
            var access = (AccessPoint) allocator.allocateInstance(AccessPoint.class);
            access.node = mock(IGridNode.class, (p, m, a) -> first);
            var feed = (WirelessPinsFeed) allocator.allocateInstance(WirelessPinsFeed.class);
            set(feed, "player", player);
            set(feed, "device", device);
            set(feed, "worn", true);
            set(feed, "link", "42");
            set(
                feed,
                "handler",
                mock(
                    IWirelessTermHandler.class,
                    (p, m, a) -> m.getName()
                        .equals("getEncryptionKey") ? "42" : true));
            set(feed, "grid", first);
            set(feed, "stations", stations);
            set(feed, "wireless", access);
            var settings = new Settings();
            settings.owner = player;
            settings.item = mock(IAEStackType.class, (p, m, a) -> "item");
            settings.filter = (AEStackTypeFilter) allocator.allocateInstance(AEStackTypeFilter.class);
            set(settings.filter, "filters", new it.unimi.dsi.fastutil.objects.Reference2BooleanOpenHashMap<>());
            set(feed, "settings", settings);
            var pin = mock(IAEItemStack.class, (p, m, a) -> switch (m.getName()) {
                case "copy", "reset" -> p;
                case "isSameType" -> p == a[0];
                case "getStackType" -> settings.item;
                case "writeToNBTGeneric" -> null;
                default -> throw new AssertionError(m);
            });
            var list = new PinList();
            list.setPin(0, pin);
            var handler = (PinsHandler) allocator.allocateInstance(PinsHandler.class);
            set(handler, "pinsInv", list);
            set(feed, "pinList", handler);
            set(feed, "pins", new IAEStack<?>[] { pin });
            set(feed, "published", new IAEStack<?>[] { pin });
            byte[] identity = PinIdentity.of(pin);
            device.setTagCompound(new NBTTagCompound());
            var saved = new NBTTagCompound();
            device.getTagCompound()
                .setTag("terminalSettings", saved);
            for (boolean enabled : new boolean[] { true, false, true }) {
                saved.setBoolean("items", enabled);
                for (Transfer gesture : new Transfer[] { Transfer.STACK, Transfer.DEPOSIT_ALL }) {
                    permissions.clear();
                    feed.transfer(null, 0, gesture, 0, null, identity);
                    var requested = gesture == Transfer.STACK ? SecurityPermissions.EXTRACT
                        : SecurityPermissions.INJECT;
                    var expected = new ArrayList<>(
                        Arrays.asList(SecurityPermissions.EXTRACT, SecurityPermissions.CRAFT));
                    if (enabled) expected.add(requested);
                    check(
                        permissions.equals(expected),
                        "item filter " + enabled + " for " + gesture + ": " + permissions);
                }
            }

            var events = new AePinsChannel.Events();
            Map<EntityPlayerMP, WirelessPinsFeed> active = map("ACTIVE");
            var removals = new AtomicInteger();
            var listener = mock(IMEMonitorHandlerReceiver.class, (p, m, a) -> true);
            var monitor = mock(IMEMonitor.class, (p, m, a) -> {
                check(
                    m.getName()
                        .equals("removeListener") && a[0] == listener,
                    "retired wrong listener");
                removals.incrementAndGet();
                return null;
            });
            set(feed, "listener", listener);
            set(feed, "monitors", new HashSet<>(Collections.singleton(monitor)));
            feed.renew(true);
            active.put(player, feed);
            current.set(second);
            permissions.clear();
            feed.transfer(null, 0, Transfer.STACK, 0, null, identity);
            check(permissions.isEmpty(), "read or used permissions on the stale grid");
            events.tick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
            check(
                replies.last != null && replies.last.statusKey()
                    .endsWith(".network_unavailable") && !active.containsKey(player) && removals.get() == 1,
                "failed to retire the stale background feed");

            // Seed another subscription on the new grid, then deliver the real Forge replacement event.
            set(feed, "grid", second);
            access.node = mock(IGridNode.class, (p, m, a) -> second);
            set(feed, "closed", false);
            set(feed, "monitors", new HashSet<>(Collections.singleton(monitor)));
            active.put(player, feed);
            feed.transfer(null, 0, Transfer.DEPOSIT_ALL, 0, null, identity);
            check(
                permissions.equals(
                    Arrays.asList(SecurityPermissions.EXTRACT, SecurityPermissions.CRAFT, SecurityPermissions.INJECT)),
                "a subscription bound to the new grid must validate there");
            map("PENDING").put(player, new AePinsChannel.Request());
            map("PENDING_EDITS").put(player, new ArrayDeque<>());
            map("PENDING").put(replacement, new AePinsChannel.Request());
            events.clone(new PlayerEvent.Clone(replacement, player, true));
            check(
                removals.get() == 2 && !active.containsKey(player)
                    && !map("PENDING").containsKey(player)
                    && !map("PENDING_EDITS").containsKey(player)
                    && map("PENDING").containsKey(replacement),
                "replacement must release only the original player's listeners and work");
            System.out
                .println("PASS AE2 item filter for insert/extract, stale-grid retirement and Forge clone cleanup");
        } finally {
            for (String state : new String[] { "ACTIVE", "PENDING", "PENDING_EDITS" }) {
                map(state).remove(player);
                map(state).remove(replacement);
            }
            channel.set(null, oldChannel);
            baubles.set(null, oldBaubles);
        }
    }

    @SuppressWarnings("unchecked")
    private static <V> Map<EntityPlayerMP, V> map(String name) throws Exception {
        return (Map<EntityPlayerMP, V>) field(AePinsChannel.class, name).get(null);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        field(target.getClass(), name).set(target, value);
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static <T> T mock(Class<T> type, InvocationHandler handler) {
        return type.cast(
            Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[] { type },
                (proxy, method, args) -> switch (method.getName()) {
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> handler.invoke(proxy, method, args);
                }));
    }

    private static void check(boolean success, String message) {
        if (!success) throw new AssertionError(message);
    }
}
