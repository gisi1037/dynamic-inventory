package dev.gilbe.dynamicinventory.client;

import com.google.gson.JsonObject;

import dev.gilbe.dynamicinventory.client.InventoryPanelLayout.Rect;

/** Pins geometry belongs to the layout. Pin contents and network options live separately. */
public final class PinsPanelGeometry {

    static final PinsPanelGeometry DEFAULT = new PinsPanelGeometry(new Rect(-1, -1, 178, 101), null);

    private final Rect bounds;
    private final PanelLayoutReference reference;

    public PinsPanelGeometry(Rect bounds, PanelLayoutReference reference) {
        if (bounds.x < -1 || bounds.x > 16384
            || bounds.y < -1
            || bounds.y > 16384
            || bounds.width < 72
            || bounds.width > 450
            || bounds.height < 43
            || bounds.height > 450) throw new IllegalArgumentException("Invalid AE2 Pins bounds");
        this.bounds = bounds;
        this.reference = reference;
    }

    public Rect requested(Rect machine, boolean custom) {
        if (reference != null) return reference.translate(bounds, machine, custom);
        return new Rect(
            bounds.x < 0 ? machine.right() + 6 : bounds.x,
            bounds.y < 0 ? machine.y : bounds.y,
            bounds.width,
            bounds.height);
    }

    JsonObject toJson() {
        JsonObject object = new JsonObject();
        object.add("bounds", PanelLayoutReference.rectangle(bounds));
        object.add("reference", reference == null ? null : reference.toJson());
        return object;
    }

    static PinsPanelGeometry fromJson(JsonObject object) {
        if (object == null || object.entrySet()
            .size() != 2 || !object.has("reference")) throw new IllegalArgumentException("Invalid AE2 Pins geometry");
        return new PinsPanelGeometry(
            PanelLayoutReference.rectangle(object.getAsJsonArray("bounds")),
            object.get("reference")
                .isJsonNull() ? null : PanelLayoutReference.fromJson(object.getAsJsonObject("reference")));
    }
}
