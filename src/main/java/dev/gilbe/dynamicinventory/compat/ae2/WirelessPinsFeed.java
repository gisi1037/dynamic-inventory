package dev.gilbe.dynamicinventory.compat.ae2;

import static dev.gilbe.dynamicinventory.network.AePinsChannel.BACKGROUND_REFRESH_TICKS;

import java.util.Arrays;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.AEApi;
import appeng.api.config.SecurityPermissions;
import appeng.api.features.ILocatableRegistry;
import appeng.api.features.IWirelessTermHandler;
import appeng.api.features.IWirelessTermRegistry;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.ISecurityGrid;
import appeng.api.networking.security.PlayerSource;
import appeng.api.networking.storage.IBaseMonitor;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.items.contents.PinList;
import appeng.items.contents.PinsHandler;
import appeng.items.contents.PinsHolder;
import appeng.util.Platform;
import appeng.util.TerminalSettings;
import appeng.util.item.AEItemStack;
import baubles.api.BaublesApi;
import dev.gilbe.dynamicinventory.inventory.HotbarBanks;
import dev.gilbe.dynamicinventory.inventory.InventoryLayout;
import dev.gilbe.dynamicinventory.network.AePinsChannel.Snapshot;
import dev.gilbe.dynamicinventory.network.AePinsChannel.Transfer;
import dev.gilbe.dynamicinventory.network.AePinsChannel.WirelessStatus;

/** A server session confined to one wireless item and the player's own pins. */
public final class WirelessPinsFeed {

    private final EntityPlayerMP player;
    private final long epoch;
    private int slot;
    private boolean worn;
    private final ItemStack device;
    private final IWirelessTermHandler handler;
    private final String link;
    private final ILocatableRegistry stations = AEApi.instance()
        .registries()
        .locatable();
    private final WirelessTerminalGuiObject wireless;
    private final IGrid grid;
    private final TerminalSettings settings = new TerminalSettings();
    private final Set<IMEMonitor<?>> monitors = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<IAEStack<?>> identities = new HashSet<>();
    private final IMEMonitorHandlerReceiver<IAEStack<?>> listener = new Listener();
    private IAEStack<?>[] pins = new IAEStack<?>[PinList.TOTAL_SLOTS];
    private IAEStack<?>[] published;
    private int craftingSlots, playerSlots;
    private int publishedCrafting = -1, publishedPlayer = -1;
    private PinsHandler pinList;
    private NBTBase savedPins, savedSettings;
    private int expires, nextUpdate;
    private boolean changed = true, closed, background;

    private static final class Terminal {

        final int slot;
        final boolean worn;
        final ItemStack stack;
        final IWirelessTermHandler handler;
        final String link;

        Terminal(int slot, boolean worn, ItemStack stack, IWirelessTermHandler handler, String link) {
            this.slot = slot;
            this.worn = worn;
            this.stack = stack;
            this.handler = handler;
            this.link = link;
        }
    }

    private WirelessPinsFeed(EntityPlayerMP player, long epoch, Terminal terminal, boolean background) {
        this.player = player;
        this.epoch = epoch;
        slot = terminal.slot;
        worn = terminal.worn;
        device = terminal.stack;
        handler = terminal.handler;
        link = terminal.link;
        wireless = new WirelessTerminalGuiObject(handler, device, player, player.worldObj, inventorySlot(), 0, 0);
        grid = wireless.getGrid();
        renew(background);
    }

    public static WirelessPinsFeed open(EntityPlayerMP player, long epoch, boolean background,
        Consumer<Snapshot> send) {
        if (player.isDead) {
            send.accept(Snapshot.unavailable(epoch, WirelessStatus.RETRYING));
            return null;
        }
        var registry = AEApi.instance()
            .registries()
            .wireless();
        Terminal held = linkedTerminal(registry, player.inventory, player.inventory.currentItem, false);
        if (held != null) {
            WirelessPinsFeed feed = new WirelessPinsFeed(player, epoch, held, background);
            WirelessStatus failure = feed.networkStatus();
            if (failure == null) return feed;
            send.accept(Snapshot.unavailable(epoch, failure));
            return null;
        }

        WirelessPinsFeed available = null;
        WirelessStatus failure = WirelessStatus.NO_LINK;
        var shape = InventoryLayout.of(player.inventory);
        IInventory baubles = BaublesApi.getBaubles(player);
        int equipment = baubles == null ? 0 : baubles.getSizeInventory();
        for (int i = 0; i < shape.mainSlots() + equipment; i++) {
            boolean worn = i >= shape.mainSlots();
            int slot = worn ? i - shape.mainSlots() : shape.rawIndex(i);
            if (!worn && HotbarBanks.isReserved(player.inventory, slot)) continue;
            Terminal terminal = linkedTerminal(registry, worn ? baubles : player.inventory, slot, worn);
            if (terminal == null || available != null && available.link.equals(terminal.link)) continue;
            WirelessPinsFeed candidate = new WirelessPinsFeed(player, epoch, terminal, background);
            WirelessStatus status = candidate.networkStatus();
            if (status != null) {
                if (failure == WirelessStatus.NO_LINK || status != WirelessStatus.NO_POWER) failure = status;
                continue;
            }
            if (available != null) {
                send.accept(Snapshot.unavailable(epoch, WirelessStatus.CHOOSE_TERMINAL));
                return null;
            }
            available = candidate;
        }
        if (available == null) send.accept(Snapshot.unavailable(epoch, failure));
        return available;
    }

    private static Terminal linkedTerminal(IWirelessTermRegistry registry, IInventory inventory, int slot,
        boolean worn) {
        ItemStack stack = inventory.getStackInSlot(slot);
        if (stack == null) return null;
        IWirelessTermHandler handler = registry.getWirelessTerminalHandler(stack);
        if (handler == null) return null;
        String link = handler.getEncryptionKey(stack);
        return link == null || link.isEmpty() ? null : new Terminal(slot, worn, stack, handler, link);
    }

    private int inventorySlot() {
        return worn ? Platform.baublesSlotsOffset + slot : slot;
    }

    public long epoch() {
        return epoch;
    }

    public void renew(boolean background) {
        int now = player.ticksExisted;
        if (this.background != background && published != null)
            nextUpdate = background ? now + BACKGROUND_REFRESH_TICKS : now;
        this.background = background;
        expires = now + (background ? BACKGROUND_REFRESH_TICKS * 3 : 100);
    }

    public boolean expired() {
        return player.ticksExisted - expires >= 0;
    }

    private WirelessStatus status() {
        if (player.isDead || !link.equals(handler.getEncryptionKey(device)) || !findDevice())
            return WirelessStatus.RETRYING;
        ItemStack held = player.inventory.getCurrentItem();
        if (held != null && held != device
            && linkedTerminal(
                AEApi.instance()
                    .registries()
                    .wireless(),
                player.inventory,
                player.inventory.currentItem,
                false) != null)
            return WirelessStatus.RETRYING;
        return networkStatus();
    }

    private boolean findDevice() {
        IInventory baubles = BaublesApi.getBaubles(player);
        IInventory inventory = worn ? baubles : player.inventory;
        if (inventory != null && slot < inventory.getSizeInventory()
            && (worn || !HotbarBanks.isReserved(player.inventory, slot))
            && inventory.getStackInSlot(slot) == device) return true;

        var shape = InventoryLayout.of(player.inventory);
        for (int i = 0; i < shape.mainSlots(); i++) {
            int raw = shape.rawIndex(i);
            if (player.inventory.getStackInSlot(raw) == device && !HotbarBanks.isReserved(player.inventory, raw)) {
                slot = raw;
                worn = false;
                return true;
            }
        }
        if (baubles != null) for (int i = 0; i < baubles.getSizeInventory(); i++) {
            if (baubles.getStackInSlot(i) == device) {
                slot = i;
                worn = true;
                return true;
            }
        }
        return false;
    }

    private WirelessStatus networkStatus() {
        if (!handler.hasInfinityPower(device) && !handler.hasPower(player, 0.5, device)) return WirelessStatus.NO_POWER;
        if (grid == null || linkedGrid() != grid || !wireless.rangeCheck()) return WirelessStatus.NETWORK_UNAVAILABLE;
        var node = wireless.getActionableNode();
        IEnergyGrid energy = grid.getCache(IEnergyGrid.class);
        if (node == null || node.getGrid() != grid || energy == null || !energy.isNetworkPowered())
            return WirelessStatus.NETWORK_UNAVAILABLE;
        ISecurityGrid security = grid.getCache(ISecurityGrid.class);
        return security != null && (security.hasPermission(player, SecurityPermissions.EXTRACT)
            || security.hasPermission(player, SecurityPermissions.CRAFT)) ? null : WirelessStatus.NO_ACCESS;
    }

    private IGrid linkedGrid() {
        // AE2's wireless GUI resolves the security station only at construction. The access point
        // can remain on the old, powered grid after the station disappears or changes networks.
        try {
            var station = stations.getLocatableBy(Long.parseLong(link));
            if (station instanceof IGridHost host) {
                var node = host.getGridNode(ForgeDirection.UNKNOWN);
                return node == null ? null : node.getGrid();
            }
        } catch (NumberFormatException ignored) {
            // AE2 uses a numeric locatable ID for a wireless link.
        }
        return null;
    }

    private boolean availableForAction() {
        if (status() == null) return true;
        nextUpdate = player.ticksExisted;
        return false;
    }

    public void edit(int index, boolean remove, byte[] expected) {
        if (published == null || index < 0 || index >= pins.length || !availableForAction()) return;
        refreshPins();
        IAEStack<?> current = pinList.getPin(index);
        IAEStack<?> visible = published[index];
        ItemStack cursor = player.inventory.getItemStack();
        if (remove) {
            if (cursor != null || !PinIdentity.matches(expected, current)
                || visible == null
                || !current.isSameType(visible)) return;
            pinList.setPin(index, null);
        } else {
            if (index < PinList.PLAYER_OFFSET || current != null
                || visible != null
                || cursor == null
                || cursor.stackSize <= 0) return;
            var item = AEItemStack.create(cursor);
            if (!PinIdentity.matches(expected, item)) return;
            pinList.setPin(index, item);
        }
        IInventory inventory = worn ? BaublesApi.getBaubles(player) : player.inventory;
        inventory.markDirty();
        nextUpdate = player.ticksExisted;
    }

    @SuppressWarnings("unchecked")
    public void transfer(Container container, int index, Transfer operation, int argument, int[] panel,
        byte[] expected) {
        if (published == null || index < 0
            || index >= pins.length
            || !(published[index] instanceof IAEItemStack visible)
            || !availableForAction()) return;
        refreshPins();
        if (!(pinList.getPin(index) instanceof IAEItemStack pin) || !pin.isSameType(visible)
            || !PinIdentity.matches(expected, pin)
            || !typeEnabled(pin.getStackType())) return;
        var monitor = (IMEMonitor<IAEItemStack>) wireless.getMEMonitor(pin.getStackType());
        operation = PinnedItemTransfer.resolveClick(operation, player.inventory.getItemStack(), monitor, pin);
        ISecurityGrid security = grid.getCache(ISecurityGrid.class);
        if (!security.hasPermission(player, PinnedItemTransfer.requiredPermission(player, operation, argument))) return;
        if (operation == Transfer.CRAFT) {
            int slotIndex = inventorySlot();
            PinnedItemTransfer.openCraftAmount(player, monitor, pin, null, null, slotIndex, null, device);
            return;
        }
        if (PinnedItemTransfer.apply(
            player,
            container,
            pin,
            operation,
            argument,
            wireless,
            monitor,
            new PlayerSource(player, wireless),
            device,
            panel)) nextUpdate = player.ticksExisted;
    }

    public boolean update(Consumer<Snapshot> send) {
        if (player.ticksExisted - nextUpdate < 0) return true;
        nextUpdate = player.ticksExisted + (background ? BACKGROUND_REFRESH_TICKS : 20);
        WirelessStatus failure = status();
        if (failure != null) {
            send.accept(Snapshot.unavailable(epoch, failure));
            return false;
        }
        refreshPins();
        if (!changed) return true;
        changed = false;
        IAEStack<?>[] next = snapshot();
        if (!sameSnapshot(published, next) || craftingSlots != publishedCrafting || playerSlots != publishedPlayer) {
            published = next;
            publishedCrafting = craftingSlots;
            publishedPlayer = playerSlots;
            send.accept(new Snapshot(epoch, next, craftingSlots, playerSlots));
        }
        return true;
    }

    private void refreshPins() {
        NBTBase current = device.getTagCompound() == null ? null
            : device.getTagCompound()
                .getTag("pins");
        if (pinList != null && java.util.Objects.equals(current, savedPins)) return;
        savedPins = current == null ? null : current.copy();
        pinList = new PinsHolder(device).getHandler(player);
        craftingSlots = pinList.getCraftingPinsRows()
            .getSlotCount();
        playerSlots = pinList.getPlayerPinsRows()
            .getSlotCount();
        Set<IMEMonitor<?>> sources = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        identities.clear();
        for (int i = 0; i < pins.length; i++) {
            IAEStack<?> pin = pinList.getPin(i);
            pins[i] = pin;
            if (pin == null) continue;
            identities.add(pin);
            IMEMonitor<?> monitor = wireless.getMEMonitor(pin.getStackType());
            if (monitor != null) sources.add(monitor);
        }
        for (IMEMonitor<?> monitor : monitors) if (!sources.contains(monitor)) monitor.removeListener(listener);
        for (IMEMonitor<?> monitor : sources) if (!monitors.contains(monitor)) monitor.addListener(listener, this);
        monitors.clear();
        monitors.addAll(sources);
        changed = true;
    }

    private boolean typeEnabled(IAEStackType<?> type) {
        var tag = device.getTagCompound();
        NBTBase current = tag == null ? null : tag.getTag("terminalSettings");
        if (!java.util.Objects.equals(current, savedSettings)) {
            settings.readFromNBT(tag);
            savedSettings = current == null ? null : current.copy();
        }
        return settings.getFilters(player)
            .isEnabled(type);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private IAEStack<?>[] snapshot() {
        IAEStack<?>[] result = new IAEStack<?>[pins.length];
        Map<IAEStackType<?>, IItemList> lists = new IdentityHashMap<>();
        for (int i = 0; i < result.length; i++) {
            IAEStack<?> pin = pins[i];
            if (pin == null) continue;
            IItemList list = lists.computeIfAbsent(pin.getStackType(), type -> {
                IMEMonitor<?> monitor = wireless.getMEMonitor(type);
                return monitor == null ? null : monitor.getStorageList();
            });
            IAEStack<?> stack = list == null ? null : list.findPrecise(pin);
            result[i] = stack == null ? pin.copy()
                .reset() : stack.copy();
        }
        return result;
    }

    private static boolean sameSnapshot(IAEStack<?>[] previous, IAEStack<?>[] next) {
        if (!Arrays.equals(previous, next)) return false;
        for (int i = 0; i < next.length; i++)
            if (next[i] != null && (previous[i].getStackSize() != next[i].getStackSize()
                || previous[i].isCraftable() != next[i].isCraftable())) return false;
        return true;
    }

    public void close() {
        closed = true;
        for (IMEMonitor<?> monitor : monitors) monitor.removeListener(listener);
        monitors.clear();
    }

    private final class Listener implements IMEMonitorHandlerReceiver<IAEStack<?>> {

        @Override
        public boolean isValid(Object token) {
            return token == WirelessPinsFeed.this && !closed;
        }

        @Override
        public void postChange(IBaseMonitor<IAEStack<?>> monitor, Iterable<IAEStack<?>> changes,
            BaseActionSource actionSource) {
            if (changed) return;
            for (IAEStack<?> stack : changes) if (identities.contains(stack)) {
                changed = true;
                return;
            }
        }

        @Override
        public void onListUpdate() {
            changed = true;
        }
    }
}
