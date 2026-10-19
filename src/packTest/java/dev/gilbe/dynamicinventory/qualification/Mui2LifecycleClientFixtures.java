package dev.gilbe.dynamicinventory.qualification;

import java.lang.reflect.Method;

import net.minecraft.client.Minecraft;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.widgets.DynamicSyncedWidget;
import com.google.gson.JsonObject;

import dev.gilbe.dynamicinventory.client.InventoryViewSettings;
import dev.gilbe.dynamicinventory.client.InventoryViewportLayout;
import dev.gilbe.dynamicinventory.compat.mui2.InventoryScrollWidget;
import dev.gilbe.dynamicinventory.compat.mui2.PlayerInventoryWidgetGroup;
import dev.gilbe.dynamicinventory.inventory.InventoryLayout;

/** Client-thread lifecycle checks using native panel admission and dynamic-child replacement. */
public final class Mui2LifecycleClientFixtures {

    private Mui2LifecycleClientFixtures() {}

    public static void run() throws Exception {
        Minecraft minecraft = Minecraft.getMinecraft();
        boolean expandBefore = InventoryViewSettings.expandMachineInventory;
        InventoryViewSettings.expandMachineInventory = true;
        ModularPanel main = ModularPanel.defaultPanel("lifecycle_main", 200, 180);
        ModularScreen screen = new ModularScreen("dynamicinventory", main);
        screen.constructOverlay(minecraft.currentScreen);
        try {
            // Only initial construction resizes the screen explicitly. All later changes use native ticks.
            screen.onResize(800, 700);
            PlayerInventoryWidgetGroup original = group();
            DynamicSyncedWidget<?> dynamic = new DynamicSyncedWidget<>().initialChild(original)
                .size(168, 76);
            ModularPanel secondary = new ModularPanel("lifecycle_secondary") {

                @Override
                public boolean shouldAnimate() {
                    return false;
                }
            }.size(200, 260)
                .child(dynamic);
            IPanelHandler handler = IPanelHandler.simple(main, (parent, player) -> secondary, false);
            handler.openPanel();
            screen.onUpdate();
            checkRows(original, secondary, 260, "secondary admission");

            PlayerInventoryWidgetGroup replacement = group();
            Method update = DynamicSyncedWidget.class.getDeclaredMethod("updateChild", IWidget.class);
            update.setAccessible(true);
            // Invoke the same native replacement path used by DynamicSyncHandler's listener.
            update.invoke(dynamic, replacement);
            screen.onUpdate();
            checkRows(replacement, secondary, 260, "dynamic child replacement");

            secondary.closeIfOpen();
            screen.onUpdate();
            handler.openPanel();
            screen.onUpdate();
            checkRows(replacement, secondary, 260, "same secondary-panel identity reopened");
            JsonObject report = new JsonObject();
            report.addProperty("success", true);
            report.addProperty("lifecycleChecks", 3);
            QualificationCommand.write(minecraft.thePlayer, "client-mui2-lifecycle", report);
        } finally {
            try {
                screen.getPanelManager()
                    .closeAll();
                screen.getPanelManager()
                    .dispose();
            } finally {
                InventoryViewSettings.expandMachineInventory = expandBefore;
            }
        }
    }

    private static PlayerInventoryWidgetGroup group() {
        // Pure geometry widgets avoid registering inventory sync handlers in this private client-only scene.
        InventoryScrollWidget viewport = new InventoryScrollWidget();
        viewport.size(168, 54);
        PlayerInventoryWidgetGroup group = new PlayerInventoryWidgetGroup(viewport, 3);
        group.size(168, 76);
        group.child(viewport);
        return group;
    }

    private static void checkRows(PlayerInventoryWidgetGroup group, ModularPanel panel, int nativeHeight,
        String operation) {
        int rows = InventoryViewportLayout.rows(InventoryLayout.storageSlots(), nativeHeight, 700, true);
        if (group.getInventoryViewport()
            .getArea().height != rows * 18 || group.getArea().height != 76 + (rows - 3) * 18
            || panel.getArea().height != nativeHeight + (rows - 3) * 18)
            throw new AssertionError(
                operation + " did not apply responsive geometry through the native tick lifecycle");
    }
}
