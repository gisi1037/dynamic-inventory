package dev.gilbe.dynamicinventory.upgrades;

import java.util.List;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import dev.gilbe.dynamicinventory.DynamicInventory;

/** Save compatibility only. No recipe or catalog entry; existing stacks become Creative Belts in place. */
final class LegacyInventoryControllerItem extends Item {

    LegacyInventoryControllerItem() {
        setUnlocalizedName("dynamicinventory.inventory_belt_uhv");
        setTextureName(DynamicInventory.MODID + ":inventory_belt_uhv");
        setMaxStackSize(1);
    }

    private static void migrate(ItemStack stack) {
        Item replacement = InventoryUpgrades.creativeBelt();
        if (replacement != null) stack.func_150996_a(replacement);
    }

    @Override
    public void onUpdate(ItemStack stack, World world, Entity owner, int slot, boolean held) {
        migrate(stack);
    }

    @Override
    public boolean onEntityItemUpdate(EntityItem entity) {
        if (!entity.worldObj.isRemote) {
            ItemStack stack = entity.getEntityItem();
            migrate(stack);
            entity.setEntityItemStack(stack);
        }
        return false;
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        migrate(stack);
        return stack.getItem() == this ? stack
            : stack.getItem()
                .onItemRightClick(stack, world, player);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void getSubItems(Item item, CreativeTabs tab, List<ItemStack> items) {
        // NEI and creative catalogs must expose only the actual Creative Belt.
    }
}
