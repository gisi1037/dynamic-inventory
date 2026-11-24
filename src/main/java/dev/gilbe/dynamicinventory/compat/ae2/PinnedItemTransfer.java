package dev.gilbe.dynamicinventory.compat.ae2;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.S2FPacketSetSlot;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import com.cleanroommc.bogosorter.api.IBogoSortAPI;

import appeng.api.config.Actionable;
import appeng.api.config.SecurityPermissions;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.ISecurityGrid;
import appeng.api.networking.security.PlayerSource;
import appeng.api.parts.ICraftingTerminal;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.ITerminalTypeFilterProvider;
import appeng.api.storage.data.IAEItemStack;
import appeng.container.AEBaseContainer;
import appeng.container.PrimaryGui;
import appeng.container.implementations.ContainerCraftAmount;
import appeng.container.implementations.ContainerMEMonitorable;
import appeng.container.slot.AppEngSlot;
import appeng.core.sync.GuiBridge;
import appeng.items.contents.PortableCellViewer;
import appeng.util.Platform;
import appeng.util.inv.AdaptorIInventory;
import appeng.util.inv.AdaptorPlayerInventory;
import appeng.util.item.AEItemStack;
import cpw.mods.fml.common.Loader;
import dev.gilbe.dynamicinventory.compat.mui2.PinsPlayerSlot;
import dev.gilbe.dynamicinventory.inventory.AutomaticInventoryRouting;
import dev.gilbe.dynamicinventory.inventory.HotbarBanks;
import dev.gilbe.dynamicinventory.inventory.InventoryLayout;
import dev.gilbe.dynamicinventory.network.AePinsChannel;
import dev.gilbe.dynamicinventory.network.AePinsChannel.Transfer;

public final class PinnedItemTransfer {

    private PinnedItemTransfer() {}

    public static void fromTerminal(EntityPlayerMP player, ContainerMEMonitorable terminal, int index,
        Transfer operation, int argument, byte[] expected) {
        if (!terminal.isPowered() || !(terminal.getActionSource() instanceof PlayerSource source)) return;
        IAEItemStack pin;
        if (operation == Transfer.DEPOSIT_TERMINAL_ALL) {
            if (argument >= terminal.inventorySlots.size()) return;
            Slot slot = terminal.getSlot(argument);
            if (playerIndex(player, slot) < 0 || !slot.canTakeStack(player)) return;
            pin = AEItemStack.create(slot.getStack());
        } else pin = terminal.getPin(index) instanceof IAEItemStack item ? item : null;
        if (!PinIdentity.matches(expected, pin)) return;
        if (terminal.getTarget() instanceof ITerminalTypeFilterProvider filter && !filter.getTypeFilter(player)
            .getBoolean(pin.getStackType())) return;
        var monitor = terminal.getItemMonitor();
        operation = resolveClick(operation, player.inventory.getItemStack(), monitor, pin);
        if (source.via == null) {
            // A Portable Cell owns its own powered inventory and has no ME grid or Security Terminal.
            if (operation != Transfer.DEPOSIT_TERMINAL_ALL || !(terminal.getTarget() instanceof PortableCellViewer))
                return;
        } else {
            var node = source.via.getActionableNode();
            if (node == null || node.getGrid() == null) return;
            ISecurityGrid security = node.getGrid()
                .getCache(ISecurityGrid.class);
            if (security == null || !security.hasPermission(player, requiredPermission(player, operation, argument)))
                return;
        }
        if (operation == Transfer.CRAFT) {
            var context = terminal.getOpenContext();
            if (context != null) openCraftAmount(
                player,
                monitor,
                pin,
                context.getTile(),
                context.getSide(),
                terminal.getTargetSlotIndex(),
                terminal.createPrimaryGui(),
                null);
            return;
        }
        apply(player, terminal, pin, operation, argument, terminal.getPowerSource(), monitor, source, null, null);
    }

    public static void moveNativePanel(EntityPlayerMP player, AEBaseContainer container, int[] slots) {
        for (int id : slots) {
            Slot slot = container.getSlot(id);
            if (slot instanceof AppEngSlot ae && ae.isPlayerSide()
                && slot.getHasStack()
                && slot.canTakeStack(player)
                && !bogoPinned(player, container, slot)) container.transferStackInSlot(player, id);
        }
    }

    public static boolean bogoPinned(EntityPlayer player, Container container, Slot slot) {
        return Platform.isBogoLoaded && IBogoSortAPI.getInstance()
            .isPinned(player, container, slot);
    }

    static SecurityPermissions requiredPermission(EntityPlayerMP player, Transfer operation, int argument) {
        if (operation == Transfer.CRAFT) return SecurityPermissions.CRAFT;
        boolean insert = switch (operation) {
            case SCROLL_CURSOR -> argument > 0;
            case SCROLL_ITEMS -> argument < 0;
            case PICKUP, SPLIT -> player.inventory.getItemStack() != null;
            case DEPOSIT_SLOT, SCROLL_DEPOSIT_SLOT, DEPOSIT_ALL, DEPOSIT_TERMINAL_ALL, SPACE_PANEL -> true;
            case STACK, ALL, CRAFT, SCROLL_WITHDRAW_SLOT -> false;
        };
        return insert ? SecurityPermissions.INJECT : SecurityPermissions.EXTRACT;
    }

    static Transfer resolveClick(Transfer action, ItemStack cursor, IMEMonitor<IAEItemStack> monitor,
        IAEItemStack pin) {
        if (monitor == null || cursor != null || (action != Transfer.PICKUP && action != Transfer.SPLIT)) return action;
        // The cached pin may have shown stock that was just withdrawn or missed by a background poll.
        var live = monitor.getStorageList()
            .findPrecise(pin);
        return live != null && live.isCraftable() && live.getStackSize() <= 0 ? Transfer.CRAFT : action;
    }

    static boolean apply(EntityPlayerMP player, Container container, IAEItemStack pin, Transfer operation, int argument,
        IEnergySource power, IMEMonitor<IAEItemStack> monitor, BaseActionSource source, ItemStack protectedItem,
        int[] panel) {
        if (power == null || monitor == null) return false;
        int maxStack = pin.getItemStack()
            .getMaxStackSize();
        ItemStack cursor = player.inventory.getItemStack();
        return switch (operation) {
            case SCROLL_ITEMS -> argument > 0 ? withdraw(player, pin, argument, power, monitor, source)
                : returnItems(player, container, pin, -argument, power, monitor, source, protectedItem);
            case STACK -> withdraw(player, pin, maxStack, power, monitor, source);
            case ALL -> withdraw(
                player,
                pin,
                InventoryLayout.of(player.inventory)
                    .mainSlots() * maxStack,
                power,
                monitor,
                source);
            case DEPOSIT_SLOT, SCROLL_DEPOSIT_SLOT -> {
                int slot = operation == Transfer.DEPOSIT_SLOT ? argument : AePinsChannel.scrollSlot(argument);
                int amount = operation == Transfer.DEPOSIT_SLOT ? Integer.MAX_VALUE
                    : AePinsChannel.scrollAmount(argument);
                yield cursor == null && slot < container.inventorySlots.size()
                    && returnFromSlot(
                        player,
                        container.getSlot(slot),
                        pin,
                        amount,
                        power,
                        monitor,
                        source,
                        protectedItem) > 0;
            }
            case SCROLL_WITHDRAW_SLOT -> cursor == null
                && withdrawToSlot(player, container, pin, argument, power, monitor, source);
            case DEPOSIT_ALL, DEPOSIT_TERMINAL_ALL -> cursor == null && argument < container.inventorySlots.size()
                && playerIndex(player, container.getSlot(argument)) >= 0
                && container.getSlot(argument)
                    .canTakeStack(player)
                && pin.isSameType(
                    container.getSlot(argument)
                        .getStack())
                && returnItems(player, container, pin, Integer.MAX_VALUE, power, monitor, source, protectedItem);
            case SPACE_PANEL -> cursor == null && panel != null
                && pin.isSameType(
                    container.getSlot(argument)
                        .getStack())
                && returnPanel(player, container, pin, panel, power, monitor, source, protectedItem);
            case SCROLL_CURSOR, PICKUP, SPLIT -> cursorTransfer(
                player,
                pin,
                operation,
                argument,
                power,
                monitor,
                source,
                protectedItem,
                maxStack,
                cursor);
            case CRAFT -> false;
        };
    }

    private static boolean returnPanel(EntityPlayerMP player, Container container, IAEItemStack pin, int[] panel,
        IEnergySource power, IMEMonitor<IAEItemStack> monitor, BaseActionSource source, ItemStack protectedItem) {
        boolean moved = false;
        for (int id : panel) {
            Slot slot = container.getSlot(id);
            if (!bogoPinned(player, container, slot))
                moved |= returnFromSlot(player, slot, pin, Integer.MAX_VALUE, power, monitor, source, protectedItem)
                    > 0;
        }
        return moved;
    }

    static void openCraftAmount(EntityPlayerMP player, IMEMonitor<IAEItemStack> monitor, IAEItemStack pin,
        TileEntity tile, ForgeDirection side, int slotIndex, PrimaryGui primary, ItemStack wirelessDevice) {
        if (monitor == null || player.inventory.getItemStack() != null) return;
        var craftable = monitor.getStorageList()
            .findPrecise(pin);
        if (craftable == null || !craftable.isCraftable()) return;
        Platform.openGUI(player, tile, side, GuiBridge.GUI_CRAFTING_AMOUNT, slotIndex);
        if (player.openContainer instanceof ContainerCraftAmount amount) {
            if (wirelessDevice != null) {
                GuiBridge bridge = amount.getTarget() instanceof ICraftingTerminal ? GuiBridge.GUI_CRAFTING_TERMINAL
                    : GuiBridge.GUI_ME;
                primary = new PrimaryGui(bridge, wirelessDevice.copy(), null, ForgeDirection.UNKNOWN);
                primary.setSlotIndex(slotIndex);
            }
            if (primary != null) amount.setPrimaryGui(primary);
            amount.setItemToCraft(craftable.copy());
            amount.setInitialCraftAmount(1);
            amount.detectAndSendChanges();
        }
    }

    private static boolean withdraw(EntityPlayerMP player, IAEItemStack pin, int amount, IEnergySource power,
        IMEMonitor<IAEItemStack> monitor, BaseActionSource source) {
        // AE2's player adapter includes expanded slots and visits the active hotbar first.
        var inventory = new AdaptorIInventory(new AdaptorPlayerInventory(player.inventory, false));
        ItemStack requested = pin.getItemStack();
        requested.stackSize = amount;
        ItemStack excess = inventory.simulateAdd(requested);
        int space = amount - (excess == null ? 0 : excess.stackSize);
        if (space <= 0) return false;
        IAEItemStack extracted = Platform.poweredExtraction(
            power,
            monitor,
            pin.copy()
                .setStackSize(space),
            source);
        if (extracted == null || extracted.getStackSize() <= 0) return false;
        ItemStack leftover = inventory.addItems(extracted.getItemStack());
        if (leftover != null) {
            IAEItemStack returned = monitor.injectItems(AEItemStack.create(leftover), Actionable.MODULATE, source);
            if (returned != null && returned.getStackSize() > 0) Platform.handleLeftover(player, returned);
        }
        return true;
    }

    private static boolean withdrawToSlot(EntityPlayerMP player, Container container, IAEItemStack pin, int argument,
        IEnergySource power, IMEMonitor<IAEItemStack> monitor, BaseActionSource source) {
        int id = AePinsChannel.scrollSlot(argument);
        if (id >= container.inventorySlots.size()) return false;
        Slot slot = container.getSlot(id);
        if (playerIndex(player, slot) < 0
            || dev.gilbe.dynamicinventory.inventory.InventorySlotLocks.locked(slot, player)) return false;
        ItemStack existing = slot.getStack();
        int amount = Math.min(AePinsChannel.scrollAmount(argument), scrollPullRoom(slot, existing, pin));
        if (amount <= 0) return false;
        IAEItemStack extracted = Platform.poweredExtraction(
            power,
            monitor,
            pin.copy()
                .setStackSize(amount),
            source);
        if (extracted == null || extracted.getStackSize() <= 0) return false;
        ItemStack merged = existing.copy();
        merged.stackSize += (int) extracted.getStackSize();
        slot.putStack(merged);
        return true;
    }

    public static int scrollPullRoom(Slot slot, ItemStack existing, IAEItemStack pin) {
        if (existing == null || !pin.isSameType(existing) || !slot.isItemValid(existing)) return 0;
        return Math.max(0, Math.min(slot.getSlotStackLimit(), existing.getMaxStackSize()) - existing.stackSize);
    }

    private static boolean cursorTransfer(EntityPlayerMP player, IAEItemStack pin, Transfer operation, int argument,
        IEnergySource power, IMEMonitor<IAEItemStack> monitor, BaseActionSource source, ItemStack protectedItem,
        int maxStack, ItemStack cursor) {
        if (operation == Transfer.SCROLL_CURSOR ? argument > 0 : cursor != null) {
            if (cursor == null || cursor == protectedItem
                || operation != Transfer.SCROLL_CURSOR && !pin.isSameType(cursor)) return false;
            int amount = switch (operation) {
                case SCROLL_CURSOR -> argument;
                case SPLIT -> 1;
                default -> cursor.stackSize;
            };
            int accepted = insert(cursor, Math.min(amount, cursor.stackSize), power, monitor, source);
            if (accepted == 0) return false;
            cursor.stackSize -= accepted;
            if (cursor.stackSize == 0) player.inventory.setItemStack(null);
        } else {
            if (cursor != null && !pin.isSameType(cursor)) return false;
            int count = operation == Transfer.SCROLL_CURSOR
                ? Math.min(-argument, maxStack - (cursor == null ? 0 : cursor.stackSize))
                : maxStack;
            if (count <= 0) return false;
            IAEItemStack request = pin.copy()
                .setStackSize(count);
            if (operation == Transfer.SPLIT) {
                var available = monitor.extractItems(request, Actionable.SIMULATE, source);
                if (available == null || available.getStackSize() <= 0) return false;
                request.setStackSize((Math.min(count, available.getStackSize()) + 1) / 2);
            }
            var extracted = Platform.poweredExtraction(power, monitor, request, source);
            if (extracted == null || extracted.getStackSize() <= 0) return false;
            if (cursor == null) player.inventory.setItemStack(extracted.getItemStack());
            else cursor.stackSize += (int) extracted.getStackSize();
        }
        player.playerNetServerHandler.sendPacket(new S2FPacketSetSlot(-1, -1, player.inventory.getItemStack()));
        return true;
    }

    private static boolean returnItems(EntityPlayerMP player, Container container, IAEItemStack pin, int amount,
        IEnergySource power, IMEMonitor<IAEItemStack> monitor, BaseActionSource source, ItemStack protectedItem) {
        Slot[] ordered = sourceSlots(player, container, amount != Integer.MAX_VALUE);
        int remaining = amount;
        for (Slot slot : ordered) {
            if (slot != null)
                remaining -= returnFromSlot(player, slot, pin, remaining, power, monitor, source, protectedItem);
            if (remaining == 0) break;
        }
        return remaining < amount;
    }

    public static Slot[] sourceSlots(EntityPlayer player, Container container, boolean mainOnly) {
        Slot[] ordered = new Slot[InventoryLayout.of(player.inventory)
            .mainSlots()];
        for (Slot slot : container.inventorySlots) {
            int index = playerIndex(player, slot);
            if (index >= 0 && (!mainOnly || AutomaticInventoryRouting.transfer(player.inventory, slot.getSlotIndex())))
                ordered[index] = slot;
        }
        return ordered;
    }

    private static int returnFromSlot(EntityPlayerMP player, Slot slot, IAEItemStack pin, int amount,
        IEnergySource power, IMEMonitor<IAEItemStack> monitor, BaseActionSource source, ItemStack protectedItem) {
        if (playerIndex(player, slot) < 0 || !slot.canTakeStack(player)
            || dev.gilbe.dynamicinventory.inventory.InventorySlotLocks.locked(slot, player)) return 0;
        ItemStack stack = slot.getStack();
        if (stack == null || stack == protectedItem || !pin.isSameType(stack)) return 0;
        int accepted = insert(stack, Math.min(amount, stack.stackSize), power, monitor, source);
        if (accepted > 0) {
            slot.onPickupFromSlot(player, slot.decrStackSize(accepted));
            player.inventory.markDirty();
        }
        return accepted;
    }

    private static int insert(ItemStack stack, int amount, IEnergySource power, IMEMonitor<IAEItemStack> monitor,
        BaseActionSource source) {
        if (amount <= 0) return 0;
        var remainder = Platform.poweredInsert(
            power,
            monitor,
            AEItemStack.create(stack)
                .setStackSize(amount),
            source);
        return amount - (remainder == null ? 0 : (int) remainder.getStackSize());
    }

    public static int playerIndex(EntityPlayer player, Slot slot) {
        if (slot.inventory != player.inventory
            && (!Loader.isModLoaded("modularui2") || !PinsPlayerSlot.belongsTo(slot, player.inventory))) return -1;
        int raw = slot.getSlotIndex();
        return HotbarBanks.isReserved(player.inventory, raw) ? -1
            : InventoryLayout.of(player.inventory)
                .ordinaryIndex(raw);
    }
}
