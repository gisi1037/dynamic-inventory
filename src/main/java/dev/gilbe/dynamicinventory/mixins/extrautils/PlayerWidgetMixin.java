package dev.gilbe.dynamicinventory.mixins.extrautils;

import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.gilbe.dynamicinventory.client.ContainerPanelAccess;
import dev.gilbe.dynamicinventory.container.ContainerInventoryRegion;

/** The replacement panel paints player cells; keep XU's real widgets for slot ownership and packets. */
@Pseudo
@Mixin(targets = "com.rwtema.extrautils.dynamicgui.WidgetSlot", remap = false)
public abstract class PlayerWidgetMixin extends Slot {

    protected PlayerWidgetMixin(IInventory inventory, int slot, int x, int y) {
        super(inventory, slot, x, y);
    }

    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$player(TextureManager manager, @Coerce Object gui, int x, int y, CallbackInfo ci) {
        if (((ContainerPanelAccess) gui).dynamicinventory$panel() == null) return;
        Container container = ((net.minecraft.client.gui.inventory.GuiContainer) gui).inventorySlots;
        ContainerInventoryRegion region = ContainerInventoryRegion.of(container);
        if (region != null && region.logical(slotNumber) >= 0) ci.cancel();
    }
}
