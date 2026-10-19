package dev.gilbe.dynamicinventory.mixins;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.gilbe.dynamicinventory.compat.BaublesInventoryBridge;
import dev.gilbe.dynamicinventory.compat.PackCompatibility;
import dev.gilbe.dynamicinventory.container.PlayerContainerLayout;
import dev.gilbe.dynamicinventory.container.PlayerInventoryTransfers;
import dev.gilbe.dynamicinventory.inventory.InventoryLayout;

/** Retains the original crafting, armor, hotbar IDs, listeners and persistent window-zero lifecycle. */
@Mixin(value = ContainerPlayer.class, priority = 500)
public abstract class ContainerPlayerMixin extends Container {

    @Unique
    private EntityPlayer dynamicinventory$owner;

    @Inject(method = "<init>", at = @At("RETURN"), order = 2000)
    private void dynamicinventory$appendStorage(InventoryPlayer inventory, boolean local, EntityPlayer player,
        CallbackInfo ci) {
        dynamicinventory$owner = player;
        if (inventorySlots.size() != PlayerContainerLayout.EXTRA_START) {
            throw new IllegalStateException(
                "Unexpected player container before storage append: " + inventorySlots.size());
        }
        for (int main = InventoryLayout.LEGACY_MAIN_SLOTS; main < InventoryLayout.mainSlots(); main++) {
            addSlotToContainer(
                new Slot(
                    inventory,
                    InventoryLayout.rawIndex(main),
                    PlayerContainerLayout.storageX(main),
                    PlayerContainerLayout.storageY(main)));
        }
        for (int main = 0; main < InventoryLayout.HOTBAR_SLOTS; main++) {
            getSlot(PlayerContainerLayout.containerId(main)).yDisplayPosition = PlayerContainerLayout.hotbarY();
        }
        if (PackCompatibility.enabled())
            for (Slot slot : BaublesInventoryBridge.slots(player)) addSlotToContainer(slot);
    }

    @Override
    public void putStackInSlot(int id, ItemStack stack) {
        boolean previous = PackCompatibility.enabled()
            && BaublesInventoryBridge.blockClientEvents(dynamicinventory$owner, true);
        try {
            super.putStackInSlot(id, stack);
        } finally {
            if (PackCompatibility.enabled()) BaublesInventoryBridge.blockClientEvents(dynamicinventory$owner, previous);
        }
    }

    @Override
    public void putStacksInSlots(ItemStack[] stacks) {
        boolean previous = PackCompatibility.enabled()
            && BaublesInventoryBridge.blockClientEvents(dynamicinventory$owner, true);
        try {
            super.putStacksInSlots(stacks);
        } finally {
            if (PackCompatibility.enabled()) BaublesInventoryBridge.blockClientEvents(dynamicinventory$owner, previous);
        }
    }

    @Inject(method = "transferStackInSlot", at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$transfer(EntityPlayer player, int id, CallbackInfoReturnable<ItemStack> cir) {
        cir.setReturnValue(PlayerInventoryTransfers.transfer(this, player, id));
    }
}
