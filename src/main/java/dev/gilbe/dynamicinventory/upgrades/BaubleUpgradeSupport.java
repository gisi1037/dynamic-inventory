package dev.gilbe.dynamicinventory.upgrades;

import java.util.List;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import baubles.api.BaubleType;
import baubles.api.BaublesApi;
import baubles.api.IBauble;
import cpw.mods.fml.common.registry.GameRegistry;
import dev.gilbe.dynamicinventory.DynamicInventory;

/** Optional Baubles code stays behind the mod-presence guard in InventoryUpgrades. */
final class BaubleUpgradeSupport extends Item implements IBauble {

    // Native item NBT supplies prediction only. The server ignores this tag when granting capacity.
    private static final String BASE_TAG = "dynamicinventoryBaseSlots";
    private static final String ENABLED_TAG = "dynamicinventoryBeltEnabled";

    static Item register() {
        Item belt = new BaubleUpgradeSupport();
        GameRegistry.registerItem(belt, "inventory_belt");
        return belt;
    }

    private BaubleUpgradeSupport() {
        setUnlocalizedName("dynamicinventory.inventory_belt");
        setTextureName(DynamicInventory.MODID + ":inventory_belt");
        setMaxStackSize(1);
        setCreativeTab(CreativeTabs.tabMisc);
    }

    static boolean isWearing(EntityPlayer player) {
        IInventory equipment = BaublesApi.getBaubles(player);
        if (equipment == null) return false;
        for (int i = 0; i < equipment.getSizeInventory(); i++) {
            ItemStack stack = equipment.getStackInSlot(i);
            if (stack != null && stack.stackSize > 0 && stack.getItem() == InventoryUpgrades.belt) return true;
        }
        return false;
    }

    static void synchronizeBase(EntityPlayer player, int base) {
        IInventory equipment = BaublesApi.getBaubles(player);
        if (equipment == null) return;
        for (int i = 0; i < equipment.getSizeInventory(); i++) {
            ItemStack stack = equipment.getStackInSlot(i);
            if (stack == null || stack.getItem() != InventoryUpgrades.belt) continue;
            if (stack.hasTagCompound() && stack.getTagCompound()
                .getInteger(BASE_TAG) == base) continue;
            if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
            stack.getTagCompound()
                .setInteger(BASE_TAG, base);
            equipment.markDirty();
        }
    }

    @Override
    public BaubleType getBaubleType(ItemStack stack) {
        return BaubleType.BELT;
    }

    @Override
    public boolean canEquip(ItemStack stack, EntityLivingBase actor) {
        boolean enabled = actor.worldObj.isRemote ? stack.hasTagCompound() && stack.getTagCompound()
            .getBoolean(ENABLED_TAG) : UpgradeSettings.beltsEnabled;
        return actor instanceof EntityPlayer && enabled && !isWearing((EntityPlayer) actor);
    }

    @Override
    public void onUpdate(ItemStack stack, World world, Entity owner, int slot, boolean held) {
        if (world.isRemote) return;
        // Ordinary item synchronization carries server enablement before client-side equip prediction.
        // A newly received belt waits one tick for this value instead of predicting from local config.
        if (stack.hasTagCompound() && stack.getTagCompound()
            .hasKey(ENABLED_TAG, 1)
            && stack.getTagCompound()
                .getBoolean(ENABLED_TAG) == UpgradeSettings.beltsEnabled)
            return;
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound()
            .setBoolean(ENABLED_TAG, UpgradeSettings.beltsEnabled);
    }

    @Override
    public boolean canUnequip(ItemStack stack, EntityLivingBase actor) {
        if (actor instanceof EntityPlayer && actor.worldObj.isRemote) {
            // Wait for the authoritative base. Client-local bonus settings can differ from the server's.
            if (!stack.hasTagCompound() || !stack.getTagCompound()
                .hasKey(BASE_TAG, 3)) return false;
            try {
                new dev.gilbe.dynamicinventory.inventory.InventoryResizePlan<>(
                    dev.gilbe.dynamicinventory.inventory.InventoryLayout.of(((EntityPlayer) actor).inventory),
                    ((EntityPlayer) actor).inventory.mainInventory,
                    stack.getTagCompound()
                        .getInteger(BASE_TAG));
                return true;
            } catch (IllegalArgumentException | IllegalStateException refused) {
                return false;
            }
        }
        return actor instanceof EntityPlayer && InventoryUpgrades.canRemoveBelt((EntityPlayer) actor);
    }

    // END-tick reconciliation owns resizing; callbacks may run in the middle of a native slot operation.
    @Override
    public void onWornTick(ItemStack stack, EntityLivingBase actor) {}

    @Override
    public void onEquipped(ItemStack stack, EntityLivingBase actor) {}

    @Override
    public void onUnequipped(ItemStack stack, EntityLivingBase actor) {}

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List lines, boolean advanced) {
        lines.add(StatCollector.translateToLocal("dynamicinventory.belt.tooltip"));
        lines.add(StatCollector.translateToLocal("dynamicinventory.belt.remove"));
        lines.add(StatCollector.translateToLocal("dynamicinventory.upgrade.rules"));
    }
}
