package example.dynamicinventory;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.PacketBuffer;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.factory.AbstractUIFactory;
import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.item.InvWrapper;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.SyncHandlers;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import dev.gilbe.dynamicinventory.api.mui2.Mui2InventoryWidgets;

/** Everything here belongs to the addon. Its sole Dynamic Inventory import is the public widget factory. */
public final class ExampleGui extends AbstractUIFactory<GuiData> implements IGuiHolder<GuiData> {

    public ExampleGui() {
        super(InventoryExampleMod.ID + ":inventory");
    }

    @Override
    public IGuiHolder<GuiData> getGuiHolder(GuiData data) {
        return this;
    }

    @Override
    public void writeGuiData(GuiData data, PacketBuffer buffer) {
        // No extra context: MUI2 already knows which player is opening the screen.
    }

    @Override
    public GuiData readGuiData(EntityPlayer player, PacketBuffer buffer) {
        return new GuiData(player);
    }

    @Override
    public ModularPanel buildUI(GuiData data, PanelSyncManager sync, UISettings settings) {
        // Integration step 1: bind the actual player inventory through MUI2's normal API.
        // Dynamic Inventory supplies its expanded canonical mapping for this binding.
        sync.bindPlayerInventory(data.getPlayer());

        // This is ordinary MUI2 machine/storage UI, independent of Dynamic Inventory.
        // Reuse the player's real ender chest so test items persist without a new save system.
        InvWrapper chest = new InvWrapper(
            data.getPlayer()
                .getInventoryEnderChest());
        sync.registerSlotGroup("ender_chest", 9);
        ModularPanel panel = ModularPanel.defaultPanel("inventory_example", 194, 200)
            .child(new TextWidget<>("Third-party API example").pos(8, 7))
            .child(new TextWidget<>("Ender chest").pos(8, 24))
            .child(new TextWidget<>("Inventory").pos(8, 100));
        for (int index = 0; index < chest.getSlots(); index++) {
            panel.child(
                new ItemSlot().slot(
                    SyncHandlers.itemSlot(chest, index)
                        .slotGroup("ender_chest"))
                    .pos(8 + index % 9 * 18, 38 + index / 9 * 18));
        }

        // Integration step 2: use the public live-slot factory instead of hardcoding 36 player slots.
        // MUI2 handles clicks and shift-clicks; the factory supplies responsive storage and the hotbar.
        return panel.child(
            Mui2InventoryWidgets.playerInventory((logicalIndex, slot) -> slot)
                .pos(8, 112));
    }

    @Override
    @SideOnly(Side.CLIENT)
    public ModularScreen createScreen(GuiData data, ModularPanel mainPanel) {
        return new ModularScreen(InventoryExampleMod.ID, mainPanel);
    }
}
