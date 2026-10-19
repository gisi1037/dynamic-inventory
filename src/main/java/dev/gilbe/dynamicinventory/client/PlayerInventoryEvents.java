package dev.gilbe.dynamicinventory.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import dev.gilbe.dynamicinventory.api.InventoryUi;
import dev.gilbe.dynamicinventory.container.ContainerInventoryRegion;

public final class PlayerInventoryEvents {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void beforeInit(GuiScreenEvent.InitGuiEvent.Pre event) {
        if (!(event.gui instanceof GuiContainer)) return;
        if (Minecraft.getMinecraft().thePlayer != null) InventoryUi
            .attachRegistered(((GuiContainer) event.gui).inventorySlots, Minecraft.getMinecraft().thePlayer.inventory);
        ContainerPanelAccess access = (ContainerPanelAccess) event.gui;
        if (access.dynamicinventory$panel() != null) access.dynamicinventory$panel()
            .restore();
        access.dynamicinventory$panel(null);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void afterInit(GuiScreenEvent.InitGuiEvent.Post event) {
        if (!(event.gui instanceof GuiContainer)) return;
        GuiContainer gui = (GuiContainer) event.gui;
        if (ContainerInventoryRegion.of(gui.inventorySlots) != null
            && InventoryUi.findLayout(gui.inventorySlots, gui.getClass()) != null)
            ((ContainerPanelAccess) gui).dynamicinventory$panel(new NativeInventoryPanel(gui));
    }

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        if (event.gui != null && event.gui.getClass() == GuiInventory.class
            && Minecraft.getMinecraft().thePlayer != null) {
            // Native inventory tabs may display GuiInventory directly after sending
            // their server close packet. Normalize the local container as well so
            // prediction and clicks use the same persistent window as this screen.
            if (Minecraft.getMinecraft().thePlayer.openContainer
                != Minecraft.getMinecraft().thePlayer.inventoryContainer)
                Minecraft.getMinecraft().thePlayer.closeScreen();
            event.gui = new PlayerInventoryScreen(Minecraft.getMinecraft().thePlayer);
        }
    }
}
