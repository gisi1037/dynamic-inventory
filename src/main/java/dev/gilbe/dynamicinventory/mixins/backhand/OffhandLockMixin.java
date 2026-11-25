package dev.gilbe.dynamicinventory.mixins.backhand;

import net.minecraft.entity.player.EntityPlayerMP;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.gtnewhorizon.gtnhlib.keybind.SyncedKeybind;

import dev.gilbe.dynamicinventory.inventory.InventoryShape;
import dev.gilbe.dynamicinventory.inventory.InventorySlotLocks;

@Pseudo
@Mixin(targets = "xonin.backhand.CommonProxy", remap = false)
public abstract class OffhandLockMixin {

    @Inject(method = "swapOffhand", at = @At("HEAD"), cancellable = true)
    private static void dynamicinventory$denySwap(EntityPlayerMP player, SyncedKeybind key, boolean pressed,
        CallbackInfo ci) {
        int active = player.inventory.currentItem, offhand = InventoryShape.OFFHAND_INDEX;
        if (!InventorySlotLocks.accepts(player.inventory, active, player.inventory.getStackInSlot(offhand))
            || !InventorySlotLocks.accepts(player.inventory, offhand, player.inventory.getStackInSlot(active)))
            ci.cancel();
    }
}
