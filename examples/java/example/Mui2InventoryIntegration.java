package example;

import net.minecraft.entity.player.EntityPlayer;

import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;

import dev.gilbe.dynamicinventory.api.mui2.Mui2InventoryWidgets;

/** Minimal examples for a developer-owned MUI2 panel with live player slots. */
public final class Mui2InventoryIntegration {

    private Mui2InventoryIntegration() {}

    public static ModularPanel buildResponsive(EntityPlayer player, PanelSyncManager sync) {
        sync.bindPlayerInventory(player);
        return ModularPanel.defaultPanel("example_inventory", 320, 240)
            .child(Mui2InventoryWidgets.playerInventory((logicalIndex, slot) -> slot, player.inventory).pos(20, 20));
    }

    /** Caller-owned fixed geometry: 12 storage columns and at most 6 visible storage rows. */
    public static ModularPanel buildFixed(EntityPlayer player, PanelSyncManager sync) {
        sync.bindPlayerInventory(player);
        return ModularPanel.defaultPanel("example_inventory_fixed", 320, 240)
            .child(Mui2InventoryWidgets.playerInventory((logicalIndex, slot) -> slot, 12, 6, player.inventory).pos(20, 20));
    }
}
