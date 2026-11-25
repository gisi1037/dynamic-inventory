package dev.gilbe.dynamicinventory.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.inventory.GuiContainer;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;

import dev.gilbe.dynamicinventory.client.InventoryPanelLayout.Rect;
import dev.gilbe.dynamicinventory.compat.PackClientCompatibility;
import dev.gilbe.dynamicinventory.compat.PackCompatibility;

/** Presentation and gestures for AE2's real inventories in breakout mode. */
public final class AeNativePanelControls extends Gui {

    public static final String MAIN = "main";
    public static final int PADDING = 6;
    private static final int CELL = 18;
    private static final int SLOT_SIZE = 16;
    private static final int FRAME_PADDING = 2 * PADDING - (CELL - SLOT_SIZE);

    public static final class Frame {

        final String id;
        final Rect suggested;
        final int slots, columns, rows;

        public Frame(String id, Rect suggested) {
            this(id, suggested, 0, 0, 0);
        }

        public Frame(String id, Rect suggested, int slots, int columns, int rows) {
            this.id = id;
            this.suggested = suggested;
            this.slots = slots;
            this.columns = columns;
            this.rows = rows;
        }

        private boolean tool() {
            return slots > 0;
        }

        private Rect size(int x, int y, int columns, int rows) {
            return tool() ? new Rect(x, y, columns * CELL + FRAME_PADDING, rows * CELL + FRAME_PADDING)
                : new Rect(x, y, suggested.width, suggested.height);
        }
    }

    public static final class Placement {

        final Rect bounds;
        final int columns, rows, scroll;

        Placement(Rect bounds, int columns, int rows, int scroll) {
            this.bounds = bounds;
            this.columns = columns;
            this.rows = rows;
            this.scroll = scroll;
        }

        public Rect bounds() {
            return bounds;
        }

        public boolean slotVisible(int index) {
            return index / columns >= scroll && index / columns < scroll + rows;
        }

        public int slotX(int index) {
            return bounds.x + PADDING + index % columns * CELL;
        }

        public int slotY(int index) {
            return bounds.y + PADDING + (index / columns - scroll) * CELL;
        }
    }

    private final GuiContainer gui;
    private final String key;
    private final Map<String, int[]> preferences = new LinkedHashMap<>();
    private final Map<String, Frame> frames = new LinkedHashMap<>();
    private final Map<String, Placement> placed = new LinkedHashMap<>();
    private final Map<String, Integer> scroll = new LinkedHashMap<>();
    private List<Rect> obstacles = Collections.emptyList();
    private Rect viewport;
    private int gap = 1;
    private Frame captured;
    private Placement initial, preview;
    private boolean resizing, releaseOwned;
    private int startX, startY;

    AeNativePanelControls(GuiContainer gui) {
        this.gui = gui;
        key = gui.getClass()
            .getName() + ".";
    }

    public Placement placement(String id) {
        return placed.get(id);
    }

    void layout(Rect area, List<Frame> requested, List<Rect> fixed, int gap) {
        viewport = area;
        obstacles = fixed;
        this.gap = gap;
        Map<String, Placement> previous = new LinkedHashMap<>(placed);
        frames.clear();
        placed.clear();
        List<Rect> occupied = new ArrayList<>(fixed);
        for (Frame frame : requested) {
            frames.put(frame.id, frame);
            Placement fitted;
            if (captured != null && captured.id.equals(frame.id)) {
                fitted = preview;
                occupied.add(initial.bounds);
            } else {
                int[] saved = preferences
                    .computeIfAbsent(frame.id, id -> InventoryViewSettings.nativeAe2Panel(key + id));
                boolean custom = saved.length == 4;
                int columns = frame.tool() ? limitColumns(frame, custom ? saved[2] : frame.columns) : 0;
                int rows = frame.tool() ? limitRows(frame, custom ? saved[3] : frame.rows) : 0;
                Rect wanted = frame
                    .size(custom ? saved[0] : frame.suggested.x, custom ? saved[1] : frame.suggested.y, columns, rows);
                Placement retained = previous.get(frame.id);
                Rect bounds = retained != null && retained.bounds.width == wanted.width
                    && retained.bounds.height == wanted.height
                    && area.contains(retained.bounds.x, retained.bounds.y)
                    && area.contains(retained.bounds.right() - 1, retained.bounds.bottom() - 1) ? retained.bounds
                        : fit(wanted, area, occupied, gap);
                if (bounds == null && frame.tool()) {
                    // Retain access to every tool slot through scrolling on a tight viewport.
                    while (bounds == null && rows > 1) {
                        wanted = frame.size(wanted.x, wanted.y, columns, --rows);
                        bounds = fit(wanted, area, occupied, gap);
                    }
                }
                if (bounds == null) bounds = clamp(wanted, area);
                fitted = placement(frame, bounds, columns, rows);
                occupied.add(bounds);
            }
            placed.put(frame.id, fitted);
        }
    }

    private int limitColumns(Frame frame, int value) {
        return Math.max(1, Math.min(value, Math.min(frame.slots, (viewport.width - FRAME_PADDING) / CELL)));
    }

    private int limitRows(Frame frame, int value) {
        return Math.max(1, Math.min(value, Math.min(frame.slots, (viewport.height - FRAME_PADDING) / CELL)));
    }

    private Placement placement(Frame frame, Rect bounds, int columns, int rows) {
        int offset = frame.tool() ? Math.min(scroll.getOrDefault(frame.id, 0), maxScroll(frame, columns, rows)) : 0;
        return new Placement(bounds, columns, rows, offset);
    }

    private static int maxScroll(Frame frame, int columns, int rows) {
        return Math.max(0, (frame.slots + columns - 1) / columns - rows);
    }

    private static Rect clamp(Rect desired, Rect viewport) {
        return new Rect(
            Math.max(viewport.x, Math.min(desired.x, viewport.right() - desired.width)),
            Math.max(viewport.y, Math.min(desired.y, viewport.bottom() - desired.height)),
            desired.width,
            desired.height);
    }

    static Rect fit(Rect desired, Rect viewport, List<Rect> occupied) {
        return fit(desired, viewport, occupied, 1);
    }

    private static Rect fit(Rect desired, Rect viewport, List<Rect> occupied, int gap) {
        if (desired.width > viewport.width || desired.height > viewport.height) return null;
        Rect clamped = clamp(desired, viewport);
        for (Rect obstacle : occupied)
            if (clamped.intersects(obstacle)) return InventoryPanelLayout.snapDrag(clamped, viewport, occupied, gap);
        return clamped;
    }

    boolean pointer(int x, int y, int button, boolean down) {
        if (releaseOwned && button == 0 && !down) {
            if (blocked()) cancel();
            else if (captured != null) finish(x, y);
            captured = null;
            releaseOwned = false;
            return true;
        }
        if (captured != null) {
            if (blocked()) cancel();
            else if (button == -1) move(x, y);
            return true;
        }
        if (releaseOwned || button != 0 || !down || blocked()) return releaseOwned;
        for (Frame frame : frames.values()) {
            Placement position = placed.get(frame.id);
            Rect bounds = position.bounds;
            boolean corner = frame.tool() && resizeGrip(bounds).contains(x, y);
            Rect track = frame.tool() && maxScroll(frame, position.columns, position.rows) > 0 ? scrollbar(bounds)
                : null;
            if (track != null && track.contains(x, y)) {
                int max = maxScroll(frame, position.columns, position.rows);
                scroll.put(frame.id, Math.min(max, (y - track.y) * (max + 1) / track.height));
                releaseOwned = true;
                return true;
            }
            if (!corner && !moveGrip(frame, bounds).contains(x, y)) continue;
            captured = frame;
            initial = preview = position;
            resizing = corner;
            startX = x;
            startY = y;
            releaseOwned = true;
            return true;
        }
        return false;
    }

    boolean wheel(int x, int y, int direction) {
        if (releaseOwned) return true;
        for (Frame frame : frames.values()) if (frame.tool()) {
            Placement position = placed.get(frame.id);
            if (!position.bounds.contains(x, y)) continue;
            int max = maxScroll(frame, position.columns, position.rows);
            if (max == 0) return false;
            scroll.put(frame.id, Math.max(0, Math.min(max, position.scroll + direction)));
            return true;
        }
        return false;
    }

    boolean cancel() {
        if (captured == null) return false;
        placed.put(captured.id, initial);
        captured = null;
        return true;
    }

    void clear() {
        captured = null;
        releaseOwned = false;
        frames.clear();
        placed.clear();
    }

    boolean dragging() {
        return captured != null;
    }

    void updateDragFrame(int x, int y) {
        if (captured == null) return;
        if (!Display.isActive() || blocked()) cancel();
        else if (Mouse.isButtonDown(0)) move(x, y);
    }

    private void move(int x, int y) {
        Rect start = initial.bounds;
        int dx = x - startX, dy = y - startY;
        int columns = initial.columns, rows = initial.rows;
        Rect bounds;
        if (resizing) {
            columns = Math.min(
                limitColumns(captured, columns + Math.round(dx / (float) CELL)),
                (viewport.right() - start.x - FRAME_PADDING) / CELL);
            rows = Math.min(
                limitRows(captured, rows + Math.round(dy / (float) CELL)),
                (viewport.bottom() - start.y - FRAME_PADDING) / CELL);
            if (columns < 1 || rows < 1) return;
            bounds = captured.size(start.x, start.y, columns, rows);
            for (Rect obstacle : obstaclesFor(captured.id)) if (bounds.intersects(obstacle)) return;
        } else {
            Rect wanted = new Rect(start.x + dx, start.y + dy, start.width, start.height);
            bounds = InventoryPanelLayout.alignDrag(wanted, viewport, obstaclesFor(captured.id), 3, gap).bounds;
        }
        preview = placement(captured, bounds, columns, rows);
    }

    private List<Rect> obstaclesFor(String id) {
        List<Rect> occupied = new ArrayList<>(obstacles);
        placed.forEach((other, position) -> { if (!id.equals(other)) occupied.add(position.bounds); });
        if (PackCompatibility.aePinnedPanel()) {
            Rect pins = AePinnedPanel.occupied(gui);
            if (pins != null) occupied.add(pins);
        }
        return occupied;
    }

    private void finish(int x, int y) {
        move(x, y);
        Rect fitted = fit(preview.bounds, viewport, obstaclesFor(captured.id), gap);
        if (fitted == null) {
            placed.put(captured.id, initial);
            return;
        }
        placed.put(captured.id, placement(captured, fitted, preview.columns, preview.rows));
        int[] saved = { fitted.x, fitted.y, preview.columns, preview.rows };
        preferences.put(captured.id, saved);
        InventoryViewSettings.setNativeAe2Panel(key + captured.id, saved);
        if (PackCompatibility.aePinnedPanel()) AePinnedPanel.retainPosition(gui);
    }

    private boolean blocked() {
        var player = Minecraft.getMinecraft().thePlayer;
        return player == null || ClientInputGuard.isPending(gui)
            || PanelSettingsOverlay.isOpen(gui)
            || player.inventory.getItemStack() != null
            || ((ContainerPanelAccess) gui).dynamicinventory$draggingItems()
            || PackCompatibility.enabled() && PackClientCompatibility.neiDragging();
    }

    private static Rect moveGrip(Frame frame, Rect bounds) {
        // The right corner of the main terminal belongs to AE2's crafting-status button.
        int x = MAIN.equals(frame.id) ? bounds.x + bounds.width / 4 - 5 : bounds.right() - 15;
        return new Rect(x, bounds.y, 11, 5);
    }

    private static Rect resizeGrip(Rect bounds) {
        return new Rect(bounds.right() - PADDING, bounds.bottom() - PADDING, PADDING, PADDING);
    }

    private static Rect scrollbar(Rect bounds) {
        return new Rect(bounds.right() - 4, bounds.y + PADDING, 2, bounds.height - 2 * PADDING);
    }

    void draw(int mouseX, int mouseY) {
        for (Frame frame : frames.values()) {
            Placement position = placed.get(frame.id);
            Rect bounds = position.bounds;
            Rect grip = moveGrip(frame, bounds);
            PanelChrome.insetDragGrip(grip, grip.contains(mouseX, mouseY) || captured == frame && !resizing);
            if (!frame.tool()) continue;
            PanelChrome.grip(bounds, 10, resizeGrip(bounds).contains(mouseX, mouseY));
            int max = maxScroll(frame, position.columns, position.rows);
            if (max == 0) continue;
            Rect track = scrollbar(bounds);
            int thumb = Math.max(8, track.height * position.rows / (position.rows + max));
            int y = track.y + (track.height - thumb) * position.scroll / max;
            drawRect(track.x, track.y, track.right(), track.bottom(), InventoryGuiTheme.get().scrollTrack);
            drawRect(track.x, y, track.right(), y + thumb, InventoryGuiTheme.get().scrollThumb);
        }
    }
}
