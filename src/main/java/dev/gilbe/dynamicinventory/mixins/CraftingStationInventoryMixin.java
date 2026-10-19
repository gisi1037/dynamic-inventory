package dev.gilbe.dynamicinventory.mixins;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.gilbe.dynamicinventory.api.InventoryUi;
import dev.gilbe.dynamicinventory.container.ContainerInventoryRegion;
import dev.gilbe.dynamicinventory.container.PlayerInventoryTransfers;
import tconstruct.api.ExtendedStackLimitHelper;
import tconstruct.library.modifier.IModifyable;

@Mixin(targets = "tconstruct.tools.inventory.CraftingStationContainer", remap = false)
public abstract class CraftingStationInventoryMixin extends Container {

    @Shadow
    EntityPlayer player;

    @Shadow
    protected abstract boolean moveToCraftingGrid(ItemStack stack);

    @Shadow
    protected abstract boolean moveToChest(ItemStack stack);

    @Inject(method = "<init>", at = @At("RETURN"))
    private void dynamicinventory$append(CallbackInfo ci) {
        InventoryUi.attachRegistered(this);
    }

    @Inject(method = "sendExtendedStackUpdates", at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$deferExtendedSnapshot(CallbackInfo ci) {
        // This custom packet corrects vanilla's byte-sized stack counts. Sending it while the vanilla
        // snapshot is withheld reverses their native order and permanently caches the wrong sync state.
        // The gate calls detectAndSendChanges after releasing vanilla and before unlocking prediction.
        if (player instanceof EntityPlayerMP && player.openContainer == this && !InventoryUi.isOpeningReady(player))
            ci.cancel();
    }

    @Inject(method = "transferStackInSlot", at = @At("HEAD"), cancellable = true, remap = true)
    private void dynamicinventory$extra(EntityPlayer player, int id, CallbackInfoReturnable<ItemStack> cir) {
        ContainerInventoryRegion region = ContainerInventoryRegion.of(this);
        // Unsupported or malformed layouts should use the container's original transfer logic.
        if (region == null) return;
        if (id < region.nativeSlotCount || id >= inventorySlots.size()) return;
        Slot slot = getSlot(id);
        if (!slot.getHasStack() || !slot.canTakeStack(player)) {
            cir.setReturnValue(null);
            return;
        }
        ItemStack stack = slot.getStack(), original = stack.copy();
        moveToCraftingGrid(stack);
        moveToChest(stack);
        if (stack.stackSize == original.stackSize) {
            cir.setReturnValue(null);
            return;
        }
        if (stack.stackSize == 0) slot.putStack(null);
        else slot.onSlotChanged();
        slot.onPickupFromSlot(player, stack);
        cir.setReturnValue(original);
    }

    @Inject(method = "mergeItemStack", at = @At("HEAD"), cancellable = true, remap = true)
    private void dynamicinventory$merge(ItemStack stack, int start, int end, boolean reverse,
        CallbackInfoReturnable<Boolean> cir) {
        ContainerInventoryRegion region = ContainerInventoryRegion.of(this);
        if (region == null) return;
        int[] ids = region.destinations(start, end, reverse);
        if (ids != null) cir.setReturnValue(PlayerInventoryTransfers.merge(this, stack, ids));
    }

    @Inject(method = "mergeCraftedStack", at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$modifiedTools(ItemStack stack, int start, int end, boolean playerInventory,
        EntityPlayer player, CallbackInfoReturnable<Boolean> cir) {
        ContainerInventoryRegion region = ContainerInventoryRegion.of(this);
        if (region == null || !playerInventory) return;
        int[] ids = region.destinations(start, end, true);
        if (ids == null) return;
        boolean moved = false;
        for (int id : ids) {
            if (stack.stackSize <= 0) break;
            Slot slot = getSlot(id);
            if (slot.getHasStack() || !slot.isItemValid(stack)) continue;
            int amount = Math.min(stack.stackSize, ExtendedStackLimitHelper.getStackLimit(slot, stack));
            if (amount <= 0) continue;
            ItemStack placed = stack.copy();
            placed.stackSize = amount;
            if (placed.hasTagCompound() && placed.getItem() instanceof IModifyable tool) placed.getTagCompound()
                .getCompoundTag(tool.getBaseTagName())
                .removeTag("ToRemove");
            slot.putStack(placed);
            slot.onSlotChanged();
            stack.stackSize -= amount;
            moved = true;
        }
        cir.setReturnValue(moved);
    }

    // The native attached-chest range ends at the original slot count, never at appended player storage.
    @Redirect(
        method = { "refillChest", "moveToChest", "moveToCraftingGrid", "isSideInventorySlot" },
        at = @At(value = "INVOKE", target = "Ljava/util/List;size()I"))
    private int dynamicinventory$nativeEnd(List<?> slots) {
        ContainerInventoryRegion region = ContainerInventoryRegion.of(this);
        return region == null ? slots.size() : region.nativeSlotCount;
    }
}
