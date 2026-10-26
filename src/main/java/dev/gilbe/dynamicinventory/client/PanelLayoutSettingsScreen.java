package dev.gilbe.dynamicinventory.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import dev.gilbe.dynamicinventory.client.editor.NativeLayoutPatches;

/** Player-facing profile management. Resetting overrides preserves the player's global preference. */
public final class PanelLayoutSettingsScreen extends GuiScreen {

    private final GuiScreen parent;
    private final List<String> groups = new ArrayList<>();
    private boolean groupMenu;
    private int page, confirm = -1;
    private String status = "";

    public PanelLayoutSettingsScreen(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        groups.clear();
        int x = width / 2 - 110, y = height / 2 - 94;
        if (groupMenu) {
            try {
                Map<String, Integer> found = new TreeMap<>(NativeLayoutPatches.groups());
                for (Map.Entry<String, Integer> group : PlayerPanelProfiles.groups()
                    .entrySet()) found.merge(group.getKey(), group.getValue(), Integer::sum);
                groups.addAll(found.keySet());
            } catch (IOException failure) {
                status = "Could not read saved layouts";
            }
            page = Math.min(page, Math.max(0, (groups.size() - 1) / 5));
            for (int i = page * 5; i < Math.min(groups.size(), page * 5 + 5); i++) buttonList.add(
                new GuiButton(
                    100 + i,
                    x,
                    y + 28 + (i % 5) * 24,
                    220,
                    20,
                    confirm == 100 + i ? "Confirm: " + groups.get(i) : "Reset " + groups.get(i)));
            if (groups.size() > 5) {
                buttonList.add(new GuiButton(4, x, y + 152, 60, 20, "<"));
                buttonList.add(new GuiButton(5, x + 160, y + 152, 60, 20, ">"));
            }
        } else {
            buttonList.add(
                new GuiButton(
                    1,
                    x,
                    y + 28,
                    220,
                    20,
                    confirm == 1 ? "Confirm reset all overrides" : "Reset all machine overrides"));
            buttonList.add(
                new GuiButton(
                    2,
                    x,
                    y + 54,
                    220,
                    20,
                    confirm == 2 ? "Confirm reset global panel" : "Reset global panel default"));
            buttonList.add(new GuiButton(3, x, y + 80, 220, 20, "Reset mod groups..."));
        }
        buttonList.add(new GuiButton(0, x, y + 178, 220, 20, "Back"));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            if (groupMenu) {
                groupMenu = false;
                confirm = -1;
                initGui();
            } else mc.displayGuiScreen(parent);
            return;
        }
        if (button.id == 3) {
            groupMenu = true;
            confirm = -1;
            status = "";
            initGui();
            return;
        }
        if (button.id == 4 || button.id == 5) {
            page = Math.max(0, Math.min(Math.max(0, (groups.size() - 1) / 5), page + (button.id == 4 ? -1 : 1)));
            confirm = -1;
            initGui();
            return;
        }
        if (confirm != button.id) {
            confirm = button.id;
            initGui();
            return;
        }
        try {
            if (button.id == 2) {
                PlayerPanelProfiles.resetGlobal();
                status = "Global panel default: attached";
            } else {
                String group = button.id >= 100 ? groups.get(button.id - 100) : null;
                NativeLayoutPatches.reset(group);
                PlayerPanelProfiles.reset(group);
                status = "Overrides reset to global default";
            }
        } catch (IOException failure) {
            status = "Reset failed; backups retained";
        }
        confirm = -1;
        initGui();
    }

    @Override
    protected void keyTyped(char character, int key) {
        if (key == org.lwjgl.input.Keyboard.KEY_ESCAPE) actionPerformed(new GuiButton(0, 0, 0, ""));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float ticks) {
        drawDefaultBackground();
        int y = height / 2 - 94;
        drawCenteredString(
            fontRendererObj,
            groupMenu ? "Reset mod layouts" : "Inventory panel layouts",
            width / 2,
            y + 8,
            0xffffff);
        if (!groupMenu) {
            drawCenteredString(fontRendererObj, "Resets keep your inventory items.", width / 2, y + 112, 0xcccccc);
            drawCenteredString(fontRendererObj, "Editor layouts are backed up.", width / 2, y + 126, 0xcccccc);
        } else if (groups.isEmpty())
            drawCenteredString(fontRendererObj, "No saved overrides", width / 2, y + 58, 0xcccccc);
        drawCenteredString(fontRendererObj, status, width / 2, y + 164, 0xffffaa);
        super.drawScreen(mouseX, mouseY, ticks);
    }
}
