package dev.gilbe.dynamicinventory.qualification;

import dev.gilbe.dynamicinventory.client.InventoryViewSettings;
import dev.gilbe.dynamicinventory.client.InventoryViewSettings.TerminalKind;
import dev.gilbe.dynamicinventory.client.PlayerPanelProfile;
import dev.gilbe.dynamicinventory.client.TerminalPanelSpace;
import dev.gilbe.dynamicinventory.client.TerminalViewportLayout;

/** A requested row count stays fixed until it needs to yield space to the player inventory. */
public final class TerminalPanelSpaceFixtures {

    private TerminalPanelSpaceFixtures() {}

    public static void main(String[] args) {
        InventoryViewSettings.setTerminalRows(TerminalKind.NORMAL, 1, 5);
        InventoryViewSettings.setTerminalRows(TerminalKind.CRAFTING, 1, 7);
        InventoryViewSettings.setTerminalRows(TerminalKind.INTERFACE, 1, 11);
        check(InventoryViewSettings.terminalRows(TerminalKind.NORMAL, 1) == 5, "Normal rows are independent");
        check(InventoryViewSettings.terminalRows(TerminalKind.CRAFTING, 1) == 7, "Crafting rows are independent");
        check(InventoryViewSettings.terminalRows(TerminalKind.INTERFACE, 1) == 11, "Interface rows are independent");
        check(InventoryViewSettings.terminalRows(TerminalKind.INTERFACE, 0) == 3, "Interface styles are independent");
        PlayerPanelProfile wide = new PlayerPanelProfile(true, 280, 164, 5000, 9000);
        check(rows(wide, 444, 145, 6) == 6, "Tall stays at six rows when the screen could fit more");
        check(
            rows(wide.withBounds(280, 100, 5000, 9000), 444, 145, 6) == 6,
            "Shrinking the detached player frame does not grow the terminal");
        check(
            rows(wide, 444, 145, 24) > rows(wide.withBounds(40, 164, 5000, 9000), 444, 145, 24),
            "Narrow player frame reserves its wrapped hotbar");
        check(rows(wide.visible(false), 444, 145, 24) == 18, "Hidden Main leaves no phantom player band");
        check(TerminalViewportLayout.terminalRows(504, 153, 6) == 6, "Attached Tall does not fill surplus space");
        check(TerminalViewportLayout.terminalRows(243, 153, 6) == 4, "Attached terminal leaves one player row");
        for (int fixed : new int[] { 72, 117, 145, 153 }) {
            for (int requested : new int[] { 3, 6, 12, 24 }) {
                int previous = 0;
                for (int available : new int[] { 120, 180, 243, 373, 444, 700 }) {
                    int actual = rows(wide, available, fixed, requested);
                    check(
                        actual >= 3 && actual <= requested && actual >= previous,
                        "Taller viewport can restore requested rows but never exceed them");
                    check(
                        actual == rows(PlayerPanelProfile.fromJson(wide.toJson()), available, fixed, requested),
                        "Closing and reopening reconstructs the same row count");
                    previous = actual;
                }
                check(previous == requested, "A tall viewport reaches the exact requested row count");
            }
        }
        System.out.println(
            "PASS fixed terminal rows, attached and detached fit, wrapped hotbars, reopening and viewport changes");
    }

    private static int rows(PlayerPanelProfile profile, int available, int fixed, int requested) {
        return TerminalPanelSpace.rows(profile, 854, 8, available, fixed, requested);
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
