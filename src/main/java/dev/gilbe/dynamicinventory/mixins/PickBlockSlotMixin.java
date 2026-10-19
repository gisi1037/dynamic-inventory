package dev.gilbe.dynamicinventory.mixins;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import dev.gilbe.dynamicinventory.container.PlayerContainerLayout;

/** Keep creative pick-block packets addressed to the original hotbar slots. */
@Mixin(Minecraft.class)
public abstract class PickBlockSlotMixin {

    @Shadow
    public EntityClientPlayerMP thePlayer;

    @ModifyArg(
        method = "func_147112_ai",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/PlayerControllerMP;sendSlotPacket(Lnet/minecraft/item/ItemStack;I)V"),
        index = 1)
    private int dynamicinventory$hotbarSlot(int original) {
        // Vanilla counts back nine slots from the container's end. Expanded storage
        // and Baubles now follow the hotbar, but its wire IDs deliberately stay 36..44.
        // Correct the final argument so Backhand's separate size()-1 adjustment
        // cannot shift it again. The stack itself still comes from Forge's pick hook.
        return PlayerContainerLayout.containerId(thePlayer.inventory.currentItem);
    }
}
