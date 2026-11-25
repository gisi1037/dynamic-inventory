package dev.gilbe.dynamicinventory.mixins.appliedenergistics2;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import appeng.core.sync.GuiBridge;
import dev.gilbe.dynamicinventory.compat.ae2.WirelessOpeningDiagnostics;

@Mixin(value = GuiBridge.class, remap = false)
public abstract class AeOpeningTraceMixin {

    @WrapOperation(
        method = "getClientGuiElement",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/core/sync/GuiBridge;getGuiObject(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/world/World;III)Ljava/lang/Object;"))
    private Object dynamicinventory$traceWirelessItem(GuiBridge bridge, ItemStack stack, EntityPlayer player,
        World world, int x, int y, int z, Operation<Object> original, @Local(argsOnly = true, ordinal = 0) int guiId) {
        int slot = (guiId & 8) != 0 || x < 0 ? player.inventory.currentItem : x;
        WirelessOpeningDiagnostics.ae2(guiId, slot, stack, y);
        return original.call(bridge, stack, player, world, x, y, z);
    }
}
