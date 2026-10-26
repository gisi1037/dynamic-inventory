package dev.gilbe.dynamicinventory.upgrades;

import java.util.List;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import dev.gilbe.dynamicinventory.DynamicInventory;

/** Reusable UHV controller. Possession authorizes a request; only the server can change capacity. */
public final class CreativeSizeItem extends Item {

    public CreativeSizeItem() {
        setUnlocalizedName("dynamicinventory.creative_inventory_controller");
        setTextureName(DynamicInventory.MODID + ":creative_inventory_controller");
        setMaxStackSize(1);
        setCreativeTab(CreativeTabs.tabMisc);
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (world.isRemote) DynamicInventory.proxy.openInventoryController();
        return stack;
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List lines, boolean advanced) {
        lines.add(net.minecraft.util.StatCollector.translateToLocal("dynamicinventory.controller.tooltip"));
        lines.add(net.minecraft.util.StatCollector.translateToLocal("dynamicinventory.controller.fixed"));
    }
}
