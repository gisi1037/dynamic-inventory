package dev.gilbe.dynamicinventory.upgrades;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.registry.GameRegistry;
import dev.gilbe.dynamicinventory.api.InventoryUi;
import dev.gilbe.dynamicinventory.inventory.InventoryLayout;
import dev.gilbe.dynamicinventory.inventory.InventoryResizePlan;
import dev.gilbe.dynamicinventory.inventory.InventoryResizing;
import dev.gilbe.dynamicinventory.inventory.InventoryShape;

/**
 * Permanent base capacity and temporary equipment bonuses have separate ownership.
 * Forge's PlayerPersisted tag keeps the base and consumed count through death and login.
 * Polling at END avoids rebuilding a container inside a slot click or Baubles callback.
 */
public final class InventoryUpgrades {

    private static final String TAG = "dynamicinventoryUpgrades";
    private static final Map<EntityPlayerMP, ItemStack> REQUESTS = new WeakHashMap<>();
    private static final Map<EntityPlayerMP, Integer> WARNED_TARGETS = new WeakHashMap<>();
    public static Item module;
    public static Item belt;
    public static Item[] belts = new Item[0];

    public static void register() {
        module = new CapacityModuleItem();
        GameRegistry.registerItem(module, "capacity_module");
        // Optional API classes are loaded only if Baubles is actually present.
        if (Loader.isModLoaded("Baubles")) {
            belts = BaubleUpgradeSupport.register();
            belt = belts[0];
        }
        // Keep a distinct hidden entry: older worlds contain BOTH this ID and the UHV belt ID.
        // Remapping both names onto one registered item makes Forge reject the world ID map.
        GameRegistry.registerItem(new LegacyInventoryControllerItem(), "creative_inventory_controller");
        CreativeSizeRequest.initialize();
    }

    /** The UHV belt supplies the size editor as well as its normal equipment bonus. */
    public static Item creativeBelt() {
        return belts.length == UpgradeSettings.BELT_TIERS.length ? belts[belts.length - 1] : null;
    }

    private static NBTTagCompound account(EntityPlayer player, boolean create) {
        NBTTagCompound data = player.getEntityData();
        NBTTagCompound persisted = data.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        if (!persisted.hasKey(TAG, 10)) {
            if (!create) return null;
            NBTTagCompound initial = new NBTTagCompound();
            initial.setInteger(
                "baseSlots",
                InventoryLayout.of(player.inventory)
                    .storageSlots());
            initial.setInteger("modulesUsed", 0);
            persisted.setTag(TAG, initial);
            data.setTag(EntityPlayer.PERSISTED_NBT_TAG, persisted);
        }
        NBTTagCompound result = persisted.getCompoundTag(TAG);
        // Corrupt metadata must never silently grant capacity or discard stored items.
        if (!result.hasKey("baseSlots", 3) || !result.hasKey("modulesUsed", 3)
            || result.getInteger("baseSlots") < InventoryShape.MIN_STORAGE
            || result.getInteger("baseSlots") > InventoryShape.MAX_STORAGE
            || result.getInteger("modulesUsed") < 0)
            throw new IllegalStateException("Invalid saved inventory upgrade metadata");
        return result;
    }

    private static int bonus(EntityPlayer player) {
        NBTTagCompound saved = account(player, false);
        if (saved != null && saved.getBoolean("fixedCapacity")) return 0;
        return belt != null && UpgradeSettings.beltsEnabled ? BaubleUpgradeSupport.bonus(player) : 0;
    }

    public static void requestModule(EntityPlayerMP player, ItemStack stack) {
        // Vanilla copies the held stack after onItemRightClick returns. Capture its value,
        // not object identity; the game-thread request still consumes the actual current stack.
        REQUESTS.put(player, stack.copy());
    }

    /** Operator command keeps its existing meaning: set TOTAL storage, including any equipped bonus. */
    public static void setCapacity(EntityPlayerMP player, int total) {
        int equipment = bonus(player);
        int base = total - equipment;
        UpgradeCapacity.effective(base, equipment); // Refuse totals too small to accommodate the equipped bonus.
        NBTTagCompound saved = account(player, false);
        InventoryShape before = InventoryLayout.of(player.inventory);
        boolean completed = false;
        try {
            InventoryResizing.resize(player, total);
            completed = true;
        } finally {
            // A transport failure AFTER publication still leaves a committed canonical shape.
            // Record its base before normal saving/disconnection; never restore an old bonus on login.
            if ((saved != null || equipment > 0) && (completed || InventoryLayout.of(player.inventory) != before)) {
                account(player, true).setInteger("baseSlots", base);
                if (belt != null) BaubleUpgradeSupport.synchronizeBase(player, base);
            }
        }
    }

    /** Controller totals are independent of equipment. Zero restores ordinary equipment rules. */
    public static void setControlledCapacity(EntityPlayerMP player, int target) {
        if (target == 0) {
            if (!InventoryUi.isOpeningReady(player))
                throw new IllegalStateException("Wait for inventory synchronization");
            NBTTagCompound saved = account(player, false);
            if (saved != null) saved.setBoolean("fixedCapacity", false);
            return;
        }
        InventoryShape before = InventoryLayout.of(player.inventory);
        boolean completed = false;
        try {
            InventoryResizing.resize(player, target);
            completed = true;
        } finally {
            if (completed || InventoryLayout.of(player.inventory) != before) {
                NBTTagCompound saved = account(player, true);
                saved.setInteger("baseSlots", target);
                saved.setBoolean("fixedCapacity", true);
                if (belt != null) BaubleUpgradeSupport.synchronizeBase(player, target);
            }
        }
    }

    /** Normal unequip is refused before moving a belt whose extra cells still contain items. */
    public static boolean canRemoveBelt(EntityPlayer player) {
        if (player.worldObj.isRemote) return true; // The server owns rules and saved upgrade metadata.
        try {
            NBTTagCompound saved = account(player, false);
            if (saved == null) return true; // No applied bonus yet.
            new InventoryResizePlan<>(
                InventoryLayout.of(player.inventory),
                player.inventory.mainInventory,
                saved.getInteger("baseSlots"));
            return true;
        } catch (IllegalArgumentException | IllegalStateException occupiedOrInvalid) {
            return false;
        }
    }

    @SubscribeEvent
    public void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        if (player.isDead || player.getHealth() <= 0 || player.playerNetServerHandler == null) return;
        CreativeSizeRequest.applyPending(player);
        ItemStack request = REQUESTS.remove(player);
        // Gear polling is cheap and bounded; unchanged capacity never rebuilds containers or sends packets.
        if (request == null && player.ticksExisted % 10 != 0) return;
        if (!InventoryUi.isOpeningReady(player)) {
            if (request != null) tell(player, "dynamicinventory.upgrade.busy");
            return;
        }
        try {
            int equipment = bonus(player);
            NBTTagCompound saved = account(player, equipment > 0);
            if (saved != null && belt != null)
                BaubleUpgradeSupport.synchronizeBase(player, saved.getInteger("baseSlots"));
            if (request != null) {
                if (!UpgradeSettings.modulesEnabled) throw new IllegalStateException("Permanent upgrades are disabled");
                ItemStack held = player.inventory.getCurrentItem();
                if (!ItemStack.areItemStacksEqual(held, request) || held.getItem() != module || held.stackSize < 1)
                    return;
                if (player.openContainer != player.inventoryContainer)
                    throw new IllegalStateException("Close other interfaces before using a capacity module");
                request = held;
                // A refused first purchase must not create permanent ownership metadata.
                // Otherwise a later startup-default change could be mistaken for a gear shrink.
                int base = saved == null ? InventoryLayout.of(player.inventory)
                    .storageSlots() : saved.getInteger("baseSlots");
                int used = saved == null ? 0 : saved.getInteger("modulesUsed");
                int nextBase = UpgradeCapacity
                    .upgradedBase(base, used, UpgradeSettings.moduleSlots, UpgradeSettings.moduleLimit);
                int target = UpgradeCapacity.effective(nextBase, equipment);
                if (target == InventoryLayout.of(player.inventory)
                    .storageSlots()) throw new IllegalStateException("This upgrade would not add any usable slots");
                InventoryShape before = InventoryLayout.of(player.inventory);
                try {
                    InventoryResizing.resize(player, target);
                } finally {
                    // Payment follows canonical publication, including the rare case where sending
                    // the subsequent peer handshake fails. Preflight/rebuild failure consumes nothing.
                    if (InventoryLayout.of(player.inventory) != before) {
                        saved = account(player, true);
                        saved.setInteger("baseSlots", nextBase);
                        saved.setInteger("modulesUsed", used + 1);
                        if (belt != null) BaubleUpgradeSupport.synchronizeBase(player, nextBase);
                        if (--request.stackSize == 0)
                            player.inventory.setInventorySlotContents(player.inventory.currentItem, null);
                        player.inventory.markDirty();
                    }
                }
                tell(player, "dynamicinventory.upgrade.applied", target);
            } else if (saved != null) {
                int target = UpgradeCapacity.effective(saved.getInteger("baseSlots"), equipment);
                if (target != InventoryLayout.of(player.inventory)
                    .storageSlots()) InventoryResizing.resize(player, target);
            }
            WARNED_TARGETS.remove(player);
        } catch (IllegalArgumentException | IllegalStateException refused) {
            // Forced gear removal or changed configuration must never delete an occupied tail.
            // Retain the current shape, warn once, and retry after the player clears the cells/cursor/grid.
            int current = InventoryLayout.of(player.inventory)
                .storageSlots();
            if (request != null || !Integer.valueOf(current)
                .equals(WARNED_TARGETS.put(player, current)))
                tell(player, "dynamicinventory.upgrade.refused", refused.getMessage());
        }
    }

    private static void tell(EntityPlayer player, String key, Object... args) {
        player.addChatMessage(new ChatComponentTranslation(key, args));
    }
}
