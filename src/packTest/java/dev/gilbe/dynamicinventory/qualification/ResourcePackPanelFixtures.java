package dev.gilbe.dynamicinventory.qualification;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.imageio.ImageIO;

import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.SimpleResource;
import net.minecraft.client.resources.data.IMetadataSerializer;
import net.minecraft.util.ResourceLocation;

import dev.gilbe.dynamicinventory.client.InventoryGuiTheme;

/** Real PNG decode and resource-manager reload paths, including an existing pack without our theme JSON. */
final class ResourcePackPanelFixtures {

    static void run() {
        try {
            for (int scale : new int[] { 1, 2, 4 }) {
                InventoryGuiTheme.initialize(manager(scale, null));
                check(
                    !InventoryGuiTheme.get().custom && InventoryGuiTheme.get().panelFill == 0x30234567,
                    "pack without theme supplies translucent fill at every resolution");
                check(
                    InventoryGuiTheme.get().panelHighlight == 0xffc0ffee
                        && InventoryGuiTheme.get().panelShadow == 0xff135724,
                    "HD palette uses vanilla logical UVs");
                check(InventoryGuiTheme.get().playerFill == 0, "transparent model backdrop remains transparent");
            }
            InventoryGuiTheme.initialize(
                manager(
                    2,
                    "{\"schemaVersion\":1,\"name\":\"Existing theme\",\"colors\":{\"panelFill\":\"#00112233\"}}"));
            check(
                InventoryGuiTheme.get().custom && InventoryGuiTheme.get().panelFill == 0x00112233,
                "explicit resource theme wins over automatic palette");
            check(InventoryGuiTheme.get().panelHighlight == 0xffc0ffee, "partial themes inherit active pack colors");
            InventoryGuiTheme.initialize(manager(1, "{\"schemaVersion\":999}"));
            check(
                !InventoryGuiTheme.get().custom && InventoryGuiTheme.get().panelFill == 0x30234567,
                "invalid theme retains the active texture palette");
            System.out.println(
                "PASS resource-pack panel palette: native/HD PNG, alpha, reload and explicit-theme precedence");
        } catch (IOException failure) {
            throw new AssertionError("Resource pack fixture", failure);
        } finally {
            InventoryGuiTheme.initialize(null);
        }
    }

    private static IResourceManager manager(int scale, String theme) throws IOException {
        BufferedImage image = new BufferedImage(256 * scale, 256 * scale, BufferedImage.TYPE_INT_ARGB);
        paint(image, scale, 6, 6, 0x30234567);
        paint(image, scale, 173, 80, 0xff135724);
        paint(image, scale, 1, 80, 0xffc0ffee);
        paint(image, scale, 0, 80, 0xff112233);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        byte[] texture = bytes.toByteArray();
        return new IResourceManager() {

            @Override
            public Set<String> getResourceDomains() {
                return Collections.singleton("minecraft");
            }

            @Override
            public IResource getResource(ResourceLocation location) throws IOException {
                boolean palette = location.getResourcePath()
                    .equals("inventory_theme.json");
                if (palette && theme == null) throw new FileNotFoundException();
                return new SimpleResource(
                    location,
                    new ByteArrayInputStream(palette ? theme.getBytes(StandardCharsets.UTF_8) : texture),
                    null,
                    new IMetadataSerializer());
            }

            @Override
            public List<IResource> getAllResources(ResourceLocation location) throws IOException {
                return Collections.singletonList(getResource(location));
            }
        };
    }

    private static void paint(BufferedImage image, int scale, int u, int v, int color) {
        for (int y = v * scale; y < (v + 1) * scale; y++)
            for (int x = u * scale; x < (u + 1) * scale; x++) image.setRGB(x, y, color);
    }

    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }
}
