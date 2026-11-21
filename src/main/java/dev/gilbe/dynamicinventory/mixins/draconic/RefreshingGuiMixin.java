package dev.gilbe.dynamicinventory.mixins.draconic;

import net.minecraft.client.gui.inventory.GuiContainer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.brandon3055.draconicevolution.common.container.ContainerPlayerDetector;

import dev.gilbe.dynamicinventory.client.ContainerPanelAccess;
import dev.gilbe.dynamicinventory.client.PlayerInventoryEvents;

/** These screens call initGui directly when their controls change. */
@Mixin(
    targets = { "com.brandon3055.draconicevolution.client.gui.GUIPlayerDetector",
        "com.brandon3055.draconicevolution.client.gui.GUIWeatherController",
        "com.brandon3055.draconicevolution.client.gui.GUIGrinder",
        "com.brandon3055.draconicevolution.client.gui.GUIGenerator" },
    remap = false)
public abstract class RefreshingGuiMixin {

    @Inject(method = "initGui", at = @At("HEAD"), remap = true)
    private void dynamicinventory$restore(CallbackInfo ci) {
        GuiContainer gui = (GuiContainer) (Object) this;
        ContainerPanelAccess access = (ContainerPanelAccess) gui;
        if (access.dynamicinventory$panel() == null) return;
        access.dynamicinventory$panel()
            .restore();
        access.dynamicinventory$panel(null);
        if (gui.inventorySlots instanceof ContainerPlayerDetector detector) detector.updateContainerSlots();
    }

    @Inject(method = "initGui", at = @At("RETURN"), remap = true)
    private void dynamicinventory$decorate(CallbackInfo ci) {
        PlayerInventoryEvents.decorate((GuiContainer) (Object) this);
    }
}
