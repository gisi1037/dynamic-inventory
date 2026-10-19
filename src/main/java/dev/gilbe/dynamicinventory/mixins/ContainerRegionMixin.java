package dev.gilbe.dynamicinventory.mixins;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ICrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.gilbe.dynamicinventory.api.InventoryUi;
import dev.gilbe.dynamicinventory.container.ContainerInventoryRegion;
import dev.gilbe.dynamicinventory.container.ExpandedContainerAccess;
import dev.gilbe.dynamicinventory.container.PlayerInventoryTransfers;
import dev.gilbe.dynamicinventory.network.ServerInventoryGate;

@Mixin(Container.class)
public abstract class ContainerRegionMixin implements ExpandedContainerAccess {

    @Unique
    private ContainerInventoryRegion dynamicinventory$region;

    @Shadow
    protected abstract Slot addSlotToContainer(Slot slot);

    @Override
    public ContainerInventoryRegion dynamicinventory$region() {
        return dynamicinventory$region;
    }

    @Override
    public void dynamicinventory$region(ContainerInventoryRegion region) {
        dynamicinventory$region = region;
    }

    @Override
    public Slot dynamicinventory$append(Slot slot) {
        return addSlotToContainer(slot);
    }

    @Inject(method = "addCraftingToCrafters", at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$registeredBeforeSync(ICrafting listener, CallbackInfo ci) {
        if (listener instanceof EntityPlayer)
            InventoryUi.attachRegistered((Container) (Object) this, ((EntityPlayer) listener).inventory);
        if (listener instanceof EntityPlayerMP && ((EntityPlayerMP) listener).playerNetServerHandler != null
            && !ServerInventoryGate.of((EntityPlayerMP) listener)
                .begin((EntityPlayerMP) listener, (Container) (Object) this))
            ci.cancel();
    }

    @Inject(method = "mergeItemStack", at = @At("HEAD"), cancellable = true)
    private void dynamicinventory$merge(ItemStack stack, int start, int end, boolean reverse,
        CallbackInfoReturnable<Boolean> cir) {
        if (dynamicinventory$region == null) return;
        int[] ids = dynamicinventory$region.destinations(start, end, reverse);
        if (ids != null) cir.setReturnValue(PlayerInventoryTransfers.merge((Container) (Object) this, stack, ids));
    }
}
