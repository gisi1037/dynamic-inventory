package dev.gilbe.dynamicinventory.mixins;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.cleanroommc.bogosorter.common.refill.RefillHandler;

import dev.gilbe.dynamicinventory.container.PlayerContainerLayout;
import dev.gilbe.dynamicinventory.inventory.InventoryLayout;
import it.unimi.dsi.fastutil.ints.IntList;

@Mixin(targets = "com.cleanroommc.bogosorter.common.refill.RefillHandler", remap = false)
public abstract class BogoRefillMixin {

    @Shadow
    private IntList slots;
    @Shadow
    private EntityPlayer player;
    @Shadow
    private InventoryPlayer inventory;

    @Inject(
        method = "<init>(ILnet/minecraft/item/ItemStack;Lnet/minecraft/entity/player/EntityPlayer;ZZ)V",
        at = @At("RETURN"))
    private void dynamicinventory$extraCandidates(int index, ItemStack stack, EntityPlayer owner, boolean swap,
        boolean allowPinned, CallbackInfo ci) {
        // Retain the original proximity order, then consider every added ordinary slot.
        for (int logical = InventoryLayout.LEGACY_MAIN_SLOTS; logical < InventoryLayout.mainSlots(); logical++) {
            slots.add(InventoryLayout.rawIndex(logical));
        }
    }

    @Redirect(
        method = "refillItem",
        at = @At(
            value = "INVOKE",
            target = "Lcom/cleanroommc/bogosorter/common/refill/RefillHandler;setAndSyncSlot(ILnet/minecraft/item/ItemStack;)V",
            ordinal = 1))
    private void dynamicinventory$ordinarySource(RefillHandler handler, int raw, ItemStack stack) {
        // The source is an ordinary raw index; it must never use Bogo's legacy 36..39 armor interpretation.
        int logical = InventoryLayout.ordinaryIndex(raw);
        if (logical < 0) throw new IllegalStateException("Refill source is not ordinary player storage");
        inventory.mainInventory[raw] = stack;
        if (stack != null)
            player.inventoryContainer.inventoryItemStacks.set(PlayerContainerLayout.containerId(logical), null);
        inventory.markDirty();
    }
}
