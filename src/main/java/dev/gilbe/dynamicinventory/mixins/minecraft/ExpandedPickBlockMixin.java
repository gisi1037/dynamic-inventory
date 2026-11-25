package dev.gilbe.dynamicinventory.mixins.minecraft;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeHooks;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.sugar.Local;

import dev.gilbe.dynamicinventory.container.PlayerContainerLayout;
import dev.gilbe.dynamicinventory.inventory.HotbarBanks;
import dev.gilbe.dynamicinventory.inventory.InventoryLayout;
import dev.gilbe.dynamicinventory.inventory.InventoryShape;
import dev.gilbe.dynamicinventory.inventory.InventorySlotLocks;

@Mixin(value = ForgeHooks.class, priority = 900, remap = false)
public abstract class ExpandedPickBlockMixin {

    // Same survival-miss return used by Hodgepodge. Its original hotbar and storage search runs first.
    @Inject(method = "onPickBlock", at = @At(value = "RETURN", ordinal = 4), cancellable = true)
    private static void dynamicinventory$pickExpandedStorage(MovingObjectPosition target, EntityPlayer player,
        World world, CallbackInfoReturnable<Boolean> cir, @Local(name = "result") ItemStack result) {
        Minecraft mc = Minecraft.getMinecraft();
        if (result == null || player != mc.thePlayer
            || mc.playerController == null
            || player.openContainer != player.inventoryContainer
            || player.inventory.getItemStack() != null) return;
        InventoryPlayer inventory = player.inventory;
        InventoryShape shape = InventoryLayout.of(inventory);
        for (int logical = InventoryShape.LEGACY_MAIN_SLOTS; logical < shape.mainSlots(); logical++) {
            int raw = shape.rawIndex(logical);
            if (HotbarBanks.isReserved(inventory, raw) || InventorySlotLocks.locked(inventory, raw)) continue;
            ItemStack stack = inventory.getStackInSlot(raw);
            if (stack == null || !stack.isItemEqual(result) || !ItemStack.areItemStackTagsEqual(stack, result))
                continue;
            int hotbar = dynamicinventory$chooseHotbar(inventory, raw, stack);
            if (hotbar < 0) return;
            int source = PlayerContainerLayout.containerId(inventory, logical);
            // Number-key swap is one ordinary, predicted window click. The server moves both stacks together.
            mc.playerController.windowClick(player.inventoryContainer.windowId, source, hotbar, 2, player);
            ItemStack selected = inventory.getStackInSlot(hotbar);
            if (selected != null && selected.isItemEqual(result) && ItemStack.areItemStackTagsEqual(selected, result)) {
                inventory.currentItem = hotbar;
                cir.setReturnValue(true);
            }
            return;
        }
    }

    @Unique
    private static int dynamicinventory$chooseHotbar(InventoryPlayer inventory, int source, ItemStack item) {
        int ordinary = -1;
        int fallback = -1;
        for (int offset = 0; offset < InventoryShape.HOTBAR_SLOTS; offset++) {
            int candidate = (inventory.currentItem + offset) % InventoryShape.HOTBAR_SLOTS;
            ItemStack stack = inventory.getStackInSlot(candidate);
            if (!InventorySlotLocks.accepts(inventory, candidate, item)
                || !InventorySlotLocks.accepts(inventory, source, stack)) continue;
            if (stack == null) return candidate;
            if (candidate == inventory.currentItem) fallback = candidate;
            if (ordinary < 0 && !stack.isItemEnchanted()) ordinary = candidate;
        }
        return ordinary < 0 ? fallback : ordinary;
    }

    @Inject(
        method = "onPickBlock",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/entity/player/InventoryPlayer;setInventorySlotContents(ILnet/minecraft/item/ItemStack;)V",
            remap = true),
        cancellable = true)
    private static void dynamicinventory$protectCreativePick(MovingObjectPosition target, EntityPlayer player,
        World world, CallbackInfoReturnable<Boolean> result, @Local(ordinal = 0) int destination,
        @Local(name = "result") ItemStack stack) {
        if (!InventorySlotLocks.accepts(player.inventory, destination, stack)) result.setReturnValue(false);
    }
}
