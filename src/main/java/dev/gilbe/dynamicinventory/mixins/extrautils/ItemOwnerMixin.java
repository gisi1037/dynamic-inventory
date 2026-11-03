package dev.gilbe.dynamicinventory.mixins.extrautils;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

import dev.gilbe.dynamicinventory.compat.extrautils.ExtraUtilitiesItemAccess;

/** Preserve the native owner lock when an item UI was opened from expanded storage. */
@Pseudo
@Mixin(
    targets = { "com.rwtema.extrautils.gui.ContainerHoldingBag", "com.rwtema.extrautils.gui.ContainerFilter" },
    remap = false)
public abstract class ItemOwnerMixin implements ExtraUtilitiesItemAccess {

    @Shadow
    private int currentFilter;

    @Override
    public int dynamicinventory$ownerSlot() {
        return currentFilter;
    }
}
