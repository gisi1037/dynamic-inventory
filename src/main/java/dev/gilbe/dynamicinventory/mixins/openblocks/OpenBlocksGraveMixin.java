package dev.gilbe.dynamicinventory.mixins.openblocks;

import net.minecraft.nbt.NBTTagCompound;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

import dev.gilbe.dynamicinventory.DynamicInventory;
import dev.gilbe.dynamicinventory.compat.openblocks.GraveInventory;
import dev.gilbe.dynamicinventory.compat.openblocks.GraveSlotCodec;
import openmods.inventory.GenericInventory;

/** Small bridge that can be removed when OpenBlocks/OpenMods owns the integer-slot fix. */
@Mixin(targets = "openblocks.common.tileentity.TileEntityGrave", remap = false)
public abstract class OpenBlocksGraveMixin {

    @Redirect(
        method = "<init>",
        at = @At(value = "NEW", target = "openmods/inventory/GenericInventory"),
        require = 1,
        expect = 1,
        allow = 1)
    private GenericInventory dynamicinventory$graveInventory(String title, boolean localized, int size) {
        // Constructor callback registration and all native grave behavior are preserved.
        return new GraveInventory(title, localized, size);
    }

    @ModifyVariable(method = "readFromNBT", at = @At("HEAD"), argsOnly = true, remap = true, require = 1)
    private NBTTagCompound dynamicinventory$prepareSlots(NBTTagCompound tag) {
        NBTTagCompound prepared = GraveSlotCodec.prepareLoad(tag);
        if (prepared != tag) DynamicInventory.LOG.warn(
            "Repacked ambiguous OpenBlocks grave slot IDs; preserved item records but discarded uncertain origin hints.");
        return prepared;
    }
}
