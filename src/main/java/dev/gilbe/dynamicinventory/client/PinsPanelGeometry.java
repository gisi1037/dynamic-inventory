package dev.gilbe.dynamicinventory.client;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import dev.gilbe.dynamicinventory.client.InventoryPanelLayout.Rect;

/** Pins geometry belongs to the layout. Pin contents and network options live separately. */
public final class PinsPanelGeometry {

    static final int MIN_WIDTH = 72, MIN_HEIGHT = 43, MAX_WIDTH = 450;
    static final PlayerPanelProfile DEFAULT_APPEARANCE = PlayerPanelProfile.DEFAULT.withAppearance(2, 2, 0, 2);
    static final PinsPanelGeometry DEFAULT = new PinsPanelGeometry(new Rect(-1, -1, 178, 101), null);

    private final Rect bounds;
    private final PanelLayoutReference reference;
    private final PlayerPanelProfile appearance;

    public PinsPanelGeometry(Rect bounds, PanelLayoutReference reference) {
        this(java.util.Objects.requireNonNull(bounds), reference, null);
    }

    private PinsPanelGeometry(Rect bounds, PanelLayoutReference reference, PlayerPanelProfile appearance) {
        if (bounds == null ? reference != null || appearance == null
            : bounds.x < -1 || bounds.x > 16384
                || bounds.y < -1
                || bounds.y > 16384
                || bounds.width < MIN_WIDTH
                || bounds.width > MAX_WIDTH
                || bounds.height < MIN_HEIGHT
                || bounds.height > InventoryPanelLayout.MAX_HEIGHT)
            throw new IllegalArgumentException("Invalid AE2 Pins bounds");
        this.bounds = bounds;
        this.reference = reference;
        this.appearance = appearance;
    }

    boolean hasBounds() {
        return bounds != null;
    }

    PlayerPanelProfile appearance() {
        return appearance;
    }

    PinsPanelGeometry withAppearance(PlayerPanelProfile style) {
        PlayerPanelProfile normalized = style == null ? null
            : DEFAULT_APPEARANCE.withAppearance(style.frameStyle, style.borderWidth, style.frameCrop, style.cornerCrop)
                .withMinimumMargin(style.minimumMargin);
        return new PinsPanelGeometry(bounds, reference, normalized);
    }

    static PinsPanelGeometry appearanceOnly(PlayerPanelProfile style) {
        return new PinsPanelGeometry(null, null, style).withAppearance(style);
    }

    static Rect resizeFromCorner(Rect start, Rect viewport, int corner, int dx, int dy) {
        return resizeFromCorner(start, viewport, corner, dx, dy, MIN_HEIGHT);
    }

    static Rect resizeFromCorner(Rect start, Rect viewport, int corner, int dx, int dy, int minimumHeight) {
        boolean left = (corner & 1) != 0, top = (corner & 4) != 0;
        int maxWidth = Math.min(MAX_WIDTH, left ? start.right() - viewport.x : viewport.right() - start.x);
        int maxHeight = Math
            .min(InventoryPanelLayout.MAX_HEIGHT, top ? start.bottom() - viewport.y : viewport.bottom() - start.y);
        int width = Math.min(maxWidth, Math.max(MIN_WIDTH, start.width + (left ? -dx : dx)));
        int height = Math.min(maxHeight, Math.max(minimumHeight, start.height + (top ? -dy : dy)));
        return new Rect(left ? start.right() - width : start.x, top ? start.bottom() - height : start.y, width, height);
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
        object.add("bounds", bounds == null ? JsonNull.INSTANCE : PanelLayoutReference.rectangle(bounds));
        object.add("reference", reference == null ? null : reference.toJson());
        if (appearance != null) object.add("appearance", appearance.toJson());
        return object;
    }

    static PinsPanelGeometry fromJson(JsonObject object) {
        if (object == null || object.entrySet()
            .size() != (object.has("appearance") ? 3 : 2) || !object.has("bounds") || !object.has("reference"))
            throw new IllegalArgumentException("Invalid AE2 Pins geometry");
        return new PinsPanelGeometry(
            object.get("bounds")
                .isJsonNull() ? null : PanelLayoutReference.rectangle(object.getAsJsonArray("bounds")),
            object.get("reference")
                .isJsonNull() ? null : PanelLayoutReference.fromJson(object.getAsJsonObject("reference")),
            object.has("appearance") ? PlayerPanelProfile.fromJson(object.getAsJsonObject("appearance")) : null);
    }
}
