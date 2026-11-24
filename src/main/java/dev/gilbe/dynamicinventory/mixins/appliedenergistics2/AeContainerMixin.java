package dev.gilbe.dynamicinventory.mixins.appliedenergistics2;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
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
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.container.slot.AppEngSlot;
import appeng.container.slot.SlotDisabled;
import appeng.container.slot.SlotPlayerInv;
import dev.gilbe.dynamicinventory.api.InventoryUi;
import dev.gilbe.dynamicinventory.compat.ae2.AppliedEnergisticsContainerAccess;
import dev.gilbe.dynamicinventory.container.ContainerInventoryRegion;
import dev.gilbe.dynamicinventory.container.PlayerInventoryTransfers;
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
        var shape = InventoryLayout.of(inventory);
        AutomaticInventoryRouting.orderPlayerSlots(
            cir.getReturnValue(),
            slot -> slot.inventory == inventory ? shape.ordinaryIndex(slot.getSlotIndex()) : -1);
    }

    @Redirect(
        method = { "transferStackInSlot", "func_82846_b" },
        at = @At(value = "INVOKE", target = "Ljava/util/List;iterator()Ljava/util/Iterator;", ordinal = 0))
    private Iterator<AppEngSlot> dynamicinventory$prioritizeHotbar(List<AppEngSlot> targets, EntityPlayer player,
        int sourceId) {
        if (ContainerInventoryRegion.of(this) == null || !(getSlot(sourceId) instanceof AppEngSlot source)
            || source.isPlayerSide()) return targets.iterator();
        InventoryPlayer inventory = getPlayerInv();
        if (targets.stream()
            .noneMatch(
                slot -> slot.inventory == inventory && slot.getSlotIndex() < 9
                    && PlayerInventoryTransfers.hasTransferRoom(slot, source.getStack())))
            return targets.iterator();
        // The second native pass handles empty slots and partial stacks in order. Defer
        // storage partials to that pass so they cannot take items ahead of an open hotbar.
        return targets.stream()
            .filter(slot -> slot.inventory != inventory || slot.getSlotIndex() < 9)
            .iterator();
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
