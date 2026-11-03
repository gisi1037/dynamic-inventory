package dev.gilbe.dynamicinventory.qualification;

import java.lang.reflect.Field;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;

import dev.gilbe.dynamicinventory.client.PanelSettingsOverlay;

/** A modal left by another native screen must never block a later panel-button menu. */
public final class PanelMenuLifecycleFixtures {

    private PanelMenuLifecycleFixtures() {}

    public static void main(String[] args) throws Exception {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        Minecraft mc = (Minecraft) unsafe.allocateInstance(Minecraft.class);
        Field singleton = Minecraft.class.getDeclaredField("theMinecraft");
        singleton.setAccessible(true);
        Object previous = singleton.get(null);
        Field active = PanelSettingsOverlay.class.getDeclaredField("active");
        active.setAccessible(true);
        Class<?> viewClass = Class.forName(PanelSettingsOverlay.class.getName() + "$View");
        try {
            singleton.set(null, mc);
            mc.thePlayer = (EntityClientPlayerMP) unsafe.allocateInstance(EntityClientPlayerMP.class);
            Container first = new Host(), second = new Host();
            GuiContainer oldScreen = new Screen(first), newScreen = new Screen(second);
            mc.currentScreen = oldScreen;
            mc.thePlayer.openContainer = first;
            Object view = unsafe.allocateInstance(viewClass);
            set(view, "host", oldScreen);
            set(view, "container", first);
            active.set(null, view);
            check(PanelSettingsOverlay.isOpen(oldScreen), "Live modal remains owned by its screen");
            check(
                !PanelSettingsOverlay.isOpen(newScreen) && active.get(null) == view,
                "A query for another screen cannot discard a live modal");
            mc.currentScreen = newScreen;
            mc.thePlayer.openContainer = second;
            check(
                !PanelSettingsOverlay.isOpen(newScreen) && active.get(null) == null,
                "Opening another native screen releases the stale modal");
            active.set(null, view);
            mc.currentScreen = null;
            check(
                !PanelSettingsOverlay.isOpen(oldScreen) && active.get(null) == null,
                "Closing the native screen releases its modal");
            active.set(null, view);
            mc.currentScreen = oldScreen;
            mc.thePlayer.openContainer = second;
            check(
                !PanelSettingsOverlay.isOpen(oldScreen) && active.get(null) == null,
                "A replaced server container invalidates the modal even on the same screen");
            System.out.println("PASS panel menu ownership, native screen close/reopen and stale-modal cleanup");
        } finally {
            active.set(null, null);
            singleton.set(null, previous);
        }
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass()
            .getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static final class Host extends Container {

        @Override
        public boolean canInteractWith(EntityPlayer player) {
            return true;
        }
    }

    private static final class Screen extends GuiContainer {

        Screen(Container container) {
            super(container);
        }

        @Override
        protected void drawGuiContainerBackgroundLayer(float partialTicks, int x, int y) {}
    }
}
