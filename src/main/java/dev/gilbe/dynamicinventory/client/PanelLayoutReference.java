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
        return adapt(PlayerPanelProfile.at(panel, currentViewport), currentMachine, currentViewport);
    }

    /** Reconstruct authored bounds before translating the group; screen percentages must not scale panel gaps. */
    public Rect adapt(PlayerPanelProfile profile, Rect currentMachine, Rect currentViewport) {
        Rect panel = profile.requested(viewport);
        return new Rect(
            panel.x + currentMachine.x + currentMachine.width / 2 - machine.x - machine.width / 2,
            panel.y + currentMachine.bottom() - machine.bottom(),
            panel.width,
            panel.height);
    }

    /** Custom interfaces follow their own origin without the global adjustment for a different machine size. */
    public Rect adaptCustom(PlayerPanelProfile profile, Rect currentMachine) {
        Rect panel = profile.requested(viewport);
        return new Rect(
            panel.x + currentMachine.x - machine.x,
            panel.y + currentMachine.y - machine.y,
            panel.width,
            panel.height);
    }

    /** Only an authored, non-overlapping position above the machine permits automatic fitting there. */
    public boolean placedAbove(PlayerPanelProfile profile) {
        Rect authored = profile.requested(viewport);
        return authored.y < machine.y && !authored.intersects(machine);
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
