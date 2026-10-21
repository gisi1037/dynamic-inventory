package dev.gilbe.dynamicinventory.client.editor;

import java.util.Arrays;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import dev.gilbe.dynamicinventory.api.NativeUiLayout;

/** Client presentation preferences. No slot IDs, stacks, capacity or transfer rules belong here. */
public final class NativeLayoutPatch {

    public static final String[] LABELS = { "Columns", "Visible rows", "Panel X", "Panel Y", "Top padding",
        "Minimum header", "Texture crop", "Player X", "Player Y", "Native X", "Native Y", "Section gap", "Frame style",
        "Content gap", "Top margin", "Bottom margin", "Border width" };
    private static final String[] KEYS = { "columns", "visibleRows", "offsetX", "offsetY", "topPadding",
        "minimumHeaderHeight", "backgroundInset", "playerOffsetX", "playerOffsetY", "nativeOffsetX", "nativeOffsetY",
        "sectionGap", "frameStyle", "contentGap", "screenTopMargin", "screenBottomMargin", "borderWidth" };
    private static final int[] MIN = { 3, 1, -4096, -4096, 0, 0, 0, -4096, -4096, -4096, -4096, -1, 0, 0, 0, 0, 0 };
    private static final int[] MAX = { 18, 12, 4096, 4096, 4096, 4096, 8, 4096, 4096, 4096, 4096, 128, 2, 128, 128, 128,
        6 };
    private final int[] values;

    public NativeLayoutPatch(int columns, int rows, int x, int y, NativeUiLayout hints) {
        this(
            new int[] { columns, rows, x, y, hints.getTopPadding(), hints.getMinimumHeaderHeight(),
                hints.getBackgroundInset(), 0, 0, 0, 0, -1, 0, 8, 8, 28, 2 });
    }

    private NativeLayoutPatch(int[] values) {
        this.values = values.clone();
        for (int i = 0; i < values.length; i++) {
            if (values[i] < MIN[i] || values[i] > MAX[i])
                throw new IllegalArgumentException("Invalid layout value: " + KEYS[i]);
        }
    }

    public int value(int field) {
        return values[field];
    }

    public NativeLayoutPatch adjust(int field, int delta) {
        int[] copy = values.clone();
        copy[field] = (int) Math.max(MIN[field], Math.min(MAX[field], (long) copy[field] + delta));
        return new NativeLayoutPatch(copy);
    }

    public NativeUiLayout hints() {
        return NativeUiLayout.builder()
            .topPadding(values[4])
            .minimumHeaderHeight(values[5])
            .backgroundInset(values[6])
            .build();
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        for (int i = 0; i < KEYS.length; i++) json.addProperty(KEYS[i], values[i]);
        return json;
    }

    public static NativeLayoutPatch fromJson(JsonObject json) {
        // Read names, not a prefix selected by property count. Missing optional controls
        // use defaults without discarding other saved values. Inventory/unknown keys stay invalid.
        for (Map.Entry<String, JsonElement> field : json.entrySet()) if (!Arrays.asList(KEYS)
            .contains(field.getKey())) throw new IllegalArgumentException("Unexpected layout field: " + field.getKey());
        int[] values = new int[KEYS.length];
        // The first editor wrote seven values. Preserve those saved layouts with neutral section offsets.
        values[11] = -1;
        values[13] = 8;
        // Screen margins reserve viewport space; they are separate from the native header's topPadding.
        values[14] = 8;
        values[15] = 28;
        values[16] = 2;
        for (int i = 0; i < KEYS.length; i++) {
            JsonElement value = json.get(KEYS[i]);
            if (value == null && i >= 7) continue;
            // Gson's getAsInt silently truncates fractions and large numbers; reject those instead.
            if (value == null || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive()
                    .isNumber()
                || !value.getAsString()
                    .matches("-?(0|[1-9][0-9]*)"))
                throw new IllegalArgumentException("Expected integer: " + KEYS[i]);
            values[i] = Integer.parseInt(value.getAsString());
        }
        return new NativeLayoutPatch(values);
    }
}
