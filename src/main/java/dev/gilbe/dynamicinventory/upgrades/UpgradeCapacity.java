package dev.gilbe.dynamicinventory.upgrades;

import dev.gilbe.dynamicinventory.inventory.InventoryShape;

/** Small, pure capacity policy shared by items, equipment and tests. Hotbar slots never change. */
public final class UpgradeCapacity {

    private UpgradeCapacity() {}

    public static int effective(int base, int bonus) {
        if (base < InventoryShape.MIN_STORAGE || base > InventoryShape.MAX_STORAGE || bonus < 0)
            throw new IllegalArgumentException("Invalid upgrade capacity");
        return (int) Math.min(InventoryShape.MAX_STORAGE, (long) base + bonus);
    }

    public static int upgradedBase(int base, int used, int step, int limit) {
        effective(base, 0);
        if (used < 0 || used >= limit) throw new IllegalStateException("Permanent upgrade limit reached");
        if (step < 1 || (long) base + step > InventoryShape.MAX_STORAGE)
            throw new IllegalStateException("Not enough capacity for another complete upgrade");
        return base + step;
    }
}
