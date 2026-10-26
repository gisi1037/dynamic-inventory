package dev.gilbe.dynamicinventory.client;

/** Pure player-panel fitting. All coordinates are scaled screen pixels, never container slot IDs. */
public final class InventoryPanelLayout {

    public static final int CELL = 18, MIN_WIDTH = 178, MIN_HEIGHT = 64;
    public final Rect bounds;
    public final int columns, rows, gridX, gridY, hotbarX, hotbarY;

    private InventoryPanelLayout(Rect bounds, int storage, int margin) {
        this(bounds, storage, margin, true);
    }

    private InventoryPanelLayout(Rect bounds, int storage, int margin, boolean hotbar) {
        int overhead = (hotbar ? 38 : 18) + margin;
        columns = Math.max(hotbar ? 9 : 3, Math.min(18, (bounds.width - margin * 2) / CELL));
        rows = Math
            .max(1, Math.min(12, Math.min((storage + columns - 1) / columns, (bounds.height - overhead) / CELL)));
        this.bounds = bounds;
        // Keep the frame under the pointer; distribute space between cell thresholds on both sides.
        // Coordinates refer to item origins. Slot artwork begins one pixel before the item origin.
        gridX = bounds.x + margin + 1 + (bounds.width - (columns * CELL + margin * 2)) / 2;
        gridY = bounds.y + 17 + (bounds.height - (rows * CELL + overhead)) / 2;
        // The scrollbar gutter belongs to neither grid, so center the hotbar under storage itself.
        hotbarX = gridX + (columns - 9) * CELL / 2;
        hotbarY = gridY + rows * CELL + 4;
    }

    public int maxScroll(int storage) {
        return Math.max(0, (storage + columns - 1) / columns - rows);
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
        margin = Math.max(8, Math.min(18, margin));
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
        margin = Math.max(8, Math.min(18, margin));
        int minWidth = (hotbar ? 162 : 54) + margin * 2;
        int minHeight = (hotbar ? 56 : 36) + margin;
        InventoryPanelLayout best = null;
        long cost = Long.MAX_VALUE;
        for (Rect area : freeAreas(viewport, obstacles, minWidth, minHeight, 4)) {
            int w = Math.min(area.width, Math.max(minWidth, requested.width));
            int h = Math.min(area.height, Math.max(minHeight, requested.height));
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
