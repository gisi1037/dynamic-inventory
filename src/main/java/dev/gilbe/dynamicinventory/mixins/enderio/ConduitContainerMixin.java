package dev.gilbe.dynamicinventory.mixins.enderio;

import java.awt.Point;
import java.util.List;

import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.gilbe.dynamicinventory.api.InventoryUi;
import dev.gilbe.dynamicinventory.container.ContainerInventoryRegion;

/** The modular player section remains usable on every conduit configuration tab. */
@Pseudo
@Mixin(targets = "crazypants.enderio.conduit.gui.ExternalConnectionContainer", remap = false)
public abstract class ConduitContainerMixin extends Container {

    @Shadow
    @Final
    private List<Point> slotLocations;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void dynamicinventory$origins(CallbackInfo ci) {
        if (InventoryUi.findAdapter(this) == null) return;
        for (int i = 0; i < 36; i++) {
            Slot slot = getSlot(i);
            slot.xDisplayPosition = slotLocations.get(i).x;
            slot.yDisplayPosition = slotLocations.get(i).y;
        }
    }

    @Inject(method = "setInventorySlotsVisible", at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$player(boolean visible, CallbackInfo ci) {
        if (ContainerInventoryRegion.of(this) != null) ci.cancel();
    }
}
