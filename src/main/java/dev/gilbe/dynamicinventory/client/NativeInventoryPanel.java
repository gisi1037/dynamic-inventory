package dev.gilbe.dynamicinventory.client;

import java.awt.Point;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import dev.gilbe.dynamicinventory.api.InventoryUi;
import dev.gilbe.dynamicinventory.api.NativeUiLayout;
import dev.gilbe.dynamicinventory.api.client.NativeUiAnchors;
import dev.gilbe.dynamicinventory.compat.PackClientCompatibility;
import dev.gilbe.dynamicinventory.compat.PackCompatibility;
import dev.gilbe.dynamicinventory.container.ContainerInventoryRegion;
import dev.gilbe.dynamicinventory.inventory.InventoryLayout;

/** Decorates the native GUI in place: its class, container, packets and header rendering survive. */
public final class NativeInventoryPanel extends Gui {

    /** One visible storage row, the hotbar and their surrounding padding. */
    public static int minimumPanelHeight() {
        return 18 + 42;
    }

    public static boolean handleWheel(net.minecraft.client.gui.GuiScreen screen) {
        if (!(screen instanceof ContainerPanelAccess) || org.lwjgl.input.Mouse.getEventDWheel() == 0) return false;
        NativeInventoryPanel panel = ((ContainerPanelAccess) screen).dynamicinventory$panel();
        if (panel == null || panel.maxScroll() == 0) return false;
        Minecraft client = Minecraft.getMinecraft();
        int x = org.lwjgl.input.Mouse.getEventX() * screen.width / client.displayWidth;
        int y = screen.height - org.lwjgl.input.Mouse.getEventY() * screen.height / client.displayHeight - 1;
        if (!panel.inPanel(x, y)) return false;
        // NEI transfers items from the hovered slot in its own input dispatcher. Cancelling here
        // would swallow that transfer. Empty cells and the scrollbar still scroll our viewport.
        if (PackCompatibility.enabled() && PackClientCompatibility.usesItemWheelTransfer(panel.gui)
            && panel.hasItemAt(x, y)) return false;
        panel.scroll(org.lwjgl.input.Mouse.getEventDWheel() < 0 ? 1 : -1);
        return true;
    }

    private boolean hasItemAt(int x, int y) {
        int localX = x - access.dynamicinventory$left();
        int localY = y - access.dynamicinventory$top();
        for (Slot slot : playerSlots) {
            // Match vanilla's one-pixel hover margin. Hidden rows are positioned off-screen.
            if (slot.func_111238_b() && slot.getHasStack()
                && localX >= slot.xDisplayPosition - 1
                && localX < slot.xDisplayPosition + 17
                && localY >= slot.yDisplayPosition - 1
                && localY < slot.yDisplayPosition + 17) return true;
        }
        return false;
    }

    private static final ResourceLocation TEXTURE = new ResourceLocation("textures/gui/container/inventory.png");
    private static NativeInventoryPanel drawingBackground;
    private final GuiContainer gui;
    private final ContainerPanelAccess access;
    private final ContainerInventoryRegion region;
    private final InventoryPresentation presentation;
    private final List<Slot> playerSlots = new ArrayList<>();
    private final NativeUiLayout layout;
    private final Map<Slot, int[]> nativePositions = new IdentityHashMap<>();
    private final List<Slot> machineSlots = new ArrayList<>();
    private final List<Slot> chestSlots = new ArrayList<>();
    private final Map<GuiButton, int[]> buttons = new IdentityHashMap<>();
    private final Map<GuiButton, Point> buttonOffsets = new IdentityHashMap<>();
    private final int nativeWidth;
    private final int nativeHeight;
    private final int panelTop;
    private final int nativeStorageTop;
    private final int headerX;
    private final int headerY;
    private final int tabPadding;
    private final int columns;
    private final int rows;
    private final int gridX;
    private final int panelHeight;
    private final int panelWidth;
    private final int[] headerOrigins;
    private final AttachedChestAccess attachedChest;
    private final AttachedChestLayout chestLayout;
    private final int chestOffsetX, chestOffsetY;
    private int scroll;
    private int frameLeft, frameTop, frameWidth;

    /** Native drawing temporarily substitutes the crafting bounds; retain the actual outer panel bounds. */
    public void drawAttachedChestFrame() {
        attachedChest
            .dynamicinventory$drawFrame(frameLeft, frameTop, frameWidth, attachedChest.dynamicinventory$chestHeight());
    }

    public NativeInventoryPanel(GuiContainer gui) {
        this.gui = gui;
        access = (ContainerPanelAccess) gui;
        region = ContainerInventoryRegion.of(gui.inventorySlots);
        layout = InventoryUi.findLayout(gui.inventorySlots, gui.getClass());
        if (layout == null)
            throw new IllegalArgumentException("No registered native presentation for " + gui.getClass());
        presentation = new InventoryPresentation(gui);
        for (int logical = 0; logical < InventoryLayout.mainSlots(); logical++)
            playerSlots.add(gui.inventorySlots.getSlot(region.id(logical)));
        nativeWidth = access.dynamicinventory$width();
        nativeHeight = access.dynamicinventory$height();
        int oldLeft = access.dynamicinventory$left(), oldTop = access.dynamicinventory$top();
        headerOrigins = gui instanceof NativeUiAnchors ? ((NativeUiAnchors) gui).captureHeaderOrigins() : null;
        if (headerOrigins != null && headerOrigins.length % 2 != 0)
            throw new IllegalArgumentException("NativeUiAnchors must return x/y pairs");
        if (headerOrigins != null)
            for (int i = 0; i < headerOrigins.length; i++) headerOrigins[i] -= i % 2 == 0 ? oldLeft : oldTop;
        attachedChest = gui instanceof AttachedChestAccess && ((AttachedChestAccess) gui).dynamicinventory$splitChest()
            ? (AttachedChestAccess) gui
            : null;
        int storageY = Integer.MAX_VALUE;
        int machineBottom = 0;
        int nativeLeft = 0, nativeRight = nativeWidth, nativeTop = 0;
        for (Slot slot : gui.inventorySlots.inventorySlots) {
            if (slot.slotNumber >= region.nativeSlotCount) continue;
            nativePositions.put(slot, new int[] { slot.xDisplayPosition, slot.yDisplayPosition });
            int logical = region.logical(slot.slotNumber);
            if (logical < 0) machineSlots.add(slot);
            if (gui instanceof AttachedChestAccess && ((AttachedChestAccess) gui).dynamicinventory$isChestSlot(slot))
                chestSlots.add(slot);
            if (logical >= 9) storageY = Math.min(storageY, slot.yDisplayPosition);
            else if (logical < 0 && slot.func_111238_b()) {
                machineBottom = Math.max(machineBottom, slot.yDisplayPosition + 16);
                nativeLeft = Math.min(nativeLeft, slot.xDisplayPosition - 8);
                nativeRight = Math.max(nativeRight, slot.xDisplayPosition + 24);
                nativeTop = Math.min(nativeTop, slot.yDisplayPosition - 18);
            }
        }
        nativeStorageTop = storageY - 4;
        tabPadding = layout.getTopPadding();
        chestLayout = attachedChest == null ? null
            : new AttachedChestLayout(
                gui.width,
                gui.height,
                InventoryLayout.storageSlots(),
                InventoryViewSettings.columns,
                InventoryViewSettings.rows,
                attachedChest.dynamicinventory$hasDescription(),
                attachedChest.dynamicinventory$chestWidth(),
                attachedChest.dynamicinventory$chestHeight(),
                chestSlots.size());
        int totalWidth, totalHeight;
        if (chestLayout != null) {
            columns = chestLayout.columns;
            rows = chestLayout.rows;
            panelWidth = chestLayout.playerWidth;
            panelHeight = chestLayout.playerHeight;
            totalWidth = chestLayout.width;
            totalHeight = chestLayout.height;
            headerX = chestLayout.craftingX - headerOrigins[0];
            headerY = chestLayout.craftingY - headerOrigins[1];
            // Crafting is centered inside the shared lower panel; player rows keep their independent top anchor.
            panelTop = chestLayout.playerY - headerY;
            chestOffsetX = chestLayout.chestX - headerOrigins[2];
            chestOffsetY = -headerOrigins[3];
            gridX = chestLayout.playerX + (panelWidth - columns * 18 - (maxScroll() > 0 ? 10 : 0)) / 2;
        } else {
            panelTop = Math.max(layout.getMinimumHeaderHeight(), Math.max(nativeStorageTop, machineBottom + 8));
            headerY = tabPadding - nativeTop;
            columns = Math.max(3, Math.min(InventoryViewSettings.columns, Math.min(18, (gui.width - 32) / 18)));
            rows = Math.max(
                1,
                Math.min(
                    Math.min(InventoryViewSettings.rows, (InventoryLayout.storageSlots() + columns - 1) / columns),
                    (gui.height - headerY - panelTop - 50) / 18));
            panelWidth = Math.max(176, columns * 18 + 26);
            panelHeight = rows * 18 + 42;
            totalWidth = Math.max(nativeRight - nativeLeft, panelWidth);
            totalHeight = headerY + panelTop + panelHeight;
            headerX = (totalWidth - (nativeRight - nativeLeft)) / 2 - nativeLeft;
            chestOffsetX = headerX;
            chestOffsetY = headerY;
            gridX = (totalWidth - columns * 18 - (maxScroll() > 0 ? 10 : 0)) / 2;
        }
        int left = (gui.width - totalWidth) / 2;
        int top = Math.max(0, (gui.height - totalHeight) / 2);
        for (Object entry : access.dynamicinventory$buttons()) {
            GuiButton button = (GuiButton) entry;
            if (button.getClass()
                .getName()
                .equals("com.cleanroommc.bogosorter.common.sort.ButtonHandler$SortButton")
                || button.getClass()
                    .getName()
                    .equals("net.blay09.mods.craftingtweaks.client.GuiTweakButton"))
                continue;
            buttons.put(button, new int[] { button.xPosition - oldLeft, button.yPosition - oldTop });
        }
        access.dynamicinventory$bounds(totalWidth, totalHeight, left, top);
        position();
    }

    public void restore() {
        presentation.dispose();
        for (Map.Entry<Slot, int[]> entry : nativePositions.entrySet()) {
            entry.getKey().xDisplayPosition = entry.getValue()[0];
            entry.getKey().yDisplayPosition = entry.getValue()[1];
        }
        access.dynamicinventory$bounds(
            nativeWidth,
            nativeHeight,
            (gui.width - nativeWidth) / 2,
            (gui.height - nativeHeight) / 2);
        if (headerOrigins != null) {
            int[] restored = headerOrigins.clone();
            for (int i = 0; i < restored.length; i++)
                restored[i] += i % 2 == 0 ? access.dynamicinventory$left() : access.dynamicinventory$top();
            ((NativeUiAnchors) gui).positionHeaderOrigins(restored);
            if (attachedChest != null) attachedChest.dynamicinventory$positionText(access.dynamicinventory$left());
        }
        for (Map.Entry<GuiButton, int[]> entry : buttons.entrySet()) {
            entry.getKey().xPosition = access.dynamicinventory$left() + entry.getValue()[0];
            entry.getKey().yPosition = access.dynamicinventory$top() + entry.getValue()[1];
        }
        for (Map.Entry<GuiButton, Point> entry : buttonOffsets.entrySet()) {
            ((NativeButtonOffset) entry.getKey()).dynamicinventory$buttonOffset()
                .setLocation(entry.getValue());
            entry.getKey().xPosition = access.dynamicinventory$left() + entry.getValue().x;
            entry.getKey().yPosition = access.dynamicinventory$top() + entry.getValue().y;
        }
        buttonOffsets.clear();
    }

    public int headerOffsetX() {
        return headerX;
    }

    public int headerOffsetY() {
        return headerY;
    }

    public int craftingOffsetX() {
        return chestLayout == null ? headerX : gui.inventorySlots.getSlot(1).xDisplayPosition - 30;
    }

    public int craftingOffsetY() {
        return chestLayout == null ? headerY : gui.inventorySlots.getSlot(1).yDisplayPosition - 17;
    }

    /** A tall adjacent chest must not reveal the crafting station's obsolete player-slot artwork. */
    public int legacyBackgroundHeight(int height) {
        return Math.min(height, nativeStorageTop);
    }

    /** Crop only the native background sheet; live slots and native widgets retain their own draws. */
    public static int[] clipBackground(int x, int y, int u, int v, int width, int height) {
        NativeInventoryPanel panel = drawingBackground;
        if (panel == null || x != panel.access.dynamicinventory$left() || width != panel.nativeWidth) return null;
        int originTop = panel.access.dynamicinventory$top();
        if (y < originTop) return null;
        // The attached layout always has one shared lower frame. Remove the native crafting sheet's
        // outer bevel even when the global frame preference is Compact, while retaining its slot artwork.
        int inset = panel.chestLayout == null && InventoryViewSettings.compactNativePanels ? 0
            : panel.layout.getBackgroundInset();
        int topInset = y == originTop ? inset : 0;
        int clippedHeight = Math.min(height, Math.max(0, originTop + panel.nativeStorageTop - y));
        if (inset == 0 && clippedHeight == height) return null;
        return new int[] { x + inset, y + topInset, u + inset, v + topInset, width - inset * 2,
            clippedHeight - topInset };
    }

    private void drawMergedFrame(int left, int top, int width, int height) {
        if (presentation.frame(left, top, width, height, false)) return;
        InventoryGuiTheme theme = InventoryGuiTheme.get();
        int right = left + width, bottom = top + height;
        drawRect(left + 2, top + 2, right - 2, bottom - 2, theme.panelFill);
        drawRect(left, top, right, top + 2, theme.panelHighlight);
        drawRect(left, top + 2, left + 2, bottom - 2, theme.panelHighlight);
        drawRect(left, bottom - 2, right, bottom, theme.panelShadow);
        drawRect(right - 2, top + 2, right, bottom - 2, theme.panelShadow);
    }

    public int maxScroll() {
        return Math.max(0, (InventoryLayout.storageSlots() + columns - 1) / columns - rows);
    }

    public void scroll(int delta) {
        if (Minecraft.getMinecraft().thePlayer.inventory.getItemStack() != null) return;
        scroll = Math.max(0, Math.min(maxScroll(), scroll + delta));
        position();
    }

    public boolean inPanel(int x, int y) {
        if (chestLayout != null) return x >= access.dynamicinventory$left() + chestLayout.playerX
            && x < access.dynamicinventory$left() + chestLayout.playerX + panelWidth
            && y >= access.dynamicinventory$top() + chestLayout.playerY
            && y < access.dynamicinventory$top() + chestLayout.playerY + panelHeight;
        return x >= access.dynamicinventory$left()
            && x < access.dynamicinventory$left() + access.dynamicinventory$width()
            && y >= access.dynamicinventory$top() + headerY + panelTop
            && y < access.dynamicinventory$top() + access.dynamicinventory$height();
    }

    public boolean scrollbar(int x, int y) {
        int left = access.dynamicinventory$left();
        int top = access.dynamicinventory$top() + headerY + panelTop + 14;
        int barX = left + gridX + columns * 18 + 2;
        if (maxScroll() == 0 || x < barX || x >= barX + 8 || y < top || y >= top + rows * 18) return false;
        if (Minecraft.getMinecraft().thePlayer.inventory.getItemStack() == null) {
            scroll = Math.max(0, Math.min(maxScroll(), (y - top) * (maxScroll() + 1) / (rows * 18)));
            position();
        }
        return true;
    }

    public void position() {
        if (headerOrigins != null) {
            int[] translated = headerOrigins.clone();
            for (int i = 0; i < translated.length; i++)
                translated[i] += i % 2 == 0 ? access.dynamicinventory$left() + headerX
                    : access.dynamicinventory$top() + headerY;
            if (chestLayout != null) {
                // Crafting/description use the normal header translation. Chest, border and slider
                // share the independent top-pane translation, retaining their native relative offsets.
                for (int index : new int[] { 2, 6, 8 }) {
                    translated[index] += chestOffsetX - headerX;
                    translated[index + 1] += chestOffsetY - headerY;
                }
                translated[4] = access.dynamicinventory$left() + chestLayout.descriptionX;
                translated[5] = access.dynamicinventory$top() + chestLayout.descriptionY;
            }
            ((NativeUiAnchors) gui).positionHeaderOrigins(translated);
            if (attachedChest != null)
                attachedChest.dynamicinventory$positionText(access.dynamicinventory$left() + headerX);
        }
        for (Slot slot : machineSlots) {
            int[] origin = nativePositions.get(slot);
            boolean chest = chestLayout != null && attachedChest.dynamicinventory$isChestSlot(slot);
            slot.xDisplayPosition = origin[0] + (chest ? chestOffsetX : headerX);
            slot.yDisplayPosition = origin[1] + (chest ? chestOffsetY : headerY);
        }
        for (int logical = 0; logical < InventoryLayout.mainSlots(); logical++) {
            Slot slot = gui.inventorySlots.getSlot(region.id(logical));
            if (logical < 9) {
                slot.xDisplayPosition = (chestLayout == null ? (access.dynamicinventory$width() - 162) / 2
                    : chestLayout.playerX + (panelWidth - 162) / 2) + logical * 18;
                slot.yDisplayPosition = headerY + panelTop + 18 + rows * 18;
            } else {
                int cell = logical - 9;
                int row = cell / columns - scroll;
                slot.xDisplayPosition = row >= 0 && row < rows ? gridX + cell % columns * 18 : -10000;
                slot.yDisplayPosition = row >= 0 && row < rows ? headerY + panelTop + 14 + row * 18 : -10000;
            }
        }
        for (Map.Entry<GuiButton, int[]> entry : buttons.entrySet()) {
            entry.getKey().xPosition = access.dynamicinventory$left() + headerX + entry.getValue()[0];
            entry.getKey().yPosition = access.dynamicinventory$top() + headerY + entry.getValue()[1];
        }
        int tweak = 0;
        for (GuiButton button : access.dynamicinventory$buttons()) {
            if (!button.getClass()
                .getName()
                .equals("net.blay09.mods.craftingtweaks.client.GuiTweakButton")) continue;
            button.xPosition = access.dynamicinventory$left() + headerX + (chestLayout == null ? -20 : 8);
            button.yPosition = access.dynamicinventory$top() + gui.inventorySlots.getSlot(1).yDisplayPosition
                - 1
                + tweak++ * 18;
            if (button instanceof NativeButtonOffset) {
                Point offset = ((NativeButtonOffset) button).dynamicinventory$buttonOffset();
                // CT may add buttons after panel construction, then recomputes x/y from this Point every draw.
                if (!buttonOffsets.containsKey(button)) buttonOffsets.put(button, new Point(offset));
                offset.setLocation(
                    button.xPosition - access.dynamicinventory$left(),
                    button.yPosition - access.dynamicinventory$top());
            }
        }
    }

    public void background(Runnable nativeDraw) {
        Minecraft mc = Minecraft.getMinecraft();
        int fullWidth = access.dynamicinventory$width(), fullHeight = access.dynamicinventory$height();
        int left = access.dynamicinventory$left(), top = access.dynamicinventory$top();
        frameLeft = left;
        frameTop = top;
        frameWidth = fullWidth;
        position();
        boolean compact = InventoryViewSettings.compactNativePanels;
        if (chestLayout != null) {
            // The chest draws its own native upper border. Enclose player inventory and crafting in
            // one lower rectangle, leaving the gap between the two panels open to the world.
            attachedChest
                .dynamicinventory$drawFrame(left, top + chestLayout.lowerY, fullWidth, chestLayout.lowerHeight);
        } else if (!compact) drawMergedFrame(left, top + tabPadding, fullWidth, fullHeight - tabPadding);
        // Native textures frequently calculate their origin directly from screen dimensions.
        // Give them their original dimensions and an equivalent virtual viewport for this draw only.
        int screenWidth = gui.width, screenHeight = gui.height;
        ScaledResolution scaled = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int factor = scaled.getScaleFactor();
        GL11.glPushAttrib(GL11.GL_SCISSOR_BIT);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(
            left * factor,
            Math.max(0, mc.displayHeight - (top + (chestLayout == null ? headerY + panelTop : fullHeight)) * factor),
            fullWidth * factor,
            (chestLayout == null ? headerY + panelTop : fullHeight) * factor);
        NativeInventoryPanel previousBackground = drawingBackground;
        try {
            access.dynamicinventory$bounds(nativeWidth, nativeHeight, left + headerX, top + headerY);
            gui.width = 2 * (left + headerX) + nativeWidth;
            gui.height = 2 * (top + headerY) + nativeHeight;
            // Tinkers recomputes chest slots only while scrolling. Give its draw native coordinates
            // even for a complete chest, so the translated positions cannot accumulate each frame.
            int extraChestX = chestOffsetX - headerX, extraChestY = chestOffsetY - headerY;
            for (Slot slot : chestSlots) {
                int[] origin = nativePositions.get(slot);
                slot.xDisplayPosition = origin[0] + extraChestX;
                slot.yDisplayPosition = origin[1] + extraChestY;
            }
            drawingBackground = this;
            nativeDraw.run();
            // Tinkers updates its attached-chest viewport during drawing. Preserve those native coordinates.
            for (Slot slot : chestSlots) {
                int[] origin = nativePositions.get(slot);
                origin[0] = slot.xDisplayPosition - extraChestX;
                origin[1] = slot.yDisplayPosition - extraChestY;
            }
        } finally {
            drawingBackground = previousBackground;
            gui.width = screenWidth;
            gui.height = screenHeight;
            access.dynamicinventory$bounds(fullWidth, fullHeight, left, top);
            GL11.glPopAttrib();
        }
        position();
        if (PackCompatibility.enabled()) PackClientCompatibility.positionSortButtons(
            access.dynamicinventory$buttons(),
            left,
            top,
            (chestLayout == null ? fullWidth : chestLayout.playerX + panelWidth) - 46,
            headerY + panelTop + 2,
            true);
        if (PackCompatibility.enabled() && chestLayout != null) PackClientCompatibility.positionChestSortButtons(
            access.dynamicinventory$buttons(),
            left,
            top,
            chestLayout.chestX + attachedChest.dynamicinventory$chestWidth() - 28,
            2);
        int panelY = top + headerY + panelTop;
        if (compact && chestLayout == null) {
            // Tall attached inventories must not expose the old player-grid texture beneath the crafting header.
            if (panelTop > nativeStorageTop) drawRect(
                left + headerX + 3,
                top + headerY + nativeStorageTop,
                left + headerX + nativeWidth - 3,
                panelY,
                InventoryGuiTheme.get().panelFill);
            drawRect(left, panelY, left + fullWidth, top + fullHeight, InventoryGuiTheme.get().panelFill);
            drawRect(left, panelY, left + headerX, panelY + 2, InventoryGuiTheme.get().panelHighlight);
            drawRect(
                left + headerX + nativeWidth,
                panelY,
                left + fullWidth,
                panelY + 2,
                InventoryGuiTheme.get().panelHighlight);
            drawRect(
                left,
                top + fullHeight - 2,
                left + fullWidth,
                top + fullHeight,
                InventoryGuiTheme.get().panelShadow);
            drawRect(left, panelY, left + 2, top + fullHeight, InventoryGuiTheme.get().panelHighlight);
            drawRect(
                left + fullWidth - 2,
                panelY,
                left + fullWidth,
                top + fullHeight,
                InventoryGuiTheme.get().panelShadow);
        }
        GL11.glColor4f(1, 1, 1, 1);
        mc.getTextureManager()
            .bindTexture(TEXTURE);
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        try {
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            if (!presentation.slots(playerSlots)) for (int i = 0; i < InventoryLayout.mainSlots(); i++) {
                Slot slot = gui.inventorySlots.getSlot(region.id(i));
                if (slot.xDisplayPosition >= 0) drawTexturedModalRect(
                    left + slot.xDisplayPosition - 1,
                    top + slot.yDisplayPosition - 1,
                    7,
                    83,
                    18,
                    18);
            }
        } finally {
            GL11.glPopAttrib();
        }
        if (maxScroll() > 0) {
            int x = left + gridX + columns * 18 + 2, y = panelY + 14;
            int track = rows * 18,
                thumb = Math.max(10, track * rows / ((InventoryLayout.storageSlots() + columns - 1) / columns));
            int offset = (track - thumb) * scroll / maxScroll();
            drawRect(x, y, x + 8, y + track, InventoryGuiTheme.get().scrollTrack);
            drawRect(x + 1, y + offset, x + 7, y + offset + thumb, InventoryGuiTheme.get().scrollThumb);
        }
    }

    public void foreground(Runnable nativeDraw) {
        int w = access.dynamicinventory$width(), h = access.dynamicinventory$height();
        int x = access.dynamicinventory$left(), y = access.dynamicinventory$top();
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(headerX, headerY, 0);
            access.dynamicinventory$bounds(nativeWidth, nativeHeight, x + headerX, y + headerY);
            nativeDraw.run();
        } finally {
            access.dynamicinventory$bounds(w, h, x, y);
            GL11.glPopMatrix();
        }
        if (chestLayout != null) Minecraft.getMinecraft().fontRenderer.drawString(
            net.minecraft.client.resources.I18n.format("container.inventory"),
            chestLayout.playerX + 8,
            chestLayout.playerY + 4,
            InventoryGuiTheme.get().label);
    }

    public String presentationStatus() {
        return presentation.status();
    }
}
