package dev.gilbe.dynamicinventory.client;

import java.awt.image.BufferedImage;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import javax.imageio.ImageIO;

import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.util.ResourceLocation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import dev.gilbe.dynamicinventory.DynamicInventory;

/** Immutable resource-pack palette for the expanded inventory panels. */
public final class InventoryGuiTheme implements IResourceManagerReloadListener {

    private static final Logger LOG = LogManager.getLogger(DynamicInventory.MODID);
    private static final ResourceLocation THEME_RESOURCE = new ResourceLocation(
        "dynamicinventory",
        "inventory_theme.json");
    private static final InventoryGuiTheme VANILLA = new InventoryGuiTheme(
        "Vanilla",
        false,
        0xff000000,
        0xff555555,
        0xffffffff,
        0xffc6c6c6,
        0xff8b8b8b,
        0xff000000,
        0xff888888,
        0xff404040,
        0xff555555,
        0xffdddddd,
        0xff222222,
        0xffc6c6c6,
        0xffffffff);

    private static final InventoryGuiTheme RELOADER = new InventoryGuiTheme();
    private static volatile InventoryGuiTheme current = VANILLA;
    private static IResourceManager registeredManager;

    public final int panelOuter;
    public final int panelShadow;
    public final int panelHighlight;
    public final int panelFill;
    public final int playerFrame;
    public final int playerFill;
    public final int arrow;
    public final int label;
    public final int scrollTrack;
    public final int scrollThumb;
    public final int settingsOuter;
    public final int settingsFill;
    public final int settingsText;
    public final boolean custom;
    public final String name;

    private InventoryGuiTheme() {
        this(
            VANILLA.name,
            VANILLA.custom,
            VANILLA.panelOuter,
            VANILLA.panelShadow,
            VANILLA.panelHighlight,
            VANILLA.panelFill,
            VANILLA.playerFrame,
            VANILLA.playerFill,
            VANILLA.arrow,
            VANILLA.label,
            VANILLA.scrollTrack,
            VANILLA.scrollThumb,
            VANILLA.settingsOuter,
            VANILLA.settingsFill,
            VANILLA.settingsText);
    }

    private InventoryGuiTheme(String name, boolean custom, int panelOuter, int panelShadow, int panelHighlight,
        int panelFill, int playerFrame, int playerFill, int arrow, int label, int scrollTrack, int scrollThumb,
        int settingsOuter, int settingsFill, int settingsText) {
        this.name = name;
        this.custom = custom;
        this.panelOuter = panelOuter;
        this.panelShadow = panelShadow;
        this.panelHighlight = panelHighlight;
        this.panelFill = panelFill;
        this.playerFrame = playerFrame;
        this.playerFill = playerFill;
        this.arrow = arrow;
        this.label = label;
        this.scrollTrack = scrollTrack;
        this.scrollThumb = scrollThumb;
        this.settingsOuter = settingsOuter;
        this.settingsFill = settingsFill;
        this.settingsText = settingsText;
    }

    public static InventoryGuiTheme get() {
        return current;
    }

    /** Registers for future pack reloads and resolves the resources that are active now. */
    public static synchronized void initialize(IResourceManager resourceManager) {
        if (resourceManager == null) {
            current = VANILLA;
            registeredManager = null;
            return;
        }
        if (resourceManager instanceof IReloadableResourceManager && registeredManager != resourceManager) {
            ((IReloadableResourceManager) resourceManager).registerReloadListener(RELOADER);
            registeredManager = resourceManager;
        }
        RELOADER.onResourceManagerReload(resourceManager);
    }

    @Override
    public void onResourceManagerReload(IResourceManager resourceManager) {
        current = load(resourceManager);
    }

    private static InventoryGuiTheme load(IResourceManager resourceManager) {
        InventoryGuiTheme base = fromInventoryTexture(resourceManager);
        try {
            IResource resource = resourceManager.getResource(THEME_RESOURCE);
            try (InputStream input = resource.getInputStream();
                Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                JsonElement parsed = new JsonParser().parse(reader);
                if (!parsed.isJsonObject()) return invalidRoot(base, "root must be an object", null);
                JsonObject root = parsed.getAsJsonObject();
                JsonElement schema = root.get("schemaVersion");
                JsonElement themeName = root.get("name");
                JsonElement colorsElement = root.get("colors");
                if (schema == null || !schema.isJsonPrimitive()
                    || !schema.getAsJsonPrimitive()
                        .isNumber()
                    || schema.getAsDouble() != 1.0
                    || themeName == null
                    || !themeName.isJsonPrimitive()
                    || !themeName.getAsJsonPrimitive()
                        .isString()
                    || themeName.getAsString()
                        .trim()
                        .isEmpty()
                    || colorsElement == null
                    || !colorsElement.isJsonObject())
                    return invalidRoot(base, "expected schemaVersion 1, a non-empty name, and a colors object", null);

                JsonObject colors = colorsElement.getAsJsonObject();
                return new InventoryGuiTheme(
                    themeName.getAsString()
                        .trim(),
                    true,
                    color(colors, "panelOuter", base.panelOuter),
                    color(colors, "panelShadow", base.panelShadow),
                    color(colors, "panelHighlight", base.panelHighlight),
                    color(colors, "panelFill", base.panelFill),
                    color(colors, "playerFrame", base.playerFrame),
                    color(colors, "playerFill", base.playerFill),
                    color(colors, "arrow", base.arrow),
                    color(colors, "label", base.label),
                    color(colors, "scrollTrack", base.scrollTrack),
                    color(colors, "scrollThumb", base.scrollThumb),
                    color(colors, "settingsOuter", base.settingsOuter),
                    color(colors, "settingsFill", base.settingsFill),
                    color(colors, "settingsText", base.settingsText));
            }
        } catch (FileNotFoundException missing) {
            return base;
        } catch (IOException error) {
            LOG.warn("Could not read {}. Using the active inventory texture palette.", THEME_RESOURCE, error);
            return base;
        } catch (JsonParseException | IllegalStateException error) {
            LOG.warn("Invalid {}. Using the active inventory texture palette.", THEME_RESOURCE, error);
            return base;
        }
    }

    private static InventoryGuiTheme invalidRoot(InventoryGuiTheme base, String reason, Throwable error) {
        if (error == null)
            LOG.warn("Invalid {}: {}. Using the active inventory texture palette.", THEME_RESOURCE, reason);
        else LOG.warn("Invalid {}: {}. Using the active inventory texture palette.", THEME_RESOURCE, reason, error);
        return base;
    }

    /** Existing packs need no Dynamic Inventory-specific theme to supply the basic panel colors. */
    private static InventoryGuiTheme fromInventoryTexture(IResourceManager manager) {
        try (InputStream input = manager.getResource(new ResourceLocation("textures/gui/container/inventory.png"))
            .getInputStream()) {
            BufferedImage image = ImageIO.read(input);
            if (image == null || image.getWidth() < 176 || image.getHeight() < 166) return VANILLA;
            int fill = sample(image, 6, 6), shadow = sample(image, 173, 80);
            int highlight = sample(image, 1, 80), outer = sample(image, 0, 80);
            int label = (fill >>> 24) < 128
                || ((fill >> 16 & 255) * 299 + (fill >> 8 & 255) * 587 + (fill & 255) * 114) < 128000 ? 0xffeeeeee
                    : 0xff404040;
            return new InventoryGuiTheme(
                "Inventory texture",
                false,
                outer,
                shadow,
                highlight,
                fill,
                sample(image, 8, 84),
                sample(image, 27, 9),
                VANILLA.arrow,
                label,
                shadow,
                highlight,
                VANILLA.settingsOuter,
                VANILLA.settingsFill,
                VANILLA.settingsText);
        } catch (IOException | RuntimeException invalid) {
            return VANILLA;
        }
    }

    private static int sample(BufferedImage image, int u, int v) {
        return image.getRGB(
            Math.min(image.getWidth() - 1, (int) ((u + 0.5) * image.getWidth() / 256)),
            Math.min(image.getHeight() - 1, (int) ((v + 0.5) * image.getHeight() / 256)));
    }

    private static int color(JsonObject colors, String key, int fallback) {
        JsonElement element = colors.get(key);
        if (element == null || !element.isJsonPrimitive()
            || !element.getAsJsonPrimitive()
                .isString()) {
            if (element != null) LOG.warn("Invalid color '{}' in {}. Using its vanilla default.", key, THEME_RESOURCE);
            return fallback;
        }
        String value = element.getAsString();
        if (!value.matches("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?")) {
            LOG.warn(
                "Invalid color '{}' in {}. Expected #RRGGBB or #AARRGGBB; using its vanilla default.",
                key,
                THEME_RESOURCE);
            return fallback;
        }
        long parsed = Long.parseLong(value.substring(1), 16);
        return value.length() == 7 ? (int) (0xff000000L | parsed) : (int) parsed;
    }
}
