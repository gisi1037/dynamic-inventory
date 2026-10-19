package dev.gilbe.dynamicinventory.qualification;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

public final class TickProbeItem extends Item {

    @Override
    public void onUpdate(ItemStack stack, World world, Entity entity, int index, boolean selected) {
        if (entity instanceof EntityPlayer) TickMeasurements.update((EntityPlayer) entity, index, selected);
    }
}
