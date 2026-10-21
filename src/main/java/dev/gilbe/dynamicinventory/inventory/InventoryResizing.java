package dev.gilbe.dynamicinventory.inventory;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.item.ItemStack;

import dev.gilbe.dynamicinventory.api.InventoryUi;
import dev.gilbe.dynamicinventory.container.ExpandedContainerAccess;
import dev.gilbe.dynamicinventory.network.InventoryResizeProtocol;
import dev.gilbe.dynamicinventory.network.ServerInventoryGate;
import dev.gilbe.dynamicinventory.network.ServerInventoryResizeGate;

/** Server-authorized capacity changes. All methods run on the corresponding game's main thread. */
public final class InventoryResizing {

    private InventoryResizing() {}

    public static void resize(EntityPlayerMP player, int storage) {
        if (player.playerNetServerHandler == null || !InventoryUi.isOpeningReady(player))
            throw new IllegalStateException("Wait for inventory synchronization before resizing");
        InventoryShape before = InventoryLayout.of(player.inventory);
        InventoryResizePlan<ItemStack> preflight = new InventoryResizePlan<>(
            before,
            player.inventory.mainInventory,
            storage);
        if (storage == before.storageSlots()) {
            ((ExpandedInventoryAccess) player.inventory).dynamicinventory$setCapacityOverride(true);
            player.inventory.markDirty();
            return;
        }
        InventoryResizeProtocol.describe(player); // Refuse unknown persistent slot/container replacements.
        requireEmptyCursorAndCrafting(player);
        if (player.openContainer != player.inventoryContainer) player.closeScreen();
        requireEmptyCursorAndCrafting(player);
        // Closing a host can return items to the player. Recheck occupied tail cells NOW.
        ItemStack[] previousContents = player.inventory.mainInventory;
        ItemStack[] resized = preflight.copyContents(InventoryLayout.of(player.inventory), previousContents);
        Container previousContainer = player.inventoryContainer;
        ExpandedInventoryAccess state = (ExpandedInventoryAccess) player.inventory;
        boolean previousOverride = state.dynamicinventory$hasCapacityOverride();
        try {
            player.inventory.mainInventory = resized;
            state.dynamicinventory$setShape(preflight.after());
            state.dynamicinventory$setCapacityOverride(true);
            rebuild(player);
            InventoryResizeProtocol.describe(player);
        } catch (RuntimeException constructionFailure) {
            player.inventory.mainInventory = previousContents;
            state.dynamicinventory$setShape(before);
            state.dynamicinventory$setCapacityOverride(previousOverride);
            player.inventoryContainer = previousContainer;
            player.openContainer = previousContainer;
            throw constructionFailure;
        }
        ((ExpandedContainerAccess) previousContainer).dynamicinventory$removeListener(player);
        player.inventory.markDirty();
        ServerInventoryGate.of(player)
            .clear();
        ServerInventoryResizeGate.of(player)
            .synchronize(player);
        // After this point the server is committed. A failed peer agreement disconnects
        // and retains this canonical state for normal player saving, rather than undoing pickups.
    }

    public static void requireEmptyCursorAndCrafting(EntityPlayer player) {
        if (player.inventory.getItemStack() != null)
            throw new IllegalStateException("Put down the cursor stack before resizing");
        for (int id = 0; id < 5; id++) if (player.inventoryContainer.getSlot(id)
            .getHasStack()) throw new IllegalStateException("Clear the player's crafting grid before resizing");
    }

    /** Rebuild only the owned persistent container; never try to reconstruct an arbitrary external GUI. */
    public static void rebuild(EntityPlayer player) {
        if (player.inventoryContainer.getClass() != ContainerPlayer.class)
            throw new IllegalStateException("Custom persistent player container needs a resize integration");
        Container previous = player.inventoryContainer;
        Container replacement = new ContainerPlayer(player.inventory, !player.worldObj.isRemote, player);
        player.inventoryContainer = replacement;
        if (player.openContainer == previous) player.openContainer = replacement;
    }

    /** Respawn constructs an empty player before clonePlayer. Adopt capacity before its inventory copy. */
    public static void inheritCapacity(EntityPlayer target, EntityPlayer original) {
        InventoryShape shape = InventoryLayout.of(original.inventory);
        InventoryResizePlan<ItemStack> plan = new InventoryResizePlan<>(
            InventoryLayout.of(target.inventory),
            target.inventory.mainInventory,
            shape.storageSlots());
        target.inventory.mainInventory = plan.copyContents(plan.before(), target.inventory.mainInventory);
        ExpandedInventoryAccess state = (ExpandedInventoryAccess) target.inventory;
        state.dynamicinventory$setShape(shape);
        state.dynamicinventory$setCapacityOverride(
            ((ExpandedInventoryAccess) original.inventory).dynamicinventory$hasCapacityOverride());
    }
}
