package dev.gilbe.dynamicinventory.mixins.bogosorter;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cleanroommc.bogosorter.client.PinnedSlotClient;
import com.cleanroommc.bogosorter.mixins.early.minecraft.SlotAccessor;

import dev.gilbe.dynamicinventory.compat.BogoExpandedPins;

@Mixin(value = PinnedSlotClient.class, remap = false)
public abstract class BogoPinClientMixin {

    @Inject(method = "toggle", at = @At("HEAD"), cancellable = true)
    private static void dynamicinventory$skipLegacyMask(GuiContainer gui, SlotAccessor slot, CallbackInfo ci) {
        if (BogoExpandedPins.index(slot, null) >= 0) ci.cancel();
    }

    @Inject(method = "isPinned", at = @At("HEAD"), cancellable = true)
    private static void dynamicinventory$showPin(GuiContainer gui, Slot slot, CallbackInfoReturnable<Boolean> result) {
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        if (player == null) return;
        int index = BogoExpandedPins.index((SlotAccessor) slot, player.inventory);
        if (index >= 0) result.setReturnValue(BogoExpandedPins.isPinned(player, index));
    }
}
