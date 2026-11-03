package dev.gilbe.dynamicinventory.mixins.bogosorter;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraftforge.client.event.GuiScreenEvent;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.cleanroommc.bogosorter.ClientEventHandler;
import com.cleanroommc.bogosorter.mixins.early.minecraft.SlotAccessor;

import dev.gilbe.dynamicinventory.client.DetachedChestPanel;
import dev.gilbe.dynamicinventory.client.NativeSortButton;

@Mixin(targets = "com.cleanroommc.bogosorter.common.sort.ButtonHandler", remap = false)
public abstract class BogoPanelSortButtonMixin {

    @Redirect(
        method = "onActionPerformed",
        at = @At(
            value = "INVOKE",
            target = "Lcom/cleanroommc/bogosorter/ClientEventHandler;sort(Lnet/minecraft/client/gui/GuiScreen;Lcom/cleanroommc/bogosorter/mixins/early/minecraft/SlotAccessor;)Z"))
    private boolean dynamicinventory$main(GuiScreen screen, SlotAccessor slot,
        GuiScreenEvent.ActionPerformedEvent.Pre event) {
        DetachedChestPanel panel = DetachedChestPanel.of(screen);
        // The relocated player button still holds Bogo's original whole-storage group.
        if (panel != null && event.button instanceof NativeSortButton button
            && button.dynamicinventory$sortGroup()
                .isPlayerInventory()
            && !button.dynamicinventory$sortGroup()
                .isHotbar()) {
            int[] ids = panel.sortSlots(-1);
            if (ids != null) {
                if (ids.length == 0) return false;
                slot = (SlotAccessor) ((GuiContainer) screen).inventorySlots.getSlot(ids[0]);
            }
        }
        return ClientEventHandler.sort(screen, slot);
    }
}
