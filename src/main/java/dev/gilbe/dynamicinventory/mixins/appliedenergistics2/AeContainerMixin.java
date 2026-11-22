package dev.gilbe.dynamicinventory.mixins.appliedenergistics2;

import java.util.HashSet;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.container.slot.AppEngSlot;
import appeng.container.slot.SlotDisabled;
import appeng.container.slot.SlotPlayerInv;
import dev.gilbe.dynamicinventory.api.InventoryUi;
import dev.gilbe.dynamicinventory.compat.ae2.AppliedEnergisticsContainerAccess;
import dev.gilbe.dynamicinventory.container.ContainerInventoryRegion;
import dev.gilbe.dynamicinventory.inventory.AutomaticInventoryRouting;
import dev.gilbe.dynamicinventory.inventory.InventoryLayout;

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

    @Inject(method = "getValidDestinationSlots", at = @At("RETURN"))
    private void dynamicinventory$routeExtraction(boolean fromPlayer, ItemStack stack,
        CallbackInfoReturnable<List<AppEngSlot>> cir) {
        if (fromPlayer || ContainerInventoryRegion.of(this) == null) return;
        InventoryPlayer inventory = getPlayerInv();
        // AE2 merges directly into this list, bypassing the usual container merge hooks.
        cir.getReturnValue()
            .removeIf(
                slot -> slot.inventory == inventory
                    && !AutomaticInventoryRouting.transfer(inventory, slot.getSlotIndex()));
        // Extra player slots are appended after the original hotbar. Sort the player-only
        // destination list by the logical grid so the hotbar remains the fallback.
        var shape = InventoryLayout.of(inventory);
        cir.getReturnValue()
            .sort(java.util.Comparator.comparingInt(slot -> {
                int logical = slot.inventory == inventory ? shape.ordinaryIndex(slot.getSlotIndex()) : -1;
                return logical < 0 ? Integer.MAX_VALUE
                    : logical < InventoryLayout.HOTBAR_SLOTS ? shape.mainSlots() + logical : logical;
            }));
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
