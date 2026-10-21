package dev.gilbe.dynamicinventory.qualification;

import java.util.Arrays;

import dev.gilbe.dynamicinventory.inventory.InventoryResizePlan;
import dev.gilbe.dynamicinventory.inventory.InventoryShape;

/** Pure Java matrix: no Minecraft, Forge, mutable process capacity, or game input. */
public final class InventoryShapeFixtures {

    private static long checks;

    public static void main(String[] args) {
        Object[] markers = new Object[InventoryShape.MAX_STORAGE + InventoryShape.HOTBAR_SLOTS + 1];
        for (int i = 0; i < markers.length; i++) markers[i] = new Object();
        int plans = 0;
        for (boolean offhand : new boolean[] { false, true }) {
            for (int storage = InventoryShape.MIN_STORAGE; storage <= InventoryShape.MAX_STORAGE; storage++) {
                InventoryShape before = new InventoryShape(storage, offhand, 7);
                for (int logical = 0; logical < before.mainSlots(); logical++) {
                    check(before.ordinaryIndex(before.rawIndex(logical)) == logical, "index round trip");
                    check(before.isExtra(logical) == (logical >= 36), "ordinary slot role");
                }
                check(
                    before.ordinaryIndex(-1) == -1 && before.ordinaryIndex(before.rawMainSlots()) == -1,
                    "out-of-range raw indices are not ordinary");
                if (offhand) check(before.ordinaryIndex(36) == -1, "offhand never becomes storage");
                for (int next = InventoryShape.MIN_STORAGE; next <= InventoryShape.MAX_STORAGE; next++) {
                    InventoryShape expected = before.resized(next);
                    Object[] source = new Object[before.rawMainSlots()];
                    int retained = Math.min(source.length, expected.rawMainSlots());
                    System.arraycopy(markers, 0, source, 0, retained);
                    Object[] original = source.clone();
                    InventoryResizePlan<Object> plan = new InventoryResizePlan<>(before, source, next);
                    Object[] copied = plan.copyContents(before, source);
                    check(copied != source && copied.length == expected.rawMainSlots(), "fresh correctly sized array");
                    check(Arrays.equals(original, source), "preflight/copy leave original contents untouched");
                    check(
                        plan.after()
                            .revision() == 8 && before.revision() == 7,
                        "immutable monotonic revision");
                    check(
                        before.storageSlots() == storage && plan.after()
                            .storageSlots() == next,
                        "independent capacities");
                    for (int raw = 0; raw < retained; raw++)
                        check(copied[raw] == markers[raw], "retained item identity");
                    for (int raw = retained; raw < copied.length; raw++) check(copied[raw] == null, "growth is empty");
                    for (int logical = 0; logical < Math.min(before.mainSlots(), expected.mainSlots()); logical++)
                        check(
                            before.rawIndex(logical) == expected.rawIndex(logical),
                            "existing logical identity stays put");
                    if (offhand) check(copied[36] == markers[36], "offhand retained at raw 36");
                    plans++;
                }
            }
        }

        InventoryShape before = new InventoryShape(72, true, 0);
        Object[] first = new Object[before.rawMainSlots()];
        InventoryResizePlan<Object> shrink = new InventoryResizePlan<>(before, first, 27);
        rejects(() -> shrink.copyContents(before, first.clone()), "another player's array");
        rejects(() -> shrink.copyContents(before.resized(72), first), "stale snapshot");
        first[0] = markers[0]; // A retained pickup after preflight must be copied at commit, not lost.
        check(shrink.copyContents(before, first)[0] == markers[0], "late retained changes included");
        first[first.length - 1] = markers[1];
        rejects(() -> shrink.copyContents(before, first), "newly occupied removed slot");
        rejects(() -> new InventoryResizePlan<>(before, first, 27), "occupied shrink at preflight");
        check(first[first.length - 1] == markers[1], "refused shrink leaves items in place");
        rejects(() -> new InventoryResizePlan<>(before, new Object[36], 72), "wrong backing array size");
        rejects(() -> new InventoryShape(26, false, 0), "below minimum");
        rejects(() -> new InventoryShape(217, false, 0), "above maximum");
        rejects(() -> new InventoryShape(72, false, -1), "negative revision");
        rejects(() -> new InventoryShape(72, false, Long.MAX_VALUE).resized(73), "revision overflow");
        System.out.println("PASS inventory shapes: " + plans + " resize plans; " + checks + " assertions");
    }

    private static void check(boolean condition, String label) {
        checks++;
        if (!condition) throw new AssertionError(label);
    }

    private static void rejects(Runnable action, String label) {
        try {
            action.run();
        } catch (IllegalArgumentException | IllegalStateException | ArithmeticException expected) {
            checks++;
            return;
        }
        throw new AssertionError("Accepted " + label);
    }
}
