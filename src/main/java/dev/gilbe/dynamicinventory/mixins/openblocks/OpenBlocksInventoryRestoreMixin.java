package dev.gilbe.dynamicinventory.mixins.openblocks;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import dev.gilbe.dynamicinventory.DynamicInventory;

/** OpenBlocks dumps contain raw indices, not the capacity/role metadata in our save codec. */
@Pseudo
@Mixin(targets = "openblocks.common.PlayerInventoryStore", remap = false)
public abstract class OpenBlocksInventoryRestoreMixin {

    @Redirect(
        method = "restoreInventory(Lnet/minecraft/entity/player/EntityPlayer;Ljava/lang/String;)Z",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/inventory/IInventory;getSizeInventory()I", remap = true),
        remap = false,
        require = 1,
        expect = 1,
        allow = 1)
    private int dynamicinventory$checkBackupSize(IInventory backup, EntityPlayer player, String name) {
        int stored = backup.getSizeInventory();
        int current = player.inventory.getSizeInventory();
        // This query precedes the native copy loop, overflow drops and equipment Load event.
        // Reject both smaller and larger dumps: old armor offsets can now denote storage.
        // A matching count permits the native restore; it does not migrate an unknown layout.
        if (stored != current) throw dynamicinventory$refuseRestore(
            "Cannot restore OpenBlocks backup '" + name
                + "': inventory size "
                + stored
                + " differs from "
                + current
                + ". Restore using the original inventory capacity and export items first.");
        // Pinned OpenMods reads each saved Slot byte as signed: indices >=128 disappear
        // while loading the temporary inventory. Do not copy that incomplete view over a player.
        // Keep native dump creation (including death events) untouched; the original file survives.
        if (stored > 128) throw dynamicinventory$refuseRestore(
            "Cannot restore OpenBlocks backup '" + name + "': more than 128 raw inventory slots are unsupported.");
        return stored;
    }

    @Unique
    private static IllegalArgumentException dynamicinventory$refuseRestore(String reason) {
        // OpenBlocks' command catches restore exceptions and only reports a generic failure.
        // Retain the actionable reason and exact dump name for server administrators.
        DynamicInventory.LOG.warn(reason);
        return new IllegalArgumentException(reason);
    }
}
