package dev.gilbe.dynamicinventory.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.network.play.client.C17PacketCustomPayload;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import dev.gilbe.dynamicinventory.inventory.InventoryLayout;
import dev.gilbe.dynamicinventory.inventory.InventorySlotLocks;
import dev.gilbe.dynamicinventory.network.InventoryLockSync;

public final class SlotLockControls {

    private static java.lang.ref.WeakReference<GuiScreen> toggledScreen = new java.lang.ref.WeakReference<>(null);
    private static int toggledButton;

    private SlotLockControls() {}

    public static boolean isToggle(int button) {
        return button == InventoryViewSettings.slotLockMouseButton && GuiScreen.isShiftKeyDown()
            && (Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU));
    }

    public static boolean toggle(Slot slot, int button) {
        if (!isToggle(button)) return false;
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.thePlayer;
        if (player == null || !(mc.currentScreen instanceof GuiContainer gui)
            || mc.getNetHandler() == null
            || PanelSettingsOverlay.isOpen(gui)
            || ClientInputGuard.isPending(gui)
            || InventorySlotLocks.index(player.inventory, slot) < 0
            || !slot.func_111238_b()
            || gui.inventorySlots != player.openContainer
            || slot.slotNumber < 0
            || slot.slotNumber >= gui.inventorySlots.inventorySlots.size()
            || gui.inventorySlots.getSlot(slot.slotNumber) != slot) return false;
        mc.getNetHandler()
            .addToSendQueue(
                new C17PacketCustomPayload(
                    InventoryLockSync.REQUEST,
                    InventoryLockSync.request(
                        gui.inventorySlots.windowId,
                        slot.slotNumber,
                        InventoryLayout.of(player.inventory)
                            .revision())));
        toggledScreen = new java.lang.ref.WeakReference<>(gui);
        toggledButton = button;
        return true;
    }

    public static boolean finishToggle(GuiScreen screen, int button) {
        if (toggledScreen.get() != screen || toggledButton != button) return false;
        toggledScreen.clear();
        return true;
    }

    public static void beginClick() {
        toggledScreen.clear();
    }

    public static void draw(Slot slot, int x, int y) {
        if (!InventorySlotLocks.locked(slot, Minecraft.getMinecraft().thePlayer)) return;
        // A small gold padlock in the upper-left corner leaves the item count visible.
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        try {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_LIGHTING);
            Gui.drawRect(x + 1, y + 1, x + 6, y + 8, 0xDC2B2113);
            Gui.drawRect(x + 2, y + 1, x + 5, y + 2, 0xFFFFD482);
            Gui.drawRect(x + 2, y + 2, x + 3, y + 4, 0xFFFFD482);
            Gui.drawRect(x + 4, y + 2, x + 5, y + 4, 0xFFFFD482);
            Gui.drawRect(x + 1, y + 4, x + 6, y + 8, 0xFFF0B44E);
            Gui.drawRect(x + 3, y + 5, x + 4, y + 7, 0xFF684626);
        } finally {
            GL11.glPopAttrib();
        }
    }
}
