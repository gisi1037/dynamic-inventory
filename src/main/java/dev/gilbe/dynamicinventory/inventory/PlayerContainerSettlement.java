package dev.gilbe.dynamicinventory.inventory;

import java.util.List;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;

/** Preserve items that native containers return when closed for death or a capacity change. */
public final class PlayerContainerSettlement {

    private static final ThreadLocal<EntityPlayerMP> CLOSING = new ThreadLocal<>();

    private PlayerContainerSettlement() {}

    public static boolean isClosing(EntityPlayer player) {
        return CLOSING.get() == player;
    }

    public static void closeForDeath(EntityPlayerMP player, boolean keepInventory) {
        Runnable close = () -> {
            Container active = player.openContainer;
            player.closeContainer();
            if (active != player.inventoryContainer) player.inventoryContainer.onContainerClosed(player);
        };
        if (keepInventory) closeAndKeep(player, close);
        else captureTosses(player, close);
    }

    private static void captureTosses(EntityPlayerMP player, Runnable close) {
        EntityPlayerMP previous = CLOSING.get();
        CLOSING.set(player);
        try {
            close.run();
        } finally {
            if (previous == null) CLOSING.remove();
            else CLOSING.set(previous);
        }
    }

    static void closeAndKeep(EntityPlayerMP player, Runnable close) {
        boolean wasCapturing = player.captureDrops;
        List<EntityItem> captured = player.capturedDrops;
        int start = captured.size();
        player.captureDrops = true;
        try {
            captureTosses(player, close);
        } finally {
            player.captureDrops = wasCapturing;
            List<EntityItem> closingDrops = captured.subList(start, captured.size());
            InventoryResizing.keepClosingDrops(player.inventory, closingDrops);
            closingDrops.clear();
        }
    }
}
