package dev.gilbe.dynamicinventory.mixins.bogosorter;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.gilbe.dynamicinventory.client.ContainerPanelAccess;
import dev.gilbe.dynamicinventory.client.DetachedChestPanel;
import dev.gilbe.dynamicinventory.inventory.InventoryLayout;
import dev.gilbe.dynamicinventory.network.PanelSortRequest;

@Mixin(targets = "com.cleanroommc.bogosorter.ShortcutHandler", remap = false)
public abstract class BogoSpaceTransferMixin {

    @Inject(
        method = "moveAllItems(Lnet/minecraft/client/gui/inventory/GuiContainer;Z)Z",
        at = @At("HEAD"),
        cancellable = true)
    private static void dynamicinventory$movePanel(GuiContainer screen, boolean sameItemOnly,
        CallbackInfoReturnable<Boolean> cir) {
        var slot = ((ContainerPanelAccess) screen).dynamicinventory$hoveredSlot();
        if (sameItemOnly || slot == null) return;
        var host = DetachedChestPanel.of(screen);
        if (host == null) return;
        int source = slot.slotNumber;
        int[] slots = host.spaceTransferSlots(source);
        if (slots == null) return;
        var inventory = Minecraft.getMinecraft().thePlayer.inventory;
        PanelSortRequest.moveAll(
            screen.inventorySlots,
            InventoryLayout.of(inventory)
                .revision(),
            source,
            slots);
        cir.setReturnValue(true);
    }
}
