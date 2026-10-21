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
import dev.gilbe.dynamicinventory.client.editor.NativeLayoutPatch;
import dev.gilbe.dynamicinventory.client.editor.NativeLayoutPatches;
import dev.gilbe.dynamicinventory.compat.InventoryTabsCompatibility;
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
    private static NativeInventoryPanel drawingForeground;
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
    private final int viewportWidth, viewportHeight;
    private int panelTop;
    private final int nativeStorageTop;
    private int headerX;
    private int headerY;
    private final int tabPadding;
    private final int columns;
    private final int rows;
    private int gridX;
    private final int playerX, playerY, shiftX, shiftY;
    private final int nativeClipLeft, nativeClipRight, nativeClipTop, nativeClipBottom;
    private final int nativeContentBottom;
    private final int measuredHeaderBoundary;
    private final int sectionGap;
    private final boolean nativeSectionMoved;
    private final int frameStyle;
    private final int borderWidth;
    private final int panelHeight;
    private final int panelWidth;
    private final int[] headerOrigins;
    private final AttachedChestAccess attachedChest;
    private final AttachedChestLayout chestLayout;
    private int chestOffsetX, chestOffsetY;
    private int scroll;
    private int frameLeft, frameTop;

    /** Native drawing temporarily substitutes the crafting bounds; retain the actual outer panel bounds. */
    public void drawAttachedChestFrame() {
        if (frameStyle != 0) NativePanelFrame.draw(
            frameLeft + shiftX,
            frameTop + shiftY,
            chestLayout.width,
            attachedChest.dynamicinventory$chestHeight(),
            frameStyle,
            borderWidth,
            null);
        else attachedChest.dynamicinventory$drawFrame(
            frameLeft + shiftX,
            frameTop + shiftY,
            chestLayout.width,
            attachedChest.dynamicinventory$chestHeight());
    }

    public NativeInventoryPanel(GuiContainer gui) {
        this.gui = gui;
        viewportWidth = gui.width;
        viewportHeight = gui.height;
        access = (ContainerPanelAccess) gui;
        region = ContainerInventoryRegion.of(gui.inventorySlots);
        if (InventoryUi.findLayout(gui.inventorySlots, gui.getClass()) == null)
            throw new IllegalArgumentException("No registered native presentation for " + gui.getClass());
        NativeLayoutPatch patch = NativeLayoutPatches.resolve(gui);
        frameStyle = patch.value(12);
        borderWidth = patch.value(16);
        nativeSectionMoved = patch.value(9) != 0 || patch.value(10) != 0;
        layout = patch.hints();
        int availableHeight = NativeScreenMargins.availableHeight(gui.height, patch.value(14), patch.value(15));
        if (gui instanceof AttachedChestAccess chest && chest.dynamicinventory$splitChest()) {
            // Recompute the native chest too: shrinking only the player grid left a tall chest
            // pinned to its old height and made live margin controls appear ineffective.
            chest.dynamicinventory$refreshChestLayout();
        }
        presentation = new InventoryPresentation(gui);
        for (int logical = 0; logical < region.getOrdinarySlotCount(); logical++)
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
                // A slot at y=8 already fits inside the native frame. Reserving 18px here invented
                // a negative top edge and left Galacticraft's header/tab ten pixels below our frame.
                nativeTop = Math.min(nativeTop, slot.yDisplayPosition - 8);
            }
        }
        nativeStorageTop = storageY - 4;
        measuredHeaderBoundary = NativeSectionGeometry
            .playerBoundary(nativeStorageTop, 0, machineBottom, patch.value(11));
        sectionGap = patch.value(11) >= 0 ? patch.value(11)
            : attachedChest != null ? AttachedChestLayout.GAP
                : Math.min(128, Math.max(0, measuredHeaderBoundary - machineBottom - 2));
        tabPadding = layout.getTopPadding();
        chestLayout = attachedChest == null ? null
            : new AttachedChestLayout(
                gui.width,
                gui.height,
                (region.getOrdinarySlotCount() - InventoryLayout.HOTBAR_SLOTS),
                patch.value(0),
                NativeLayoutPatches.requestedRows(gui, patch, 1),
                attachedChest.dynamicinventory$hasDescription(),
                attachedChest.dynamicinventory$chestWidth(),
                attachedChest.dynamicinventory$chestHeight(),
                chestSlots.size(),
                sectionGap,
                patch.value(13),
                patch.value(14),
                patch.value(15));
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
            panelTop = NativeSectionGeometry
                .playerBoundary(nativeStorageTop, layout.getMinimumHeaderHeight(), machineBottom, patch.value(11));
            headerY = tabPadding - nativeTop;
            int fixedColumns = InventoryUi.findLayout(gui.inventorySlots, gui.getClass())
                .getFixedPlayerColumns();
            columns = fixedColumns > 0 ? fixedColumns
                : Math.max(3, Math.min(patch.value(0), Math.min(18, (gui.width - 32) / 18)));
            rows = Math.max(
                1,
                Math.min(
                    Math.min(
                        NativeLayoutPatches.requestedRows(gui, patch, columns),
                        ((region.getOrdinarySlotCount() - InventoryLayout.HOTBAR_SLOTS) + columns - 1) / columns),
                    (availableHeight - headerY - panelTop - 50) / 18));
            panelWidth = Math.max(176, columns * 18 + 26);
            panelHeight = rows * 18 + 42;
            totalWidth = Math.max(nativeRight - nativeLeft, panelWidth);
            totalHeight = headerY + panelTop + panelHeight;
            headerX = (totalWidth - (nativeRight - nativeLeft)) / 2 - nativeLeft;
            chestOffsetX = headerX;
            chestOffsetY = headerY;
            gridX = (totalWidth - columns * 18 - (maxScroll() > 0 ? 10 : 0)) / 2;
        }
        // At tiny scales a fixed native description may need more than the requested usable
        // height. Retain that minimum rather than squeezing independently positioned sections together.
        int sectionHeight = Math.max(availableHeight, totalHeight);
        NativeSectionGeometry sections = new NativeSectionGeometry(
            totalWidth,
            chestLayout == null ? 0 : totalHeight,
            headerX,
            headerY,
            chestLayout == null ? nativeLeft : -headerX,
            chestLayout == null ? nativeRight : totalWidth - headerX,
            chestLayout == null ? nativeTop : -headerY,
            chestLayout == null ? nativeStorageTop : totalHeight - headerY,
            chestLayout == null ? (totalWidth - panelWidth) / 2 : chestLayout.playerX,
            headerY + panelTop,
            panelWidth,
            panelHeight,
            chestLayout == null ? patch.value(9) : 0,
            chestLayout == null
                ? fittedOffset(headerY + nativeTop, headerY + nativeStorageTop, patch.value(10), sectionHeight)
                : 0,
            patch.value(7),
            fittedOffset(headerY + panelTop, headerY + panelTop + panelHeight, patch.value(8), sectionHeight));
        headerX = sections.headerX;
        headerY = sections.headerY;
        playerX = sections.playerX;
        playerY = sections.playerY;
        shiftX = sections.shiftX;
        shiftY = sections.shiftY;
        chestOffsetX += shiftX;
        chestOffsetY += shiftY;
        panelTop = playerY - headerY;
        gridX = playerX + (panelWidth - columns * 18 - (maxScroll() > 0 ? 10 : 0)) / 2;
        totalWidth = sections.width;
        totalHeight = sections.height;
        nativeClipLeft = nativeLeft;
        nativeClipRight = nativeRight;
        nativeClipTop = nativeTop;
        // Never crop a live native slot. Blank artwork below it may be removed when the player band moves up.
        nativeContentBottom = machineBottom + 2;
        nativeClipBottom = Math.max(nativeContentBottom, Math.min(nativeStorageTop, playerY - headerY));
        // Offsets are preferences relative to the centered layout. Keep the fitted panel on screen.
        int left = Math
            .max(0, Math.min(Math.max(0, gui.width - totalWidth), (gui.width - totalWidth) / 2 + patch.value(2)));
        int top = NativeScreenMargins.top(gui.height, totalHeight, patch.value(3), patch.value(14), patch.value(15));
        for (Object entry : access.dynamicinventory$buttons()) {
            GuiButton button = (GuiButton) entry;
            if (!isNativeButton(button)) continue;
            buttons.put(button, new int[] { button.xPosition - oldLeft, button.yPosition - oldTop });
        }
        access.dynamicinventory$bounds(totalWidth, totalHeight, left, top);
        position();
    }

    /** Section offsets remain editable preferences, but cannot push a fitting section outside the viewport. */
    private static int fittedOffset(int top, int bottom, int requested, int availableHeight) {
        if (bottom - top >= availableHeight) return -top;
        return Math.max(-top, Math.min(availableHeight - bottom, requested));
    }

    public int visibleColumns() {
        return columns;
    }

    public int visibleRows() {
        return rows;
    }

    /** Absolute origin shared by native controls which draw or handle clicks outside the normal layers. */
    public int nativeHeaderLeft() {
        return access.dynamicinventory$left() + (drawingBackground == this || drawingForeground == this ? 0 : headerX);
    }

    public int nativeHeaderTop() {
        return access.dynamicinventory$top() + (drawingBackground == this || drawingForeground == this ? 0 : headerY);
    }

    /** Use the real viewport, even while background drawing temporarily substitutes native screen dimensions. */
    public int nativeViewportOffsetX() {
        return nativeHeaderLeft() - (viewportWidth - nativeWidth) / 2;
    }

    public int nativeViewportOffsetY() {
        return nativeHeaderTop() - (viewportHeight - nativeHeight) / 2;
    }

    private static boolean isNativeButton(GuiButton button) {
        String name = button.getClass()
            .getName();
        return !name.equals("com.cleanroommc.bogosorter.common.sort.ButtonHandler$SortButton")
            && !name.equals("net.blay09.mods.craftingtweaks.client.GuiTweakButton");
    }

    /** Some vanilla screens add buttons after initGui; retain each new object's native offset exactly once. */
    public void captureNewNativeButtons() {
        for (GuiButton button : access.dynamicinventory$buttons()) {
            if (isNativeButton(button) && !buttons.containsKey(button)) buttons.put(
                button,
                new int[] { button.xPosition - access.dynamicinventory$left(),
                    button.yPosition - access.dynamicinventory$top() });
        }
        position();
    }

    public boolean hasAttachedChest() {
        return attachedChest != null;
    }

    /** The header control is a floor, not an offset; expose the measured floor to explain small no-op changes. */
    public int nativeHeaderBoundary() {
        return measuredHeaderBoundary;
    }

    public int sectionGap() {
        return sectionGap;
    }

    public int[] editorSectionBounds(int target) {
        int x = access.dynamicinventory$left(), y = access.dynamicinventory$top();
        if (target == 1) return new int[] { x + playerX, y + playerY, panelWidth, panelHeight };
        if (target == 2) return new int[] { x + headerX + nativeClipLeft, y + headerY + nativeClipTop,
            nativeClipRight - nativeClipLeft, nativeClipBottom - nativeClipTop };
        return new int[] { x, y, access.dynamicinventory$width(), access.dynamicinventory$height() };
    }

    /** Drag coordinates use the same source preferences as the +/- controls. */
    public boolean isPlayerSection(int x, int y) {
        int localX = x - access.dynamicinventory$left(), localY = y - access.dynamicinventory$top();
        return localX >= playerX && localX < playerX + panelWidth
            && localY >= playerY
            && localY < playerY + panelHeight;
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
        int inset = panel.frameStyle != 0 ? Math.max(4, panel.layout.getBackgroundInset())
            : panel.chestLayout == null && InventoryViewSettings.compactNativePanels ? 0
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
        return Math
            .max(0, ((region.getOrdinarySlotCount() - InventoryLayout.HOTBAR_SLOTS) + columns - 1) / columns - rows);
    }

    public void scroll(int delta) {
        if (Minecraft.getMinecraft().thePlayer.inventory.getItemStack() != null) return;
        scroll = Math.max(0, Math.min(maxScroll(), scroll + delta));
        position();
    }

    public boolean inPanel(int x, int y) {
        return isPlayerSection(x, y);
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
                translated[4] = access.dynamicinventory$left() + chestLayout.descriptionX + shiftX;
                translated[5] = access.dynamicinventory$top() + chestLayout.descriptionY + shiftY;
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
        for (int logical = 0; logical < region.getOrdinarySlotCount(); logical++) {
            Slot slot = gui.inventorySlots.getSlot(region.id(logical));
            if (logical < 9) {
                slot.xDisplayPosition = playerX + (panelWidth - 162) / 2 + logical * 18;
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
        // Tabs belong to the visible frame, not the old 176px header centered inside a wider frame.
        // Compact mode still keeps that native header frame, so retain its own anchor there.
        boolean compactTabs = InventoryViewSettings.compactNativePanels && frameStyle == 0;
        InventoryTabsCompatibility.positionTabs(
            access.dynamicinventory$buttons(),
            access.dynamicinventory$left() + (compactTabs ? headerX : 0),
            access.dynamicinventory$top() + (compactTabs ? headerY : tabPadding));
        int tweak = 0;
        for (GuiButton button : access.dynamicinventory$buttons()) {
            if (!button.getClass()
                .getName()
                .equals("net.blay09.mods.craftingtweaks.client.GuiTweakButton")) continue;
            // Anchor to the actual grid, not the old GUI edge. This also follows editor section movement.
            button.xPosition = access.dynamicinventory$left() + gui.inventorySlots.getSlot(1).xDisplayPosition - 22;
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
        position();
        boolean compact = InventoryViewSettings.compactNativePanels && frameStyle == 0;
        // Native textures frequently calculate their origin directly from screen dimensions.
        // Give them their original dimensions and an equivalent virtual viewport for this draw only.
        int screenWidth = gui.width, screenHeight = gui.height;
        ScaledResolution scaled = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int factor = scaled.getScaleFactor();
        // Scissor cropping also works for Tessellator/custom-drawn frames such as Avaritiaddons.
        // The old drawTexturedModalRect hook alone could never affect those frames.
        int inset = chestLayout == null && !compact
            ? (frameStyle == 0 ? layout.getBackgroundInset() : Math.max(4, layout.getBackgroundInset()))
            : 0;
        int clipLeft = chestLayout == null && inset > 0 ? left + headerX + nativeClipLeft + inset : left;
        int clipTop = chestLayout == null ? top + headerY + nativeClipTop + inset : top;
        int clipRight = chestLayout == null && inset > 0 ? left + headerX + nativeClipRight - inset : left + fullWidth;
        int clipBottom = chestLayout == null ? top + headerY + Math.max(nativeContentBottom, nativeClipBottom - inset)
            : top + fullHeight;
        if (chestLayout != null) {
            int lowerTop = Math.min(chestLayout.lowerY + shiftY, playerY);
            if (frameStyle == 0)
                attachedChest.dynamicinventory$drawFrame(left, top + lowerTop, fullWidth, fullHeight - lowerTop);
            else NativePanelFrame
                .draw(left, top + lowerTop, fullWidth, fullHeight - lowerTop, frameStyle, borderWidth, null);
        } else if (frameStyle != 0) {
            NativePanelFrame.draw(
                left,
                top + tabPadding,
                fullWidth,
                fullHeight - tabPadding,
                frameStyle,
                borderWidth,
                new int[] { clipLeft, clipTop, clipRight, clipBottom });
        } else if (!compact) drawMergedFrame(left, top + tabPadding, fullWidth, fullHeight - tabPadding);
        GL11.glPushAttrib(GL11.GL_SCISSOR_BIT);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(
            clipLeft * factor,
            Math.max(0, mc.displayHeight - clipBottom * factor),
            Math.max(0, clipRight - clipLeft) * factor,
            Math.max(0, clipBottom - clipTop) * factor);
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
            playerX + panelWidth - 46,
            headerY + panelTop + 2,
            true);
        if (PackCompatibility.enabled() && chestLayout != null) PackClientCompatibility.positionChestSortButtons(
            access.dynamicinventory$buttons(),
            left,
            top,
            chestLayout.chestX + attachedChest.dynamicinventory$chestWidth() - 28,
            2);
        if (PackCompatibility.enabled() && chestLayout == null && nativeSectionMoved)
            PackClientCompatibility.positionMovedNativeSortButtons(access.dynamicinventory$buttons(), left, top);
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
            if (!presentation.slots(playerSlots)) for (int i = 0; i < region.getOrdinarySlotCount(); i++) {
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
            int track = rows * 18, thumb = Math.max(
                10,
                track * rows
                    / (((region.getOrdinarySlotCount() - InventoryLayout.HOTBAR_SLOTS) + columns - 1) / columns));
            int offset = (track - thumb) * scroll / maxScroll();
            drawRect(x, y, x + 8, y + track, InventoryGuiTheme.get().scrollTrack);
            drawRect(x + 1, y + offset, x + 7, y + offset + thumb, InventoryGuiTheme.get().scrollThumb);
        }
    }

    public void foreground(Runnable nativeDraw) {
        int w = access.dynamicinventory$width(), h = access.dynamicinventory$height();
        int x = access.dynamicinventory$left(), y = access.dynamicinventory$top();
        GL11.glPushMatrix();
        NativeInventoryPanel previousForeground = drawingForeground;
        try {
            drawingForeground = this;
            GL11.glTranslatef(headerX, headerY, 0);
            access.dynamicinventory$bounds(nativeWidth, nativeHeight, x + headerX, y + headerY);
            nativeDraw.run();
        } finally {
            drawingForeground = previousForeground;
            access.dynamicinventory$bounds(w, h, x, y);
            GL11.glPopMatrix();
        }
        if (chestLayout != null) Minecraft.getMinecraft().fontRenderer.drawString(
            net.minecraft.client.resources.I18n.format("container.inventory"),
            playerX + 8,
            playerY + 4,
            InventoryGuiTheme.get().label);
    }

    /** Move only the native player-inventory caption, leaving all machine/chest text in its native section. */
    public static int[] playerLabel(String text, int x, int y) {
        NativeInventoryPanel panel = drawingForeground;
        if (panel == null || panel.chestLayout != null
            || y < panel.nativeStorageTop - 16
            || y > panel.nativeStorageTop + 14
            || !net.minecraft.client.resources.I18n.format("container.inventory")
                .equals(text))
            return null;
        int targetX = panel.playerX + 8 - panel.headerX;
        int targetY = panel.playerY + 4 - panel.headerY;
        return targetX == x && targetY == y ? null : new int[] { targetX, targetY };
    }

    public String presentationStatus() {
        return presentation.status();
    }
}
