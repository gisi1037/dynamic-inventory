package dev.gilbe.dynamicinventory.client;

import java.util.List;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;

import cpw.mods.fml.common.Loader;
import dev.gilbe.dynamicinventory.compat.mui2.NativeInventoryPresentation;

/** Optional presentation backend. The owner retains its native container, items and input handling. */
public final class InventoryPresentation {

    private final GuiContainer owner;
    private NativeInventoryPresentation mui2;

    public InventoryPresentation(GuiContainer owner) {
        this.owner = owner;
    }

    public static boolean available() {
        return Loader.isModLoaded("modularui2");
    }

    private boolean enabled() {
        return InventoryViewSettings.mui2Presentation && available();
    }

    private NativeInventoryPresentation renderer() {
        if (mui2 == null) mui2 = new NativeInventoryPresentation(owner);
        return mui2;
    }

    public boolean frame(int x, int y, int width, int height, boolean playerStyle) {
        if (!enabled()) return false;
        renderer().frame(x, y, width, height, playerStyle);
        return true;
    }

    public boolean slots(List<Slot> slots) {
        if (!enabled()) return false;
        renderer().slots(slots);
        return true;
    }

    public String status() {
        return "enabled=" + enabled() + (mui2 == null ? " initialized=false" : " " + mui2.status());
    }

    public void dispose() {
        if (mui2 != null) {
            mui2.dispose();
            mui2 = null;
        }
    }
}
