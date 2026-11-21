package dev.gilbe.dynamicinventory.mixins.draconic;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.gilbe.dynamicinventory.api.InventoryUi;
import dev.gilbe.dynamicinventory.compat.draconic.ReactorFuelSlots;

@Mixin(targets = "com.brandon3055.draconicevolution.common.container.ContainerReactor", remap = false)
public abstract class ReactorContainerMixin extends Container implements ReactorFuelSlots {

    @Shadow
    @Final
    private EntityPlayer player;
    @Unique
    private boolean dynamicinventory$fuelSlotsVisible;

    @Invoker("addFuelSlots")
    protected abstract void dynamicinventory$addFuelSlots();

    @Inject(method = "<init>", at = @At("RETURN"))
    private void dynamicinventory$reserveFuelSlots(CallbackInfo ci) {
        if (inventorySlots.size() == 36) {
            dynamicinventory$addFuelSlots();
            dynamicinventory$showFuelSlots(false);
        }
    }

    @Inject(method = "addFuelSlots", at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$showFuelSlots(CallbackInfo ci) {
        if (inventorySlots.size() >= 38) {
            dynamicinventory$showFuelSlots(true);
            ci.cancel();
        }
    }

    @Inject(method = "addFuelSlots", at = @At("RETURN"))
    private void dynamicinventory$createdFuelSlots(CallbackInfo ci) {
        dynamicinventory$fuelSlotsVisible = true;
    }

    @Inject(method = "removeFuelSlots", at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$hideFuelSlots(CallbackInfo ci) {
        // Keep the native fuel IDs and the appended player IDs stable through reactor state changes.
        dynamicinventory$showFuelSlots(false);
        ci.cancel();
    }

    @Unique
    private void dynamicinventory$showFuelSlots(boolean visible) {
        dynamicinventory$fuelSlotsVisible = visible;
        getSlot(36).xDisplayPosition = visible ? 15 : -10000;
        getSlot(37).xDisplayPosition = visible ? 217 : -10000;
        getSlot(36).yDisplayPosition = getSlot(37).yDisplayPosition = visible ? 140 : -10000;
    }

    @Override
    public boolean dynamicinventory$fuelSlotsVisible() {
        return dynamicinventory$fuelSlotsVisible;
    }

    @Inject(method = "slotClick", at = @At("HEAD"), cancellable = true, remap = true)
    private void dynamicinventory$blockHiddenFuelSlots(int slot, int button, int mode, EntityPlayer player,
        CallbackInfoReturnable<ItemStack> cir) {
        if (dynamicinventory$hiddenFuelSlot(slot)) cir.setReturnValue(null);
    }

    @Override
    public boolean func_94530_a(ItemStack stack, Slot slot) {
        return !dynamicinventory$hiddenFuelSlot(slot.slotNumber) && super.func_94530_a(stack, slot);
    }

    @Override
    public boolean canDragIntoSlot(Slot slot) {
        return !dynamicinventory$hiddenFuelSlot(slot.slotNumber) && super.canDragIntoSlot(slot);
    }

    @Unique
    private boolean dynamicinventory$hiddenFuelSlot(int id) {
        return !dynamicinventory$fuelSlotsVisible && (id == 36 || id == 37);
    }

    @Inject(method = "detectAndSendChanges", at = @At("HEAD"), cancellable = true, remap = true)
    private void dynamicinventory$deferSync(CallbackInfo ci) {
        if (player instanceof EntityPlayerMP serverPlayer && serverPlayer.openContainer == this
            && !InventoryUi.isOpeningReady(serverPlayer)) ci.cancel();
    }
}
