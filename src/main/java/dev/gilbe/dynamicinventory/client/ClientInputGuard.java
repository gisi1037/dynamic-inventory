package dev.gilbe.dynamicinventory.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;

import org.lwjgl.input.Keyboard;

import dev.gilbe.dynamicinventory.api.InventoryUi;

/** Shared pending-window input guard for ModularUI2 events and the vanilla fallback mixin. */
public final class ClientInputGuard {

    private ClientInputGuard() {}

    /** Resize agreement locks all inventory screens; ordinary opening checks remain adapter-specific. */
    public static boolean isPending(GuiScreen screen) {
        Minecraft client = Minecraft.getMinecraft();
        EntityPlayer player = client.thePlayer;
        if (screen == null || screen != client.currentScreen || player == null || player.openContainer == null)
            return false;
        if (client.getNetHandler() != null && !((ClientGateAccess) client.getNetHandler()).dynamicinventory$resizeGate()
            .allowsInput(player)) return true;
        if (player.openContainer.windowId == 0 || InventoryUi.findAdapter(player.openContainer) == null) return false;
        return !InventoryUi.isOpeningReady(player);
    }

    public static boolean cancelFallbackMouse(GuiScreen screen) {
        return isPending(screen);
    }

    public static boolean cancelFallbackKeyboard(GuiScreen screen) {
        return isPending(screen) && !(Keyboard.getEventKey() == Keyboard.KEY_ESCAPE && Keyboard.getEventKeyState());
    }
}
