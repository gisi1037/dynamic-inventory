package dev.gilbe.dynamicinventory.mixins.draconic;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.gilbe.dynamicinventory.client.ContainerPanelAccess;
import dev.gilbe.dynamicinventory.client.NativeInventoryPanel;
import dev.gilbe.dynamicinventory.compat.draconic.ReactorFuelSlots;

@Mixin(targets = "com.brandon3055.draconicevolution.client.gui.componentguis.GUIReactor", remap = false)
public abstract class ReactorGuiMixin {

    @Inject(method = "updateScreen", at = @At("RETURN"), remap = true)
    private void dynamicinventory$refreshFuelSlots(CallbackInfo ci) {
        GuiContainer gui = (GuiContainer) (Object) this;
        NativeInventoryPanel panel = ((ContainerPanelAccess) gui).dynamicinventory$panel();
        if (panel == null || !(gui.inventorySlots instanceof ReactorFuelSlots reactor)) return;
        boolean visible = reactor.dynamicinventory$fuelSlotsVisible();
        Slot input = gui.inventorySlots.getSlot(36);
        Slot output = gui.inventorySlots.getSlot(37);
        input.xDisplayPosition = visible ? 15 : -10000;
        output.xDisplayPosition = visible ? 217 : -10000;
        input.yDisplayPosition = output.yDisplayPosition = visible ? 140 : -10000;
        panel.refreshNativeMachinePositions(36, 38);
    }
}
