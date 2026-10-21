package dev.gilbe.dynamicinventory.inventory;

import java.util.Arrays;

/**
 * Pure preflight/copy step, not a live resize API. The game-thread coordinator
 * must close screens, settle the cursor, rebuild containers, persist and synchronize
 * before publishing this result. This class never changes an inventory or drops items.
 */
public final class InventoryResizePlan<T> {

    private final InventoryShape before;
    private final InventoryShape after;
    private final T[] source;

    public InventoryResizePlan(InventoryShape before, T[] source, int storageSlots) {
        if (before == null || source == null) throw new NullPointerException("shape/source");
        this.before = before;
        this.after = before.resized(storageSlots);
        this.source = source;
        validate(source);
    }

    public InventoryShape before() {
        return before;
    }

    public InventoryShape after() {
        return after;
    }

    private void validate(T[] contents) {
        if (contents.length != before.rawMainSlots())
            throw new IllegalArgumentException("Backing size differs from shape");
        for (int raw = after.rawMainSlots(); raw < contents.length; raw++)
            if (contents[raw] != null) throw new IllegalStateException("Removed slot is occupied: " + raw);
    }

    /**
     * Recheck at commit time: pickups or callbacks may have changed cells since preflight.
     * Retained item objects keep their identity. A detached copy of the ARRAY is returned;
     * there is still only one canonical inventory when the coordinator publishes it.
     */
    public T[] copyContents(InventoryShape current, T[] contents) {
        if (current != before || contents != source) throw new IllegalStateException("Stale or foreign resize plan");
        validate(contents);
        return Arrays.copyOf(contents, after.rawMainSlots());
    }
}
