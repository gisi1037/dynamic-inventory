package dev.gilbe.dynamicinventory.inventory;

import java.util.Arrays;
import java.util.List;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagList;

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
        InventoryPlayer inventory = player.inventory;
        InventoryShape before = InventoryLayout.of(inventory);
        if (storage == before.storageSlots()) {
            ((ExpandedInventoryAccess) player.inventory).dynamicinventory$setCapacityOverride(true);
            player.inventory.markDirty();
            return;
        }
        InventoryShape after = before.resized(storage);
        InventoryResizeProtocol.describe(player); // Refuse unknown persistent slot/container replacements.
        requireEmptyCursorAndCrafting(player);
        if (player.openContainer != player.inventoryContainer)
            PlayerContainerSettlement.closeAndKeep(player, player::closeScreen);
        requireEmptyCursorAndCrafting(player);
        // Closing a host can return items to the player. Pack the resulting contents, including those returns.
        ItemStack[] previousContents = inventory.mainInventory;
        if (InventoryLayout.of(inventory) != before || previousContents.length != before.rawMainSlots())
            throw new IllegalStateException("Inventory changed while closing the interface");
        ItemStack[] resized = Arrays.copyOf(previousContents, after.rawMainSlots());
        NBTTagList overflow = new NBTTagList();
        for (int raw = after.rawMainSlots(); raw < previousContents.length; raw++) {
            ItemStack stack = previousContents[raw];
            if (stack == null || stack.stackSize <= 0) continue;
            int remaining = pack(inventory, after, resized, stack);
            InventoryOverflowDrops.append(overflow, stack, remaining);
        }
        Container previousContainer = player.inventoryContainer;
        ExpandedInventoryAccess state = (ExpandedInventoryAccess) player.inventory;
        boolean previousOverride = state.dynamicinventory$hasCapacityOverride();
        try {
            player.inventory.mainInventory = resized;
            state.dynamicinventory$setShape(after);
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
        InventoryOverflowDrops.enqueue(inventory, overflow);
        ((ExpandedContainerAccess) previousContainer).dynamicinventory$removeListener(player);
        player.inventory.markDirty();
        ServerInventoryGate.of(player)
            .clear();
        ServerInventoryResizeGate.of(player)
            .synchronize(player);
        // After this point the server is committed. A failed peer agreement disconnects
        // and retains this canonical state for normal player saving, rather than undoing pickups.
    }

    private static int pack(InventoryPlayer inventory, InventoryShape after, ItemStack[] slots, ItemStack source) {
        int remaining = source.stackSize;
        for (boolean merge : new boolean[] { true, false }) {
            for (int ordinary = 0; ordinary < after.mainSlots() && remaining > 0; ordinary++) {
                int raw = after.rawIndex(ordinary);
                ItemStack target = slots[raw];
                if (merge ? !canMerge(target, source) : target != null) continue;
                if (!inventory.isItemValidForSlot(raw, source)) continue;
                int count = target == null ? 0 : target.stackSize;
                int limit = Math
                    .min(inventory.getInventoryStackLimit(), (target == null ? source : target).getMaxStackSize());
                int moved = Math.min(remaining, limit - count);
                if (moved <= 0) continue;
                // The old inventory remains an exact rollback snapshot until the container rebuild succeeds.
                ItemStack packed = (target == null ? source : target).copy();
                packed.stackSize = count + moved;
                slots[raw] = packed;
                remaining -= moved;
            }
        }
        return remaining;
    }

    private static boolean canMerge(ItemStack target, ItemStack source) {
        return target != null && target.getItem() == source.getItem()
            && target.isStackable()
            && target.getItemDamage() == source.getItemDamage()
            && ItemStack.areItemStackTagsEqual(target, source);
    }

    static void keepClosingDrops(InventoryPlayer inventory, List<EntityItem> drops) {
        if (drops.isEmpty()) return;
        InventoryShape shape = InventoryLayout.of(inventory);
        ItemStack[] packed = inventory.mainInventory.clone();
        NBTTagList overflow = new NBTTagList();
        for (EntityItem drop : drops) {
            ItemStack stack = drop.getEntityItem();
            if (stack != null && stack.stackSize > 0) {
                int remaining = pack(inventory, shape, packed, stack);
                InventoryOverflowDrops.append(overflow, stack, remaining);
            }
        }
        InventoryOverflowDrops.enqueue(inventory, overflow);
        inventory.mainInventory = packed;
        inventory.markDirty();
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
