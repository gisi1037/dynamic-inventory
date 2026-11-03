package dev.gilbe.dynamicinventory.mixins.enderio;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.gilbe.dynamicinventory.container.ContainerInventoryRegion;
import dev.gilbe.dynamicinventory.inventory.AutomaticInventoryRouting;

/** Network extraction/crafting use the same ordinary capacity as the visible player section. */
@Pseudo
@Mixin(targets = "crazypants.enderio.machine.invpanel.InventoryPanelContainer", remap = false)
public abstract class InventoryPanelSlotsMixin extends Container {

    @Inject(method = "getPlayerInventorySlots", at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$storage(CallbackInfoReturnable<List<Slot>> cir) {
        ContainerInventoryRegion region = ContainerInventoryRegion.of(this);
        if (region == null) return;
        List<Slot> slots = new ArrayList<>();
        for (int id : region.getStorageSlotIds()) {
            Slot slot = getSlot(id);
            if (AutomaticInventoryRouting.transfer(region.inventory, slot.getSlotIndex())) slots.add(slot);
        }
        cir.setReturnValue(slots);
    }

    @Inject(method = "getPlayerHotbarSlots", at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$hotbar(CallbackInfoReturnable<List<Slot>> cir) {
        ContainerInventoryRegion region = ContainerInventoryRegion.of(this);
        if (region == null) return;
        List<Slot> slots = new ArrayList<>();
        for (int id : region.getHotbarSlotIds()) slots.add(getSlot(id));
        cir.setReturnValue(slots);
    }
}
