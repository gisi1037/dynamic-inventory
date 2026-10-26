package dev.gilbe.dynamicinventory.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import dev.gilbe.dynamicinventory.api.InventoryUi;
import dev.gilbe.dynamicinventory.client.InventoryPanelLayout.Rect;
import dev.gilbe.dynamicinventory.compat.PackClientCompatibility;
import dev.gilbe.dynamicinventory.compat.PackCompatibility;
import dev.gilbe.dynamicinventory.container.ContainerInventoryRegion;
import dev.gilbe.dynamicinventory.container.PlayerContainerLayout;
import dev.gilbe.dynamicinventory.inventory.InventoryLayout;

/** Shared native-slot panel controller. Admitted hosts: the owned player inventory and single vanilla chest. */
public final class DetachedChestPanel extends Gui {

    private static final ResourceLocation ATLAS = new ResourceLocation("textures/gui/container/inventory.png");
    private final GuiContainer gui;
    private final ContainerPanelAccess access;
    private final Rect machine, viewport, attached;
    private final List<Rect> fixed = new ArrayList<>();
    private final String key;
    private final int style, border, crop, corners;
    private final int[] slotIds;
    private final List<Panel> panels = new ArrayList<>();
    private String selected = PlayerPanelSet.MAIN;
    private PlayerPanelProfile gestureStart;
    private InventoryPanelLayout gestureLayout;
    private int gestureScroll;
    private Panel captured;
    private Rect dragStart;
    private int startX, startY, grip;
    private boolean releaseOwned, usable;
    private long revision = 1;

    private static final class Panel {

        final String id, name;
        final List<Integer> members;
        PlayerPanelProfile profile;
        InventoryPanelLayout layout;
        int scroll;

        Panel(PlayerPanelSet.Entry entry, List<Integer> members, PlayerPanelProfile profile) {
            id = entry.id;
            name = entry.name;
            this.members = members;
            this.profile = profile;
        }

        boolean main() {
            return PlayerPanelSet.MAIN.equals(id);
        }

        int maxScroll() {
            return layout == null ? 0 : layout.maxScroll(members.size());
        }
    }

    public DetachedChestPanel(GuiContainer gui, Rect machine, Rect attached, int style, int border, int crop,
        int corners) {
        this(gui, machine, attached, style, border, crop, corners, java.util.Collections.emptyList());
    }

    public DetachedChestPanel(GuiContainer gui, Rect machine, Rect attached, int style, int border, int crop,
        int corners, List<Rect> auxiliary) {
        this.gui = gui;
        this.machine = machine;
        fixed.add(machine);
        fixed.addAll(auxiliary);
        this.attached = attached;
        this.style = style;
        this.border = border;
        this.crop = crop;
        this.corners = corners;
        access = (ContainerPanelAccess) gui;
        ContainerInventoryRegion region = ContainerInventoryRegion.of(gui.inventorySlots);
        int count = InventoryLayout.of(Minecraft.getMinecraft().thePlayer.inventory)
            .mainSlots();
        slotIds = new int[count];
        for (int i = 0; i < count; i++) slotIds[i] = region != null ? region.id(i)
            : PlayerContainerLayout.containerId(Minecraft.getMinecraft().thePlayer.inventory, i);
        viewport = new Rect(0, 8, gui.width, Math.max(0, gui.height - 36));
        String adapter = gui instanceof PlayerInventoryScreen ? "dynamicinventory:player"
            : InventoryUi.findAdapter(gui.inventorySlots)
                .getId();
        key = PlayerPanelProfiles.key(
            adapter,
            gui.inventorySlots.getClass()
                .getName(),
            gui.getClass()
                .getName());
        reload();
    }

    public static DetachedChestPanel of(GuiScreen screen) {
        if (screen instanceof PlayerInventoryScreen player) return player.floating();
        if (!(screen instanceof ContainerPanelAccess access)) return null;
        NativeInventoryPanel nativePanel = access.dynamicinventory$panel();
        return nativePanel == null ? null : nativePanel.floating();
    }

    public String key() {
        return key;
    }

    public String selected() {
        return selected;
    }

    public void select(String id) {
        selected = id;
    }

    private Panel main() {
        return panels.get(0);
    }

    private Panel selection() {
        for (Panel panel : panels) if (panel.id.equals(selected)) return panel;
        return main();
    }

    public boolean detached() {
        return main().profile.detached && usable;
    }

    public PlayerPanelProfile preference() {
        return selection().profile;
    }

    public int inheritedFrameStyle() {
        return style;
    }

    public int frameStyle() {
        return preference().frameStyle < 0 ? style : preference().frameStyle;
    }

    public int capacity() {
        return slotIds.length - 9;
    }

    public void apply(PlayerPanelProfile value) throws IOException {
        Panel panel = selection();
        PlayerPanelSet set = PlayerPanelProfiles.panels();
        if (!panel.main()) set = set.replace(
            set.get(panel.id)
                .profile(key, value));
        apply(set, panel.main() ? value : main().profile, false);
    }

    public void apply(PlayerPanelSet set, PlayerPanelProfile mainProfile, boolean global) throws IOException {
        // Validate effective geometry before publishing either disk or live state.
        List<Panel> before = new ArrayList<>(panels);
        load(set, mainProfile);
        if (mainProfile.detached && !usable) {
            panels.clear();
            panels.addAll(before);
            refit();
            throw new IOException("Not enough space for all panels");
        }
        try {
            PlayerPanelProfiles.applyPanels(set, global ? null : key, mainProfile);
        } catch (IOException failure) {
            panels.clear();
            panels.addAll(before);
            refit();
            throw failure;
        }
        position();
    }

    public void inherit() throws IOException {
        if (selection().main()) PlayerPanelProfiles.inherit(key);
        else {
            PlayerPanelSet set = PlayerPanelProfiles.panels();
            PlayerPanelProfiles.applyPanels(
                set.replace(
                    set.get(selected)
                        .profile(key, null)),
                key,
                main().profile);
        }
        reload();
    }

    public void reload() {
        load(PlayerPanelProfiles.panels(), PlayerPanelProfiles.resolve(key));
    }

    private void load(PlayerPanelSet set, PlayerPanelProfile mainProfile) {
        panels.clear();
        for (PlayerPanelSet.Entry entry : set.entries) panels.add(
            new Panel(
                entry,
                set.members(entry.id, capacity()),
                entry.id.equals(PlayerPanelSet.MAIN) ? mainProfile : entry.resolve(key)));
        refit();
    }

    public long revision() {
        return detached() ? revision : 0;
    }

    public Rect playerBounds() {
        return detached() ? main().layout.bounds : attached;
    }

    public Rect machineBounds() {
        return machine;
    }

    public List<Rect> bounds() {
        List<Rect> result = new ArrayList<>();
        if (detached()) for (Panel panel : panels) if (panel.layout != null) result.add(panel.layout.bounds);
        return result;
    }

    public List<Rect> occupiedBounds() {
        List<Rect> result = new ArrayList<>(fixed);
        result.addAll(bounds());
        return result;
    }

    public boolean inPlayer(int x, int y) {
        if (!detached()) return attached.contains(x, y);
        for (Rect rect : bounds()) if (rect.contains(x, y)) return true;
        return false;
    }

    public boolean inside(int x, int y) {
        for (Rect rect : fixed) if (rect.contains(x, y)) return true;
        return inPlayer(x, y);
    }

    public boolean excludes(int x, int y, int width, int height) {
        if (!detached()) return false;
        Rect cell = new Rect(x, y, width, height);
        for (Rect rect : fixed) if (rect.intersects(cell)) return true;
        for (Rect rect : bounds()) if (rect.intersects(cell)) return true;
        return false;
    }

    private int margin(Panel panel) {
        PlayerPanelProfile profile = panel.profile;
        return Math.max(8, (profile.frameStyle < 0 ? border + crop : profile.borderWidth + profile.frameCrop) + 4);
    }

    private void refit() {
        List<Rect> obstacles = new ArrayList<>(fixed);
        usable = true;
        for (Panel panel : panels) {
            if (!panel.main() && panel.members.isEmpty()) {
                panel.layout = null;
                continue;
            }
            panel.layout = InventoryPanelLayout.fit(
                panel.profile.requested(viewport),
                viewport,
                obstacles,
                panel.members.size(),
                margin(panel),
                panel.main());
            if (panel.layout == null) {
                usable = false;
                break;
            }
            panel.scroll = Math.max(0, Math.min(panel.maxScroll(), panel.scroll));
            obstacles.add(panel.layout.bounds);
        }
        revision++;
    }

    private void refitGesture(Panel moved) {
        List<Rect> obstacles = new ArrayList<>(fixed);
        for (Panel panel : panels) if (panel != moved && panel.layout != null) obstacles.add(panel.layout.bounds);
        InventoryPanelLayout fit = InventoryPanelLayout.fit(
            moved.profile.requested(viewport),
            viewport,
            obstacles,
            moved.members.size(),
            margin(moved),
            moved.main());
        if (fit != null) moved.layout = fit;
        revision++;
    }

    public int maxScroll() {
        return main().maxScroll();
    }

    public int columns() {
        return main().layout.columns;
    }

    public int rows() {
        return main().layout.rows;
    }

    public void scroll(int delta) {
        main().scroll = Math.max(0, Math.min(main().maxScroll(), main().scroll + delta));
        position();
    }

    public boolean scrollAt(int x, int y, int delta) {
        if (!detached() || blocked()
            || Minecraft.getMinecraft().thePlayer.inventory.getItemStack() != null
            || access.dynamicinventory$draggingItems()
            || PackCompatibility.enabled() && PackClientCompatibility.neiDragging()) return false;
        for (Panel panel : panels) if (panel.layout != null && panel.layout.bounds.contains(x, y)) {
            if (panel.maxScroll() == 0) return false;
            panel.scroll = Math.max(0, Math.min(panel.maxScroll(), panel.scroll + delta));
            position();
            return true;
        }
        return false;
    }

    public boolean hasItemAt(int x, int y) {
        for (int id : slotIds) {
            Slot slot = gui.inventorySlots.getSlot(id);
            int sx = access.dynamicinventory$left() + slot.xDisplayPosition,
                sy = access.dynamicinventory$top() + slot.yDisplayPosition;
            if (slot.getHasStack() && x >= sx - 1 && x < sx + 17 && y >= sy - 1 && y < sy + 17) return true;
        }
        return false;
    }

    public void position() {
        if (!detached()) return;
        for (int id : slotIds) {
            Slot slot = gui.inventorySlots.getSlot(id);
            slot.xDisplayPosition = -10000;
            slot.yDisplayPosition = -10000;
        }
        for (Panel panel : panels) {
            if (panel.layout == null) continue;
            InventoryPanelLayout layout = panel.layout;
            if (panel.main()) for (int i = 0; i < 9; i++) place(i, layout.hotbarX + i * 18, layout.hotbarY);
            for (int i = 0; i < panel.members.size(); i++) {
                int row = i / layout.columns - panel.scroll;
                if (row >= 0 && row < layout.rows)
                    place(panel.members.get(i) + 9, layout.gridX + i % layout.columns * 18, layout.gridY + row * 18);
            }
        }
    }

    private void place(int logical, int x, int y) {
        Slot slot = gui.inventorySlots.getSlot(slotIds[logical]);
        slot.xDisplayPosition = x - access.dynamicinventory$left();
        slot.yDisplayPosition = y - access.dynamicinventory$top();
    }

    public void background(Runnable nativeDraw, int nativeWidth, int nativeHeight) {
        position();
        NativePanelFrame.draw(machine.x, machine.y, machine.width, machine.height, style, border, crop, corners, null);
        int w = access.dynamicinventory$width(), h = access.dynamicinventory$height();
        int x = access.dynamicinventory$left(), y = access.dynamicinventory$top();
        int screenW = gui.width, screenH = gui.height;
        Minecraft mc = Minecraft.getMinecraft();
        int scale = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight).getScaleFactor();
        GL11.glPushAttrib(GL11.GL_SCISSOR_BIT);
        try {
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(
                (machine.x + 4) * scale,
                Math.max(0, mc.displayHeight - (machine.bottom() - 4) * scale),
                Math.max(0, machine.width - 8) * scale,
                Math.max(0, machine.height - 8) * scale);
            access.dynamicinventory$bounds(nativeWidth, nativeHeight, machine.x, machine.y);
            gui.width = 2 * machine.x + nativeWidth;
            gui.height = 2 * machine.y + nativeHeight;
            nativeDraw.run();
        } finally {
            gui.width = screenW;
            gui.height = screenH;
            access.dynamicinventory$bounds(w, h, x, y);
            GL11.glPopAttrib();
        }
        drawPanels();
    }

    public void drawPanels() {
        position();
        Minecraft mc = Minecraft.getMinecraft();
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        try {
            for (Panel panel : panels) {
                if (panel.layout == null) continue;
                Rect bounds = panel.layout.bounds;
                PlayerPanelProfile profile = panel.profile;
                NativePanelFrame.draw(
                    bounds.x,
                    bounds.y,
                    bounds.width,
                    bounds.height,
                    profile.frameStyle < 0 ? style : profile.frameStyle,
                    profile.frameStyle < 0 ? border : profile.borderWidth,
                    profile.frameStyle < 0 ? crop : profile.frameCrop,
                    profile.frameStyle < 0 ? corners : profile.cornerCrop,
                    null);
                mc.getTextureManager()
                    .bindTexture(ATLAS);
                GL11.glColor4f(1, 1, 1, 1);
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                if (panel.main()) for (int i = 0; i < 9; i++) slotBackground(slotIds[i]);
                for (int member : panel.members) {
                    Slot slot = gui.inventorySlots.getSlot(slotIds[member + 9]);
                    if (slot.xDisplayPosition != -10000) slotBackground(slotIds[member + 9]);
                }
                if (panel.maxScroll() > 0) {
                    Rect track = panel.layout.scrollbar();
                    int thumb = Math
                        .max(10, track.height * panel.layout.rows / (panel.layout.rows + panel.maxScroll()));
                    int offset = (track.height - thumb) * panel.scroll / panel.maxScroll();
                    drawRect(track.x, track.y, track.right(), track.bottom(), InventoryGuiTheme.get().scrollTrack);
                    drawRect(
                        track.x,
                        track.y + offset,
                        track.right(),
                        track.y + offset + thumb,
                        InventoryGuiTheme.get().scrollThumb);
                }
            }
        } finally {
            GL11.glPopAttrib();
        }
        if (PackCompatibility.enabled()) PackClientCompatibility.positionSortButtons(
            access.dynamicinventory$buttons(),
            access.dynamicinventory$left(),
            access.dynamicinventory$top(),
            main().layout.bounds.right() - access.dynamicinventory$left() - 46,
            main().layout.bounds.y - access.dynamicinventory$top() + 2,
            true);
    }

    private void slotBackground(int id) {
        Slot slot = gui.inventorySlots.getSlot(id);
        drawTexturedModalRect(
            access.dynamicinventory$left() + slot.xDisplayPosition - 1,
            access.dynamicinventory$top() + slot.yDisplayPosition - 1,
            7,
            83,
            18,
            18);
    }

    public void drawControl() {
        if (captured != null && (!org.lwjgl.opengl.Display.isActive() || blocked())) cancel();
        Minecraft mc = Minecraft.getMinecraft();
        int mx = org.lwjgl.input.Mouse.getX() * gui.width / mc.displayWidth;
        int my = gui.height - org.lwjgl.input.Mouse.getY() * gui.height / mc.displayHeight - 1;
        for (Panel panel : panels) {
            if (!panel.main() && (!detached() || panel.layout == null)) continue;
            Rect bounds = detached() ? panel.layout.bounds : attached;
            Rect control = control(bounds);
            PanelChrome.icon(control, control.contains(mx, my));
            if (detached()) {
                PanelChrome.grips(bounds, PanelChrome.corner(bounds, mx, my));
                int labelWidth = Math.max(0, bounds.width - (panel.main() ? 74 : 32));
                mc.fontRenderer.drawString(
                    mc.fontRenderer.trimStringToWidth(
                        panel.name.equals("Main") && panel.main() ? "Inventory" : panel.name,
                        labelWidth),
                    bounds.x + 24,
                    bounds.y + 4,
                    InventoryGuiTheme.get().label);
            }
            if (!PanelSettingsOverlay.isOpen(gui) && control.contains(mx, my)) {
                String text = "Left: " + (detached() ? "attach" : "detach") + "  Right: panel settings";
                int tx = Math.max(0, Math.min(gui.width - mc.fontRenderer.getStringWidth(text) - 8, control.x));
                int ty = Math.max(0, control.y - 14);
                drawRect(tx, ty, tx + mc.fontRenderer.getStringWidth(text) + 8, ty + 13, 0xf0100010);
                mc.fontRenderer.drawStringWithShadow(text, tx + 4, ty + 2, 0xffffff);
            }
        }
    }

    private Rect control(Rect bounds) {
        return gui instanceof PlayerInventoryScreen && !detached() ? new Rect(bounds.right() - 68, bounds.y + 1, 12, 12)
            : PanelChrome.control(bounds);
    }

    private boolean blocked() {
        Minecraft mc = Minecraft.getMinecraft();
        return ClientInputGuard.isPending(gui) || mc.thePlayer == null
            || mc.thePlayer.openContainer != gui.inventorySlots;
    }

    public boolean pointer(int x, int y, int button, boolean down) {
        if (releaseOwned && button == 0 && !down) {
            if (captured != null) {
                if (blocked()) cancel();
                else {
                    move(x, y);
                    captured = null;
                    save();
                }
            }
            releaseOwned = false;
            return true;
        }
        if (captured != null) {
            if (blocked()) cancel();
            else if (button == -1) move(x, y);
            return true;
        }
        if (releaseOwned) return true;
        for (Panel panel : panels) {
            if (!panel.main() && (!detached() || panel.layout == null)) continue;
            Rect bounds = detached() ? panel.layout.bounds : attached;
            boolean icon = PanelChrome.control(bounds)
                .contains(x, y);
            if (button == 1 && down && icon) {
                selected = panel.id;
                PanelSettingsOverlay.open(gui, this);
                return true;
            }
            int corner = detached() ? PanelChrome.corner(bounds, x, y) : 0;
            if (!icon && corner == 0 && overNativeButton(x, y)) return false;
            boolean title = detached() && y >= bounds.y && y < bounds.y + 13 && x >= bounds.x && x < bounds.right();
            boolean bar = detached() && panel.maxScroll() > 0
                && panel.layout.scrollbarTarget()
                    .contains(x, y);
            if (button != 0 || !down || !(icon || corner != 0 || title || bar)) continue;
            releaseOwned = true;
            selected = panel.id;
            Minecraft mc = Minecraft.getMinecraft();
            if (blocked() || mc.thePlayer.inventory.getItemStack() != null
                || access.dynamicinventory$draggingItems()
                || PackCompatibility.enabled() && PackClientCompatibility.neiDragging()) return true;
            if (icon) {
                PlayerPanelProfile before = main().profile;
                main().profile = before.detached(!detached());
                refit();
                if (main().profile.detached && !usable) {
                    main().profile = before;
                    refit();
                    message("Not enough screen space for all panels");
                } else {
                    selected = PlayerPanelSet.MAIN;
                    save();
                }
                return true;
            }
            if (bar) {
                panel.scroll = Math.min(
                    panel.maxScroll(),
                    (y - panel.layout.scrollbar().y) * (panel.maxScroll() + 1) / (panel.layout.rows * 18));
                position();
                return true;
            }
            gestureStart = panel.profile;
            gestureLayout = panel.layout;
            gestureScroll = panel.scroll;
            dragStart = bounds;
            startX = x;
            startY = y;
            grip = corner;
            captured = panel;
            return true;
        }
        return false;
    }

    private boolean overNativeButton(int x, int y) {
        for (GuiButton button : access.dynamicinventory$buttons()) if (button.visible && x >= button.xPosition
            && y >= button.yPosition
            && x < button.xPosition + button.width
            && y < button.yPosition + button.height) return true;
        return false;
    }

    private void move(int x, int y) {
        Panel panel = captured;
        int dx = x - startX, dy = y - startY;
        int left = dragStart.x, top = dragStart.y, right = dragStart.right(), bottom = dragStart.bottom();
        int minimumWidth = panel.main() ? 178 : 70, minimumHeight = panel.main() ? 64 : 44;
        if (grip == 0) {
            left += dx;
            right += dx;
            top += dy;
            bottom += dy;
        } else {
            if ((grip & 1) != 0) left = Math.max(right - 340, Math.min(right - minimumWidth, left + dx));
            if ((grip & 2) != 0) right = Math.min(left + 340, Math.max(left + minimumWidth, right + dx));
            if ((grip & 4) != 0) top = Math.max(bottom - 262, Math.min(bottom - minimumHeight, top + dy));
            if ((grip & 8) != 0) bottom = Math.min(top + 262, Math.max(top + minimumHeight, bottom + dy));
        }
        int anchor = panel.scroll * panel.layout.columns;
        panel.profile = panel.profile
            .atBounds(new Rect(left, top, Math.min(340, right - left), Math.min(262, bottom - top)), viewport);
        if (grip == 0) panel.profile = panel.profile
            .withBounds(gestureStart.width, gestureStart.height, panel.profile.anchorX, panel.profile.anchorY);
        refitGesture(panel);
        panel.scroll = Math.min(panel.maxScroll(), anchor / panel.layout.columns);
        position();
    }

    public boolean cancel() {
        if (captured == null) return false;
        captured.profile = gestureStart;
        captured.layout = gestureLayout;
        captured.scroll = gestureScroll;
        revision++;
        captured = null;
        position();
        return true;
    }

    private void save() {
        try {
            Panel chosen = selection();
            if (chosen.main()) PlayerPanelProfiles.save(key, chosen.profile);
            else {
                PlayerPanelSet set = PlayerPanelProfiles.panels();
                PlayerPanelProfiles.applyPanels(
                    set.replace(
                        set.get(chosen.id)
                            .profile(key, chosen.profile)),
                    key,
                    main().profile);
            }
        } catch (IOException failure) {
            reload();
            message("Could not save panel layout");
        }
    }

    private static void message(String text) {
        Minecraft.getMinecraft().thePlayer.addChatMessage(new ChatComponentText("Inventory: " + text));
    }
}
