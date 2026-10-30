package dev.gilbe.dynamicinventory.client;

/** Pure player-panel fitting. All coordinates are scaled screen pixels, never container slot IDs. */
public final class InventoryPanelLayout {

    public static final int CELL = 18, MIN_WIDTH = 178, MIN_HEIGHT = 64;
    public final Rect bounds;
    public final int columns, rows, gridX, gridY, hotbarX, hotbarY, hotbarColumns;

    private InventoryPanelLayout(Rect bounds, int storage, int margin) {
        this(bounds, storage, margin, true);
    }

    private InventoryPanelLayout(Rect bounds, int storage, int margin, boolean hotbar) {
        columns = Math.max(1, Math.min(18, (bounds.width - margin * 2) / CELL));
        hotbarColumns = Math.min(9, columns);
        int overhead = (hotbar ? 20 + ((9 + hotbarColumns - 1) / hotbarColumns) * CELL : 18) + margin;
        rows = Math
            .max(1, Math.min(12, Math.min((storage + columns - 1) / columns, (bounds.height - overhead) / CELL)));
        this.bounds = bounds;
        // Keep the frame under the pointer; distribute space between cell thresholds on both sides.
        // Coordinates refer to item origins. Slot artwork begins one pixel before the item origin.
        gridX = bounds.x + margin + 1 + (bounds.width - (columns * CELL + margin * 2)) / 2;
        gridY = bounds.y + 17 + (bounds.height - (rows * CELL + overhead)) / 2;
        // The scrollbar gutter belongs to neither grid, so center the hotbar under storage itself.
        hotbarX = gridX + (columns - hotbarColumns) * CELL / 2;
        hotbarY = gridY + rows * CELL + 4;
    }

    public int maxScroll(int storage) {
        return Math.max(0, (storage + columns - 1) / columns - rows);
    }

    public int hotbarSlotX(int index) {
        int row = index / hotbarColumns;
        int occupied = Math.min(hotbarColumns, 9 - row * hotbarColumns);
        return gridX + (columns - occupied) * CELL / 2 + index % hotbarColumns * CELL;
    }

    public int hotbarSlotY(int index) {
        return hotbarY + index / hotbarColumns * CELL;
    }

    public static int minimumHeight(int width, int margin, boolean hotbar) {
        int columns = Math.max(1, Math.min(9, (width - margin * 2) / CELL));
        return (hotbar ? 38 + ((9 + columns - 1) / columns) * CELL : 36) + margin;
    }

    /** Center a partial storage row without changing its slot identities or row order. */
    public static int rowOffset(int index, int storage, int columns) {
        int occupied = Math.min(columns, storage - index / columns * columns);
        return (columns - occupied) * CELL / 2;
    }

    public Rect scrollbar() {
        return new Rect(gridX + columns * CELL, gridY - 1, 3, rows * CELL);
    }

    public Rect scrollbarTarget() {
        Rect track = scrollbar();
        return new Rect(track.x, track.y, Math.min(6, bounds.right() - 2 - track.x), track.height);
    }

    /** Pick a fitting non-overlapping region. Null means this viewport cannot fit two usable panels. */
    public static InventoryPanelLayout fit(Rect requested, Rect viewport, Rect machine, int storage) {
        return fit(requested, viewport, machine, storage, 8);
    }

    public static InventoryPanelLayout fit(Rect requested, Rect viewport, Rect machine, int storage, int margin) {
        margin = Math.max(4, Math.min(18, margin));
        int minimumWidth = 162 + margin * 2, minimumHeight = 56 + margin;
        Rect[] areas = { viewport };
        if (machine != null) areas = new Rect[] { viewport,
            new Rect(viewport.x, viewport.y, machine.x - viewport.x - 4, viewport.height),
            new Rect(machine.right() + 4, viewport.y, viewport.right() - machine.right() - 4, viewport.height),
            new Rect(viewport.x, viewport.y, viewport.width, machine.y - viewport.y - 4),
            new Rect(viewport.x, machine.bottom() + 4, viewport.width, viewport.bottom() - machine.bottom() - 4) };
        InventoryPanelLayout best = null;
        long bestCost = Long.MAX_VALUE;
        for (Rect area : areas) {
            if (area.width < minimumWidth || area.height < minimumHeight) continue;
            int w = Math.min(Math.max(minimumWidth, requested.width), area.width);
            int h = Math.min(Math.max(minimumHeight, requested.height), area.height);
            Rect fitted = new Rect(
                clamp(requested.x, area.x, area.right() - w),
                clamp(requested.y, area.y, area.bottom() - h),
                w,
                h);
            InventoryPanelLayout candidate = new InventoryPanelLayout(fitted, storage, margin);
            if (machine != null && candidate.bounds.intersects(machine)) continue;
            long cost = square(candidate.bounds.x - requested.x) + square(candidate.bounds.y - requested.y)
                + square(candidate.bounds.width - requested.width)
                + square(candidate.bounds.height - requested.height);
            if (cost < bestCost) {
                best = candidate;
                bestCost = cost;
            }
        }
        return best;
    }

    public static InventoryPanelLayout fit(Rect requested, Rect viewport, java.util.List<Rect> obstacles, int storage,
        int margin, boolean hotbar) {
        margin = Math.max(4, Math.min(18, margin));
        int minWidth = 18 + margin * 2;
        int minHeight = (hotbar ? 56 : 36) + margin;
        InventoryPanelLayout best = null;
        long cost = Long.MAX_VALUE;
        for (Rect area : freeAreas(viewport, obstacles, minWidth, minHeight, 1)) {
            int w = Math.min(area.width, Math.max(minWidth, requested.width));
            while (minimumHeight(w, margin, hotbar) > area.height) {
                int wider = ((w - margin * 2) / CELL + 1) * CELL + margin * 2;
                if (wider > area.width) break;
                w = wider;
            }
            int requiredHeight = minimumHeight(w, margin, hotbar);
            if (requiredHeight > area.height) continue;
            int h = Math.min(area.height, Math.max(requiredHeight, requested.height));
            Rect rect = new Rect(
                clamp(requested.x, area.x, area.right() - w),
                clamp(requested.y, area.y, area.bottom() - h),
                w,
                h);
            long candidate = square(rect.x - requested.x) + square(rect.y - requested.y)
                + square(rect.width - requested.width)
                + square(rect.height - requested.height);
            if (candidate < cost) {
                best = new InventoryPanelLayout(rect, storage, margin, hotbar);
                cost = candidate;
            }
        }
        return best;
    }

    /** Closest unobstructed position to the pointer, keeping the panel's dimensions and a one-pixel gap. */
    public static Rect snapDrag(Rect requested, Rect viewport, java.util.List<Rect> obstacles) {
        Rect best = null;
        long cost = Long.MAX_VALUE;
        for (Rect area : freeAreas(viewport, obstacles, requested.width, requested.height, 1)) {
            Rect candidate = new Rect(
                clamp(requested.x, area.x, area.right() - requested.width),
                clamp(requested.y, area.y, area.bottom() - requested.height),
                requested.width,
                requested.height);
            long distance = square(candidate.x - requested.x) + square(candidate.y - requested.y);
            if (distance < cost) {
                best = candidate;
                cost = distance;
            }
        }
        return best;
    }

    public static final class Alignment {

        public final Rect bounds;
        public final int guideX, guideY;

        private Alignment(Rect bounds, int guideX, int guideY) {
            this.bounds = bounds;
            this.guideX = guideX;
            this.guideY = guideY;
        }
    }

    /** Small edge/center magnets, measured from the requested position rather than the previous frame. */
    public static Alignment alignDrag(Rect requested, Rect viewport, java.util.List<Rect> panels, int tolerance) {
        int x = clamp(requested.x, viewport.x, viewport.right() - requested.width);
        int y = clamp(requested.y, viewport.y, viewport.bottom() - requested.height);
        int dx = tolerance + 1, dy = tolerance + 1;
        int guideX = Integer.MIN_VALUE, guideY = Integer.MIN_VALUE;
        java.util.List<Rect> references = new java.util.ArrayList<>();
        references.add(viewport);
        references.addAll(panels);
        for (Rect reference : references) {
            int[] horizontal = { reference.x, reference.right() - requested.width,
                reference.x + (reference.width - requested.width) / 2 };
            int[] vertical = { reference.y, reference.bottom() - requested.height,
                reference.y + (reference.height - requested.height) / 2 };
            int[] xGuides = { reference.x, reference.right(), reference.x + reference.width / 2 };
            int[] yGuides = { reference.y, reference.bottom(), reference.y + reference.height / 2 };
            for (int i = 0; i < 3; i++) {
                int offsetX = horizontal[i] - x, offsetY = vertical[i] - y;
                if (Math.abs(offsetX) < Math.abs(dx)) {
                    dx = offsetX;
                    guideX = xGuides[i];
                }
                if (Math.abs(offsetY) < Math.abs(dy)) {
                    dy = offsetY;
                    guideY = yGuides[i];
                }
            }
        }
        if (Math.abs(dx) > tolerance) dx = 0;
        if (Math.abs(dy) > tolerance) dy = 0;
        return new Alignment(new Rect(x + dx, y + dy, requested.width, requested.height), guideX, guideY);
    }

    /** Resizing follows the pointer up to the first obstacle, preserving its anchored edge. */
    public static Rect constrainGesture(Rect before, Rect requested, Rect viewport, java.util.List<Rect> obstacles,
        int minimumWidth, int minimumHeight) {
        int w = Math.max(minimumWidth, Math.min(viewport.width, requested.width));
        int h = Math.max(minimumHeight, Math.min(viewport.height, requested.height));
        Rect target = new Rect(
            clamp(requested.x, viewport.x, viewport.right() - w),
            clamp(requested.y, viewport.y, viewport.bottom() - h),
            w,
            h);
        int steps = Math.max(
            Math.max(Math.abs(target.x - before.x), Math.abs(target.y - before.y)),
            Math.max(Math.abs(target.width - before.width), Math.abs(target.height - before.height)));
        Rect safe = before;
        for (int step = 1; step <= steps; step++) {
            Rect candidate = new Rect(
                before.x + (int) ((long) (target.x - before.x) * step / steps),
                before.y + (int) ((long) (target.y - before.y) * step / steps),
                before.width + (int) ((long) (target.width - before.width) * step / steps),
                before.height + (int) ((long) (target.height - before.height) * step / steps));
            for (Rect obstacle : obstacles) if (blocksResize(before, candidate, obstacle)) return safe;
            safe = candidate;
        }
        return safe;
    }

    /** Existing touching edges may stay touching while resizing away or parallel to them. */
    public static boolean blocksResize(Rect before, Rect after, Rect obstacle) {
        Rect padded = new Rect(obstacle.x - 1, obstacle.y - 1, obstacle.width + 2, obstacle.height + 2);
        return after.intersects(before.intersects(padded) && !before.intersects(obstacle) ? obstacle : padded);
    }

    /** Nearest complete grid size, keeping the opposite corner fixed and respecting neighboring frames. */
    public static Rect snapResize(Rect before, Rect requested, int grip, Rect viewport, java.util.List<Rect> obstacles,
        int margin, boolean hotbar, int storage) {
        Rect best = null;
        long cost = Long.MAX_VALUE;
        for (int columns = 1; columns <= 18; columns++) {
            int width = columns * CELL + margin * 2;
            if (width > 340) break;
            int overhead = minimumHeight(width, margin, hotbar) - CELL;
            int rowLimit = Math.max(1, Math.min(12, (storage + columns - 1) / columns));
            for (int rows = 1; rows <= rowLimit; rows++) {
                int height = rows * CELL + overhead;
                if (height > 262) break;
                Rect candidate = new Rect(
                    (grip & 1) != 0 ? before.right() - width : before.x,
                    (grip & 4) != 0 ? before.bottom() - height : before.y,
                    width,
                    height);
                if (!contains(viewport, candidate)) continue;
                boolean blocked = false;
                for (Rect obstacle : obstacles) if (blocksResize(before, candidate, obstacle)) {
                    blocked = true;
                    break;
                }
                if (blocked) continue;
                long distance = square(width - requested.width) + square(height - requested.height);
                if (distance < cost) {
                    best = candidate;
                    cost = distance;
                }
            }
        }
        return best;
    }

    private static long square(int value) {
        return (long) value * value;
    }

    /** Keep grouped bookmarks rectangular: their first column and group gutter must remain uninterrupted. */
    public static Rect freeRegion(Rect bounds, java.util.List<Rect> obstacles, int minimumWidth, int minimumHeight) {
        java.util.List<Rect> areas = freeAreas(bounds, obstacles, minimumWidth, minimumHeight, 2);
        Rect best = null;
        for (Rect area : areas) if (best == null || area.height == bounds.height && best.height != bounds.height
            || (area.height == bounds.height) == (best.height == bounds.height)
                && (long) area.width * area.height > (long) best.width * best.height)
            best = area;
        return best;
    }

    private static java.util.List<Rect> freeAreas(Rect bounds, java.util.List<Rect> obstacles, int minimumWidth,
        int minimumHeight, int gap) {
        java.util.List<Rect> areas = new java.util.ArrayList<>();
        areas.add(bounds);
        for (Rect obstacle : obstacles) {
            java.util.List<Rect> next = new java.util.ArrayList<>();
            for (Rect area : areas) {
                if (!area.intersects(obstacle)) next.add(area);
                else {
                    next.add(new Rect(area.x, area.y, obstacle.x - area.x - gap, area.height));
                    next.add(
                        new Rect(obstacle.right() + gap, area.y, area.right() - obstacle.right() - gap, area.height));
                    next.add(new Rect(area.x, area.y, area.width, obstacle.y - area.y - gap));
                    next.add(
                        new Rect(area.x, obstacle.bottom() + gap, area.width, area.bottom() - obstacle.bottom() - gap));
                }
            }
            areas = new java.util.ArrayList<>();
            for (Rect area : next) {
                if (area.width < minimumWidth || area.height < minimumHeight) continue;
                boolean contained = false;
                for (Rect other : areas) if (contains(other, area)) {
                    contained = true;
                    break;
                }
                if (!contained) {
                    areas.removeIf(other -> contains(area, other));
                    areas.add(area);
                }
            }
        }
        return areas;
    }

    private static boolean contains(Rect outer, Rect inner) {
        return outer.x <= inner.x && outer.y <= inner.y
            && outer.right() >= inner.right()
            && outer.bottom() >= inner.bottom();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static final class Rect {

        public final int x, y, width, height;

        public Rect(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        public int right() {
            return x + width;
        }

        public int bottom() {
            return y + height;
        }

        public boolean contains(int px, int py) {
            return px >= x && px < right() && py >= y && py < bottom();
        }

        public boolean intersects(Rect other) {
            return width > 0 && height > 0
                && other.width > 0
                && other.height > 0
                && x < other.right()
                && right() > other.x
                && y < other.bottom()
                && bottom() > other.y;
        }
    }
}
