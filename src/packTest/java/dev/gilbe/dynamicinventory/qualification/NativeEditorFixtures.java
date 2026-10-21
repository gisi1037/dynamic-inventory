package dev.gilbe.dynamicinventory.qualification;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.gilbe.dynamicinventory.api.NativeUiLayout;
import dev.gilbe.dynamicinventory.client.AttachedChestLayout;
import dev.gilbe.dynamicinventory.client.InventoryViewportLayout;
import dev.gilbe.dynamicinventory.client.NativeSectionGeometry;
import dev.gilbe.dynamicinventory.client.editor.NativeLayoutPatch;
import dev.gilbe.dynamicinventory.client.editor.NativeLayoutPatches;

/** Focused, headless checks for editor input files and live chest resize budgets. No world is needed. */
public final class NativeEditorFixtures {

    private NativeEditorFixtures() {}

    public static void main(String[] args) {
        AutomaticScrollFixtures.main(args);
        check(NativeUiLayout.DEFAULT.getPresentationRevision() == 1, "default presentation revision");
        check(
            NativeUiLayout.builder()
                .presentationRevision(2)
                .build()
                .getPresentationRevision() == 2,
            "presentation revision can change independently of transfer behavior");
        rejects(
            () -> NativeUiLayout.builder()
                .presentationRevision(0),
            "nonpositive presentation revision");
        // Both modes retain the same columns. Height fitting must not disguise a partial final row.
        check(
            InventoryViewportLayout.requestedRows(73, 9, true) == 9
                && InventoryViewportLayout.requestedRows(73, 9, false) == 3,
            "mode handles partial storage rows");
        check(InventoryViewportLayout.requestedRows(2, 9, false) == 1, "scroll mode does not invent empty rows");
        AttachedChestLayout vertical = new AttachedChestLayout(
            960,
            540,
            72,
            9,
            InventoryViewportLayout.requestedRows(72, 1, true),
            false,
            372,
            100,
            243);
        AttachedChestLayout scrolling = new AttachedChestLayout(
            960,
            540,
            72,
            9,
            InventoryViewportLayout.requestedRows(72, 1, false),
            false,
            372,
            100,
            243);
        check(
            vertical.columns == scrolling.columns && vertical.width == scrolling.width
                && vertical.rows == 8
                && scrolling.rows == 3,
            "attached chest mode changes height without reflowing columns");
        NativeLayoutPatch original = new NativeLayoutPatch(
            9,
            8,
            -17,
            23,
            NativeUiLayout.builder()
                .topPadding(4)
                .minimumHeaderHeight(70)
                .backgroundInset(3)
                .build());
        JsonObject target = new JsonObject();
        target.addProperty("adapterId", "test:chest");
        target.addProperty("presentationRevision", 1);
        target.addProperty("ordinarySlotCount", 81);
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 5);
        root.add("target", target);
        root.add("layout", original.toJson());
        check(
            NativeLayoutPatches.decode(root, target)
                .toJson()
                .equals(original.toJson()),
            "round trip");
        JsonObject otherTarget = copy(target);
        otherTarget.addProperty("ordinarySlotCount", 90);
        rejects(() -> NativeLayoutPatches.decode(root, otherTarget), "different capacity");
        otherTarget.addProperty("ordinarySlotCount", 81);
        otherTarget.addProperty("presentationRevision", 2);
        rejects(() -> NativeLayoutPatches.decode(root, otherTarget), "different presentation revision");
        // A transfer-only revision has no place in the new layout identity. Existing schema-4
        // files migrate by exact GUI/capacity, with the old revision left out of the new target.
        JsonObject oldRoot = copy(root);
        oldRoot.addProperty("schemaVersion", 4);
        JsonObject oldTarget = copy(target);
        oldTarget.remove("presentationRevision");
        oldTarget.addProperty("adapterRevision", 1);
        oldRoot.add("target", oldTarget);
        check(
            NativeLayoutPatches.decodeLegacy(oldRoot, target, 63)
                .toJson()
                .equals(original.toJson()),
            "old transfer revision does not invalidate compatible geometry");
        oldTarget.addProperty("adapterRevision", 20);
        check(
            NativeLayoutPatches.decodeLegacy(oldRoot, target, 63)
                .toJson()
                .equals(original.toJson()),
            "legacy layout is independent of transfer revision");
        rejects(() -> NativeLayoutPatches.decodeLegacy(oldRoot, otherTarget, 63), "legacy presentation change");
        oldTarget.addProperty("nativeSlotCount", 63);
        check(
            NativeLayoutPatches.decodeLegacy(oldRoot, target, 63)
                .toJson()
                .equals(original.toJson()),
            "legacy chest variant");
        rejects(() -> NativeLayoutPatches.decodeLegacy(oldRoot, target, 90), "different legacy chest variant");
        JsonObject wrongCapacity = copy(target);
        wrongCapacity.addProperty("ordinarySlotCount", 90);
        rejects(() -> NativeLayoutPatches.decodeLegacy(oldRoot, wrongCapacity, 63), "legacy capacity change");
        for (String schema : new String[] { "6", "1.0", "\"1\"", "null" }) {
            JsonObject bad = copy(root);
            bad.add("schemaVersion", new JsonParser().parse(schema));
            rejects(() -> NativeLayoutPatches.decode(bad, target), "schema " + schema);
        }
        for (String number : new String[] { "2", "19", "9.5", "1e1", "2147483650", "\"9\"", "null", "{}" }) {
            JsonObject bad = original.toJson();
            bad.add("columns", new JsonParser().parse(number));
            rejects(() -> NativeLayoutPatch.fromJson(bad), "columns " + number);
        }
        JsonObject missing = original.toJson();
        missing.remove("visibleRows");
        rejects(() -> NativeLayoutPatch.fromJson(missing), "missing setting");
        JsonObject extra = original.toJson();
        extra.addProperty("slotCount", 900);
        rejects(() -> NativeLayoutPatch.fromJson(extra), "capacity is not a layout setting");
        JsonObject named = original.adjust(16, 3)
            .toJson();
        named.remove("frameStyle");
        check(
            NativeLayoutPatch.fromJson(named)
                .value(12) == 0
                && NativeLayoutPatch.fromJson(named)
                    .value(16) == 5,
            "missing optional field keeps later named settings");
        check(
            original.adjust(2, Integer.MAX_VALUE)
                .value(2) == 4096,
            "positive offset clamps without overflow");
        check(
            original.adjust(3, Integer.MIN_VALUE)
                .value(3) == -4096,
            "negative offset clamps without overflow");
        check(original.value(2) == -17 && original.value(3) == 23, "preview values are immutable");
        JsonObject legacy = original.toJson();
        for (String key : new String[] { "playerOffsetX", "playerOffsetY", "nativeOffsetX", "nativeOffsetY",
            "sectionGap", "frameStyle", "contentGap", "screenTopMargin", "screenBottomMargin", "borderWidth" })
            legacy.remove(key);
        NativeLayoutPatch migrated = NativeLayoutPatch.fromJson(legacy);
        check(
            migrated.value(7) == 0 && migrated.value(11) == -1
                && migrated.value(14) == 8
                && migrated.value(15) == 28
                && migrated.value(16) == 2,
            "old layouts retain neutral section offsets/auto gap");
        JsonObject previous = original.adjust(7, 12)
            .toJson();
        previous.remove("frameStyle");
        previous.remove("contentGap");
        previous.remove("screenTopMargin");
        previous.remove("screenBottomMargin");
        previous.remove("borderWidth");
        NativeLayoutPatch previousVersion = NativeLayoutPatch.fromJson(previous);
        check(
            previousVersion.value(7) == 12 && previousVersion.value(12) == 0
                && previousVersion.value(14) == 8
                && previousVersion.value(15) == 28
                && previousVersion.value(16) == 2,
            "section-editor saves retain offsets and original styling");
        NativeLayoutPatch styled = original.adjust(12, 2);
        check(
            NativeLayoutPatch.fromJson(styled.toJson())
                .value(12) == 2,
            "frame style persists");
        JsonObject invalidStyle = styled.toJson();
        invalidStyle.addProperty("frameStyle", 3);
        rejects(() -> NativeLayoutPatch.fromJson(invalidStyle), "unknown frame style");
        JsonObject beforeGap = original.toJson();
        beforeGap.remove("contentGap");
        beforeGap.remove("screenTopMargin");
        beforeGap.remove("screenBottomMargin");
        beforeGap.remove("borderWidth");
        check(
            NativeLayoutPatch.fromJson(beforeGap)
                .value(13) == 8
                && NativeLayoutPatch.fromJson(beforeGap)
                    .value(14) == 8
                && NativeLayoutPatch.fromJson(beforeGap)
                    .value(15) == 28
                && NativeLayoutPatch.fromJson(beforeGap)
                    .value(16) == 2,
            "old styling saves retain eight-pixel content gap");
        JsonObject beforeMargins = original.adjust(13, 24)
            .toJson();
        beforeMargins.remove("screenTopMargin");
        beforeMargins.remove("screenBottomMargin");
        beforeMargins.remove("borderWidth");
        NativeLayoutPatch migratedMargins = NativeLayoutPatch.fromJson(beforeMargins);
        check(
            migratedMargins.value(13) == 32 && migratedMargins.value(14) == 8
                && migratedMargins.value(15) == 28
                && migratedMargins.value(16) == 2,
            "fourteen-field saves retain content gap and gain screen margin defaults");
        NativeLayoutPatch margins = original.adjust(14, 12)
            .adjust(15, 17);
        NativeLayoutPatch savedMargins = NativeLayoutPatch.fromJson(margins.toJson());
        check(
            savedMargins.value(14) == 20 && savedMargins.value(15) == 45
                && savedMargins.hints()
                    .getTopPadding() == 4,
            "screen margins persist separately from header padding");
        check(original.value(14) == 8 && original.value(15) == 28, "margin previews do not mutate previous values");
        check(
            original.adjust(14, Integer.MAX_VALUE)
                .value(14) == 128
                && original.adjust(15, Integer.MIN_VALUE)
                    .value(15) == 0,
            "screen margins clamp without overflow");
        for (String key : new String[] { "screenTopMargin", "screenBottomMargin" }) {
            for (String number : new String[] { "-1", "129", "1.5", "1e1", "2147483650", "\"8\"", "null", "{}" }) {
                JsonObject bad = original.toJson();
                bad.add(key, new JsonParser().parse(number));
                rejects(() -> NativeLayoutPatch.fromJson(bad), key + " " + number);
            }
            JsonObject partialMargins = margins.toJson();
            partialMargins.remove(key);
            NativeLayoutPatch restoredMargins = NativeLayoutPatch.fromJson(partialMargins);
            check(
                restoredMargins.value(14) == (key.equals("screenTopMargin") ? 8 : 20)
                    && restoredMargins.value(15) == (key.equals("screenBottomMargin") ? 28 : 45),
                "missing optional margin defaults without losing the other named value");
        }
        JsonObject beforeBorder = margins.toJson();
        beforeBorder.remove("borderWidth");
        NativeLayoutPatch oldMargins = NativeLayoutPatch.fromJson(beforeBorder);
        check(
            oldMargins.value(14) == 20 && oldMargins.value(15) == 45 && oldMargins.value(16) == 2,
            "sixteen-field margin saves retain margins and default two-pixel borders");
        for (int border = 0; border <= 6; border++) {
            check(
                NativeLayoutPatch.fromJson(
                    original.adjust(16, border - 2)
                        .toJson())
                    .value(16) == border,
                "border width persists: " + border);
        }
        check(
            original.value(16) == 2 && original.adjust(16, Integer.MAX_VALUE)
                .value(16) == 6
                && original.adjust(16, Integer.MIN_VALUE)
                    .value(16) == 0,
            "border previews are immutable and bounded");
        for (String number : new String[] { "-1", "7", "1.5", "1e1", "2147483650", "\"2\"", "null", "{}" }) {
            JsonObject bad = original.toJson();
            bad.add("borderWidth", new JsonParser().parse(number));
            rejects(() -> NativeLayoutPatch.fromJson(bad), "border width " + number);
        }
        check(
            NativeLayoutPatch.fromJson(
                original.adjust(13, 24)
                    .toJson())
                .value(13) == 32,
            "content gap persists");
        check(
            NativeSectionGeometry.playerBoundary(190, 0, 178, -1) == 190,
            "compressed chest default boundary retained");
        check(NativeSectionGeometry.playerBoundary(190, 0, 178, 0) == 180, "zero gap removes obsolete native spacing");
        NativeSectionGeometry sections = new NativeSectionGeometry(
            500,
            0,
            0,
            0,
            0,
            500,
            0,
            180,
            75,
            180,
            350,
            96,
            10,
            8,
            -20,
            -6);
        check(sections.headerX == 10 && sections.headerY == 8, "native section moves independently");
        check(sections.playerX == 55 && sections.playerY == 174, "player section moves independently");
        check(sections.playerY + 14 >= sections.headerY + 178, "tight layout retains separate live slot rows");
        // The UI accepts more extreme offsets too, normalizing them into the outer GUI rectangle.
        NativeSectionGeometry negative = new NativeSectionGeometry(
            176,
            0,
            0,
            0,
            0,
            176,
            0,
            70,
            0,
            80,
            176,
            96,
            -20,
            -10,
            -30,
            -100);
        check(
            negative.headerX >= 0 && negative.headerY >= 0 && negative.playerX >= 0 && negative.playerY >= 0,
            "negative section offsets stay in the GUI bounds");
        AttachedChestLayout joined = new AttachedChestLayout(640, 360, 72, 9, 8, false, 412, 150, 243, 0);
        check(joined.lowerY == 150, "attached chest gap can also be closed");
        // Native chest geometry is retained during a preview, even if the user requests more player rows.
        for (int rows = 1; rows <= 12; rows++) {
            AttachedChestLayout layout = new AttachedChestLayout(640, 360, 72, 12, rows, false, 412, 168, 243);
            check(layout.height <= 360 && layout.playerY >= 176, "live chest reserves existing height: " + rows);
        }
        int cases = AttachedChestLayoutFixtures.run();
        System.out.println(
            "PASS native editor codec/target guards, immutable bounds, 12 live resize cases; " + cases
                + " existing geometry cases");
    }

    private static JsonObject copy(JsonObject json) {
        return new JsonParser().parse(json.toString())
            .getAsJsonObject();
    }

    private static void rejects(Runnable action, String label) {
        try {
            action.run();
        } catch (RuntimeException expected) {
            return;
        }
        throw new AssertionError("Editor accepted " + label);
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError("Editor: " + label);
    }
}
