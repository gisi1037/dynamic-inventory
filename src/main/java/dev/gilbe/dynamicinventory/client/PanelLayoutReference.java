package dev.gilbe.dynamicinventory.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import dev.gilbe.dynamicinventory.client.InventoryPanelLayout.Rect;

/** The machine position at which a global layout was authored; no per-interface geometry is changed. */
public final class PanelLayoutReference {

    private final Rect machine, viewport;

    public PanelLayoutReference(Rect machine, Rect viewport) {
        this.machine = machine;
        this.viewport = viewport;
    }

    public Rect adapt(Rect panel, Rect currentMachine, Rect currentViewport) {
        int sourceCenter = currentViewport.x
            + (int) Math.round((machine.x + machine.width / 2.0 - viewport.x) * currentViewport.width / viewport.width);
        int sourceBottom = currentViewport.y
            + (int) Math.round((machine.bottom() - (double) viewport.y) * currentViewport.height / viewport.height);
        return new Rect(
            panel.x + currentMachine.x + currentMachine.width / 2 - sourceCenter,
            panel.y + currentMachine.bottom() - sourceBottom,
            panel.width,
            panel.height);
    }

    public JsonObject toJson() {
        JsonObject object = new JsonObject();
        object.add("machine", rectangle(machine));
        object.add("viewport", rectangle(viewport));
        return object;
    }

    public static PanelLayoutReference fromJson(JsonObject object) {
        if (object.entrySet()
            .size() != 2) throw new IllegalArgumentException("Invalid global reference");
        return new PanelLayoutReference(
            rectangle(object.getAsJsonArray("machine")),
            rectangle(object.getAsJsonArray("viewport")));
    }

    private static JsonArray rectangle(Rect rect) {
        JsonArray array = new JsonArray();
        for (int value : new int[] { rect.x, rect.y, rect.width, rect.height })
            array.add(new com.google.gson.JsonPrimitive(value));
        return array;
    }

    private static Rect rectangle(JsonArray array) {
        if (array == null || array.size() != 4) throw new IllegalArgumentException("Invalid reference rectangle");
        int[] values = new int[4];
        for (int i = 0; i < 4; i++) {
            if (!array.get(i)
                .isJsonPrimitive()
                || !array.get(i)
                    .getAsJsonPrimitive()
                    .isNumber()
                || !array.get(i)
                    .getAsString()
                    .matches("-?[0-9]{1,5}"))
                throw new IllegalArgumentException("Invalid reference coordinate");
            values[i] = array.get(i)
                .getAsInt();
            if (Math.abs(values[i]) > 16384) throw new IllegalArgumentException("Reference too large");
        }
        if (values[2] <= 0 || values[3] <= 0) throw new IllegalArgumentException("Invalid reference size");
        return new Rect(values[0], values[1], values[2], values[3]);
    }
}
