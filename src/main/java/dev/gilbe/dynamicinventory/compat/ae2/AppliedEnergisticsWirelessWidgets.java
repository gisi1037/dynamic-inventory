package dev.gilbe.dynamicinventory.compat.ae2;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;

import dev.gilbe.dynamicinventory.client.ContainerPanelAccess;

/** Keep the universal terminal's original controls beside the browser, including hidden state variants. */
public final class AppliedEnergisticsWirelessWidgets {

    private AppliedEnergisticsWirelessWidgets() {}

    public static void position(GuiContainer gui, int left, int top, int bottom) {
        boolean interfaceTerminal = gui.getClass()
            .getName()
            .equals("appeng.client.gui.implementations.GuiInterfaceTerminal");
        if (!(gui instanceof AppliedEnergisticsBrowserAccess) && !interfaceTerminal) return;
        ContainerPanelAccess access = (ContainerPanelAccess) gui;
        List<GuiButton> magnet = new ArrayList<>(), filter = new ArrayList<>(), restock = new ArrayList<>(),
            modes = new ArrayList<>();
        int outer = left - 36, next = top + 8, primaryNext = top + 8;
        for (GuiButton button : access.dynamicinventory$buttons()) {
            String setting = setting(button, "getSetting");
            if ("MAGNET_CARD".equals(setting)) {
                ("FILTER".equals(setting(button, "getCurrentValue")) ? filter : magnet).add(button);
            } else if ("RESTOCK".equals(setting)) restock.add(button);
            else if (isMode(setting)) modes.add(button);
            else if (button.visible) {
                if (button.xPosition == outer)
                    next = Math.max(next, button.yPosition + Math.max(20, button.height + 4));
                if (button.xPosition == left - 18)
                    primaryNext = Math.max(primaryNext, button.yPosition + Math.max(20, button.height + 4));
            }
        }
        if (magnet.isEmpty() && filter.isEmpty() && restock.isEmpty() && modes.isEmpty()) return;
        List<List<GuiButton>> outerRows = new ArrayList<>();
        for (List<GuiButton> row : java.util.Arrays.asList(magnet, filter, restock))
            if (!row.isEmpty()) outerRows.add(row);
        long visibleModes = modes.stream()
            .filter(button -> button.visible)
            .count();
        modes.sort(java.util.Comparator.comparingInt(button -> modeOrder(setting(button, "getSetting"))));
        boolean modesOutside = primaryNext + visibleModes * 20 > bottom;
        // Visibility can change after initGui when the server sends the actual terminal mode.
        // Reflow from that live state; do not restore overlapping initialization coordinates.
        for (GuiButton button : modes) {
            if (modesOutside) {
                if (button.visible) outerRows.add(java.util.Collections.singletonList(button));
                else {
                    button.xPosition = outer;
                    button.yPosition = top + 8;
                }
            } else {
                button.xPosition = left - 18;
                button.yPosition = primaryNext;
                if (button.visible) primaryNext += 20;
            }
        }
        int column = outer;
        for (List<GuiButton> row : outerRows) {
            int height = row.stream()
                .mapToInt(button -> button.height)
                .max()
                .orElse(16);
            if (next + height > bottom) {
                column -= 18;
                next = top + 8;
            }
            for (GuiButton button : row) {
                // Move each native button and its alternate states together, retaining native callbacks and hitboxes.
                button.xPosition = column;
                button.yPosition = next;
            }
            next += Math.max(20, height + 4);
        }
    }

    private static boolean isMode(String setting) {
        return "CRAFT_TEM".equals(setting) || "PATTERN_TEM".equals(setting)
            || "PATTERN_EX_TEM".equals(setting)
            || "INTERFACE_TEM".equals(setting)
            || "LEVEL_TEM".equals(setting);
    }

    private static int modeOrder(String setting) {
        if ("CRAFT_TEM".equals(setting)) return 0;
        if ("PATTERN_TEM".equals(setting)) return 1;
        if ("PATTERN_EX_TEM".equals(setting)) return 2;
        if ("INTERFACE_TEM".equals(setting)) return 3;
        return 4;
    }

    private static String setting(GuiButton button, String getter) {
        if (!button.getClass()
            .getName()
            .equals("com.glodblock.github.client.gui.GuiFCImgButton")) return null;
        try {
            return (String) button.getClass()
                .getMethod(getter)
                .invoke(button);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
