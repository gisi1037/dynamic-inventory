package dev.gilbe.dynamicinventory.qualification;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import com.google.gson.JsonObject;

import codechicken.nei.ItemsPanelGrid;
import dev.gilbe.dynamicinventory.client.InventoryPanelLayout;
import dev.gilbe.dynamicinventory.client.InventoryPanelLayout.Rect;
import dev.gilbe.dynamicinventory.client.PlayerPanelProfile;
import dev.gilbe.dynamicinventory.client.PlayerPanelProfiles;
import dev.gilbe.dynamicinventory.client.editor.NativeLayoutPatches;

/** Geometry, real NEI compact masks, and isolated on-disk reset checks. Never opens a user configuration. */
public final class PlayerPanelFixtures {

    private PlayerPanelFixtures() {}

    public static void run() {
        geometry();
        allocations();
        neiMasks();
        try {
            profilesAndResets();
        } catch (java.io.IOException failure) {
            throw new AssertionError("Panel persistence fixture", failure);
        }
        System.out.println(
            "PASS detachable panel geometry, NEI compact pagination/hit mapping, profile persistence and scoped resets");
    }

    private static void geometry() {
        Rect fullScreen = new Rect(0, 8, 640, 324);
        for (int pixels = 0; pixels <= 36; pixels++) {
            Rect requested = new Rect(0, 8, 178 + pixels, 64 + pixels);
            InventoryPanelLayout smooth = InventoryPanelLayout.fit(requested, fullScreen, null, 72);
            check(
                smooth.bounds.width == requested.width && smooth.bounds.height == requested.height,
                "frame tracks every pixel between row/column thresholds");
            check(
                smooth.columns == 9 + pixels / 18 && smooth.rows == 1 + pixels / 18,
                "cells change only at full row/column thresholds");
            check(
                2 * smooth.hotbarX + 162 == 2 * smooth.gridX + smooth.columns * 18,
                "hotbar centered under storage, excluding scrollbar gutter");
            int horizontalSlack = requested.width - (smooth.columns * 18 + 16);
            int verticalSlack = requested.height - (smooth.rows * 18 + 46);
            check(
                Math.abs(2 * (smooth.gridX - requested.x - 9) - horizontalSlack) <= 1,
                "horizontal slack split equally across borders");
            check(
                Math.abs(2 * (smooth.gridY - requested.y - 17) - verticalSlack) <= 1,
                "vertical slack split equally across borders");
            Rect restored = PlayerPanelProfile.at(new Rect(51 + pixels, 23 + pixels, 188, 96), fullScreen)
                .requested(fullScreen);
            check(
                restored.x == 51 + pixels && restored.y == 23 + pixels,
                "drag anchors round trip without pixel drift");
        }
        InventoryPanelLayout left = InventoryPanelLayout
            .fit(new Rect(0, 30, 188, 186), fullScreen, new Rect(250, 30, 176, 80), 72);
        check(left != null && left.bounds.x == 0, "panel can occupy the left bookmark area");
        Rect viewport = new Rect(60, 8, 580, 324), machine = new Rect(210, 30, 176, 80);
        check(
            InventoryPanelLayout.fit(new Rect(0, 0, 178, 64), new Rect(0, 0, 177, 300), null, 72) == null,
            "unusable viewport stays attached");
        for (int width : new int[] { 188, 205, 206, 350, 2000 }) {
            for (int height : new int[] { 60, 77, 78, 186, 1000 }) {
                for (int x : new int[] { -500, 70, 220, 620 }) {
                    InventoryPanelLayout fit = InventoryPanelLayout
                        .fit(new Rect(x, 40, width, height), viewport, machine, 73);
                    check(fit != null, "usable region found");
                    Rect b = fit.bounds;
                    check(
                        b.x >= viewport.x && b.y >= viewport.y
                            && b.right() <= viewport.right()
                            && b.bottom() <= viewport.bottom(),
                        "fitted bounds stay on screen");
                    check(!b.intersects(machine), "machine remains clickable");
                    check(
                        fit.columns >= 9 && fit.columns <= 18 && fit.rows >= 1 && fit.rows <= 12,
                        "bounded whole-cell grid");
                    check(
                        fit.hotbarX >= b.x && fit.hotbarX + 162 < b.right() && fit.hotbarY + 17 < b.bottom(),
                        "fixed nine-slot hotbar fits");
                    Set<Integer> visited = new HashSet<>();
                    for (int scroll = 0; scroll <= fit.maxScroll(73); scroll++) {
                        for (int cell = 0; cell < fit.columns * fit.rows; cell++) {
                            int logical = 9 + scroll * fit.columns + cell;
                            if (logical < 82) visited.add(logical);
                        }
                    }
                    check(visited.size() == 73, "scrolling exposes each storage identity including partial final row");
                }
            }
        }
        PlayerPanelProfile preferred = new PlayerPanelProfile(true, 350, 258, 10000, 10000);
        InventoryPanelLayout.fit(preferred.requested(viewport), new Rect(60, 8, 188, 100), null, 27);
        check(preferred.width == 350 && preferred.height == 258, "fitting cannot overwrite saved preference");
        check(
            preferred.requested(viewport)
                .right() == viewport.right(),
            "right anchor survives screen changes");
        JsonObject fractional = preferred.toJson();
        fractional.addProperty("width", 188.5);
        rejects(() -> PlayerPanelProfile.fromJson(fractional), "fractional dimensions rejected");
        JsonObject unknown = preferred.toJson();
        unknown.addProperty("unexpected", 1);
        rejects(() -> PlayerPanelProfile.fromJson(unknown), "unknown profile fields rejected");
    }

    private static void allocations() {
        dev.gilbe.dynamicinventory.client.PlayerPanelSet set = dev.gilbe.dynamicinventory.client.PlayerPanelSet.DEFAULT
            .add()
            .add();
        set = set.allocate(new int[] { 36, 18, 18 }, 72);
        String a = set.entries.get(1).id, b = set.entries.get(2).id;
        java.util.List<Integer> stable = set.members(b, 72);
        set = set.allocate(new int[] { 27, 27, 18 }, 72);
        check(
            set.members(b, 72)
                .equals(stable),
            "editing another panel preserves stable memberships");
        for (int capacity : new int[] { 27, 72, 81, 216 }) {
            Set<Integer> seen = new HashSet<>();
            for (dev.gilbe.dynamicinventory.client.PlayerPanelSet.Entry entry : set.entries)
                for (int slot : set.members(entry.id, capacity))
                    check(seen.add(slot), "one panel per canonical storage index");
            check(seen.size() == capacity, "every active slot allocated across capacity changes");
        }
        check(
            set.members(b, 72)
                .equals(stable),
            "dormant membership returns after regrowth");
        dev.gilbe.dynamicinventory.client.PlayerPanelSet restored = dev.gilbe.dynamicinventory.client.PlayerPanelSet
            .fromJson(set.toJson());
        check(
            restored.toJson()
                .equals(set.toJson()),
            "named panel profiles and membership round trip");
        check(
            restored.remove(a)
                .members("main", 72)
                .size() == 54,
            "removal returns slots to Main");
        final dev.gilbe.dynamicinventory.client.PlayerPanelSet allocated = set;
        rejects(() -> allocated.allocate(new int[] { 72, 72, 72 }, 72), "over-allocation rejected");
        Rect screen = new Rect(0, 8, 640, 324), machine = new Rect(250, 30, 176, 80);
        java.util.List<Rect> obstacles = new java.util.ArrayList<>();
        obstacles.add(machine);
        for (boolean main : new boolean[] { true, false, false }) {
            InventoryPanelLayout layout = InventoryPanelLayout
                .fit(new Rect(400, 120, main ? 178 : 70, main ? 190 : 90), screen, obstacles, 27, 8, main);
            check(layout != null, "multiple usable panel regions found");
            for (Rect obstacle : obstacles)
                check(!layout.bounds.intersects(obstacle), "panel union does not overlap machines or panels");
            obstacles.add(layout.bounds);
        }
        Rect bookmarks = InventoryPanelLayout.freeRegion(
            new Rect(0, 8, 180, 324),
            java.util.Collections.singletonList(new Rect(60, 120, 178, 190)),
            40,
            72);
        check(
            bookmarks != null && bookmarks.width == 58 && bookmarks.height == 324,
            "bookmarks shrink in width instead of disappearing");
    }

    private static void profilesAndResets() throws java.io.IOException {
        rejects(
            () -> PlayerPanelProfiles.key("dynamicinventory:chest", ".", "net.minecraft.client.gui.inventory.GuiChest"),
            "malformed class names rejected");
        check(
            !PlayerPanelProfiles.group("com.example.one.ContainerOne")
                .equals(PlayerPanelProfiles.group("com.example.two.ContainerTwo")),
            "unknown owners are not merged by generic package root");
        Path directory = Files.createTempDirectory("dynamicinventory-panels-");
        Path file = directory.resolve("player-panels.json");
        String vanilla = PlayerPanelProfiles.key(
            "dynamicinventory:vanilla_chest",
            "net.minecraft.inventory.ContainerChest",
            "net.minecraft.client.gui.inventory.GuiChest");
        String forestry = PlayerPanelProfiles.key(
            "dynamicinventory:forestry",
            "forestry.factory.gui.ContainerCarpenter",
            "forestry.factory.gui.GuiCarpenter");
        PlayerPanelProfile global = new PlayerPanelProfile(true, 242, 150, 7500, 9000);
        JsonObject legacyRoot = new JsonObject(), legacyGlobal = global.toJson();
        for (String key : new String[] { "frameStyle", "borderWidth", "frameCrop", "cornerCrop" })
            legacyGlobal.remove(key);
        legacyRoot.addProperty("schemaVersion", 1);
        legacyRoot.add("global", legacyGlobal);
        legacyRoot.add("interfaces", new JsonObject());
        byte[] legacyBytes = legacyRoot.toString()
            .getBytes(StandardCharsets.UTF_8);
        Files.write(file, legacyBytes);
        PlayerPanelProfiles.initialize(file.toFile());
        check(
            PlayerPanelProfiles.global().width == 242 && PlayerPanelProfiles.global().frameStyle == -1,
            "schema one geometry keeps inherited appearance");
        PlayerPanelProfile themed = global.withAppearance(2, 3, 1, 2);
        PlayerPanelProfiles.setGlobal(themed);
        check(
            java.util.Arrays
                .equals(legacyBytes, Files.readAllBytes(directory.resolve("player-panels.schema1-backup.json"))),
            "migration retains exact schema one backup");
        PlayerPanelProfiles.initialize(file.toFile());
        check(
            PlayerPanelProfiles.global().frameStyle == 2 && PlayerPanelProfiles.global().borderWidth == 3,
            "appearance and geometry round trip together");
        dev.gilbe.dynamicinventory.client.PlayerPanelSet split = dev.gilbe.dynamicinventory.client.PlayerPanelSet.DEFAULT
            .add()
            .allocate(new int[] { 54, 18 }, 72);
        String satellite = split.entries.get(1).id;
        split = split.replace(
            split.get(satellite)
                .profile(vanilla, new PlayerPanelProfile(true, 88, 100, 0, 5000)));
        PlayerPanelProfiles.applyPanels(split, vanilla, themed);
        PlayerPanelProfiles.initialize(file.toFile());
        check(
            PlayerPanelProfiles.panels()
                .members(satellite, 72)
                .size() == 18
                && PlayerPanelProfiles.panels()
                    .get(satellite)
                    .resolve(vanilla).width == 88,
            "allocation and per-interface geometry persist atomically");
        PlayerPanelProfiles.reset("Vanilla");
        check(
            PlayerPanelProfiles.panels()
                .get(satellite).overrides.isEmpty() && PlayerPanelProfiles.panels()
                    .members(satellite, 72)
                    .size() == 18,
            "mod reset preserves named allocation and clears geometry overrides");
        Files.delete(file);
        PlayerPanelProfiles.initialize(file.toFile());
        check(!PlayerPanelProfiles.resolve(vanilla).detached, "factory default attached");
        PlayerPanelProfiles.setGlobal(global);
        PlayerPanelProfiles.save(vanilla, PlayerPanelProfile.DEFAULT);
        PlayerPanelProfiles.save(forestry, PlayerPanelProfile.DEFAULT);
        PlayerPanelProfiles.initialize(file.toFile());
        check(
            PlayerPanelProfiles.global().width == 242 && !PlayerPanelProfiles.resolve(vanilla).detached,
            "global and overrides round trip independently");
        PlayerPanelProfiles.reset("Vanilla");
        check(
            PlayerPanelProfiles.resolve(vanilla).detached && !PlayerPanelProfiles.resolve(forestry).detached,
            "group reset inherits global and preserves other mods");
        PlayerPanelProfiles.resetGlobal();
        check(
            !PlayerPanelProfiles.global().detached && PlayerPanelProfiles.groups()
                .containsKey("Forestry"),
            "reset global preserves overrides");
        PlayerPanelProfiles.reset(null);
        check(
            PlayerPanelProfiles.groups()
                .isEmpty(),
            "reset all overrides");
        PlayerPanelProfiles.save(vanilla, global);
        PlayerPanelProfiles.inherit(vanilla);
        check(
            !PlayerPanelProfiles.resolve(vanilla).detached && PlayerPanelProfiles.groups()
                .isEmpty(),
            "use global removes interface override");
        byte[] invalid = "{\"schemaVersion\":999}".getBytes(StandardCharsets.UTF_8);
        Files.write(file, invalid);
        PlayerPanelProfiles.initialize(file.toFile());
        check(
            java.util.Arrays.equals(invalid, Files.readAllBytes(file)) && !PlayerPanelProfiles.global().detached,
            "invalid documents are not overwritten during load");
        Path blockedFile = directory.resolve("blocked");
        Files.createDirectory(blockedFile);
        PlayerPanelProfiles.initialize(blockedFile.toFile());
        try {
            PlayerPanelProfiles.setGlobal(global);
            throw new AssertionError("unwritable destination accepted");
        } catch (java.io.IOException expected) {
            check(!PlayerPanelProfiles.global().detached, "failed write does not change current profile");
        }
        Path layouts = directory.resolve("ui-layouts");
        Files.createDirectories(layouts);
        for (String name : new String[] { "vanilla-old", "vanilla-new", "forestry" }) {
            JsonObject target = new JsonObject(), document = new JsonObject();
            target.addProperty("adapterId", "dynamicinventory:fixture");
            target.addProperty(
                "containerClass",
                name.startsWith("vanilla") ? "net.minecraft.inventory.ContainerChest"
                    : "forestry.factory.gui.ContainerCarpenter");
            document.addProperty("schemaVersion", name.endsWith("old") ? 1 : 9);
            document.add("target", target);
            document.add("layout", new JsonObject());
            Files.write(
                layouts.resolve(name + ".json"),
                document.toString()
                    .getBytes(StandardCharsets.UTF_8));
        }
        Files.write(layouts.resolve("unrelated.json"), "{}".getBytes(StandardCharsets.UTF_8));
        NativeLayoutPatches.initialize(layouts.toFile());
        check(
            NativeLayoutPatches.groups()
                .get("Vanilla") == 2,
            "reset discovers old capacity variants");
        check(
            NativeLayoutPatches.reset("Vanilla") == 2 && NativeLayoutPatches.groups()
                .get("Forestry") == 1,
            "mod reset leaves other editor layouts");
        check(
            Files.exists(layouts.resolve("unrelated.json")) && !Files.exists(layouts.resolve("vanilla-old.json")),
            "unknown files preserved and migration source removed");
        try (java.util.stream.Stream<Path> backups = Files.walk(layouts.resolve("reset-backups"))) {
            check(
                backups.filter(Files::isRegularFile)
                    .count() == 2,
                "both old editor layouts retained as backups");
        }
    }

    private static void neiMasks() {
        MaskGrid grid = new MaskGrid();
        boolean[] blocked = new boolean[40];
        // An interior obstacle has clear cells on both sides: NEI's edge shortcut misses it.
        for (int index : new int[] { 2, 3, 10, 11, 18, 19, 25 }) blocked[index] = true;
        grid.configure(blocked, 85);
        Set<Integer> seen = new HashSet<>();
        for (int page = 0; page < grid.getNumPages(); page++) {
            grid.setPage(page);
            for (ItemsPanelGrid.ItemsPanelGridSlot slot : grid.getMask()) {
                check(
                    !blocked[slot.slotIndex] && seen.add(slot.itemIndex),
                    "NEI mask avoids obstacle with no duplicate result");
                check(
                    grid.getSlotMouseOver(slot.slotIndex % 8 * 18 + 9, slot.slotIndex / 8 * 18 + 9).itemIndex
                        == slot.itemIndex,
                    "NEI click and hover resolve displayed item identity");
            }
        }
        check(seen.size() == 85, "NEI pagination contains every result exactly once");
        java.util.Arrays.fill(blocked, true);
        grid.configure(blocked, 85);
        grid.setPage(100);
        check(
            grid.getMask()
                .isEmpty() && grid.getNumPages() == 0,
            "zero available NEI cells remain bounded");
        grid.configure(new boolean[40], 85);
        check(
            grid.getMask()
                .size() == 40,
            "reattachment invalidates old compact mask");
    }

    private static final class MaskGrid extends ItemsPanelGrid {

        void configure(boolean[] blocked, int count) {
            rows = 5;
            columns = 8;
            width = columns * 18;
            height = rows * 18;
            page = 0;
            invalidSlotMap = blocked;
            perPage = 0;
            for (boolean invalid : blocked) if (!invalid) perPage++;
            realItems.clear();
            for (int i = 0; i < count; i++) realItems.add(null);
            onGridChanged();
        }
    }

    private static void rejects(Runnable operation, String description) {
        try {
            operation.run();
            throw new AssertionError(description);
        } catch (IllegalArgumentException expected) {}
    }

    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
    }
}
