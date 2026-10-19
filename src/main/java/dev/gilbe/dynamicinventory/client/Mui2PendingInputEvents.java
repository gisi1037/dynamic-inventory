package dev.gilbe.dynamicinventory.client;

import org.lwjgl.input.Keyboard;

import com.cleanroommc.modularui.api.event.KeyboardInputEvent;
import com.cleanroommc.modularui.api.event.MouseInputEvent;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

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
        cancelMouseInput(event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void beforeKeyboardInput(KeyboardInputEvent.Pre event) {
        cancelKeyboardInput(event);
    }
}
