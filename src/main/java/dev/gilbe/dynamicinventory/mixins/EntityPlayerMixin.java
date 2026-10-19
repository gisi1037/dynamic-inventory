package dev.gilbe.dynamicinventory.mixins;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.gilbe.dynamicinventory.inventory.ExpandedInventoryAccess;
import dev.gilbe.dynamicinventory.inventory.InventoryLayout;
import dev.gilbe.dynamicinventory.persistence.InventoryCodec;

@Mixin(EntityPlayer.class)
public class EntityPlayerMixin {

    @Shadow
    public InventoryPlayer inventory;

    @Redirect(
        method = "readEntityFromNBT",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/entity/player/InventoryPlayer;readFromNBT(Lnet/minecraft/nbt/NBTTagList;)V"))
    private void dynamicinventory$readInventory(InventoryPlayer target, NBTTagList list, NBTTagCompound tag) {
        // Select the player root before decoding inline inventory metadata, so extras are never loaded twice.
        InventoryCodec.readPlayerInventory(target, list, tag);
    }

    @Inject(method = "readEntityFromNBT", at = @At("RETURN"))
    private void dynamicinventory$validateSelected(NBTTagCompound tag, CallbackInfo ci) {
        if (inventory.currentItem < 0 || inventory.currentItem >= InventoryLayout.HOTBAR_SLOTS)
            inventory.currentItem = 0;
    }

    @Inject(method = "writeEntityToNBT", at = @At("RETURN"))
    private void dynamicinventory$writeExtra(NBTTagCompound tag, CallbackInfo ci) {
        InventoryCodec.writeExtra(inventory, tag);
    }

    @Inject(method = "clonePlayer", at = @At("RETURN"))
    private void dynamicinventory$keepRecovery(EntityPlayer original, boolean keepEverything, CallbackInfo ci) {
        // Opaque recovery data is never spawned as drops, and must survive even an ordinary death.
        ((ExpandedInventoryAccess) inventory).dynamicinventory$setRecovery(
            (NBTTagCompound) ((ExpandedInventoryAccess) original.inventory).dynamicinventory$getRecovery()
                .copy());
    }
}
