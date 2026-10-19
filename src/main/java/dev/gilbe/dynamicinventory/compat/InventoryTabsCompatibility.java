package dev.gilbe.dynamicinventory.compat;

import java.lang.reflect.Method;
import java.util.List;

import net.minecraft.client.gui.GuiButton;

import cpw.mods.fml.common.Loader;

/** Optional native tab registry: TiC owns registration, visibility, icons and clicks. */
public final class InventoryTabsCompatibility {

    private static Class<?> tabClass;
    private static Class<?> vanillaTab;
    private static Method tabs;
    private static Method update;
    private static Method add;
    private static Method visible;

    private InventoryTabsCompatibility() {}

    private static boolean available() {
        if (!PackCompatibility.enabled() || !Loader.isModLoaded("TConstruct")) return false;
        if (tabClass == null) {
            try {
                Class<?> registry = Class.forName("tconstruct.client.tabs.TabRegistry");
                tabClass = Class.forName("tconstruct.client.tabs.AbstractTab");
                vanillaTab = Class.forName("tconstruct.client.tabs.InventoryTabVanilla");
                tabs = registry.getMethod("getTabList");
                update = registry.getMethod("updateTabValues", int.class, int.class, Class.class);
                add = registry.getMethod("addTabsToList", List.class);
                visible = tabClass.getMethod("shouldAddToList");
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Unable to bind the RC-2 inventory tab registry", e);
            }
        }
        return true;
    }

    public static int topSpace() {
        if (!available()) return 0;
        try {
            int count = 0;
            for (Object tab : (List<?>) tabs.invoke(null)) if ((boolean) visible.invoke(tab)) count++;
            return count > 1 ? 28 : 0;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to query native inventory tabs", e);
        }
    }

    public static boolean registeredTab(GuiButton button) {
        return available() && tabClass.isInstance(button);
    }

    public static void addTabs(List<GuiButton> buttons, int left, int top) {
        if (topSpace() == 0) return;
        try {
            update.invoke(null, left, top, vanillaTab);
            add.invoke(null, buttons);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to attach native inventory tabs", e);
        }
    }

    public static void positionTabs(List<GuiButton> buttons, int left, int top) {
        if (!available()) return;
        int column = 0;
        for (GuiButton button : buttons) {
            if (!tabClass.isInstance(button)) continue;
            button.xPosition = left + column++ * 28;
            button.yPosition = top - 28;
        }
        // Backpack appends its own native tab during Forge's outer Post event.
        // Keep that button on the same row when a layout changes without that event.
        for (GuiButton button : buttons) {
            if (!button.getClass()
                .getName()
                .startsWith("de.eydamos.backpack.gui.tab.")) continue;
            button.xPosition = left + column++ * 28;
            button.yPosition = top - 28;
        }
    }
}
