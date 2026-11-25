package dev.gilbe.dynamicinventory.mixins.minecraft;

import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import dev.gilbe.dynamicinventory.inventory.ExpandedInventoryAccess;
import dev.gilbe.dynamicinventory.inventory.InventoryLayout;
import dev.gilbe.dynamicinventory.inventory.InventoryOverflowDrops;
import dev.gilbe.dynamicinventory.inventory.InventoryResizing;
import dev.gilbe.dynamicinventory.inventory.InventorySlotLocks;
import dev.gilbe.dynamicinventory.inventory.PlayerContainerSettlement;
import dev.gilbe.dynamicinventory.persistence.InventoryCodec;

@Mixin(EntityPlayer.class)
public class EntityPlayerMixin {

    @Shadow
    public InventoryPlayer inventory;

    @Inject(method = "dropOneItem", at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$lockDrop(boolean entireStack, CallbackInfoReturnable<EntityItem> result) {
        if (InventorySlotLocks.locked(inventory, inventory.currentItem)) result.setReturnValue(null);
    }

    @Inject(method = "dropPlayerItemWithRandomChoice", at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$captureClosingDrop(ItemStack stack, boolean random,
        CallbackInfoReturnable<EntityItem> result) {
        EntityPlayer player = (EntityPlayer) (Object) this;
        if (PlayerContainerSettlement.isClosing(player)) {
            // Forge's manual toss wrapper would clear the active drop capture.
            result.setReturnValue(player.func_146097_a(stack, true, false));
        }
    }

    @WrapOperation(
        method = "joinEntityItemWithWorld",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/World;spawnEntityInWorld(Lnet/minecraft/entity/Entity;)Z"))
    private boolean dynamicinventory$preserveRejectedDeathDrop(World world, Entity drop, Operation<Boolean> original) {
        if ((Object) this instanceof EntityPlayerMP player && player.getHealth() <= 0
            && drop instanceof EntityItem item)
            return InventoryOverflowDrops.spawnDeathDrop(player, item, () -> original.call(world, drop));
        return original.call(world, drop);
    }

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
        EntityPlayer player = (EntityPlayer) (Object) this;
        if (((ExpandedInventoryAccess) inventory).dynamicinventory$hasCapacityOverride() && !player.worldObj.isRemote
            && (!(player instanceof EntityPlayerMP) || ((EntityPlayerMP) player).playerNetServerHandler == null))
            InventoryResizing.rebuild(player);
    }

    @Inject(method = "writeEntityToNBT", at = @At("RETURN"))
    private void dynamicinventory$writeExtra(NBTTagCompound tag, CallbackInfo ci) {
        InventoryCodec.writeExtra(inventory, tag);
    }

    @Inject(method = "clonePlayer", at = @At("RETURN"))
    private void dynamicinventory$rebuildAfterClone(EntityPlayer original, boolean keepEverything, CallbackInfo ci) {
        InventoryResizing.rebuild((EntityPlayer) (Object) this);
    }

    @Inject(method = "clonePlayer", at = @At("HEAD"))
    private void dynamicinventory$inheritCapacity(EntityPlayer original, boolean keepEverything, CallbackInfo ci) {
        if ((Object) this == original) return;
        InventoryResizing.inheritCapacity((EntityPlayer) (Object) this, original);
        // Ordinary death skips the vanilla inventory copy, but pending drops and recovery still belong to the player.
        ((ExpandedInventoryAccess) inventory).dynamicinventory$setRecovery(
            (NBTTagCompound) ((ExpandedInventoryAccess) original.inventory).dynamicinventory$getRecovery()
                .copy());
    }
}
