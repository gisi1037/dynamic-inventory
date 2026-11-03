package dev.gilbe.dynamicinventory.qualification;

import dev.gilbe.dynamicinventory.client.InventoryPanelLayout.Rect;
import dev.gilbe.dynamicinventory.client.PanelLayoutReference;
import dev.gilbe.dynamicinventory.client.PlayerPanelProfile;

/** Saved panel geometry keeps GUI-pixel gaps as the scaled viewport changes. */
public final class PanelScaleFixtures {

    private PanelScaleFixtures() {}

    public static void main(String[] args) throws java.io.IOException {
        Rect sourceViewport = new Rect(0, 8, 640, 324), sourceMachine = new Rect(230, 30, 178, 90);
        Rect[] group = { new Rect(230, 121, 178, 190), new Rect(191, 121, 38, 180), new Rect(409, 121, 38, 180) };
        PanelLayoutReference reference = new PanelLayoutReference(sourceMachine, sourceViewport);
        for (Rect viewport : new Rect[] { sourceViewport, new Rect(0, 8, 960, 504), new Rect(0, 8, 1280, 684) }) {
            Rect machine = new Rect(viewport.width / 2 - 89, viewport.height / 5, 178, 90);
            Rect[] adapted = new Rect[3];
            for (int i = 0; i < group.length; i++) {
                PlayerPanelProfile profile = PlayerPanelProfile.at(group[i], sourceViewport);
                adapted[i] = reference.adapt(profile, machine, viewport);
                check(adapted[i].x - machine.x == group[i].x - sourceMachine.x, "horizontal machine offset");
                check(
                    adapted[i].y - machine.bottom() == group[i].y - sourceMachine.bottom(),
                    "vertical machine offset");
                check(
                    adapted[i].width == group[i].width && adapted[i].height == group[i].height,
                    "GUI-pixel dimensions");
            }
            check(
                adapted[0].x - adapted[1].right() == 1 && adapted[2].x - adapted[0].right() == 1,
                "satellite spacing must not expand with viewport percentages");
            Rect shorter = new Rect(machine.x, machine.y, machine.width, 78);
            check(
                reference.adapt(PlayerPanelProfile.at(group[0], sourceViewport), shorter, viewport).y
                    == adapted[0].y - 12,
                "inherited layouts still adapt to a different machine height");
        }
        persistence(sourceViewport, sourceMachine, group[0]);
        System.out.println(
            "PASS stable panel-group gaps, custom/global references, persistence, migration and scoped resets");
    }

    private static void persistence(Rect viewport, Rect machine, Rect bounds) throws java.io.IOException {
        java.nio.file.Path directory = java.nio.file.Files.createTempDirectory("dinv-panel-scale-");
        java.nio.file.Path file = directory.resolve("player-panels.json");
        String globalKey = "dynamicinventory:player|net.minecraft.inventory.ContainerPlayer|dev.gilbe.dynamicinventory.client.PlayerInventoryScreen";
        String customKey = "dynamicinventory:vanilla/chest|net.minecraft.inventory.ContainerChest|net.minecraft.client.gui.inventory.GuiChest";
        dev.gilbe.dynamicinventory.client.PlayerPanelSet set = dev.gilbe.dynamicinventory.client.PlayerPanelSet.DEFAULT;
        dev.gilbe.dynamicinventory.client.PlayerPanelProfiles.initialize(file.toFile());
        PlayerPanelProfile main = PlayerPanelProfile.at(bounds, viewport);
        dev.gilbe.dynamicinventory.client.PlayerPanelProfiles
            .applyGlobalPanels(set, globalKey, main, new PanelLayoutReference(machine, viewport));
        PlayerPanelProfile custom = PlayerPanelProfile.at(new Rect(80, 140, 178, 190), viewport);
        dev.gilbe.dynamicinventory.client.PlayerPanelProfiles
            .applyPanels(set, customKey, custom, new PanelLayoutReference(new Rect(50, 20, 178, 90), viewport));
        dev.gilbe.dynamicinventory.client.PlayerPanelProfiles.initialize(file.toFile());
        Rect targetViewport = new Rect(0, 8, 960, 504), targetMachine = new Rect(400, 80, 178, 90);
        Rect inherited = dev.gilbe.dynamicinventory.client.PlayerPanelProfiles
            .requestedBounds(globalKey, main, targetMachine, targetViewport);
        Rect overridden = dev.gilbe.dynamicinventory.client.PlayerPanelProfiles
            .requestedBounds(customKey, custom, targetMachine, targetViewport);
        check(inherited.x == 400 && inherited.y == 171, "global reference survives reload");
        check(overridden.x == 430 && overridden.y == 200, "custom reference survives reload independently");
        dev.gilbe.dynamicinventory.client.PlayerPanelProfiles
            .applyGlobalPanels(set, globalKey, main, new PanelLayoutReference(new Rect(200, 20, 178, 78), viewport));
        Rect unchanged = dev.gilbe.dynamicinventory.client.PlayerPanelProfiles
            .requestedBounds(customKey, custom, targetMachine, targetViewport);
        check(
            unchanged.x == overridden.x && unchanged.y == overridden.y,
            "new global reference preserves custom geometry");
        dev.gilbe.dynamicinventory.client.PlayerPanelProfiles.inherit(customKey);
        com.google.gson.JsonObject current = new com.google.gson.JsonParser()
            .parse(new String(java.nio.file.Files.readAllBytes(file), java.nio.charset.StandardCharsets.UTF_8))
            .getAsJsonObject();
        check(
            current.getAsJsonObject("interfaceReferences")
                .entrySet()
                .isEmpty(),
            "reset removes custom reference");
        current.remove("interfaceReferences");
        current.addProperty("schemaVersion", 6);
        byte[] old = current.toString()
            .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        java.nio.file.Files.write(file, old);
        dev.gilbe.dynamicinventory.client.PlayerPanelProfiles.initialize(file.toFile());
        dev.gilbe.dynamicinventory.client.PlayerPanelProfiles.applyPanels(set, customKey, custom);
        dev.gilbe.dynamicinventory.client.PlayerPanelProfiles
            .establishReference(customKey, new PanelLayoutReference(machine, viewport));
        check(
            java.util.Arrays
                .equals(old, java.nio.file.Files.readAllBytes(directory.resolve("player-panels.schema6-backup.json"))),
            "schema 6 migration retains an exact backup");
        dev.gilbe.dynamicinventory.client.PlayerPanelProfiles.initialize(file.toFile());
        check(
            dev.gilbe.dynamicinventory.client.PlayerPanelProfiles.resolve(customKey)
                .toJson()
                .equals(custom.toJson()),
            "migration preserves existing profile values");
        try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.list(directory)) {
            for (java.nio.file.Path path : files.collect(java.util.stream.Collectors.toList()))
                java.nio.file.Files.delete(path);
        }
        java.nio.file.Files.delete(directory);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
