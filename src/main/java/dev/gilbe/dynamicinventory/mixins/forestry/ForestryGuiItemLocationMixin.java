package dev.gilbe.dynamicinventory.mixins.forestry;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.sugar.Local;

import dev.gilbe.dynamicinventory.inventory.InventoryShape;

/** Use the full item-location byte. The handler ID and signed page data keep their native bits. */
@Pseudo
@Mixin(targets = "forestry.core.gui.GuiHandler", remap = false)
public abstract class ForestryGuiItemLocationMixin {

    private static final String ENCODE = "encodeGuiData(Lforestry/core/gui/IGuiHandlerForestry;SI)I";

    @ModifyConstant(
        method = { ENCODE,
            "openGui(Lnet/minecraft/entity/player/EntityPlayer;Lforestry/core/gui/IGuiHandlerItem;SI)V" },
        constant = @Constant(intValue = 40))
    private static int dynamicinventory$allowExpandedOwner(int vanilla) {
        return InventoryShape.MAX_STORAGE + InventoryShape.HOTBAR_SLOTS + InventoryShape.ARMOR_SLOTS + 1;
    }

    @ModifyVariable(method = ENCODE, at = @At("STORE"), index = 3)
    private static int dynamicinventory$encodeSlot(int original, @Local(argsOnly = true) int raw) {
        return raw + 1;
    }

    @Inject(method = "decodeInvSlotIdx", at = @At("HEAD"), cancellable = true)
    private static void dynamicinventory$decodeSlot(int data, CallbackInfoReturnable<Integer> result) {
        result.setReturnValue((data >>> 8 & 0xFF) - 1);
    }
}
