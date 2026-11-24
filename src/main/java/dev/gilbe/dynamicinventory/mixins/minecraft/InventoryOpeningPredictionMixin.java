package dev.gilbe.dynamicinventory.mixins.minecraft;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.gilbe.dynamicinventory.client.AePinnedPanel;
import dev.gilbe.dynamicinventory.client.ClientGateAccess;
import dev.gilbe.dynamicinventory.compat.PackCompatibility;

/** Stop local prediction AND packet emission, including containers which override slotClick. */
@Mixin(PlayerControllerMP.class)
public abstract class InventoryOpeningPredictionMixin {

    @Inject(method = "windowClick", at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$pending(int window, int slot, int button, int mode, EntityPlayer player,
        CallbackInfoReturnable<ItemStack> cir) {
        ClientGateAccess access = (ClientGateAccess) Minecraft.getMinecraft()
            .getNetHandler();
        if (access != null && !access.dynamicinventory$openingGate()
            .allowsInput(player, window)) {
            cir.setReturnValue(null);
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (player != mc.thePlayer || player.openContainer.windowId != window) return;
        if (dev.gilbe.dynamicinventory.inventory.InventorySlotLocks
            .blocksClick(player.openContainer, player, slot, button, mode)) {
            cir.setReturnValue(null);
            return;
        }
        boolean quickMove = mode == 1 && (button == 0 || button == 1);
        if (PackCompatibility.aePinnedPanel() && (quickMove && AePinnedPanel.depositMatching(mc.currentScreen, slot)
            || AePinnedPanel.protects(mc.currentScreen, player.inventory, slot, mode))) {
            cir.setReturnValue(null);
            return;
        }
        if (!quickMove) return;
        dev.gilbe.dynamicinventory.client.DetachedChestPanel panel = dev.gilbe.dynamicinventory.client.DetachedChestPanel
            .of(mc.currentScreen);
        // Mouse Tweaks calls the controller directly while sweeping held Shift over slots.
        if (panel != null && panel.quickMoveToMain(slot)) cir.setReturnValue(null);
    }
}
