package dev.gilbe.dynamicinventory.mixins.minecraft;

import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Rebind only our native Baubles slots when Baubles replaces its canonical inventory. */
@Mixin(Slot.class)
public interface SlotInventoryAccess {

    @Mutable
    @Accessor("inventory")
    void dynamicinventory$setInventory(IInventory inventory);
}
