package dev.gilbe.dynamicinventory.mixins.appliedenergistics2;

import java.util.HashSet;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import appeng.container.slot.SlotDisabled;
import appeng.container.slot.SlotPlayerInv;
import dev.gilbe.dynamicinventory.api.InventoryUi;
import dev.gilbe.dynamicinventory.compat.ae2.AppliedEnergisticsContainerAccess;

/** Shared AE2 seam only; declarations still choose exact containers, never the entire class hierarchy. */
@Mixin(targets = "appeng.container.AEBaseContainer", remap = false)
public abstract class AeContainerMixin extends Container implements AppliedEnergisticsContainerAccess {

    @Shadow
    @Final
    private HashSet<Integer> locked;

    @Shadow
    public abstract InventoryPlayer getPlayerInv();

    @Override
    public Slot dynamicinventory$playerSlot(InventoryPlayer inventory, int rawIndex) {
        // Upgradeable machines can borrow a network tool from ANY player slot. The added slots must
        // retain the same lock as AE2's original 36 slots, including a tool in expanded storage.
        return locked.contains(rawIndex) ? new SlotDisabled(inventory, rawIndex, 0, 0)
            : new SlotPlayerInv(inventory, rawIndex, 0, 0);
    }

    @Inject(method = "detectAndSendChanges", at = @At("HEAD"), cancellable = true, remap = true)
    private void dynamicinventory$deferSync(CallbackInfo ci) {
        // Do not advance AE2's custom sync caches before both peers agree on the slot map. The opening
        // gate calls detectAndSendChanges again after acceptance, before releasing client prediction.
        if (InventoryUi.findAdapter(this) != null && getPlayerInv().player instanceof EntityPlayerMP player
            && player.openContainer == this
            && !InventoryUi.isOpeningReady(player)) ci.cancel();
    }
}
