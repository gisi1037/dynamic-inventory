package dev.gilbe.dynamicinventory.qualification;

import dev.gilbe.dynamicinventory.upgrades.UpgradeCapacity;

/** Boundary/configuration matrix without Minecraft or Forge. Runtime tests cover the actual items. */
public final class UpgradeCapacityFixtures {

    public static void main(String[] args) {
        int checks = 0;
        for (int base = 27; base <= 216; base++) {
            for (int bonus = 0; bonus <= 189; bonus++) {
                check(UpgradeCapacity.effective(base, bonus) == Math.min(216, base + bonus));
                check(UpgradeCapacity.effective(base, 0) == base); // Removal restores base; never compound bonuses.
                checks += 2;
            }
            for (int step = 1; step <= 189; step++) {
                final int previous = base, increment = step;
                if (base + step <= 216) check(UpgradeCapacity.upgradedBase(base, 0, step, 16) == base + step);
                else refused(() -> UpgradeCapacity.upgradedBase(previous, 0, increment, 16));
                checks++;
            }
        }
        refused(() -> UpgradeCapacity.upgradedBase(72, 16, 9, 16));
        refused(() -> UpgradeCapacity.upgradedBase(72, 0, 9, 0));
        refused(() -> UpgradeCapacity.upgradedBase(72, -1, 9, 16));
        refused(() -> UpgradeCapacity.upgradedBase(72, 0, 0, 16));
        refused(() -> UpgradeCapacity.effective(26, 9));
        refused(() -> UpgradeCapacity.effective(217, 9));
        refused(() -> UpgradeCapacity.effective(72, -1));
        check(UpgradeCapacity.effective(216, Integer.MAX_VALUE) == 216);
        System.out.println("PASS upgrade policy: " + (checks + 8) + " boundary, cap, removal and limit checks");
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("Upgrade policy mismatch");
    }

    private static void refused(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException | IllegalStateException expected) {
            return;
        }
        throw new AssertionError("Unsafe upgrade policy accepted");
    }
}
