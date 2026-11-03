package dev.gilbe.dynamicinventory.mixins.minecraft;

import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.gilbe.dynamicinventory.client.MainInventoryRoutingClient;

@Mixin(PlayerControllerMP.class)
public abstract class MainInventoryRoutingClientMixin {

    @Inject(method = "windowClick", at = @At("HEAD"))
    private void dynamicinventory$routing(int window, int slot, int button, int mode, EntityPlayer player,
        CallbackInfoReturnable<ItemStack> cir) {
        MainInventoryRoutingClient.sync();
    }
}
