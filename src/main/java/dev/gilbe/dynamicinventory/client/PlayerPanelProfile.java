package dev.gilbe.dynamicinventory.client;

import com.google.gson.JsonObject;

import dev.gilbe.dynamicinventory.client.InventoryPanelLayout.Rect;

/** Saved preferred bounds survive screen fitting and capacity changes. Anchors use 0..10000 screen travel. */
public final class PlayerPanelProfile {

    public static final PlayerPanelProfile DEFAULT = new PlayerPanelProfile(false, 178, 190, 5000, 5000);
    public final boolean detached;
    public final int width, height, anchorX, anchorY;
    public final int frameStyle, borderWidth, frameCrop, cornerCrop;

    public PlayerPanelProfile(boolean detached, int width, int height, int anchorX, int anchorY) {
        this(detached, width, height, anchorX, anchorY, -1, 2, 0, 2);
    }

    public PlayerPanelProfile(boolean detached, int width, int height, int anchorX, int anchorY, int frameStyle,
        int borderWidth, int frameCrop, int cornerCrop) {
        if (width < 70 || width > 4096
            || height < 42
            || height > 4096
            || anchorX < 0
            || anchorX > 10000
            || anchorY < 0
            || anchorY > 10000
            || frameStyle < -1
            || frameStyle > 2
            || borderWidth < 0
            || borderWidth > 6
            || frameCrop < 0
            || frameCrop > 8
            || cornerCrop < 0
            || cornerCrop > 8) throw new IllegalArgumentException("Invalid player panel bounds");
        this.detached = detached;
        this.width = width;
        this.height = height;
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.frameStyle = frameStyle;
        this.borderWidth = borderWidth;
        this.frameCrop = frameCrop;
        this.cornerCrop = cornerCrop;
    }

    public PlayerPanelProfile detached(boolean value) {
        return new PlayerPanelProfile(
            value,
            width,
            height,
            anchorX,
            anchorY,
            frameStyle,
            borderWidth,
            frameCrop,
            cornerCrop);
    }

    public PlayerPanelProfile withBounds(int width, int height, int x, int y) {
        return new PlayerPanelProfile(detached, width, height, x, y, frameStyle, borderWidth, frameCrop, cornerCrop);
    }

    public PlayerPanelProfile withAppearance(int style, int border, int crop, int corners) {
        return new PlayerPanelProfile(detached, width, height, anchorX, anchorY, style, border, crop, corners);
    }

    public PlayerPanelProfile atBounds(Rect bounds, Rect viewport) {
        PlayerPanelProfile moved = at(bounds, viewport);
        return moved.withAppearance(frameStyle, borderWidth, frameCrop, cornerCrop)
            .detached(detached);
    }

    public Rect requested(Rect viewport) {
        return new Rect(
            viewport.x + (int) ((long) Math.max(0, viewport.width - width) * anchorX / 10000),
            viewport.y + (int) ((long) Math.max(0, viewport.height - height) * anchorY / 10000),
            width,
            height);
    }

    public static PlayerPanelProfile at(Rect bounds, Rect viewport) {
        int x = anchor(bounds.x - viewport.x, viewport.width - bounds.width);
        int y = anchor(bounds.y - viewport.y, viewport.height - bounds.height);
        return new PlayerPanelProfile(
            true,
            Math.max(70, Math.min(4096, bounds.width)),
            Math.max(42, Math.min(4096, bounds.height)),
            x,
            y);
    }

    private static int anchor(int offset, int travel) {
        // Round up so converting the anchor back to pixels does not subtract one pixel on every drag.
        return travel <= 0 ? 0 : (int) Math.max(0, Math.min(10000, ((long) offset * 10000 + travel - 1) / travel));
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("detached", detached);
        json.addProperty("width", width);
        json.addProperty("height", height);
        json.addProperty("anchorX", anchorX);
        json.addProperty("anchorY", anchorY);
        json.addProperty("frameStyle", frameStyle);
        json.addProperty("borderWidth", borderWidth);
        json.addProperty("frameCrop", frameCrop);
        json.addProperty("cornerCrop", cornerCrop);
        return json;
    }

    public static PlayerPanelProfile fromJson(JsonObject json) {
        if (json.entrySet()
            .size() != 5
            && json.entrySet()
                .size() != 9
            || !json.has("detached")
            || !json.get("detached")
                .isJsonPrimitive()
            || !json.getAsJsonPrimitive("detached")
                .isBoolean())
            throw new IllegalArgumentException("Invalid panel profile");
        PlayerPanelProfile profile = new PlayerPanelProfile(
            json.get("detached")
                .getAsBoolean(),
            integer(json, "width"),
            integer(json, "height"),
            integer(json, "anchorX"),
            integer(json, "anchorY"));
        return json.entrySet()
            .size() == 5
                ? profile
                : profile.withAppearance(
                    signedStyle(json),
                    integer(json, "borderWidth"),
                    integer(json, "frameCrop"),
                    integer(json, "cornerCrop"));
    }

    private static int signedStyle(JsonObject json) {
        if (json.has("frameStyle") && json.getAsJsonPrimitive("frameStyle")
            .isNumber()
            && "-1".equals(
                json.get("frameStyle")
                    .getAsString()))
            return -1;
        return integer(json, "frameStyle");
    }

    private static int integer(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key)
            .isJsonPrimitive()
            || !json.getAsJsonPrimitive(key)
                .isNumber()
            || !json.get(key)
                .getAsString()
                .matches("0|[1-9][0-9]*"))
            throw new IllegalArgumentException("Invalid " + key);
        return Integer.parseInt(
            json.get(key)
                .getAsString());
    }
}
