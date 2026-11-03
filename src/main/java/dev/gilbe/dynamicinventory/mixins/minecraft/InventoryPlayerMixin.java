package dev.gilbe.dynamicinventory.mixins.minecraft;

import java.util.Arrays;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.gilbe.dynamicinventory.compat.BackhandCompatibility;
import dev.gilbe.dynamicinventory.inventory.AutomaticInventoryRouting;
import dev.gilbe.dynamicinventory.inventory.ExpandedInventoryAccess;
import dev.gilbe.dynamicinventory.inventory.InventoryLayout;
import dev.gilbe.dynamicinventory.inventory.InventoryShape;
import dev.gilbe.dynamicinventory.persistence.InventoryCodec;

/** Expanded backing storage. Vanilla array-length-based access, pickup and ticking remain in use. */
@Mixin(value = InventoryPlayer.class, priority = 500)
public class InventoryPlayerMixin implements ExpandedInventoryAccess {

    @Shadow
    public ItemStack[] mainInventory;

    @Unique
    private NBTTagCompound dynamicinventory$recovery = new NBTTagCompound();

    @Unique
    private InventoryShape dynamicinventory$shape;

    @Unique
    private boolean dynamicinventory$capacityOverride;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void dynamicinventory$mainCapacity(CallbackInfo ci) {
        boolean offhand = BackhandCompatibility.enabled();
        if (mainInventory.length != InventoryLayout.LEGACY_MAIN_SLOTS + (offhand ? 1 : 0))
            throw new IllegalStateException("Unsupported initial inventory layout: " + mainInventory.length);
        dynamicinventory$shape = InventoryLayout.initialShape();
        mainInventory = Arrays.copyOf(mainInventory, dynamicinventory$shape.rawMainSlots());
    }

    @Inject(method = "getFirstEmptyStack", at = @At("RETURN"), cancellable = true, order = 2000)
    private void dynamicinventory$findOrdinaryEmpty(CallbackInfoReturnable<Integer> cir) {
        InventoryPlayer inventory = (InventoryPlayer) (Object) this;
        if (cir.getReturnValue() >= 0 && AutomaticInventoryRouting.pickup(inventory, cir.getReturnValue())) return;
        cir.setReturnValue(AutomaticInventoryRouting.firstEmpty(inventory));
    }

    @Inject(method = "storeItemStack", at = @At("RETURN"), cancellable = true, order = 2000)
    private void dynamicinventory$skipReservedMerge(ItemStack source, CallbackInfoReturnable<Integer> cir) {
        InventoryPlayer inventory = (InventoryPlayer) (Object) this;
        if (cir.getReturnValue() >= 0 && AutomaticInventoryRouting.pickup(inventory, cir.getReturnValue())) return;
        cir.setReturnValue(AutomaticInventoryRouting.mergeSlot(inventory, source));
    }

    @Inject(method = "writeToNBT", at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$writeInventory(NBTTagList list, CallbackInfoReturnable<NBTTagList> cir) {
        cir.setReturnValue(InventoryCodec.writeInventory((InventoryPlayer) (Object) this, list));
    }

    @Inject(method = "readFromNBT", at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$readInventory(NBTTagList list, CallbackInfo ci) {
        InventoryCodec.readInventory((InventoryPlayer) (Object) this, list);
        ci.cancel();
    }

    @Inject(method = "copyInventory", at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$copy(InventoryPlayer source, CallbackInfo ci) {
        InventoryCodec.copy((InventoryPlayer) (Object) this, source);
        ci.cancel();
    }

    @Override
    public InventoryShape dynamicinventory$getShape() {
        return dynamicinventory$shape;
    }

    @Override
    public boolean dynamicinventory$hasCapacityOverride() {
        return dynamicinventory$capacityOverride;
    }

    @Override
    public void dynamicinventory$setCapacityOverride(boolean overridden) {
        dynamicinventory$capacityOverride = overridden;
    }

    @Override
    public void dynamicinventory$setShape(InventoryShape shape) {
        if (shape == null) throw new NullPointerException("shape");
        dynamicinventory$shape = shape;
    }

    @Override
    public NBTTagCompound dynamicinventory$getRecovery() {
        return dynamicinventory$recovery;
    }

    @Override
    public void dynamicinventory$setRecovery(NBTTagCompound recovery) {
        dynamicinventory$recovery = recovery;
    }
}
