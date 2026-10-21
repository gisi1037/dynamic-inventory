package dev.gilbe.dynamicinventory.upgrades;

import java.util.List;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import dev.gilbe.dynamicinventory.DynamicInventory;

/** Right-click requests an upgrade; the server consumes it only after the resize commits. */
public final class CapacityModuleItem extends Item {

    public CapacityModuleItem() {
        setUnlocalizedName("dynamicinventory.capacity_module");
        setTextureName(DynamicInventory.MODID + ":capacity_module");
        setCreativeTab(CreativeTabs.tabMisc);
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (!world.isRemote && player instanceof EntityPlayerMP)
            InventoryUpgrades.requestModule((EntityPlayerMP) player, stack);
        return stack;
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List lines, boolean advanced) {
        lines.add(StatCollector.translateToLocal("dynamicinventory.module.tooltip"));
        lines.add(StatCollector.translateToLocal("dynamicinventory.upgrade.rules"));
    }
}
