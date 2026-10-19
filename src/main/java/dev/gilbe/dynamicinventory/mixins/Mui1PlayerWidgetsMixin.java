package dev.gilbe.dynamicinventory.mixins;

import net.minecraft.entity.player.EntityPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.gtnewhorizons.modularui.api.drawable.IDrawable;
import com.gtnewhorizons.modularui.common.internal.wrapper.BaseSlot;
import com.gtnewhorizons.modularui.common.widget.SlotGroup;
import com.gtnewhorizons.modularui.common.widget.SlotWidget;

import dev.gilbe.dynamicinventory.compat.mui1.InventoryViewport;
import dev.gilbe.dynamicinventory.compat.mui1.Mui1PlayerInventory;
import dev.gilbe.dynamicinventory.compat.mui1.PlayerInventoryGroup;
import dev.gilbe.dynamicinventory.compat.mui1.PlayerInventorySlot;
import dev.gilbe.dynamicinventory.compat.mui1.PlayerInventorySlotWidget;
import dev.gilbe.dynamicinventory.inventory.InventoryLayout;

@Mixin(value = SlotGroup.class, remap = false)
public abstract class Mui1PlayerWidgetsMixin {

    @Inject(
        method = "playerInventoryGroup(Lnet/minecraft/entity/player/EntityPlayer;Lcom/gtnewhorizons/modularui/api/drawable/IDrawable;)Lcom/gtnewhorizons/modularui/common/widget/SlotGroup;",
        at = @At("HEAD"),
        cancellable = true)
    private static void dynamicinventory$widgets(EntityPlayer player, IDrawable background,
        CallbackInfoReturnable<SlotGroup> cir) {
        Mui1PlayerInventory inventory = new Mui1PlayerInventory(player.inventory);
        InventoryViewport viewport = new InventoryViewport();
        viewport.setVerticalScroll();
        SlotGroup group = new PlayerInventoryGroup(viewport);
        viewport.setSize(168, 54);
        for (int i = 9; i < InventoryLayout.mainSlots(); i++) {
            PlayerInventorySlot playerSlot = new PlayerInventorySlot(inventory, i);
            playerSlot.setShiftClickPriority(40);
            SlotWidget slot = new PlayerInventorySlotWidget(playerSlot, viewport);
            slot.setPos((i - 9) % 9 * 18, (i - 9) / 9 * 18);
            if (background != null) slot.setBackground(background);
            viewport.widget(slot);
        }
        group.addChild(viewport);
        for (int i = 0; i < 9; i++) {
            SlotWidget slot = new SlotWidget(new BaseSlot(inventory, i).setShiftClickPriority(20));
            slot.setPos(i * 18, 58);
            if (background != null) slot.setBackground(background);
            group.addSlot(slot);
        }
        cir.setReturnValue(group);
    }
}
