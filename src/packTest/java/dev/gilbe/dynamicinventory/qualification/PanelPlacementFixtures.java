package dev.gilbe.dynamicinventory.qualification;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import dev.gilbe.dynamicinventory.client.InventoryPanelLayout;
import dev.gilbe.dynamicinventory.client.InventoryPanelLayout.Rect;
import dev.gilbe.dynamicinventory.client.PanelLayoutReference;
import dev.gilbe.dynamicinventory.client.PlayerPanelProfile;
import dev.gilbe.dynamicinventory.client.PlayerPanelProfiles;
import dev.gilbe.dynamicinventory.client.PlayerPanelSet;

/** Observed MUI2 reopen geometry and authored placement intent; only temporary preferences are used. */
public final class PanelPlacementFixtures {

    private static final String KEY = "dynamicinventory:mui2/test|fixture.DebugInputHatch|fixture.Gui";
    private static final Rect VIEWPORT = new Rect(0, 8, 854, 415), MACHINE = new Rect(339, 97, 176, 85);

    private PanelPlacementFixtures() {}

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("dinv-placement-"), file = directory.resolve("player-panels.json");
        try {
            PlayerPanelProfiles.initialize(file.toFile());
            PlayerPanelProfiles.applyGlobalPanels(
                PlayerPanelSet.DEFAULT,
                KEY,
                PlayerPanelProfile.DEFAULT.detached(true),
                new PanelLayoutReference(new Rect(119, 42, 188, 90), new Rect(0, 8, 427, 204)));
            Rect requested = PlayerPanelProfiles
                .requestedBounds(KEY, PlayerPanelProfiles.resolve(KEY), MACHINE, VIEWPORT);
            check(same(requested, new Rect(338, 65, 178, 190)), "Replay captured Debug Input Hatch request");
            byte[] saved = Files.readAllBytes(file);
            Rect first = null;
            for (int reopen = 0; reopen < 4; reopen++) {
                PlayerPanelProfiles.initialize(file.toFile());
                InventoryPanelLayout fitted = fit(MACHINE, VIEWPORT);
                check(
                    fitted != null && fitted.bounds.y >= MACHINE.bottom() + 1,
                    "Inherited reopen must stay below the hatch, not choose the strip above");
                check(fitted.bounds.height == 190 && fitted.bounds.width == 178, "Full panel fits below hatch");
                if (first == null) first = fitted.bounds;
                else check(same(first, fitted.bounds), "Fresh reopen must keep identical geometry");
                check(!PlayerPanelProfiles.hasOverride(KEY), "Fitting must not create a custom override");
            }
            check(Arrays.equals(saved, Files.readAllBytes(file)), "Automatic fitting never writes preferences");
            // Player header, native container and changing MUI2 header geometry use the same policy.
            for (Rect host : new Rect[] { new Rect(338, 111, 178, 90), new Rect(339, 30, 176, 78),
                new Rect(339, 97, 176, 110), new Rect(339, 30, 176, 300) }) {
                Rect requestedHost = PlayerPanelProfiles
                    .requestedBounds(KEY, PlayerPanelProfiles.resolve(KEY), host, VIEWPORT);
                Rect permitted = PlayerPanelProfiles
                    .fittingViewport(KEY, PlayerPanelProfiles.resolve(KEY), host, VIEWPORT);
                InventoryPanelLayout firstFit = InventoryPanelLayout
                    .fit(requestedHost, permitted, Collections.singletonList(host), 72, 8, true);
                check(
                    firstFit != null && firstFit.bounds.y >= host.y,
                    "Automatic player/native/MUI2 fitting cannot choose space above the host");
                InventoryPanelLayout retained = InventoryPanelLayout
                    .fit(firstFit.bounds, permitted, Collections.singletonList(host), 72, 8, true);
                check(same(firstFit.bounds, retained.bounds), "Later host clearance cannot undo the initial placement");
                InventoryPanelLayout reopened = fit(host, VIEWPORT);
                check(same(firstFit.bounds, reopened.bounds), "Fresh binding and retained binding agree");
            }
            authored(file, new Rect(338, 183, 178, 190), false);
            authored(file, new Rect(338, 8, 178, 64), false);
            authored(file, new Rect(100, 90, 178, 190), false);
            authored(file, new Rect(100, 130, 178, 190), false);
            authored(file, new Rect(338, 8, 178, 64), true);
            PlayerPanelProfiles.initialize(
                directory.resolve("fresh.json")
                    .toFile());
            Rect tight = new Rect(0, 8, 210, 292), fullMachine = new Rect(0, 170, 210, 130);
            check(fit(fullMachine, tight) == null, "No automatic placement above when only the top area is free");
            Rect sideViewport = new Rect(0, 8, 310, 292), sideMachine = new Rect(120, 100, 190, 200);
            InventoryPanelLayout side = fit(sideMachine, sideViewport);
            check(
                side != null && side.bounds.right() < sideMachine.x && side.bounds.y >= sideMachine.y,
                "A constrained viewport can use space beside the machine");
            System.out.println(
                "PASS captured MUI2 inherited reopen, custom/global above and beside intent, constrained fallback, scope and byte preservation");
        } finally {
            try (java.util.stream.Stream<Path> files = Files.list(directory)) {
                for (Path path : (Iterable<Path>) files::iterator) Files.delete(path);
            }
            Files.delete(directory);
        }
    }

    private static void authored(Path file, Rect authored, boolean global) throws Exception {
        PlayerPanelProfile profile = PlayerPanelProfile.at(authored, VIEWPORT);
        PanelLayoutReference origin = new PanelLayoutReference(MACHINE, VIEWPORT);
        if (global) PlayerPanelProfiles.applyGlobalPanels(PlayerPanelSet.DEFAULT, KEY, profile, origin);
        else PlayerPanelProfiles.applyPanels(PlayerPanelSet.DEFAULT, KEY, profile, origin);
        byte[] saved = Files.readAllBytes(file);
        for (int reopen = 0; reopen < 3; reopen++) {
            PlayerPanelProfiles.initialize(file.toFile());
            InventoryPanelLayout fitted = fit(MACHINE, VIEWPORT);
            check(
                fitted != null && same(fitted.bounds, authored),
                "Authored above/beside/below position survives reopen");
            check(PlayerPanelProfiles.hasOverride(KEY) != global, "Fitting preserves global/custom scope");
        }
        check(Arrays.equals(saved, Files.readAllBytes(file)), "Authored layout bytes stay unchanged");
    }

    private static InventoryPanelLayout fit(Rect machine, Rect viewport) {
        PlayerPanelProfile profile = PlayerPanelProfiles.resolve(KEY);
        return InventoryPanelLayout.fit(
            PlayerPanelProfiles.requestedBounds(KEY, profile, machine, viewport),
            PlayerPanelProfiles.fittingViewport(KEY, profile, machine, viewport),
            Collections.singletonList(machine),
            72,
            8,
            true);
    }

    private static boolean same(Rect a, Rect b) {
        return a.x == b.x && a.y == b.y && a.width == b.width && a.height == b.height;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
