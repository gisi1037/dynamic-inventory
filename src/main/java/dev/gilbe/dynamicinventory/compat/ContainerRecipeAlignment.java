package dev.gilbe.dynamicinventory.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;

import codechicken.nei.recipe.GuiRecipe;
import dev.gilbe.dynamicinventory.client.ContainerPanelAccess;
import dev.gilbe.dynamicinventory.client.NativeInventoryPanel;

public final class ContainerRecipeAlignment {

    private ContainerRecipeAlignment() {}

    public static NativeInventoryPanel currentPanel() {
        GuiScreen gui = Minecraft.getMinecraft().currentScreen;
        if (gui instanceof GuiRecipe<?>) gui = ((GuiRecipe<?>) gui).firstGui;
        return gui instanceof ContainerPanelAccess ? ((ContainerPanelAccess) gui).dynamicinventory$panel() : null;
    }

    public static int[] craftingOrigin(GuiContainer gui) {
        if (((ContainerPanelAccess) gui).dynamicinventory$panel() == null) return null;
        String name = gui.getClass()
            .getName();
        if (name.equals("net.minecraft.client.gui.inventory.GuiCrafting")
            || name.equals("tconstruct.tools.gui.CraftingStationGui"))
            return new int[] { gui.inventorySlots.getSlot(1).xDisplayPosition - 25,
                gui.inventorySlots.getSlot(1).yDisplayPosition - 6 };
        if (name.equals("net.minecraft.client.gui.inventory.GuiFurnace")) return new int[] {
            gui.inventorySlots.getSlot(0).xDisplayPosition - 51, gui.inventorySlots.getSlot(0).yDisplayPosition - 6 };
        return null;
    }
}
