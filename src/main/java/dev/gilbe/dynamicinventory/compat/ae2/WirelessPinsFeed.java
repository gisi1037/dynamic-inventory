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

import appeng.api.AEApi;
import appeng.api.config.SecurityPermissions;
import appeng.api.features.IWirelessTermHandler;
import appeng.api.networking.IGrid;
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
import appeng.container.PrimaryGui;
import appeng.core.sync.GuiBridge;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.items.contents.PinList;
import appeng.items.contents.PinsHolder;
import appeng.util.Platform;
import appeng.util.item.AEItemStack;
import baubles.api.BaublesApi;
import dev.gilbe.dynamicinventory.inventory.HotbarBanks;
import dev.gilbe.dynamicinventory.inventory.InventoryLayout;
import dev.gilbe.dynamicinventory.network.AePinsChannel.Snapshot;
import dev.gilbe.dynamicinventory.network.AePinsChannel.Transfer;

/** A server session confined to one wireless item and the player's own pins. */
public final class WirelessPinsFeed {

    private final EntityPlayerMP player;
    private final long epoch;
    private final int slot;
    private final boolean worn;
    private final ItemStack device;
    private final IWirelessTermHandler handler;
    private final String link;
    private final WirelessTerminalGuiObject wireless;
    private final IGrid grid;
    private final Set<IMEMonitor<?>> monitors = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<IAEStack<?>> identities = new HashSet<>();
    private final IMEMonitorHandlerReceiver<IAEStack<?>> listener = new Listener();
    private IAEStack<?>[] pins = new IAEStack<?>[PinList.TOTAL_SLOTS];
    private IAEStack<?>[] published;
    private int craftingSlots, playerSlots;
    private NBTBase savedPins;
    private int expires, nextUpdate;
    private boolean changed = true, closed, background;

    private WirelessPinsFeed(EntityPlayerMP player, long epoch, int slot, boolean worn, ItemStack device,
        IWirelessTermHandler handler, boolean background) {
        this.player = player;
        this.epoch = epoch;
        this.slot = slot;
        this.worn = worn;
        this.device = device;
        this.handler = handler;
        link = handler.getEncryptionKey(device);
        wireless = new WirelessTerminalGuiObject(handler, device, player, player.worldObj, slot, 0, 0);
        grid = wireless.getGrid();
        renew(background);
    }

    public static WirelessPinsFeed open(EntityPlayerMP player, long epoch, boolean background) {
        var registry = AEApi.instance()
            .registries()
            .wireless();
        int slot = player.inventory.currentItem;
        ItemStack stack = player.inventory.getStackInSlot(slot);
        IWirelessTermHandler handler = handler(registry, stack);
        boolean worn = false;
        if (handler == null) {
            slot = -1;
            var shape = InventoryLayout.of(player.inventory);
            for (int i = 0; i < shape.mainSlots(); i++) {
                int raw = shape.rawIndex(i);
                if (HotbarBanks.isReserved(player.inventory, raw)) continue;
                ItemStack candidate = player.inventory.getStackInSlot(raw);
                IWirelessTermHandler candidateHandler = handler(registry, candidate);
                if (candidateHandler == null) continue;
                // Several terminals may reach different grids. Require a held terminal to choose explicitly.
                if (handler != null) return null;
                slot = raw;
                stack = candidate;
                handler = candidateHandler;
            }
            IInventory baubles = BaublesApi.getBaubles(player);
            if (baubles != null) for (int index = 0; index < baubles.getSizeInventory(); index++) {
                ItemStack candidate = baubles.getStackInSlot(index);
                IWirelessTermHandler candidateHandler = handler(registry, candidate);
                if (candidateHandler == null) continue;
                if (handler != null) return null;
                slot = index;
                stack = candidate;
                handler = candidateHandler;
                worn = true;
            }
        }
        if (handler == null) return null;
        String link = handler.getEncryptionKey(stack);
        if (link == null || link.isEmpty()) return null;
        return new WirelessPinsFeed(player, epoch, slot, worn, stack, handler, background);
    }

    private static IWirelessTermHandler handler(appeng.api.features.IWirelessTermRegistry registry, ItemStack stack) {
        return stack == null ? null : registry.getWirelessTerminalHandler(stack);
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

    private boolean allowed() {
        IInventory inventory = worn ? BaublesApi.getBaubles(player) : player.inventory;
        if (player.isDead || inventory == null
            || slot >= inventory.getSizeInventory()
            || inventory.getStackInSlot(slot) != device
            || !link.equals(handler.getEncryptionKey(device))
            || grid == null
            || (!handler.hasInfinityPower(device) && !handler.hasPower(player, 0.5, device))) return false;
        ItemStack held = player.inventory.getCurrentItem();
        if (held != device && handler(
            AEApi.instance()
                .registries()
                .wireless(),
            held) != null) return false;
        var node = wireless.getActionableNode();
        if (node == null || node.getGrid() != grid || !wireless.rangeCheck()) return false;
        IEnergyGrid energy = grid.getCache(IEnergyGrid.class);
        ISecurityGrid security = grid.getCache(ISecurityGrid.class);
        return energy != null && energy.isNetworkPowered()
            && security != null
            && (security.hasPermission(player, SecurityPermissions.EXTRACT)
                || security.hasPermission(player, SecurityPermissions.CRAFT));
    }

    public void edit(int index, boolean remove) {
        if (published == null || index < 0 || index >= pins.length || !allowed()) return;
        var list = new PinsHolder(device).getHandler(player);
        IAEStack<?> current = list.getPin(index);
        IAEStack<?> visible = published[index];
        ItemStack cursor = player.inventory.getItemStack();
        if (remove) {
            if (cursor != null || current == null || visible == null || !current.isSameType(visible)) return;
            list.setPin(index, null);
        } else {
            if (index < PinList.PLAYER_OFFSET || current != null
                || visible != null
                || cursor == null
                || cursor.stackSize <= 0) return;
            list.setPin(index, AEItemStack.create(cursor));
        }
        IInventory inventory = worn ? BaublesApi.getBaubles(player) : player.inventory;
        inventory.markDirty();
        nextUpdate = player.ticksExisted;
    }

    @SuppressWarnings("unchecked")
    public void transfer(Container container, int index, Transfer operation, int argument, int[] panel) {
        if (published == null || !(published[index] instanceof IAEItemStack visible) || !allowed()) return;
        var list = new PinsHolder(device).getHandler(player);
        if (!(list.getPin(index) instanceof IAEItemStack pin) || !pin.isSameType(visible)) return;
        ISecurityGrid security = grid.getCache(ISecurityGrid.class);
        if (!security.hasPermission(player, PinnedItemTransfer.requiredPermission(player, operation, argument))) return;
        var monitor = (IMEMonitor<IAEItemStack>) wireless.getMEMonitor(pin.getStackType());
        if (operation == Transfer.CRAFT) {
            int slotIndex = worn ? Platform.baublesSlotsOffset + slot : slot;
            var primary = new PrimaryGui(GuiBridge.GUI_ME, device.copy(), null, null);
            primary.setSlotIndex(slotIndex);
            PinnedItemTransfer.openCraftAmount(player, monitor, pin, null, null, slotIndex, primary);
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
        if (!allowed()) {
            send.accept(new Snapshot(epoch, null, 0, 0));
            return false;
        }
        int previousCrafting = craftingSlots, previousPlayer = playerSlots;
        refreshPins();
        if (!changed) return true;
        changed = false;
        IAEStack<?>[] next = snapshot();
        if (!sameSnapshot(published, next) || craftingSlots != previousCrafting || playerSlots != previousPlayer) {
            published = next;
            send.accept(new Snapshot(epoch, next, craftingSlots, playerSlots));
        }
        return true;
    }

    private void refreshPins() {
        NBTBase current = device.getTagCompound() == null ? null
            : device.getTagCompound()
                .getTag("pins");
        if (java.util.Objects.equals(current, savedPins)) return;
        savedPins = current == null ? null : current.copy();
        var list = new PinsHolder(device).getHandler(player);
        craftingSlots = list.getCraftingPinsRows()
            .getSlotCount();
        playerSlots = list.getPlayerPinsRows()
            .getSlotCount();
        Set<IMEMonitor<?>> sources = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        identities.clear();
        for (int i = 0; i < pins.length; i++) {
            IAEStack<?> pin = list.getPin(i);
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
