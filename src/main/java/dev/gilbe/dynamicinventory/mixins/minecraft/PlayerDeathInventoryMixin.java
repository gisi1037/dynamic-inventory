package dev.gilbe.dynamicinventory.mixins.minecraft;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.DamageSource;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import dev.gilbe.dynamicinventory.inventory.PlayerContainerSettlement;

@Mixin(EntityPlayerMP.class)
public abstract class PlayerDeathInventoryMixin {

    // Player API moves the native death body into localOnDeath and leaves its dispatcher in onDeath.
    @Inject(
        method = { "onDeath", "localOnDeath" },
        at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/InventoryPlayer;dropAllItems()V"),
        allow = 1)
    private void dynamicinventory$closeBeforeDeathDrops(DamageSource source, CallbackInfo ci) {
        // Forge is already collecting this death's drops for PlayerDropsEvent and grave mods.
        PlayerContainerSettlement.closeForDeath((EntityPlayerMP) (Object) this, false);
    }

    @ModifyExpressionValue(
        method = { "onDeath", "localOnDeath" },
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/GameRules;getGameRuleBooleanValue(Ljava/lang/String;)Z"),
        allow = 1)
    private boolean dynamicinventory$keepItemsFromClosedContainer(boolean keepInventory) {
        if (keepInventory) PlayerContainerSettlement.closeForDeath((EntityPlayerMP) (Object) this, true);
        return keepInventory;
    }
}
