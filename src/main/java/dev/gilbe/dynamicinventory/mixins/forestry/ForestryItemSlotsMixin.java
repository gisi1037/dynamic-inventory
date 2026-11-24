package dev.gilbe.dynamicinventory.mixins.forestry;

import java.lang.reflect.Method;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.gilbe.dynamicinventory.compat.ForestryCompatibility;

@Pseudo
@Mixin(targets = "forestry.core.gui.ContainerItemInventory", remap = false)
public abstract class ForestryItemSlotsMixin implements ForestryCompatibility.ItemSlots {

    @Unique
    private Object dynamicinventory$owner;
    @Unique
    private Method dynamicinventory$isOwner;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void dynamicinventory$captureOwner(@Coerce Object owner, InventoryPlayer inventory, int x, int y,
        CallbackInfo ci) {
        dynamicinventory$owner = owner;
    }

    @Override
    public Slot dynamicinventory$additionalSlot(InventoryPlayer inventory, int raw) {
        ItemStack stack = inventory.getStackInSlot(raw);
        if (stack != null) {
            try {
                if (dynamicinventory$isOwner == null) dynamicinventory$isOwner = dynamicinventory$owner.getClass()
                    .getMethod("isParentItemInventory", ItemStack.class);
                if ((Boolean) dynamicinventory$isOwner.invoke(dynamicinventory$owner, stack))
                    return Class.forName("forestry.core.gui.slots.SlotLocked")
                        .asSubclass(Slot.class)
                        .getConstructor(IInventory.class, int.class, int.class, int.class)
                        .newInstance(inventory, raw, -10000, -10000);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Cannot preserve Forestry's item inventory lock", failure);
            }
        }
        return new Slot(inventory, raw, -10000, -10000);
    }
}
