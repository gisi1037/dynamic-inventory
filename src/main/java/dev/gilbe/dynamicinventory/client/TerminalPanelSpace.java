package dev.gilbe.dynamicinventory.client;

import net.minecraft.client.gui.inventory.GuiContainer;

import appeng.api.config.TerminalStyle;
import appeng.client.gui.implementations.GuiCraftingTerm;
import dev.gilbe.dynamicinventory.api.InventoryUi;
import dev.gilbe.dynamicinventory.api.InventoryUiAdapter;
import dev.gilbe.dynamicinventory.client.editor.NativeLayoutPatch;
import dev.gilbe.dynamicinventory.compat.ae2.AppliedEnergisticsBackground;

/** Resolve saved panel preferences during native terminal initialization, before the decorator exists. */
public final class TerminalPanelSpace {

    private TerminalPanelSpace() {}

    public static int rows(GuiContainer gui, NativeLayoutPatch patch, int available, int fixed, TerminalStyle style) {
        boolean crafting = gui instanceof GuiCraftingTerm || AppliedEnergisticsBackground.WIRELESS_CRAFTING_GUI.equals(
            gui.getClass()
                .getName());
        int requestedRows = InventoryViewSettings.terminalRows(crafting, switch (style) {
            case SMALL -> 0;
            case TALL -> 1;
            case FULL -> 2;
        });
        InventoryUiAdapter adapter = InventoryUi.findAdapter(gui.inventorySlots);
        if (adapter == null) return TerminalViewportLayout.terminalRows(available, fixed, requestedRows);
        String key = PlayerPanelProfiles.key(
            adapter.getId(),
            gui.inventorySlots.getClass()
                .getName(),
            gui.getClass()
                .getName());
        PlayerPanelProfile main = PlayerPanelProfiles.resolve(key);
        int margin = Math.max(
            main.minimumMargin,
            (main.frameStyle < 0 ? patch.value(16) + patch.value(18) : main.borderWidth + main.frameCrop) + 4);
        return rows(main, gui.width, margin, available, fixed, requestedRows);
    }

    public static int rows(PlayerPanelProfile main, int screenWidth, int margin, int available, int fixed,
        int requestedRows) {
        if (!main.detached) return TerminalViewportLayout.terminalRows(available, fixed, requestedRows);
        int width = Math.max(1, Math.min(screenWidth, main.width));
        int minimum = main.visible ? InventoryPanelLayout.minimumHeight(width, Math.min(18, margin), true) : 0;
        // Existing native budgets include 42px of attached player chrome; the detached frame already includes it.
        return TerminalViewportLayout.detachedRows(available, fixed - 42, minimum, requestedRows);
    }
}
