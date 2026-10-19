package dev.gilbe.dynamicinventory.compat.mui1;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import com.gtnewhorizons.modularui.api.forge.PlayerMainInvWrapper;

import dev.gilbe.dynamicinventory.inventory.InventoryLayout;

/** Native handler type is retained for framework transfer/sorting classification; logical indices skip Backhand. */
public final class Mui1PlayerInventory extends PlayerMainInvWrapper {

    private final PlayerMainInvWrapper canonical;

    public Mui1PlayerInventory(InventoryPlayer inventory) {
        super(inventory);
        canonical = new PlayerMainInvWrapper(inventory);
    }

    @Override
    public int getSlots() {
        return InventoryLayout.mainSlots();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return canonical.getStackInSlot(InventoryLayout.rawIndex(slot));
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        canonical.setStackInSlot(InventoryLayout.rawIndex(slot), stack);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        return canonical.insertItem(InventoryLayout.rawIndex(slot), stack, simulate);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return canonical.extractItem(InventoryLayout.rawIndex(slot), amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
        return canonical.getSlotLimit(InventoryLayout.rawIndex(slot));
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return canonical.isItemValid(InventoryLayout.rawIndex(slot), stack);
    }

    @Override
    public IInventory getSourceInventory() {
        return getInventoryPlayer();
    }
}
