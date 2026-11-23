package dev.gilbe.dynamicinventory.client;

import net.minecraft.client.Minecraft;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import com.cleanroommc.modularui.api.IMuiScreen;
import com.cleanroommc.modularui.api.event.KeyboardInputEvent;
import com.cleanroommc.modularui.api.event.MouseInputEvent;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import dev.gilbe.dynamicinventory.compat.PackCompatibility;
import dev.gilbe.dynamicinventory.compat.mui2.Mui2PanelAccess;

/** Registered only when ModularUI2 is loaded; runs before its GUI input dispatchers. */
public final class Mui2PendingInputEvents {

    /** Returns true and cancels the event only for a registered pending native window. */
    public static boolean cancelMouseInput(MouseInputEvent.Pre event) {
        if (!ClientInputGuard.isPending(event.gui)) return false;
        event.setCanceled(true);
        return true;
    }

    /** Escape remains available to close an uncommitted screen; every other key is frozen. */
    public static boolean cancelKeyboardInput(KeyboardInputEvent.Pre event) {
        if (!ClientInputGuard.isPending(event.gui)
            || (Keyboard.getEventKey() == Keyboard.KEY_ESCAPE && Keyboard.getEventKeyState())) return false;
        event.setCanceled(true);
        return true;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void beforeMouseInput(MouseInputEvent.Pre event) {
        if (cancelMouseInput(event)) return;
        if (!(event.gui instanceof IMuiScreen mui) || !(mui.getScreen() instanceof Mui2PanelAccess access)) {
            DetachedChestPanel panel = DetachedChestPanel.of(event.gui);
            if (panel != null && !PanelSettingsOverlay.isOpen(event.gui)) {
                if (PackCompatibility.aePinnedPanel() && AePinnedPanel.mouse(event.gui)) {
                    event.setCanceled(true);
                    return;
                }
                Minecraft mc = Minecraft.getMinecraft();
                int x = Mouse.getEventX() * event.gui.width / mc.displayWidth;
                int y = event.gui.height - Mouse.getEventY() * event.gui.height / mc.displayHeight - 1;
                if (panel.resizeMouse(x, y, Mouse.getEventButton(), Mouse.getEventButtonState()))
                    event.setCanceled(true);
            }
            return;
        }
        var screen = mui.getScreen();
        var panels = access.dynamicinventory$detachedPanels();
        panels.update(screen);
        if (PanelSettingsOverlay.handleMouse(event.gui) || NativeInventoryPanel.handlePointer(event.gui)
            || NativeInventoryPanel.handleWheel(event.gui)) {
            event.setCanceled(true);
            panels.invalidateHover();
            panels.update(screen);
        } else {
            Minecraft mc = Minecraft.getMinecraft();
            int x = Mouse.getEventX() * event.gui.width / mc.displayWidth;
            int y = event.gui.height - Mouse.getEventY() * event.gui.height / mc.displayHeight - 1;
            panels.refreshHover(screen, x, y);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void beforeKeyboardInput(KeyboardInputEvent.Pre event) {
        if (cancelKeyboardInput(event)) return;
        if (PanelSettingsOverlay.handleKeyboard(event.gui)) {
            event.setCanceled(true);
            return;
        }
        DetachedChestPanel panel = DetachedChestPanel.of(event.gui);
        if (panel != null && Keyboard.getEventKey() == Keyboard.KEY_ESCAPE
            && Keyboard.getEventKeyState()
            && panel.cancel()) event.setCanceled(true);
    }
}
