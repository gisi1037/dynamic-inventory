package dev.gilbe.dynamicinventory.mixins;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import dev.gilbe.dynamicinventory.api.InventoryUi;

/** WrapMethod's higher-priority outer wrapper preserves extras around Hodgepodge's quick-move shortcut. */
@Mixin(value = Container.class, priority = 2000)
public abstract class RegisteredInventoryClickMixin {

    @WrapMethod(method = "slotClick")
    private ItemStack dynamicinventory$registeredShiftClick(int slotId, int button, int mode, EntityPlayer player,
        Operation<ItemStack> original) {
        Container container = (Container) (Object) this;
        // Custom quick-move dispatch may call the container directly instead of the vanilla packet handler.
        if (player instanceof EntityPlayerMP && player.openContainer == container
            && !InventoryUi.isOpeningReady(player)) return null;
        if (mode == 1 && (button == 0 || button == 1)
            && InventoryUi.handlesAdditionalTransfer((Container) (Object) this, slotId))
            return InventoryUi.transferAdditional((Container) (Object) this, player, slotId);
        return original.call(slotId, button, mode, player);
    }

}
