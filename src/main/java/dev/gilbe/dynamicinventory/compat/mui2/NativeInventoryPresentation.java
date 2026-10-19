package dev.gilbe.dynamicinventory.compat.mui2;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widget.WidgetTree;

import dev.gilbe.dynamicinventory.DynamicInventory;
import dev.gilbe.dynamicinventory.client.ContainerPanelAccess;
import dev.gilbe.dynamicinventory.client.InventoryGuiTheme;

/**
 * First migration stage: MUI2 widget trees paint native-container surfaces in the background pass.
 * Slot backgrounds reference existing slots; vanilla still renders and interacts with the actual items.
 * These private presentation scenes are not registered with MUI2's global overlay/input stack.
 */
public final class NativeInventoryPresentation {

    private static final ResourceLocation ATLAS = new ResourceLocation("textures/gui/container/inventory.png");
    private final GuiContainer owner;
    private final Container container;
    private final Surface frame;
    private final FrameWidget skin = new FrameWidget();
    private Surface grid;
    private final List<SlotBackground> cells = new ArrayList<>();
    private long drawCalls;

    public NativeInventoryPresentation(GuiContainer owner) {
        this.owner = owner;
        container = owner.inventorySlots;
        frame = new Surface("native_frame");
        frame.panel.child(skin);
    }

    public void frame(int x, int y, int width, int height, boolean playerStyle) {
        boolean changed = skin.getArea().x != x || skin.getArea().y != y
            || skin.getArea().width != width
            || skin.getArea().height != height;
        skin.pos(x, y)
            .size(width, height);
        skin.playerStyle = playerStyle;
        frame.draw(changed);
    }

    public void slots(List<Slot> slots) {
        boolean rebuild = grid == null || cells.size() != slots.size();
        if (!rebuild) {
            for (int i = 0; i < cells.size(); i++) {
                if (cells.get(i).slot != slots.get(i)) {
                    rebuild = true;
                    break;
                }
            }
        }
        if (rebuild) {
            if (grid != null) grid.dispose();
            grid = new Surface("native_slot_backgrounds");
            cells.clear();
            for (Slot slot : slots) {
                SlotBackground cell = new SlotBackground(slot);
                cells.add(cell);
                grid.panel.child(cell);
            }
        }
        ContainerPanelAccess bounds = (ContainerPanelAccess) owner;
        boolean changed = false;
        for (int i = 0; i < cells.size(); i++) {
            SlotBackground cell = cells.get(i);
            Slot slot = cell.slot;
            boolean visible = slot.xDisplayPosition >= 0 && slot.yDisplayPosition >= 0;
            int x = visible ? bounds.dynamicinventory$left() + slot.xDisplayPosition - 1 : 0;
            int y = visible ? bounds.dynamicinventory$top() + slot.yDisplayPosition - 1 : 0;
            changed |= cell.getArea().x != x || cell.getArea().y != y || cell.isEnabled() != visible;
            cell.pos(x, y);
            cell.setEnabled(visible);
        }
        grid.draw(changed);
    }

    public String status() {
        int visible = 0;
        boolean canonical = owner.inventorySlots == container
            && Minecraft.getMinecraft().thePlayer.openContainer == container;
        for (SlotBackground cell : cells) {
            if (cell.isEnabled()) visible++;
            canonical &= container.getSlot(cell.slot.slotNumber) == cell.slot;
        }
        return "initialized=true frameWidgets=1 slotWidgets=" + cells
            .size() + " visibleSlotWidgets=" + visible + " canonicalContainer=" + canonical + " drawCalls=" + drawCalls;
    }

    public void dispose() {
        frame.dispose();
        if (grid != null) grid.dispose();
    }

    private final class Surface {

        final ModularPanel panel;
        final ModularScreen screen;
        int width = -1, height = -1;

        Surface(String name) {
            panel = new ModularPanel(name).pos(0, 0);
            panel.disableThemeBackground(true)
                .disableHoverThemeBackground(true);
            screen = new ModularScreen(DynamicInventory.MODID, panel).drawDarkBackground(false);
            screen.constructOverlay(owner);
        }

        void draw(boolean changed) {
            // Pinned MUI2's GL wrapper is uncached. Preserve the native caller's GL state and transforms.
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            GL11.glPushMatrix();
            try {
                if (changed || width != owner.width || height != owner.height) {
                    width = owner.width;
                    height = owner.height;
                    panel.size(width, height);
                    screen.onResize(width, height);
                }
                GL11.glDisable(GL12.GL_RESCALE_NORMAL);
                GL11.glDisable(GL11.GL_LIGHTING);
                GL11.glDisable(GL11.GL_DEPTH_TEST);
                GL11.glDisable(GL11.GL_ALPHA_TEST);
                GL11.glDepthMask(false);
                ModularGuiContext context = screen.getContext();
                context.updateState(-10000, -10000, 0);
                context.reset();
                context.pushViewport(null, context.getScreenArea());
                try {
                    context.updateZ(0);
                    // Draw only our background widgets. Full screen drawing clears the caller's depth
                    // buffer and runs post-render callbacks, neither of which belongs in this pass.
                    WidgetTree.drawTree(panel, context);
                } finally {
                    context.updateZ(0);
                    context.popViewport(null);
                }
                drawCalls++;
            } finally {
                GL11.glPopMatrix();
                GL11.glPopAttrib();
            }
        }

        void dispose() {
            // Dispose only this private widget tree; never close the owner's server window.
            if (width < 0) return;
            screen.getPanelManager()
                .closeAll();
            screen.getPanelManager()
                .dispose();
        }
    }

    private static final class SlotBackground extends Widget<SlotBackground> {

        final Slot slot;

        SlotBackground(Slot slot) {
            this.slot = slot;
            size(18, 18);
            disableThemeBackground(true).disableHoverThemeBackground(true);
        }

        @Override
        public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glColor4f(1, 1, 1, 1);
            GuiDraw.drawTexture(ATLAS, 0, 0, 18, 18, 7, 83, 256, 256);
        }
    }

    private static final class FrameWidget extends Widget<FrameWidget> {

        boolean playerStyle;

        FrameWidget() {
            disableThemeBackground(true).disableHoverThemeBackground(true);
        }

        @Override
        public void draw(ModularGuiContext context, WidgetThemeEntry<?> ignored) {
            InventoryGuiTheme theme = InventoryGuiTheme.get();
            int width = getArea().width, height = getArea().height;
            if (playerStyle) {
                outline(0, 0, width, height, theme.panelOuter);
                outline(1, 1, width - 2, height - 2, theme.panelShadow);
                GuiDraw.drawRect(2, 2, width - 4, 2, theme.panelHighlight);
                GuiDraw.drawRect(2, 4, 2, height - 6, theme.panelHighlight);
                GuiDraw.drawRect(4, 4, width - 6, height - 6, theme.panelFill);
            } else {
                GuiDraw.drawRect(2, 2, width - 4, height - 4, theme.panelFill);
                GuiDraw.drawRect(0, 0, width, 2, theme.panelHighlight);
                GuiDraw.drawRect(0, 2, 2, height - 4, theme.panelHighlight);
                GuiDraw.drawRect(0, height - 2, width, 2, theme.panelShadow);
                GuiDraw.drawRect(width - 2, 2, 2, height - 4, theme.panelShadow);
            }
        }

        private static void outline(int x, int y, int width, int height, int color) {
            GuiDraw.drawRect(x, y, width, 1, color);
            GuiDraw.drawRect(x, y + height - 1, width, 1, color);
            GuiDraw.drawRect(x, y + 1, 1, height - 2, color);
            GuiDraw.drawRect(x + width - 1, y + 1, 1, height - 2, color);
        }
    }
}
