package dev.gilbe.dynamicinventory.compat.mui2;

import net.minecraft.entity.player.InventoryPlayer;

import com.cleanroommc.modularui.utils.item.PlayerMainInvWrapper;

import dev.gilbe.dynamicinventory.inventory.InventoryLayout;
import dev.gilbe.dynamicinventory.inventory.InventoryShape;

/** Marker for our player binding. All handler indices retain MUI2's native raw-inventory contract. */
public final class Mui2PlayerInventory extends PlayerMainInvWrapper {

    private final InventoryShape shape;

    public Mui2PlayerInventory(InventoryPlayer inventory) {
        super(inventory);
        shape = InventoryLayout.of(inventory);
    }

    public InventoryShape getInventoryShape() {
        return shape;
    }
}
