package dev.gilbe.dynamicinventory.client;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;

import org.lwjgl.input.Keyboard;

import dev.gilbe.dynamicinventory.inventory.InventoryLayout;
import dev.gilbe.dynamicinventory.inventory.InventoryShape;
import dev.gilbe.dynamicinventory.upgrades.CreativeSizeRequest;

/** Small numeric editor opened by the held UHV item; never replaces an active inventory container. */
public final class InventoryControllerScreen extends GuiScreen {

    private GuiTextField size;
    private String error = "";

    @Override
    public void initGui() {
        int x = width / 2 - 100, y = height / 2 - 58;
        size = new GuiTextField(fontRendererObj, x + 12, y + 35, 176, 20);
        size.setMaxStringLength(3);
        size.setText(
            Integer.toString(
                InventoryLayout.of(mc.thePlayer.inventory)
                    .storageSlots()));
        size.setFocused(true);
        buttonList.clear();
        buttonList.add(new GuiButton(1, x + 12, y + 62, 84, 20, "Apply"));
        buttonList.add(new GuiButton(0, x + 104, y + 62, 84, 20, "Cancel"));
        buttonList.add(new GuiButton(2, x + 12, y + 88, 176, 20, "Resume belt bonuses"));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            mc.displayGuiScreen(null);
            return;
        }
        int target = 0;
        if (button.id == 1) {
            try {
                target = Integer.parseInt(size.getText());
            } catch (NumberFormatException invalid) {
                error = "Enter a whole number";
                return;
            }
            if (target < InventoryShape.MIN_STORAGE || target > InventoryShape.MAX_STORAGE) {
                error = "Storage must be 27-216 slots";
                return;
            }
        }
        CreativeSizeRequest.send(target);
        mc.displayGuiScreen(null);
    }

    @Override
    protected void keyTyped(char character, int key) {
        if (key == Keyboard.KEY_ESCAPE) mc.displayGuiScreen(null);
        else if (key == Keyboard.KEY_RETURN) actionPerformed((GuiButton) buttonList.get(0));
        else if (Character.isDigit(character) || key == Keyboard.KEY_BACK
            || key == Keyboard.KEY_DELETE
            || key == Keyboard.KEY_LEFT
            || key == Keyboard.KEY_RIGHT) size.textboxKeyTyped(character, key);
    }

    @Override
    protected void mouseClicked(int x, int y, int button) {
        size.mouseClicked(x, y, button);
        super.mouseClicked(x, y, button);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void drawScreen(int x, int y, float ticks) {
        drawDefaultBackground();
        int left = width / 2 - 100, top = height / 2 - 58;
        drawRect(left, top, left + 200, top + 116, InventoryGuiTheme.get().settingsFill);
        drawCenteredString(
            fontRendererObj,
            "UHV Inventory Controller",
            width / 2,
            top + 8,
            InventoryGuiTheme.get().settingsText);
        drawCenteredString(
            fontRendererObj,
            error.isEmpty() ? "Total storage (hotbar excluded)" : error,
            width / 2,
            top + 23,
            0xffffff);
        size.drawTextBox();
        super.drawScreen(x, y, ticks);
    }
}
