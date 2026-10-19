package dev.gilbe.dynamicinventory.qualification;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;

import com.cleanroommc.bogosorter.mixins.early.minecraft.GuiScreenAccessor;
import com.google.gson.JsonObject;

import dev.gilbe.dynamicinventory.client.PlayerInventoryScreen;

/** Client-thread fixture for foreign buttons that reuse Dynamic Inventory's numeric IDs. */
public final class InventoryScreenFixtures {

    private InventoryScreenFixtures() {}

    @SuppressWarnings("unchecked")
    public static void run() throws Exception {
        Minecraft minecraft = Minecraft.getMinecraft();
        PlayerInventoryScreen screen = (PlayerInventoryScreen) minecraft.currentScreen;
        if (screen.layoutSettingsOpen())
            throw new IllegalStateException("Close layout settings before testing button ownership");
        List<GuiButton> buttons = ((GuiScreenAccessor) (Object) screen).getButtonList();
        Field integrationField = PlayerInventoryScreen.class.getDeclaredField("integrationButtons");
        integrationField.setAccessible(true);
        List<GuiButton> integrations = (List<GuiButton>) integrationField.get(screen);
        Method action = PlayerInventoryScreen.class.getDeclaredMethod("actionPerformed", GuiButton.class);
        action.setAccessible(true);
        Method position = PlayerInventoryScreen.class.getDeclaredMethod("positionButtons");
        position.setAccessible(true);
        List<GuiButton> foreign = new ArrayList<>();
        try {
            for (int id = 100; id <= 112; id++) {
                GuiButton button = new GuiButton(id, id + 17, id + 23, 10, 10, "foreign");
                button.enabled = false;
                foreign.add(button);
                buttons.add(button);
            }
            screen.initGui();
            screen.initGui();
            position.invoke(screen);
            for (GuiButton button : foreign) {
                check(buttons.contains(button), "foreign button survives relayout for ID " + button.id);
                check(
                    button.xPosition == button.id + 17 && button.yPosition == button.id + 23,
                    "foreign button position remains owned by its mod for ID " + button.id);
                check(!button.enabled, "foreign button enabled state remains unchanged for ID " + button.id);
                action.invoke(screen, button);
                check(!screen.layoutSettingsOpen(), "foreign button is not dispatched as our layout control");
            }
            JsonObject report = new JsonObject();
            report.addProperty("success", true);
            report.addProperty("foreignButtons", foreign.size());
            QualificationCommand.write(minecraft.thePlayer, "client-screen-button-ownership", report);
        } finally {
            buttons.removeAll(foreign);
            integrations.removeAll(foreign);
            screen.initGui();
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
