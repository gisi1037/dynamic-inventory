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

/** Shared native-slot panel controller for the owned player inventory and vanilla containers. */
public final class DetachedChestPanel extends Gui {

    private static final ResourceLocation ATLAS = new ResourceLocation("textures/gui/container/inventory.png");
    private final GuiContainer gui;
    private final ContainerPanelAccess access;
    private Rect machine;
    private final Rect viewport, attached;
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
    private int lastDragX, lastDragY;
    private boolean lastResizeSnap;
    private int guideX = Integer.MIN_VALUE, guideY = Integer.MIN_VALUE;
    private boolean releaseOwned, usable;
    private boolean inherited;
    private long revision = 1;
    private long resizePlanRevision = -1;
    private final java.util.Map<String, List<ResizePlan>> resizePlans = new java.util.HashMap<>();
    private long toolbarRevision = -1;
    private final java.util.Map<String, Rect> resizeToolbars = new java.util.HashMap<>();

    private static final class ResizePlan {

        Rect bounds;
        PlayerPanelSet set;
        String problem;
        final java.util.Set<Integer> highlighted = new java.util.HashSet<>();
    }

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

    public PlayerPanelProfile displayedProfile(String id) {
        for (Panel panel : panels) if (panel.id.equals(id))
            return detached() && panel.layout != null ? panel.profile.atBounds(panel.layout.bounds, viewport)
                : panel.profile;
        throw new IllegalArgumentException("Unknown panel");
    }

    public PanelLayoutReference layoutReference() {
        return new PanelLayoutReference(machine, viewport);
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
        return slotIds.length - 9
            - dev.gilbe.dynamicinventory.inventory.HotbarBanks.reserved(Minecraft.getMinecraft().thePlayer.inventory);
    }

    private int slotId(int logical) {
        return slotIds[logical];
    }

    private java.util.Set<Integer> excluded() {
        return dev.gilbe.dynamicinventory.inventory.HotbarBanks.excluded(Minecraft.getMinecraft().thePlayer.inventory);
    }

    /** A split panel is a storage group, not the vanilla storage-to-hotbar transfer region. */
    public boolean quickMoveToMain(int sourceId) {
        if (!detached() || blocked() || Minecraft.getMinecraft().thePlayer.inventory.getItemStack() != null)
            return false;
        int logical = -1;
        for (int i = 9; i < slotIds.length; i++) if (slotId(i) == sourceId) logical = i;
        if (logical < 9) return false;
        boolean subpanel = false;
        for (Panel panel : panels) if (!panel.main() && panel.members.contains(logical - 9)) subpanel = true;
        if (!subpanel) return false;
        int[] destinations = new int[9 + main().members.size()];
        System.arraycopy(slotIds, 0, destinations, 0, 9);
        for (int i = 0; i < main().members.size(); i++) destinations[i + 9] = slotId(main().members.get(i) + 9);
        dev.gilbe.dynamicinventory.network.PanelTransferRequest.send(
            gui.inventorySlots,
            InventoryLayout.of(Minecraft.getMinecraft().thePlayer.inventory)
                .revision(),
            sourceId,
            destinations);
        return true;
    }

    public void apply(PlayerPanelProfile value) throws IOException {
        Panel panel = selection();
        PlayerPanelSet set = PlayerPanelProfiles.panels(key);
        if (!panel.main()) set = set.replace(
            set.get(panel.id)
                .profile(key, value));
        apply(set, panel.main() ? value : main().profile, false);
    }

    public void apply(PlayerPanelSet set, PlayerPanelProfile mainProfile, boolean global) throws IOException {
        // Validate effective geometry before publishing either disk or live state.
        if (global) {
            List<PlayerPanelSet.Entry> entries = new ArrayList<>();
            for (PlayerPanelSet.Entry entry : set.entries) entries.add(entry.profile(key, null));
            set = new PlayerPanelSet(entries);
        }
        List<Panel> before = new ArrayList<>(panels);
        boolean wasInherited = inherited;
        inherited = false;
        load(set, mainProfile);
        if (mainProfile.detached && !usable) {
            panels.clear();
            panels.addAll(before);
            inherited = wasInherited;
            refit();
            throw new IOException("Not enough space for all panels");
        }
        try {
            if (global) PlayerPanelProfiles
                .applyGlobalPanels(set, key, mainProfile, new PanelLayoutReference(machine, viewport));
            else PlayerPanelProfiles.applyPanels(set, key, mainProfile);
        } catch (IOException failure) {
            panels.clear();
            panels.addAll(before);
            inherited = wasInherited;
            refit();
            throw failure;
        }
        inherited = !PlayerPanelProfiles.hasOverride(key);
        refit();
        position();
    }

    public void inherit() throws IOException {
        PlayerPanelProfiles.inherit(key);
        reload();
    }

    public void reload() {
        inherited = !PlayerPanelProfiles.hasOverride(key);
        load(PlayerPanelProfiles.panels(key), PlayerPanelProfiles.resolve(key));
        position();
    }

    private void load(PlayerPanelSet set, PlayerPanelProfile mainProfile) {
        java.util.Map<String, Panel> previous = new java.util.HashMap<>();
        for (Panel panel : panels) previous.put(panel.id, panel);
        panels.clear();
        for (PlayerPanelSet.Entry entry : set.entries) panels.add(
            new Panel(
                entry,
                set.members(entry.id, slotIds.length - 9, excluded()),
                entry.id.equals(PlayerPanelSet.MAIN) ? mainProfile : entry.resolve(key)));
        refit();
        for (Panel panel : panels) {
            Panel before = previous.get(panel.id);
            if (before != null && before.layout != null && panel.layout != null) panel.scroll = Math
                .min(panel.maxScroll(), before.scroll * before.layout.columns / panel.layout.columns);
        }
    }

    public long revision() {
        return detached() ? revision : 0;
    }

    public Rect playerBounds() {
        return detached() && main().layout != null ? main().layout.bounds : attached;
    }

    public Rect machineBounds() {
        return machine;
    }

    public List<Rect> bounds() {
        List<Rect> result = new ArrayList<>();
        if (detached()) for (Panel panel : panels) if (panel.layout != null) result.add(panel.layout.bounds);
        return result;
    }

    public boolean mainVisible() {
        return !detached() || main().profile.visible;
    }

    private Rect recoveryControl() {
        return gui instanceof PlayerInventoryScreen ? new Rect(attached.right() - 68, attached.y + 1, 12, 12)
            : PanelChrome.control(attached);
    }

    public List<Rect> occupiedBounds() {
        List<Rect> result = new ArrayList<>(fixed);
        result.addAll(bounds());
        if (detached() && !mainVisible()) result.add(recoveryControl());
        // Temporary Shift controls draw over NEI; pressing Shift must not repaginate bookmarks/results.
        return result;
    }

    public boolean inPlayer(int x, int y) {
        if (!detached()) return attached.contains(x, y);
        for (Rect rect : bounds()) if (rect.contains(x, y)) return true;
        return false;
    }

    public boolean inside(int x, int y) {
        if (resizeControlsVisible()) for (Panel panel : panels) {
            Rect toolbar = resizeToolbar(panel);
            if (toolbar != null && toolbar.contains(x, y)) return true;
        }
        if (detached() && !mainVisible() && recoveryControl().contains(x, y)) return true;
        for (Rect rect : fixed) if (rect.contains(x, y)) return true;
        return inPlayer(x, y);
    }

    public boolean excludes(int x, int y, int width, int height) {
        if (!detached()) return false;
        Rect cell = new Rect(x, y, width, height);
        if (!mainVisible() && recoveryControl().intersects(cell)) return true;
        for (Rect rect : fixed) if (rect.intersects(cell)) return true;
        for (Rect rect : bounds()) if (rect.intersects(cell)) return true;
        return false;
    }

    private int margin(Panel panel) {
        PlayerPanelProfile profile = panel.profile;
        return Math.max(
            profile.minimumMargin,
            (profile.frameStyle < 0 ? border + crop : profile.borderWidth + profile.frameCrop) + 4);
    }

    private void refit() {
        List<Rect> obstacles = new ArrayList<>(fixed);
        if (main().profile.detached && !main().profile.visible) obstacles.add(recoveryControl());
        usable = true;
        for (Panel panel : panels) {
            if (!panel.profile.visible || !panel.main() && panel.members.isEmpty()) {
                panel.layout = null;
                continue;
            }
            Rect requested = panel.profile.requested(viewport);
            if (inherited) requested = PlayerPanelProfiles.inheritedBounds(requested, machine, viewport);
            panel.layout = InventoryPanelLayout
                .fit(requested, viewport, obstacles, panel.members.size(), margin(panel), panel.main());
            if (panel.layout == null) {
                usable = false;
                break;
            }
            panel.scroll = Math.max(0, Math.min(panel.maxScroll(), panel.scroll));
            obstacles.add(panel.layout.bounds);
        }
        revision++;
    }

    private void refitGesture(Panel moved, Rect requested) {
        List<Rect> obstacles = new ArrayList<>(fixed);
        if (main().profile.detached && !main().profile.visible) obstacles.add(recoveryControl());
        for (Panel panel : panels) if (panel != moved && panel.layout != null) obstacles.add(panel.layout.bounds);
        int margin = margin(moved);
        Rect safe;
        guideX = guideY = Integer.MIN_VALUE;
        if (grip == 0) {
            InventoryPanelLayout.Alignment aligned = InventoryPanelLayout.alignDrag(requested, viewport, obstacles, 3);
            safe = aligned.bounds;
            guideX = aligned.guideX;
            guideY = aligned.guideY;
        } else if (lastResizeSnap) {
            safe = InventoryPanelLayout.snapResize(
                dragStart,
                requested,
                grip,
                viewport,
                obstacles,
                margin,
                moved.main(),
                moved.members.size());
            if (safe == null) return;
        } else safe = InventoryPanelLayout.constrainGesture(
            moved.layout.bounds,
            requested,
            viewport,
            obstacles,
            18 + margin * 2,
            InventoryPanelLayout.minimumHeight(requested.width, margin, moved.main()));
        InventoryPanelLayout fit = InventoryPanelLayout
            .fit(safe, safe, java.util.Collections.emptyList(), moved.members.size(), margin, moved.main());
        if (fit != null) moved.layout = fit;
        revision++;
    }

    private void settleDrag() {
        if (grip != 0) return;
        List<Rect> obstacles = new ArrayList<>(fixed);
        if (!mainVisible()) obstacles.add(recoveryControl());
        for (Panel panel : panels) if (panel != captured && panel.layout != null) obstacles.add(panel.layout.bounds);
        Rect safe = InventoryPanelLayout.snapDrag(captured.layout.bounds, viewport, obstacles);
        if (safe == null) {
            captured.layout = gestureLayout;
            captured.profile = gestureStart;
            captured.scroll = gestureScroll;
        } else {
            captured.layout = InventoryPanelLayout.fit(
                safe,
                safe,
                java.util.Collections.emptyList(),
                captured.members.size(),
                margin(captured),
                captured.main());
            captured.profile = captured.profile.atBounds(safe, viewport);
        }
        revision++;
        position();
    }

    private boolean resizeControlsVisible() {
        return detached() && captured == null && GuiScreen.isShiftKeyDown() && !PanelSettingsOverlay.isOpen(gui);
    }

    private boolean outerLeft(Panel panel) {
        return panel.layout.bounds.x + panel.layout.bounds.width / 2 < playerBounds().x + playerBounds().width / 2;
    }

    private Rect resizeToolbar(Panel panel) {
        if (toolbarRevision != revision) {
            resizeToolbars.clear();
            for (Panel candidate : panels) {
                Rect toolbar = placeResizeToolbar(candidate);
                if (toolbar != null) resizeToolbars.put(candidate.id, toolbar);
            }
            toolbarRevision = revision;
        }
        return resizeToolbars.get(panel.id);
    }

    private Rect placeResizeToolbar(Panel panel) {
        if (panel.main() || panel.layout == null) return null;
        Rect b = panel.layout.bounds;
        int x = Math.max(viewport.x, Math.min(viewport.right() - 44, outerLeft(panel) ? b.right() - 44 : b.x));
        for (Rect candidate : new Rect[] { new Rect(x, b.bottom() + 1, 44, 32), new Rect(x, b.y - 33, 44, 32),
            new Rect(b.right() + 1, b.bottom() - 32, 44, 32), new Rect(b.x - 45, b.bottom() - 32, 44, 32) }) {
            if (!withinViewport(candidate)) continue;
            boolean clear = true;
            if (!mainVisible() && candidate.intersects(recoveryControl())) clear = false;
            for (Rect obstacle : fixed) if (candidate.intersects(obstacle)) clear = false;
            for (Rect other : resizeToolbars.values()) if (candidate.intersects(other)) clear = false;
            for (Panel other : panels)
                if (other.layout != null && candidate.intersects(other.layout.bounds)) clear = false;
            if (clear) return candidate;
        }
        return null;
    }

    private boolean withinViewport(Rect bounds) {
        return bounds.x >= viewport.x && bounds.y >= viewport.y
            && bounds.right() <= viewport.right()
            && bounds.bottom() <= viewport.bottom();
    }

    private static Rect resizeButton(Rect toolbar, int action) {
        return new Rect(toolbar.x + 12 + action % 2 * 14, toolbar.y + 3 + action / 2 * 14, 12, 12);
    }

    private List<ResizePlan> resizePlans(Panel panel) {
        if (resizePlanRevision != revision) {
            resizePlans.clear();
            resizePlanRevision = revision;
        }
        if (resizePlans.containsKey(panel.id)) return resizePlans.get(panel.id);
        List<ResizePlan> plans = new ArrayList<>();
        for (int action = 0; action < 4; action++) {
            ResizePlan plan = new ResizePlan();
            plans.add(plan);
            boolean row = action >= 2, left = outerLeft(panel);
            int delta = action % 2 == 0 ? -1 : 1;
            Rect before = panel.layout.bounds;
            plan.bounds = new Rect(
                before.x - (!row && left ? delta * 18 : 0),
                before.y,
                before.width + (row ? 0 : delta * 18),
                before.height + (row ? delta * 18 : 0));
            try {
                if (plan.bounds.width < 18 + margin(panel) * 2 || plan.bounds.height < 36 + margin(panel)
                    || plan.bounds.width > 340
                    || plan.bounds.height > 262) throw new IllegalArgumentException("No room for that panel size");
                List<Rect> obstacles = new ArrayList<>(fixed);
                for (Panel other : panels)
                    if (other != panel && other.layout != null) obstacles.add(other.layout.bounds);
                Rect fitted = InventoryPanelLayout.snapDrag(plan.bounds, viewport, obstacles);
                if (fitted == null || Math.abs(fitted.x - plan.bounds.x) > 19
                    || Math.abs(fitted.y - plan.bounds.y) > 19)
                    throw new IllegalArgumentException("No room for that panel size");
                plan.bounds = fitted;
                plan.set = PlayerPanelProfiles.panels(key)
                    .resizeGrid(panel.id, panel.layout.columns, row, delta, left, slotIds.length - 9, excluded());
                java.util.Set<Integer> after = new java.util.HashSet<>(
                    plan.set.members(panel.id, slotIds.length - 9, excluded()));
                if (delta > 0) {
                    plan.highlighted.addAll(after);
                    plan.highlighted.removeAll(panel.members);
                } else {
                    plan.highlighted.addAll(panel.members);
                    plan.highlighted.removeAll(after);
                }
            } catch (IllegalArgumentException invalid) {
                plan.problem = invalid.getMessage();
            }
        }
        resizePlans.put(panel.id, plans);
        return plans;
    }

    private boolean resizeInput(int x, int y, int button, boolean down) {
        if (!resizeControlsVisible()) return false;
        for (Panel panel : panels) {
            Rect toolbar = resizeToolbar(panel);
            if (toolbar == null || !toolbar.contains(x, y)) continue;
            if (button == 0 && down) {
                releaseOwned = true;
                Minecraft mc = Minecraft.getMinecraft();
                if (blocked() || mc.thePlayer.inventory.getItemStack() != null
                    || access.dynamicinventory$draggingItems()
                    || PackCompatibility.enabled() && PackClientCompatibility.neiDragging()) return true;
                for (int action = 0; action < 4; action++) if (resizeButton(toolbar, action).contains(x, y)) {
                    ResizePlan plan = resizePlans(panel).get(action);
                    if (plan.problem != null) {
                        message(plan.problem);
                        return true;
                    }
                    try {
                        selected = panel.id;
                        PlayerPanelSet set = plan.set.replace(
                            plan.set.get(panel.id)
                                .profile(key, panel.profile.atBounds(plan.bounds, viewport)));
                        for (Panel other : panels) if (other != panel) set = set.replace(
                            set.get(other.id)
                                .profile(key, displayedProfile(other.id)));
                        apply(set, displayedProfile(PlayerPanelSet.MAIN), false);
                    } catch (IOException failure) {
                        message("Could not save panel size; previous layout retained");
                    }
                    return true;
                }
            }
            return true;
        }
        return false;
    }

    public int maxScroll() {
        return main().maxScroll();
    }

    public int columns() {
        return main().layout == null ? 9 : main().layout.columns;
    }

    public int rows() {
        return main().layout == null ? 0 : main().layout.rows;
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
        refreshClearance();
        if (!detached()) {
            if (gui instanceof PlayerInventoryScreen player) player.positionSlots();
            return;
        }
        for (int id : slotIds) {
            Slot slot = gui.inventorySlots.getSlot(id);
            slot.xDisplayPosition = -10000;
            slot.yDisplayPosition = -10000;
        }
        for (Panel panel : panels) {
            if (panel.layout == null) continue;
            InventoryPanelLayout layout = panel.layout;
            if (panel.main()) for (int i = 0; i < 9; i++) place(i, layout.hotbarSlotX(i), layout.hotbarSlotY(i));
            for (int i = 0; i < panel.members.size(); i++) {
                int row = i / layout.columns - panel.scroll;
                int inset = panel.main() && InventoryViewSettings.centerLastRow
                    ? InventoryPanelLayout.rowOffset(i, panel.members.size(), layout.columns)
                    : 0;
                if (row >= 0 && row < layout.rows) place(
                    panel.members.get(i) + 9,
                    layout.gridX + inset + i % layout.columns * 18,
                    layout.gridY + row * 18);
            }
        }
    }

    /** Recheck the live player header: other inventory integrations can move it after initGui. */
    private void refreshClearance() {
        if (!(gui instanceof PlayerInventoryScreen player) || captured != null) return;
        List<Rect> current = player.fixedPanelBounds();
        boolean changed = current.size() != fixed.size();
        for (int i = 0; !changed && i < current.size(); i++) {
            Rect a = current.get(i), b = fixed.get(i);
            changed = a.x != b.x || a.y != b.y || a.width != b.width || a.height != b.height;
        }
        List<Rect> occupied = new ArrayList<>(current);
        for (Panel panel : panels) if (panel.layout != null) {
            for (Rect obstacle : occupied) if (panel.layout.bounds.intersects(obstacle)) changed = true;
            occupied.add(panel.layout.bounds);
        }
        if (changed) {
            fixed.clear();
            fixed.addAll(current);
            machine = current.get(0);
            refit();
        }
    }

    private void place(int logical, int x, int y) {
        Slot slot = gui.inventorySlots.getSlot(slotId(logical));
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
                List<Slot> visible = new ArrayList<>();
                if (panel.main()) for (int i = 0; i < 9; i++) visible.add(gui.inventorySlots.getSlot(slotIds[i]));
                for (int member : panel.members) {
                    Slot slot = gui.inventorySlots.getSlot(slotId(member + 9));
                    if (slot.xDisplayPosition != -10000) visible.add(slot);
                }
                ResourceSlotGrid.draw(visible, access.dynamicinventory$left(), access.dynamicinventory$top());
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
            playerBounds().right() - access.dynamicinventory$left() - 46,
            playerBounds().y - access.dynamicinventory$top() + 2,
            mainVisible());
    }

    public void drawControl() {
        if (captured != null && (!org.lwjgl.opengl.Display.isActive() || blocked())) cancel();
        Minecraft mc = Minecraft.getMinecraft();
        int mx = org.lwjgl.input.Mouse.getX() * gui.width / mc.displayWidth;
        int my = gui.height - org.lwjgl.input.Mouse.getY() * gui.height / mc.displayHeight - 1;
        GL11.glPushAttrib(
            GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        try {
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            if (captured != null && grip == 0) {
                int guideColor = 0x55000000 | InventoryGuiTheme.get().panelHighlight & 0xffffff;
                if (guideX != Integer.MIN_VALUE)
                    drawRect(guideX, viewport.y, guideX + 1, viewport.bottom(), guideColor);
                if (guideY != Integer.MIN_VALUE) drawRect(viewport.x, guideY, viewport.right(), guideY + 1, guideColor);
            }
            for (Panel panel : panels) {
                if (!panel.main() && (!detached() || panel.layout == null)) continue;
                Rect bounds = detached() && panel.layout != null ? panel.layout.bounds : attached;
                Rect control = detached() && panel.main() && !mainVisible() ? recoveryControl() : control(bounds);
                if (detached() && panel.main() && !mainVisible()) NativePanelFrame
                    .draw(control.x - 2, control.y - 2, control.width + 4, control.height + 4, style, 1, 0, 1, null);
                PanelChrome.icon(control, control.contains(mx, my), inherited);
                if (detached() && panel.layout != null) {
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
                    String scope = inherited ? "Global default: adjusts to this machine"
                        : "Custom layout: fixed interface positions";
                    int width = Math.max(mc.fontRenderer.getStringWidth(text), mc.fontRenderer.getStringWidth(scope));
                    int tx = Math.max(0, Math.min(gui.width - width - 8, control.x));
                    int ty = Math.max(0, control.y - 25);
                    drawRect(tx, ty, tx + width + 8, ty + 24, 0xf0100010);
                    mc.fontRenderer.drawStringWithShadow(text, tx + 4, ty + 2, 0xffffff);
                    mc.fontRenderer.drawStringWithShadow(scope, tx + 4, ty + 13, 0xffffff);
                }
            }
            drawResizeControls(mx, my);
        } finally {
            GL11.glPopAttrib();
        }
    }

    private Rect control(Rect bounds) {
        return gui instanceof PlayerInventoryScreen && !detached() ? new Rect(bounds.right() - 68, bounds.y + 1, 12, 12)
            : PanelChrome.control(bounds);
    }

    private void drawResizeControls(int mouseX, int mouseY) {
        if (!resizeControlsVisible()) return;
        Minecraft mc = Minecraft.getMinecraft();
        InventoryGuiTheme theme = InventoryGuiTheme.get();
        ResizePlan hovered = null;
        int hoveredAction = -1;
        for (Panel panel : panels) {
            Rect toolbar = resizeToolbar(panel);
            if (toolbar == null) continue;
            int frame = panel.profile.frameStyle < 0 ? style : panel.profile.frameStyle;
            NativePanelFrame.draw(toolbar.x, toolbar.y, toolbar.width, toolbar.height, frame, 1, 0, 1, null);
            mc.fontRenderer.drawString("W", toolbar.x + 3, toolbar.y + 5, theme.label);
            mc.fontRenderer.drawString("H", toolbar.x + 3, toolbar.y + 19, theme.label);
            List<ResizePlan> plans = resizePlans(panel);
            for (int action = 0; action < 4; action++) {
                Rect target = resizeButton(toolbar, action);
                ResizePlan plan = plans.get(action);
                boolean over = target.contains(mouseX, mouseY);
                PanelChrome.raisedButton(target, plan.problem == null, over);
                mc.fontRenderer.drawString(
                    action % 2 == 0 ? "-" : "+",
                    target.x + 3,
                    target.y + 2,
                    plan.problem == null ? theme.label : theme.panelShadow);
                if (over) {
                    hovered = plan;
                    hoveredAction = action;
                }
            }
        }
        if (hovered == null) return;
        if (hovered.problem == null) for (int storage : hovered.highlighted) {
            Slot slot = gui.inventorySlots.getSlot(slotId(storage + 9));
            if (slot.xDisplayPosition == -10000) continue;
            int x = access.dynamicinventory$left() + slot.xDisplayPosition,
                y = access.dynamicinventory$top() + slot.yDisplayPosition;
            drawRect(x, y, x + 16, y + 16, 0x66000000 | theme.panelHighlight & 0xffffff);
        }
        String text = hovered.problem != null ? hovered.problem
            : (hoveredAction % 2 == 0 ? "Remove " : "Add ") + (hoveredAction >= 2 ? "row" : "column")
                + ": "
                + hovered.highlighted.size()
                + " slots "
                + (hoveredAction % 2 == 0 ? "back to Main" : "from Main");
        text = mc.fontRenderer.trimStringToWidth(text, Math.max(20, gui.width - 12));
        int width = mc.fontRenderer.getStringWidth(text) + 8;
        int x = Math.max(0, Math.min(gui.width - width, mouseX + 8));
        int y = Math.max(0, Math.min(gui.height - 13, mouseY - 16));
        drawRect(x, y, x + width, y + 13, 0xf0100010);
        mc.fontRenderer.drawStringWithShadow(text, x + 4, y + 2, 0xffffff);
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
                    settleDrag();
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
        if (resizeInput(x, y, button, down)) return true;
        for (Panel panel : panels) {
            if (!panel.main() && (!detached() || panel.layout == null)) continue;
            Rect bounds = detached() && panel.layout != null ? panel.layout.bounds : attached;
            boolean icon = (detached() && panel.main() && !mainVisible() ? recoveryControl() : control(bounds))
                .contains(x, y);
            if (button == 1 && down && icon) {
                selected = panel.id;
                PanelSettingsOverlay.open(gui, this);
                return true;
            }
            int corner = detached() && panel.layout != null ? PanelChrome.corner(bounds, x, y) : 0;
            if (!icon && corner == 0 && overNativeButton(x, y)) return false;
            boolean title = detached() && panel.layout != null
                && y >= bounds.y
                && y < bounds.y + 13
                && x >= bounds.x
                && x < bounds.right();
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
            lastDragX = x;
            lastDragY = y;
            lastResizeSnap = corner != 0 && GuiScreen.isShiftKeyDown();
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

    /** Follow the current cursor before rendering, independently of Minecraft's input tick. */
    public void updateDragFrame() {
        if (captured == null) return;
        if (!org.lwjgl.opengl.Display.isActive() || blocked()) {
            cancel();
            return;
        }
        // A queued release still owns committing the layout; do not follow the cursor after button-up.
        if (!org.lwjgl.input.Mouse.isButtonDown(0)) return;
        Minecraft mc = Minecraft.getMinecraft();
        int x = org.lwjgl.input.Mouse.getX() * gui.width / mc.displayWidth;
        int y = gui.height - org.lwjgl.input.Mouse.getY() * gui.height / mc.displayHeight - 1;
        move(x, y);
    }

    private void move(int x, int y) {
        boolean snap = grip != 0 && GuiScreen.isShiftKeyDown();
        if (x == lastDragX && y == lastDragY && snap == lastResizeSnap) return;
        lastDragX = x;
        lastDragY = y;
        lastResizeSnap = snap;
        Panel panel = captured;
        int dx = x - startX, dy = y - startY;
        int left = dragStart.x, top = dragStart.y, right = dragStart.right(), bottom = dragStart.bottom();
        int minimumWidth = 18 + margin(panel) * 2;
        if (grip == 0) {
            left += dx;
            right += dx;
            top += dy;
            bottom += dy;
        } else {
            if ((grip & 1) != 0) left = Math.max(right - 340, Math.min(right - minimumWidth, left + dx));
            if ((grip & 2) != 0) right = Math.min(left + 340, Math.max(left + minimumWidth, right + dx));
            int minimumHeight = InventoryPanelLayout.minimumHeight(right - left, margin(panel), panel.main());
            if (minimumHeight > viewport.height) return;
            if (bottom - top < minimumHeight) {
                if ((grip & 4) != 0) top = bottom - minimumHeight;
                else bottom = top + minimumHeight;
            }
            if ((grip & 4) != 0) top = Math.max(bottom - 262, Math.min(bottom - minimumHeight, top + dy));
            if ((grip & 8) != 0) bottom = Math.min(top + 262, Math.max(top + minimumHeight, bottom + dy));
        }
        int anchor = panel.scroll * panel.layout.columns;
        refitGesture(panel, new Rect(left, top, Math.min(340, right - left), Math.min(262, bottom - top)));
        panel.profile = panel.profile.atBounds(panel.layout.bounds, viewport);
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
            PlayerPanelSet set = PlayerPanelProfiles.panels(key);
            for (Panel panel : panels) set = set.replace(
                set.get(panel.id)
                    .profile(key, displayedProfile(panel.id)));
            PlayerPanelProfiles.applyPanels(set, key, displayedProfile(PlayerPanelSet.MAIN));
            inherited = !PlayerPanelProfiles.hasOverride(key);
        } catch (IOException failure) {
            reload();
            message("Could not save panel layout");
        }
        position();
    }

    private static void message(String text) {
        Minecraft.getMinecraft().thePlayer.addChatMessage(new ChatComponentText("Inventory: " + text));
    }
}
