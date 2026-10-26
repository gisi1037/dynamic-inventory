package dev.gilbe.dynamicinventory.mixins.baubles;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.gilbe.dynamicinventory.compat.BaublesInventoryBridge;

/** One shared item-use hook covers Baubles items which do not implement their own equip action. */
@Mixin(ItemStack.class)
public abstract class BaubleRightClickMixin {

    @Inject(method = "useItemRightClick", at = @At("RETURN"), cancellable = true)
    private void dynamicinventory$equip(World world, EntityPlayer player, CallbackInfoReturnable<ItemStack> cir) {
        ItemStack stack = (ItemStack) (Object) this;
        // Native actions run first: terminals must open, and usable baubles retain their behavior.
        if (cir.getReturnValue() == stack && player.openContainer == player.inventoryContainer
            && !player.isUsingItem()
            && BaublesInventoryBridge.equipHeld(player, stack))
            // Vanilla dereferences the returned stack even after its final item is consumed.
            cir.setReturnValue(stack);
    }
}
