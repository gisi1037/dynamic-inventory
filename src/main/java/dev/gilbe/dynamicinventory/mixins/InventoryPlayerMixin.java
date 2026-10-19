package dev.gilbe.dynamicinventory.mixins;

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
import dev.gilbe.dynamicinventory.inventory.ExpandedInventoryAccess;
import dev.gilbe.dynamicinventory.inventory.InventoryLayout;
import dev.gilbe.dynamicinventory.persistence.InventoryCodec;

/** Expanded backing storage. Vanilla array-length-based access, pickup and ticking remain in use. */
@Mixin(value = InventoryPlayer.class, priority = 500)
public class InventoryPlayerMixin implements ExpandedInventoryAccess {

    @Shadow
    public ItemStack[] mainInventory;

    @Unique
    private NBTTagCompound dynamicinventory$recovery = new NBTTagCompound();

    @Inject(method = "<init>", at = @At("RETURN"))
    private void dynamicinventory$mainCapacity(CallbackInfo ci) {
        boolean offhand = BackhandCompatibility.enabled();
        if (mainInventory.length != InventoryLayout.LEGACY_MAIN_SLOTS + (offhand ? 1 : 0))
            throw new IllegalStateException("Unsupported initial inventory layout: " + mainInventory.length);
        mainInventory = Arrays.copyOf(mainInventory, InventoryLayout.rawMainSlots());
    }

    @Inject(method = "getFirstEmptyStack", at = @At("RETURN"), cancellable = true, order = 2000)
    private void dynamicinventory$findOrdinaryEmpty(CallbackInfoReturnable<Integer> cir) {
        if (!BackhandCompatibility.enabled() || cir.getReturnValue() != -1) return;
        // Backhand rejects an empty offhand when automatic pickup is disabled. Skip
        // equipment first, so that rejection cannot hide free ordinary slots after it.
        for (int ordinary = 0; ordinary < InventoryLayout.mainSlots(); ordinary++) {
            int raw = InventoryLayout.rawIndex(ordinary);
            if (mainInventory[raw] == null) {
                cir.setReturnValue(raw);
                return;
            }
        }
        // Keep Backhand's own result when no ordinary space remains, including its
        // configured offhand-pickup behavior. No copied configuration policy is needed.
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
    public NBTTagCompound dynamicinventory$getRecovery() {
        return dynamicinventory$recovery;
    }

    @Override
    public void dynamicinventory$setRecovery(NBTTagCompound recovery) {
        dynamicinventory$recovery = recovery;
    }
}
