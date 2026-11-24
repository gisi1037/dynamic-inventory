package dev.gilbe.dynamicinventory.client;

import java.util.Arrays;
import java.util.Collections;

/** A saved panel keeps the same cells when a lost belt temporarily reduces inventory capacity. */
public final class PlayerPanelRespawnFixtures {

    private PlayerPanelRespawnFixtures() {}

    public static void main(String[] args) {
        PlayerPanelSet set = PlayerPanelSet.DEFAULT.add()
            .add();
        var belt = set.entries.get(1)
            .withSlots(Arrays.asList(153, 154, 155));
        var mixed = set.entries.get(2)
            .withSlots(Arrays.asList(63, 156, 64, 157, 65));
        set = set.replace(belt)
            .replace(mixed);
        var saved = set.toJson();
        int unavailable = PlayerPanelSet.UNAVAILABLE_SLOT;
        check(
            set.displayCells(belt, 72, Collections.emptySet())
                .equals(Arrays.asList(unavailable, unavailable, unavailable)),
            "A panel with no surviving slots still has the same three display cells");
        check(
            set.displayCells(mixed, 72, Collections.emptySet())
                .equals(Arrays.asList(63, unavailable, 64, unavailable, 65)),
            "Surviving cells stay in their original positions");
        check(
            set.members(belt.id, 72)
                .isEmpty()
                && set.members(mixed.id, 72)
                    .equals(Arrays.asList(63, 64, 65)),
            "Unavailable slots cannot be routed to, clicked, or sorted");
        check(
            set.displayCells(mixed, 72, Collections.singleton(64))
                .equals(Arrays.asList(63, unavailable, unavailable, 65)),
            "Active hotbar bank assignments retain their existing exclusion");
        check(
            set.displayCells(belt, 180, Collections.emptySet())
                .equals(belt.slots)
                && set.displayCells(mixed, 180, Collections.emptySet())
                    .equals(mixed.slots),
            "Restoring belt capacity reactivates the original storage identities");
        check(
            set.toJson()
                .equals(saved),
            "Capacity changes do not rewrite saved allocations or appearance");
        System.out
            .println("PASS saved panels keep disabled cells through capacity loss and restore their original slots");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
