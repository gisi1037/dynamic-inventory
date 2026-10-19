package dev.gilbe.dynamicinventory.mixins;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.gilbe.dynamicinventory.client.ClientGateAccess;

/** Stop local prediction AND packet emission, including containers which override slotClick. */
@Mixin(PlayerControllerMP.class)
public abstract class InventoryOpeningPredictionMixin {

    @Inject(method = "windowClick", at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$pending(int window, int slot, int button, int mode, EntityPlayer player,
        CallbackInfoReturnable<ItemStack> cir) {
        ClientGateAccess access = (ClientGateAccess) Minecraft.getMinecraft()
            .getNetHandler();
        if (access != null && !access.dynamicinventory$openingGate()
            .allowsInput(player, window)) cir.setReturnValue(null);
    }
}
