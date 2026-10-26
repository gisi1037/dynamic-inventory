package dev.gilbe.dynamicinventory.mixins.appliedenergistics2;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IInventory;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.gilbe.dynamicinventory.inventory.InventoryLayout;
import dev.gilbe.dynamicinventory.inventory.InventoryShape;

/** AE2's extraction simulation and insertion must see the same complete ordinary player inventory. */
@Mixin(targets = "appeng.util.inv.AdaptorPlayerInventory", remap = false)
public abstract class AePlayerInventoryAdaptorMixin {

    @Shadow
    @Final
    @Mutable
    private IInventory src;
    @Unique
    private InventoryPlayer dynamicinventory$player;
    @Unique
    private boolean dynamicinventory$storageFirst;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void dynamicinventory$bind(IInventory inventory, boolean swap, CallbackInfo ci) {
        if (!(inventory instanceof InventoryPlayer player)) return;
        dynamicinventory$player = player;
        dynamicinventory$storageFirst = swap;
        // Replace the optional fixed 27+9 wrapper. Mapping below preserves its storage-first order.
        src = player;
    }

    @Inject(method = "getSizeInventory", remap = true, at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$size(CallbackInfoReturnable<Integer> cir) {
        if (dynamicinventory$player != null) cir.setReturnValue(
            InventoryLayout.of(dynamicinventory$player)
                .mainSlots());
    }

    @ModifyVariable(
        method = { "getStackInSlot", "decrStackSize", "getStackInSlotOnClosing", "setInventorySlotContents",
            "isItemValidForSlot" },
        remap = true,
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0)
    private int dynamicinventory$slot(int logical) {
        if (dynamicinventory$player == null) return logical;
        InventoryShape shape = InventoryLayout.of(dynamicinventory$player);
        if (dynamicinventory$storageFirst)
            logical = logical < shape.storageSlots() ? logical + 9 : logical - shape.storageSlots();
        // Equipment (including Backhand's raw slot 36) is never an ordinary transfer destination.
        return shape.rawIndex(logical);
    }
}
