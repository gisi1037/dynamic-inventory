package dev.gilbe.dynamicinventory.mixins.thaumicenergistics;

import net.minecraft.inventory.Container;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.gilbe.dynamicinventory.container.ContainerInventoryRegion;
import dev.gilbe.dynamicinventory.inventory.InventoryLayout;

/** Let the Assembler and Inscriber apply their native shift-click rules to appended storage slots. */
@Pseudo
@Mixin(targets = "thaumicenergistics.common.container.ContainerWithPlayerInventory", remap = false)
public abstract class PlayerStorageTransferMixin extends Container {

    @Inject(method = "slotClickedWasInPlayerInventory", at = @At("RETURN"), cancellable = true)
    private void dynamicinventory$expandedStorage(int slotId, CallbackInfoReturnable<Boolean> result) {
        ContainerInventoryRegion region = ContainerInventoryRegion.of(this);
        if (region != null && slotId >= region.nativeSlotCount
            && region.logical(slotId) >= InventoryLayout.HOTBAR_SLOTS) result.setReturnValue(true);
    }
}
