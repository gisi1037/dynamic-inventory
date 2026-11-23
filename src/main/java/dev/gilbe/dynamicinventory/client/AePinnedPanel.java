package dev.gilbe.dynamicinventory.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntFunction;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.GuiScreenEvent;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

import appeng.api.config.PinSectionOrder;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.container.implementations.ContainerMEMonitorable;
import appeng.core.AEConfig;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.items.contents.PinList;
import appeng.util.item.AEItemStack;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import dev.gilbe.dynamicinventory.DynamicInventory;
import dev.gilbe.dynamicinventory.client.InventoryPanelLayout.Rect;
import dev.gilbe.dynamicinventory.compat.PackClientCompatibility;
import dev.gilbe.dynamicinventory.compat.ae2.AppliedEnergisticsPatternTransfers;
import dev.gilbe.dynamicinventory.compat.ae2.PinnedItemTransfer;
import dev.gilbe.dynamicinventory.compat.ae2.TerminalPinsView;
import dev.gilbe.dynamicinventory.network.AePinsChannel;
import dev.gilbe.dynamicinventory.network.AePinsChannel.Transfer;

/** One client-side panel, with either the open terminal's pins or an explicitly opened wireless feed. */
public final class AePinnedPanel extends Gui {

    private static final AePinnedPanel PANEL = new AePinnedPanel();
    private static final AtomicReference<AePinsChannel.Snapshot> INCOMING = new AtomicReference<>();
    private static final int FOREGROUND_RENEW_TICKS = 40;
    private static final int PADDING = 8, HEADER = 20, CELL = 18, SCROLLBAR = 5;
    private static final boolean LWJGL3 = Loader.isModLoaded("lwjgl3ify");

    private boolean enabled, remote, foreground;
    private long epoch;
    private int renewIn, scroll, columnScroll, pressX, pressY;
    private int lastX, lastY;
    private int guideX = Integer.MIN_VALUE, guideY = Integer.MIN_VALUE;
    private boolean resizing;
    private Rect gesture, lastBounds;
    private GuiScreen gestureScreen;
    private GuiScreen paintedScreen;
    private GuiScreen optionsScreen, scrollingScreen, pressedScreen;
    private int scrollGrab;
    private Rect paintedBounds;
    private Object connection;
    private AePinsChannel.Snapshot snapshot;
    private final AePinsPrediction prediction = new AePinsPrediction();
    private GuiContainer shiftClickScreen;
    private ItemStack shiftClickItem;
    private int shiftClickSlot;
    private long shiftClickAt;
    private boolean shiftClickReleased;

    public static boolean separates(GuiScreen gui) {
        return PANEL.enabled && terminal(gui) != null
            && gui instanceof GuiContainer container
            && dev.gilbe.dynamicinventory.compat.NativePanelHosts.supports(container.inventorySlots, gui.getClass());
    }

    public static boolean usesTerminalPins(GuiScreen gui) {
        return sourceTerminal(gui) != null;
    }

    public static int inlinePinIndex(int original) {
        var grid = InventoryViewSettings.pinsGrid;
        return grid == null ? original : grid.inlineId(original);
    }

    public static boolean hasCustomGrid() {
        return InventoryViewSettings.pinsGrid != null;
    }

    public static void preserveGrid(GuiScreen gui) {
        if (hasCustomGrid() || !separates(gui)) return;
        Rect area = occupied(gui);
        List<Entry> entries = PANEL.entries(gui);
        if (area != null && !entries.isEmpty()) InventoryViewSettings.setPinsGrid(editableGrid(area, entries));
    }

    public static void receive(AePinsChannel.Snapshot reply) {
        INCOMING.set(reply);
    }

    public static void receiveTransfer(long sequence) {
        PANEL.prediction.receive(sequence);
    }

    public static ItemStack renderedStack(InventoryPlayer inventory, int raw, ItemStack original) {
        return PANEL.prediction.renderStack(inventory, raw, original);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void beforeScreen(GuiScreenEvent.DrawScreenEvent.Pre event) {
        var player = Minecraft.getMinecraft().thePlayer;
        if (event.gui instanceof GuiContainer gui && player != null) {
            prediction.update(gui, player.inventory, index -> pin(gui, index));
            prediction.render(gui, true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void afterScreen(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (event.gui instanceof GuiContainer gui) prediction.render(gui, false);
    }

    public static AePinnedPanel instance() {
        return PANEL;
    }

    private static TerminalPinsView terminal(GuiScreen gui) {
        return gui instanceof TerminalPinsView view && view.dynamicinventory$hasPins() ? view : null;
    }

    private static TerminalPinsView sourceTerminal(GuiScreen gui) {
        TerminalPinsView view = terminal(gui);
        if (view == null || !InventoryViewSettings.pinsWirelessOnly) return view;
        // The open wireless terminal already syncs the pins for this exact item.
        return gui instanceof GuiContainer container && container.inventorySlots instanceof ContainerMEMonitorable me
            && me.getTarget() instanceof WirelessTerminalGuiObject ? view : null;
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft client = Minecraft.getMinecraft();
        GuiScreen gui = client.currentScreen;
        if (shiftClickScreen != gui) clearShiftClick();
        if (!(gui instanceof GuiContainer) || client.thePlayer == null) prediction.clear();
        if (optionsScreen != gui) optionsScreen = null;
        if (scrollingScreen != gui) scrollingScreen = null;
        if (pressedScreen != gui) pressedScreen = null;
        Object activeConnection = client.getNetHandler();
        if (activeConnection != connection) {
            connection = activeConnection;
            enabled = remote = foreground = false;
            clearGesture();
            optionsScreen = scrollingScreen = pressedScreen = null;
            paintedScreen = null;
            paintedBounds = null;
            snapshot = null;
            INCOMING.set(null);
            prediction.clear();
        }
        if (!enabled || connection == null || client.thePlayer == null) return;
        if (gesture != null && gestureScreen != gui) clearGesture();
        tickWireless(DetachedChestPanel.of(gui) != null && sourceTerminal(gui) == null);
        if (gui instanceof GuiContainer container)
            prediction.update(container, client.thePlayer.inventory, index -> pin(gui, index));
    }

    private void tickWireless(boolean visible) {
        AePinsChannel.Snapshot reply = INCOMING.getAndSet(null);
        if (reply != null && remote && reply.epoch() == epoch) {
            if (!reply.available()) {
                renewIn = Math.min(renewIn, visible ? FOREGROUND_RENEW_TICKS : AePinsChannel.BACKGROUND_REFRESH_TICKS);
                remote = false;
                prediction.clear();
            }
            snapshot = reply;
        }

        boolean changedMode = visible != foreground;
        foreground = visible;
        if (!remote && visible && changedMode) renewIn = Math.min(renewIn, FOREGROUND_RENEW_TICKS);
        if (--renewIn > 0 && (!remote || !changedMode)) return;

        AePinsChannel.send(remote ? epoch : ++epoch, true, remote, !visible);
        remote = true;
        renewIn = visible ? FOREGROUND_RENEW_TICKS : AePinsChannel.BACKGROUND_REFRESH_TICKS;
    }

    private void toggle(GuiScreen gui) {
        enabled = !enabled;
        clearShiftClick();
        prediction.clear();
        scroll = columnScroll = 0;
        clearGesture();
        paintedBounds = null;
        if (!enabled) {
            if (connection != null) AePinsChannel.send(++epoch, false, false, false);
            remote = foreground = false;
            snapshot = null;
            INCOMING.set(null);
        }
        renewIn = 0;
        TerminalPinsView nativeView = terminal(gui);
        if (nativeView != null) nativeView.dynamicinventory$rebuildPins();
    }

    private static Rect button(DetachedChestPanel host) {
        Rect control = host.mainControl();
        return new Rect(control.right(), control.y, 10, 12);
    }

    private static Rect optionsBounds(GuiScreen gui, DetachedChestPanel host) {
        var font = Minecraft.getMinecraft().fontRenderer;
        int width = Math.max(
            font.getStringWidth(I18n.format("dynamicinventory.ae2pins.source.open")),
            font.getStringWidth(I18n.format("dynamicinventory.ae2pins.source.wireless"))) + 18;
        Rect button = button(host);
        return new Rect(
            Math.max(2, Math.min(gui.width - width - 2, button.x)),
            Math.max(2, Math.min(gui.height - 42, button.bottom() + 2)),
            width,
            40);
    }

    private static Rect optionsControl(Rect menu) {
        return new Rect(menu.x + 4, menu.y + 18, menu.width - 8, 18);
    }

    private static Rect headerWidget(Rect panel, int index) {
        return new Rect(panel.right() - 18 - index * 15, panel.y + 4, 13, 12);
    }

    private static Rect viewport(GuiScreen gui) {
        return new Rect(2, 18, Math.max(1, gui.width - 4), Math.max(1, gui.height - 44));
    }

    private static List<Rect> obstacles(GuiScreen gui, DetachedChestPanel host) {
        List<Rect> occupied = new ArrayList<>(host.occupiedBounds());
        occupied.add(host.playerBounds());
        occupied.add(button(host));
        if (gui instanceof GuiContainer container) {
            Rect hotbars = HotbarBankPanel.bounds(container);
            if (hotbars != null) occupied.add(hotbars);
        }
        return occupied;
    }

    private Rect bounds(GuiScreen gui, DetachedChestPanel host) {
        if (gesture != null && gestureScreen == gui && lastBounds != null) return lastBounds;
        Rect viewport = viewport(gui);
        Rect preferred = PlayerPanelProfiles.requestedPinsBounds(host.key(), host.machineBounds());
        int width = Math.min(preferred.width, viewport.width);
        int height = Math.min(preferred.height, viewport.height);
        List<Rect> occupied = obstacles(gui, host);
        Rect desired = new Rect(preferred.x, preferred.y, width, height);
        Rect fitted = InventoryPanelLayout.snapDrag(desired, viewport, occupied);
        if (fitted != null) return fitted;
        Rect free = InventoryPanelLayout.freeRegion(viewport, occupied, 72, 43);
        if (free != null) return new Rect(free.x, free.y, Math.min(width, free.width), Math.min(height, free.height));
        // At very small scales retain access to the pins as an overlay, with input owned by this panel.
        return InventoryPanelLayout.snapDrag(desired, viewport, java.util.Collections.emptyList());
    }

    public static Rect occupied(GuiScreen gui) {
        return PANEL.enabled && gui == PANEL.paintedScreen ? PANEL.paintedBounds : null;
    }

    public static void draw(GuiScreen gui, int x, int y) {
        DetachedChestPanel host = DetachedChestPanel.of(gui);
        if (host == null || PanelSettingsOverlay.isOpen(gui)) {
            if (PANEL.gestureScreen == gui) PANEL.clearGesture();
            if (PANEL.optionsScreen == gui) PANEL.optionsScreen = null;
            if (PANEL.scrollingScreen == gui) PANEL.scrollingScreen = null;
            return;
        }
        PANEL.updateDragFrame(gui, host, x, y);
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glPushMatrix();
        try {
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glTranslatef(0, 0, 180);
            PANEL.paint(gui, host, x, y);
            if (PANEL.optionsScreen == gui) PANEL.paintOptions(gui, host, x, y);
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    private void paintOptions(GuiScreen gui, DetachedChestPanel host, int x, int y) {
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        Rect menu = optionsBounds(gui, host), control = optionsControl(menu);
        var font = Minecraft.getMinecraft().fontRenderer;
        int text = InventoryGuiTheme.get().label;
        NativePanelFrame.draw(menu.x, menu.y, menu.width, menu.height, 2, null);
        font.drawString(I18n.format("dynamicinventory.ae2pins.options"), menu.x + 7, menu.y + 6, text);
        PanelChrome.raisedButton(control, true, control.contains(x, y));
        String source = InventoryViewSettings.pinsWirelessOnly ? "wireless" : "open";
        font.drawString(I18n.format("dynamicinventory.ae2pins.source." + source), control.x + 4, control.y + 5, text);
    }

    private void paint(GuiScreen gui, DetachedChestPanel host, int x, int y) {
        Minecraft client = Minecraft.getMinecraft();
        int text = InventoryGuiTheme.get().label;
        Rect toggle = button(host);
        PanelChrome.raisedButton(toggle, true, toggle.contains(x, y));
        int pinColor = enabled ? 0xffffcb77 : text;
        drawRect(toggle.x + 3, toggle.y + 3, toggle.x + 9, toggle.y + 5, pinColor);
        drawRect(toggle.x + 5, toggle.y + 5, toggle.x + 7, toggle.y + 10, pinColor);
        if (toggle.contains(x, y) && optionsScreen != gui) drawTooltip(
            gui,
            I18n.format("dynamicinventory.ae2pins.title"),
            I18n.format("dynamicinventory.ae2pins.options.hint"),
            x,
            y);
        if (!enabled) return;
        Rect area = bounds(gui, host);
        paintedScreen = gui;
        paintedBounds = area;
        if (area == null) return;
        if (gesture != null && gestureScreen == gui && !resizing) {
            int color = 0x55000000 | InventoryGuiTheme.get().panelHighlight & 0xffffff;
            Rect viewport = viewport(gui);
            if (guideX != Integer.MIN_VALUE) drawRect(guideX, viewport.y, guideX + 1, viewport.bottom(), color);
            if (guideY != Integer.MIN_VALUE) drawRect(viewport.x, guideY, viewport.right(), guideY + 1, color);
        }
        NativePanelFrame.draw(area.x, area.y, area.width, area.height, 2, null);
        Rect returnWidget = headerWidget(area, 0);
        String title = client.fontRenderer
            .trimStringToWidth(I18n.format("dynamicinventory.ae2pins.title"), Math.max(0, returnWidget.x - area.x - 8));
        client.fontRenderer.drawString(title, area.x + 6, area.y + 7, text);
        drawReturnWidget(gui, returnWidget, x, y, text);
        PanelChrome.grip(area, 10, PanelChrome.corner(area, x, y) == 10 || gesture != null && resizing);
        List<Entry> entries = entries(gui);
        int columns = gridColumns(area), visibleColumns = Math.min(columns(area), columns), rows = rows(area);
        scroll = Math.min(scroll, maxScroll(entries.size(), columns, rows));
        columnScroll = Math.min(columnScroll, columns - visibleColumns);
        if (entries.isEmpty()) {
            String status = sourceTerminal(gui) != null ? "dynamicinventory.ae2pins.empty"
                : snapshot == null ? "dynamicinventory.ae2pins.loading" : snapshot.statusKey();
            client.fontRenderer.drawSplitString(
                I18n.format(status),
                area.x + PADDING,
                area.y + HEADER + 2,
                area.width - PADDING * 2,
                text);
            return;
        }
        Rect track = scrollTrack(area, entries.size());
        if (track != null) {
            if (scrollingScreen == gui && Mouse.isButtonDown(0)) scrollTo(area, track, entries.size(), y);
            Rect thumb = scrollThumb(area, track, entries.size());
            drawRect(track.x, track.y, track.right(), track.bottom(), InventoryGuiTheme.get().panelShadow);
            drawRect(thumb.x, thumb.y, thumb.right(), thumb.bottom(), InventoryGuiTheme.get().panelHighlight);
        }
        Rect horizontal = columnTrack(area);
        if (horizontal != null) {
            int thumb = Math.max(6, horizontal.width * visibleColumns / columns);
            int left = horizontal.x + (horizontal.width - thumb) * columnScroll / (columns - visibleColumns);
            drawRect(
                horizontal.x,
                horizontal.y,
                horizontal.right(),
                horizontal.bottom(),
                InventoryGuiTheme.get().panelShadow);
            drawRect(left, horizontal.y, left + thumb, horizontal.bottom(), InventoryGuiTheme.get().panelHighlight);
        }
        Entry hovered = null;
        for (int row = 0; row < rows; row++) for (int col = 0; col < visibleColumns; col++) {
            int index = (scroll + row) * columns + columnScroll + col;
            if (index >= entries.size()) break;
            Entry entry = entries.get(index);
            int left = area.x + PADDING + col * CELL, top = area.y + HEADER + row * CELL;
            drawRect(left, top, left + CELL - 1, top + CELL - 1, InventoryGuiTheme.get().panelShadow);
            if (entry.stack != null) {
                var display = prediction.renderPin(gui, entry.stack);
                display.drawInGui(client, left + 1, top + 1);
                display.drawOverlayInGui(client, left + 1, top + 1, true, true, true, true);
            }
            if (new Rect(left, top, CELL, CELL).contains(x, y)
                && (optionsScreen != gui || !optionsBounds(gui, host).contains(x, y))) hovered = entry;
        }
        if (altDown() && optionsScreen != gui) drawGridControls(gui, area, entries, x, y, text);
        if (hovered != null && hovered.index >= 0) drawPinTooltip(gui, hovered, x, y);
    }

    private static void drawPinTooltip(GuiScreen gui, Entry entry, int x, int y) {
        if (entry.stack == null) {
            drawTooltip(
                gui,
                I18n.format(
                    entry.index >= PinList.PLAYER_OFFSET ? "dynamicinventory.ae2pins.add"
                        : "dynamicinventory.ae2pins.crafting.empty"),
                null,
                x,
                y);
            return;
        }
        ItemStack stack = entry.stack.getItemStackForNEI();
        if (stack != null && gui instanceof ContainerPanelAccess access)
            access.dynamicinventory$itemTooltip(stack, x, y);
        else drawTooltip(gui, entry.stack.getDisplayName(), null, x, y);
    }

    private static Rect gridToolbar(GuiScreen gui, Rect area) {
        int x = Math.max(1, area.right() - 44);
        if (area.y >= 33) return new Rect(x, area.y - 32, 42, 30);
        if (area.bottom() + 32 < gui.height) return new Rect(x, area.bottom() + 2, 42, 30);
        if (area.right() + 44 < gui.width) return new Rect(area.right() + 2, area.y, 42, 30);
        if (area.x >= 44) return new Rect(area.x - 44, area.y, 42, 30);
        return new Rect(x, 1, 42, 30);
    }

    private static Rect gridButton(Rect toolbar, int action) {
        return new Rect(toolbar.x + 13 + action % 2 * 14, toolbar.y + 2 + action / 2 * 13, 13, 12);
    }

    private void drawGridControls(GuiScreen gui, Rect area, List<Entry> entries, int x, int y, int text) {
        Rect toolbar = gridToolbar(gui, area);
        AePinsGrid grid = editableGrid(area, entries);
        var font = Minecraft.getMinecraft().fontRenderer;
        NativePanelFrame.draw(toolbar.x, toolbar.y, toolbar.width, toolbar.height, 2, null);
        font.drawString("W", toolbar.x + 3, toolbar.y + 4, text);
        font.drawString("H", toolbar.x + 3, toolbar.y + 17, text);
        for (int action = 0; action < 4; action++) {
            boolean column = action < 2, add = action % 2 == 1;
            Rect button = gridButton(toolbar, action);
            boolean available = grid.resize(column, add, id -> pin(gui, id) != null) != null;
            boolean hover = button.contains(x, y);
            PanelChrome.raisedButton(button, available, hover);
            font.drawString(
                add ? "+" : "-",
                button.x + 4,
                button.y + 2,
                available ? text : InventoryGuiTheme.get().panelShadow);
            if (hover) drawTooltip(
                gui,
                I18n.format("dynamicinventory.ae2pins.grid." + (add ? "add" : "remove") + (column ? "Column" : "Row")),
                !available && !add ? I18n.format("dynamicinventory.ae2pins.grid.edge") : null,
                x,
                y);
        }
    }

    private static AePinsGrid editableGrid(Rect area, List<Entry> entries) {
        return AePinsGrid.of(
            gridColumns(area),
            entries.stream()
                .mapToInt(entry -> entry.index)
                .toArray());
    }

    private void resizeGrid(GuiScreen gui, DetachedChestPanel host, Rect area, List<Entry> entries, int action) {
        boolean column = action < 2, add = action % 2 == 1;
        AePinsGrid next = editableGrid(area, entries).resize(column, add, id -> pin(gui, id) != null);
        if (next == null) return;
        InventoryViewSettings.setPinsGrid(next);
        if (!add) return;
        int w = column && next.columns > columns(area) ? Math.min(450, area.width + CELL) : area.width;
        int h = !column && next.slots.length / next.columns > rows(area) ? Math.min(450, area.height + CELL)
            : area.height;
        Rect requested = new Rect(area.x, area.y, w, h);
        Rect fitted = InventoryPanelLayout.snapDrag(requested, viewport(gui), obstacles(gui, host));
        if (fitted != null && fitted.x == area.x && fitted.y == area.y && (w != area.width || h != area.height))
            saveGeometry(gui, host, requested, true);
    }

    private static void saveGeometry(GuiScreen gui, DetachedChestPanel host, Rect bounds, boolean resize) {
        Rect machine = host.machineBounds();
        Rect preferred = PlayerPanelProfiles.requestedPinsBounds(host.key(), machine);
        int width = resize ? Math.max(72, bounds.width) : preferred.width;
        int height = resize ? Math.max(43, bounds.height) : preferred.height;
        try {
            PlayerPanelProfiles.savePins(
                host.key(),
                new PinsPanelGeometry(
                    new Rect(bounds.x, bounds.y, width, height),
                    new PanelLayoutReference(machine, viewport(gui))));
        } catch (IOException | IllegalArgumentException failure) {
            DynamicInventory.LOG.warn("Could not save AE2 Pins layout for {}", host.key(), failure);
        }
    }

    private void drawReturnWidget(GuiScreen gui, Rect widget, int x, int y, int text) {
        boolean hover = widget.contains(x, y) && optionsScreen != gui;
        boolean active = InventoryViewSettings.pinsReturnMatching;
        PanelChrome.raisedButton(widget, true, hover);
        int ink = active ? 0xffffcb77 : text;
        int cx = widget.x + 6, cy = widget.y + 5;
        drawRect(cx, cy - 3, cx + 1, cy + 3, ink);
        drawRect(cx - 1, cy + 1, cx + 2, cy + 2, ink);
        drawRect(cx - 2, cy, cx + 3, cy + 1, ink);
        drawRect(cx - 4, cy + 3, cx + 5, cy + 4, ink);
        if (hover) drawTooltip(
            gui,
            I18n.format("dynamicinventory.ae2pins.return." + (active ? "on" : "off")),
            I18n.format("dynamicinventory.ae2pins.return.hint"),
            x,
            y);
    }

    private static void drawTooltip(GuiScreen gui, String name, String hint, int x, int y) {
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_LIGHTING);
        var font = Minecraft.getMinecraft().fontRenderer;
        int width = font.getStringWidth(name);
        if (hint != null) width = Math.max(width, font.getStringWidth(hint));
        int left = Math.max(3, Math.min(gui.width - width - 6, x + 9));
        int height = hint == null ? 11 : 22;
        int top = Math.max(3, y - height - 2);
        drawRect(left - 3, top - 3, left + width + 3, top + height, 0xf0100010);
        font.drawStringWithShadow(name, left, top, 0xffffff);
        if (hint != null) font.drawStringWithShadow(hint, left, top + 11, 0xffffff);
    }

    public static boolean mouse(GuiScreen gui, int x, int y, int button, boolean down) {
        if (button == 0 && !down && PANEL.shiftClickScreen == gui) PANEL.shiftClickReleased = true;
        if (button >= 0 && down && (button != 0 || !GuiScreen.isShiftKeyDown() || bulkModifier()))
            PANEL.clearShiftClick();
        DetachedChestPanel host = DetachedChestPanel.of(gui);
        if (host == null || PanelSettingsOverlay.isOpen(gui)) return false;
        if (PANEL.pointer(gui, host, x, y, button, down)) {
            if (button >= 0 && down) PANEL.clearShiftClick();
            return true;
        }
        if (PANEL.enabled && button < 0
            && Mouse.getEventDWheel() != 0
            && gui instanceof GuiContainer container
            && PackClientCompatibility.usesItemWheelTransfer(container)
            && PANEL.transferPlayerWheel(
                container,
                host,
                host.playerSlotAt(x, y, false),
                y,
                wheelSteps(Mouse.getEventDWheel())))
            return true;
        if (button == 0 && down && gui instanceof GuiContainer container) {
            int slotId = host.playerSlotAt(x, y, false);
            if (slotId < 0) PANEL.clearShiftClick();
            if (PANEL.playerClick(container, slotId, true)) {
                PANEL.pressedScreen = gui;
                return true;
            }
        }
        return false;
    }

    public static boolean depositMatching(GuiScreen gui, int slotId) {
        return gui instanceof GuiContainer container && PANEL.playerClick(container, slotId, false);
    }

    public static boolean protects(GuiScreen gui, InventoryPlayer inventory, int slotId, int mode) {
        return gui instanceof GuiContainer container && PANEL.prediction.protects(container, inventory, slotId, mode);
    }

    private void clearShiftClick() {
        shiftClickScreen = null;
        shiftClickItem = null;
    }

    private boolean playerClick(GuiContainer container, int slotId, boolean trackDoubleClick) {
        boolean shift = GuiScreen.isShiftKeyDown();
        boolean bulk = bulkModifier();
        boolean space = Keyboard.isKeyDown(Keyboard.KEY_SPACE);
        GuiScreen gui = container;
        if (space && gui instanceof TerminalPinsView && spaceTransferNative(container, slotId)) return true;
        if (!enabled || !shift && !bulk
            || DetachedChestPanel.of(gui) == null
            || PanelSettingsOverlay.isOpen(gui)
            || ClientInputGuard.isPending(gui)
            || slotId < 0
            || slotId >= container.inventorySlots.inventorySlots.size()) return false;
        var player = Minecraft.getMinecraft().thePlayer;
        if (player == null || player.openContainer != container.inventorySlots
            || prediction.cursor(container, player.inventory) != null) return false;
        var slot = container.inventorySlots.getSlot(slotId);
        // Bogo Sorter suppresses canTakeStack on the client. The server checks it before returning the stack.
        if (PinnedItemTransfer.playerIndex(player, slot) < 0) return false;
        ItemStack stack = prediction.slot(container, player.inventory, slot);
        if (shift && !bulk && AppliedEnergisticsPatternTransfers.canLoadPattern(container.inventorySlots, stack))
            return false;
        if (gui instanceof TerminalPinsView) {
            if (bulk && stack != null) {
                move(container, 0, 0, Transfer.DEPOSIT_TERMINAL_ALL, slotId, AEItemStack.create(stack));
                return true;
            }
            if (shift && trackDoubleClick) {
                // ModularUI's pre-event and NEI's dispatcher can see the same mouse press.
                if (shiftClickScreen == container && !shiftClickReleased) return false;
                long now = Minecraft.getSystemTime();
                boolean repeated = shiftClickScreen == container && shiftClickSlot == slotId
                    && shiftClickItem != null
                    && now - shiftClickAt < 250;
                if (repeated) {
                    IAEItemStack item = AEItemStack.create(shiftClickItem);
                    if (stack == null || item.isSameType(stack)) {
                        clearShiftClick();
                        for (var source : PinnedItemTransfer.sourceSlots(player, container.inventorySlots, false)) {
                            // The first click can empty this slot on the server before its update reaches us.
                            if (source == null || source.slotNumber == slotId) continue;
                            ItemStack candidate = prediction.slot(container, player.inventory, source);
                            if (candidate != null && item.isSameType(candidate)) {
                                move(container, 0, 0, Transfer.DEPOSIT_TERMINAL_ALL, source.slotNumber, item);
                                return true;
                            }
                        }
                        return true;
                    }
                }
                shiftClickScreen = container;
                shiftClickSlot = slotId;
                shiftClickAt = now;
                shiftClickItem = ItemStack.copyItemStack(stack);
                shiftClickReleased = false;
            }
        }
        if (!InventoryViewSettings.pinsReturnMatching || stack == null) return false;
        var view = sourceTerminal(gui);
        for (Entry entry : entries(gui)) {
            if (entry.stack instanceof IAEItemStack pin && pin.isSameType(stack)) {
                int[] scope = space ? DetachedChestPanel.of(gui)
                    .spaceTransferSlots(slotId) : null;
                if (scope != null) {
                    movePanel(container, view == null ? epoch : 0, entry.index, slotId, pin, scope);
                    return true;
                }
                move(
                    container,
                    view == null ? epoch : 0,
                    entry.index,
                    bulk ? Transfer.DEPOSIT_ALL : Transfer.DEPOSIT_SLOT,
                    slotId,
                    pin);
                return true;
            }
        }
        return false;
    }

    public static boolean spaceTransferNative(GuiContainer gui, int sourceId) {
        var host = DetachedChestPanel.of(gui);
        if (host == null || PanelSettingsOverlay.isOpen(gui)) return false;
        int[] slots = host.spaceTransferSlots(sourceId);
        if (slots == null) return false;
        PANEL.movePanel(gui, 0, 0, sourceId, null, slots);
        return true;
    }

    private void movePanel(GuiContainer gui, long source, int index, int clicked, IAEItemStack item, int[] slots) {
        if (slots.length == 0) return;
        var inventory = Minecraft.getMinecraft().thePlayer.inventory;
        long id = prediction
            .predict(gui, inventory, index, Transfer.SPACE_PANEL, clicked, item, slot -> pin(gui, slot), slots);
        AePinsChannel.transferPanel(
            source,
            gui.inventorySlots.windowId,
            index,
            clicked,
            id,
            dev.gilbe.dynamicinventory.inventory.InventoryLayout.of(inventory)
                .revision(),
            slots);
    }

    private void move(GuiContainer gui, long source, int index, Transfer action, int argument, IAEItemStack item) {
        var player = Minecraft.getMinecraft().thePlayer;
        if (argument == 0 && (action == Transfer.SCROLL_ITEMS || action == Transfer.SCROLL_CURSOR)) return;
        int amount = action == Transfer.SCROLL_ITEMS || action == Transfer.SCROLL_CURSOR
            ? Math.max(-64, Math.min(64, argument))
            : argument;
        long id = prediction.predict(gui, player.inventory, index, action, amount, item, slot -> pin(gui, slot), null);
        AePinsChannel.transfer(source, gui.inventorySlots.windowId, index, action, amount, id);
    }

    private IAEStack<?> pin(GuiScreen gui, int index) {
        var view = sourceTerminal(gui);
        if (view != null) return view.dynamicinventory$pin(index);
        return snapshot != null && snapshot.available() ? snapshot.stacks()[index] : null;
    }

    private static boolean bulkModifier() {
        return altDown() || Keyboard.isKeyDown(Keyboard.KEY_SPACE);
    }

    private static boolean altDown() {
        return Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU);
    }

    private static int wheelSteps(int wheel) {
        return LWJGL3 ? wheel : Integer.signum(wheel) * (1 + (Math.abs(wheel) - 1) / 120);
    }

    public static boolean mouse(GuiScreen gui) {
        Minecraft client = Minecraft.getMinecraft();
        int x = Mouse.getEventX() * gui.width / client.displayWidth;
        int y = gui.height - Mouse.getEventY() * gui.height / client.displayHeight - 1;
        return mouse(gui, x, y, Mouse.getEventButton(), Mouse.getEventButtonState());
    }

    private boolean pointer(GuiScreen gui, DetachedChestPanel host, int x, int y, int mouseButton, boolean down) {
        if (gesture != null && gestureScreen != gui) clearGesture();
        if (mouseButton == 0 && !down && pressedScreen == gui) {
            pressedScreen = null;
            if (scrollingScreen == gui) dragScrollTo(gui, host, y);
            scrollingScreen = null;
            if (gesture != null) {
                move(gui, host, x, y);
                Rect settled = resizing ? lastBounds
                    : InventoryPanelLayout.snapDrag(lastBounds, viewport(gui), obstacles(gui, host));
                if (settled != null && (settled.x != gesture.x || settled.y != gesture.y
                    || settled.width != gesture.width
                    || settled.height != gesture.height)) saveGeometry(gui, host, settled, resizing);
            }
            clearGesture();
            return true;
        }
        if (gesture != null) {
            if (mouseButton < 0) move(gui, host, x, y);
            return true;
        }
        if (scrollingScreen == gui) {
            if (mouseButton < 0) dragScrollTo(gui, host, y);
            return true;
        }
        if (mouseButton == 1 && button(host).contains(x, y)) {
            if (down) optionsScreen = optionsScreen == gui ? null : gui;
            return true;
        }
        if (optionsScreen == gui) {
            Rect menu = optionsBounds(gui, host);
            if (menu.contains(x, y)) {
                if (mouseButton == 0 && down) {
                    pressedScreen = gui;
                    if (optionsControl(menu).contains(x, y)) {
                        InventoryViewSettings.setPinsWirelessOnly(!InventoryViewSettings.pinsWirelessOnly);
                        prediction.clear();
                        scroll = columnScroll = 0;
                        TerminalPinsView view = terminal(gui);
                        if (view != null) view.dynamicinventory$rebuildPins();
                    }
                }
                return true;
            }
            if (mouseButton >= 0 && down) optionsScreen = null;
        }
        if (mouseButton == 0 && down && button(host).contains(x, y)) {
            pressedScreen = gui;
            toggle(gui);
            return true;
        }
        Rect area = enabled ? bounds(gui, host) : null;
        if (area == null) return false;
        if (altDown() && gridToolbar(gui, area).contains(x, y)) {
            var entries = entries(gui);
            if (!entries.isEmpty()) {
                if (mouseButton == 0 && down) {
                    pressedScreen = gui;
                    Rect toolbar = gridToolbar(gui, area);
                    for (int action = 0; action < 4; action++) if (gridButton(toolbar, action).contains(x, y)) {
                        resizeGrid(gui, host, area, entries, action);
                        break;
                    }
                }
                return true;
            }
        }
        if (!area.contains(x, y)) return false;
        int wheel = mouseButton < 0 ? Mouse.getEventDWheel() : 0;
        List<Entry> entries = entries(gui);
        int columns = gridColumns(area), rows = rows(area);
        int max = maxScroll(entries.size(), columns, rows);
        int horizontalMax = Math.max(0, columns - columns(area));
        scroll = Math.min(scroll, max);
        columnScroll = Math.min(columnScroll, horizontalMax);
        Entry entry = entryAt(area, entries, x, y);
        TerminalPinsView view = sourceTerminal(gui);
        if (wheel != 0) {
            int steps = wheelSteps(wheel);
            if (horizontalMax > 0 && altDown() && entry == null) {
                columnScroll = Math.max(0, Math.min(horizontalMax, columnScroll - steps));
                return true;
            }
            if (!transferOnWheel(gui, host, area, view, entry, steps))
                scroll = Math.max(0, Math.min(max, scroll - steps));
            return true;
        }
        if (mouseButton < 0 || !down) return true;
        if (mouseButton == 0) pressedScreen = gui;
        if (headerWidget(area, 0).contains(x, y)) {
            if (mouseButton == 0) InventoryViewSettings.togglePinsReturnMatching();
            return true;
        }
        Rect horizontal = columnTrack(area);
        if (mouseButton == 0 && horizontal != null && horizontal.contains(x, y)) {
            columnScroll = Math.round(horizontalMax * (x - horizontal.x) / (float) (horizontal.width - 1));
            return true;
        }
        Rect track = scrollTrack(area, entries.size());
        if (mouseButton == 0 && track != null && track.contains(x, y)) {
            Rect thumb = scrollThumb(area, track, entries.size());
            scrollGrab = thumb.contains(x, y) ? y - thumb.y : thumb.height / 2;
            scrollingScreen = gui;
            scrollTo(area, track, entries.size(), y);
            return true;
        }
        boolean corner = PanelChrome.corner(area, x, y) == 10;
        if (mouseButton == 0 && (y < area.y + HEADER || corner)) {
            pressX = lastX = x;
            pressY = lastY = y;
            gesture = lastBounds = area;
            gestureScreen = gui;
            resizing = corner;
            return true;
        }
        if (entry != null && entry.index >= 0 && gui instanceof GuiContainer container)
            clickEntry(container, view, entry, mouseButton);
        return true;
    }

    private void clickEntry(GuiContainer gui, TerminalPinsView view, Entry entry, int button) {
        if (entry.stack == null && entry.index < PinList.PLAYER_OFFSET) return;
        var inventory = Minecraft.getMinecraft().thePlayer.inventory;
        ItemStack cursor = prediction.cursor(gui, inventory);
        boolean shift = GuiScreen.isShiftKeyDown();
        int window = gui.inventorySlots.windowId;
        if (button == 2 && cursor == null && entry.stack instanceof IAEItemStack item && item.isCraftable()) {
            AePinsChannel.transfer(view == null ? epoch : 0, window, entry.index, Transfer.CRAFT, 0);
            return;
        }
        boolean remove = shift && button == 1 && cursor == null;
        boolean bulk = bulkModifier();
        if (entry.stack instanceof IAEItemStack item && (button == 0 || button == 1)
            && !remove
            && (view == null || !GuiScreen.isCtrlKeyDown())) {
            boolean matchingCursor = cursor != null && item.isSameType(cursor);
            Transfer action = bulk ? matchingCursor ? Transfer.PICKUP : Transfer.ALL
                : shift && button == 0 ? Transfer.STACK : button == 0 ? Transfer.PICKUP : Transfer.SPLIT;
            boolean inventoryMove = action == Transfer.ALL || action == Transfer.STACK;
            if ((view != null || remote)
                && (inventoryMove || matchingCursor || cursor == null && item.getStackSize() > 0)) {
                move(gui, view == null ? epoch : 0, entry.index, action, 0, item);
                return;
            }
        }
        if (view != null) view.dynamicinventory$clickPin(entry.index, button);
        else if (remote && (remove && entry.stack != null || button == 0 && entry.stack == null && cursor != null))
            AePinsChannel.edit(epoch, window, entry.index, remove);
    }

    private boolean transferOnWheel(GuiScreen gui, DetachedChestPanel host, Rect area, TerminalPinsView view,
        Entry entry, int steps) {
        if (entry == null || !(entry.stack instanceof IAEItemStack item)
            || !(gui instanceof GuiContainer container)
            || (view == null && !remote)) return false;
        if (GuiScreen.isShiftKeyDown()) {
            var inventory = Minecraft.getMinecraft().thePlayer.inventory;
            var cursor = prediction.cursor(container, inventory);
            if (view != null && steps > 0 && cursor != null && !item.isSameType(cursor))
                view.dynamicinventory$scrollPin(entry.index, steps);
            else move(container, view == null ? epoch : 0, entry.index, Transfer.SCROLL_CURSOR, steps, item);
            return true;
        }
        Rect inventory = host.playerBounds();
        int amount = PackClientCompatibility
            .pinsTransferAmount(steps, inventory.y + inventory.height / 2 < area.y + area.height / 2);
        if (amount == 0 || amount > 0 && item.getStackSize() <= 0) return false;
        move(container, view == null ? epoch : 0, entry.index, Transfer.SCROLL_ITEMS, amount, item);
        return true;
    }

    public static boolean mouseTweaksWheel(GuiScreen screen, Slot slot, int wheel) {
        if (!PANEL.enabled || wheel == 0
            || slot == null
            || !(screen instanceof GuiContainer gui)
            || !(gui instanceof ContainerPanelAccess access)
            || PanelSettingsOverlay.isOpen(gui)) return false;
        var host = DetachedChestPanel.of(gui);
        if (host == null) return false;
        int x = access.dynamicinventory$left() + slot.xDisplayPosition + 8;
        int y = access.dynamicinventory$top() + slot.yDisplayPosition + 8;
        return host.playerSlotAt(x, y, false) == slot.slotNumber
            && PANEL.transferPlayerWheel(gui, host, slot.slotNumber, y, wheelSteps(wheel));
    }

    private boolean transferPlayerWheel(GuiContainer gui, DetachedChestPanel host, int slotId, int y, int steps) {
        if (!enabled || GuiScreen.isShiftKeyDown()
            || altDown()
            || ClientInputGuard.isPending(gui)
            || slotId < 0
            || PackClientCompatibility.neiDragging()) return false;
        var view = sourceTerminal(gui);
        if (view == null && (snapshot == null || !snapshot.available())) return false;
        var player = Minecraft.getMinecraft().thePlayer;
        if (player == null || player.openContainer != gui.inventorySlots
            || prediction.cursor(gui, player.inventory) != null) return false;
        Rect area = bounds(gui, host);
        if (area == null) return false;
        int amount = PackClientCompatibility.pinsTransferAmount(steps, area.y + area.height / 2 < y);
        if (amount == 0) return false;
        var slot = gui.inventorySlots.getSlot(slotId);
        if (!slot.func_111238_b() || PinnedItemTransfer.playerIndex(player, slot) < 0) return false;
        ItemStack stack = prediction.slot(gui, player.inventory, slot);
        if (stack == null) return false;
        for (Entry entry : entries(gui)) {
            if (!(entry.stack instanceof IAEItemStack pin) || !pin.isSameType(stack)) continue;
            if (amount < 0 && (prediction.renderPin(gui, pin)
                .getStackSize() <= 0 || PinnedItemTransfer.scrollPullRoom(slot, stack, pin) == 0)) continue;
            move(
                gui,
                view == null ? epoch : 0,
                entry.index,
                amount > 0 ? Transfer.SCROLL_DEPOSIT_SLOT : Transfer.SCROLL_WITHDRAW_SLOT,
                AePinsChannel.scrollSlotArgument(slotId, Math.abs(amount)),
                pin);
            return true;
        }
        return false;
    }

    private void updateDragFrame(GuiScreen gui, DetachedChestPanel host, int x, int y) {
        if (gesture == null || gestureScreen != gui) return;
        if (!Display.isActive() || ClientInputGuard.isPending(gui)) {
            clearGesture();
            return;
        }
        // Release commits the position using its own final coordinates.
        if (Mouse.isButtonDown(0)) move(gui, host, x, y);
    }

    private void move(GuiScreen gui, DetachedChestPanel host, int x, int y) {
        if (x == lastX && y == lastY) return;
        lastX = x;
        lastY = y;
        Rect viewport = viewport(gui);
        List<Rect> obstacles = obstacles(gui, host);
        if (resizing) {
            Rect requested = new Rect(
                gesture.x,
                gesture.y,
                Math.max(72, Math.min(450, gesture.width + x - pressX)),
                Math.max(43, Math.min(450, gesture.height + y - pressY)));
            lastBounds = InventoryPanelLayout.constrainGesture(lastBounds, requested, viewport, obstacles, 72, 43);
        } else {
            Rect requested = new Rect(gesture.x + x - pressX, gesture.y + y - pressY, gesture.width, gesture.height);
            InventoryPanelLayout.Alignment aligned = InventoryPanelLayout.alignDrag(requested, viewport, obstacles, 3);
            lastBounds = aligned.bounds;
            guideX = aligned.guideX;
            guideY = aligned.guideY;
        }
    }

    private void clearGesture() {
        gesture = lastBounds = null;
        gestureScreen = null;
        guideX = guideY = Integer.MIN_VALUE;
    }

    private List<Entry> entries(GuiScreen gui) {
        List<Entry> result = new ArrayList<>();
        TerminalPinsView view = sourceTerminal(gui);
        IAEStack<?>[] remotePins = remote && snapshot != null ? snapshot.stacks() : null;
        if (view == null && remotePins == null) return result;
        IntFunction<IAEStack<?>> pins = view != null ? view::dynamicinventory$pin : index -> remotePins[index];
        AePinsGrid saved = InventoryViewSettings.pinsGrid;
        if (saved != null) {
            for (int id : saved.includeOccupied(index -> pins.apply(index) != null).slots)
                result.add(new Entry(id, id < 0 ? null : pins.apply(id)));
            return result;
        }
        int crafting = view != null ? view.dynamicinventory$pinSlots(true) : snapshot.pinSlots(true);
        int player = view != null ? view.dynamicinventory$pinSlots(false) : snapshot.pinSlots(false);
        boolean playerFirst = AEConfig.instance.pinSectionOrder == PinSectionOrder.PLAYER_FIRST;
        if (playerFirst) addSection(result, pins, PinList.PLAYER_OFFSET, PinList.PLAYER_SLOTS, player);
        addSection(result, pins, 0, PinList.CRAFTING_SLOTS, crafting);
        if (!playerFirst) addSection(result, pins, PinList.PLAYER_OFFSET, PinList.PLAYER_SLOTS, player);
        return result;
    }

    private static void addSection(List<Entry> result, IntFunction<IAEStack<?>> pins, int start, int limit,
        int configured) {
        boolean editable = start == PinList.PLAYER_OFFSET;
        int last = start + limit - 1;
        while (last >= start && pins.apply(last) == null) last--;
        int required = Math.max(configured, Math.max(last + 1 - start, editable ? 1 : 0));
        int end = start + Math.min(limit, (required + 8) / 9 * 9);
        for (int index = start; index < end; index++) result.add(new Entry(index, pins.apply(index)));
    }

    private Entry entryAt(Rect area, List<Entry> entries, int x, int y) {
        int gridX = x - area.x - PADDING, gridY = y - area.y - HEADER, columns = gridColumns(area);
        if (gridX < 0 || gridY < 0 || gridX >= Math.min(columns(area), columns) * CELL || gridY >= rows(area) * CELL)
            return null;
        int index = (scroll + gridY / CELL) * columns + columnScroll + gridX / CELL;
        return index < entries.size() ? entries.get(index) : null;
    }

    private static Rect scrollTrack(Rect area, int count) {
        int rows = rows(area);
        return maxScroll(count, gridColumns(area), rows) == 0 ? null
            : new Rect(area.right() - SCROLLBAR - 2, area.y + HEADER, SCROLLBAR, rows * CELL - 1);
    }

    private static Rect columnTrack(Rect area) {
        int visible = columns(area);
        return gridColumns(area) <= visible ? null
            : new Rect(area.x + PADDING, area.bottom() - 5, visible * CELL - 1, 4);
    }

    private Rect scrollThumb(Rect area, Rect track, int count) {
        int rows = rows(area), max = maxScroll(count, gridColumns(area), rows);
        int height = Math.max(6, track.height * rows / (rows + max));
        return new Rect(
            track.x,
            track.y + Math.round((track.height - height) * (float) scroll / max),
            track.width,
            height);
    }

    private void scrollTo(Rect area, Rect track, int count, int y) {
        int max = maxScroll(count, gridColumns(area), rows(area));
        int travel = track.height - scrollThumb(area, track, count).height;
        if (travel > 0)
            scroll = Math.max(0, Math.min(max, Math.round((y - track.y - scrollGrab) * (float) max / travel)));
    }

    private void dragScrollTo(GuiScreen gui, DetachedChestPanel host, int y) {
        Rect area = bounds(gui, host);
        if (area == null) return;
        int count = entries(gui).size();
        Rect track = scrollTrack(area, count);
        if (track != null) scrollTo(area, track, count, y);
    }

    private static int columns(Rect area) {
        return Math.max(1, (area.width - PADDING * 2) / CELL);
    }

    private static int gridColumns(Rect area) {
        AePinsGrid saved = InventoryViewSettings.pinsGrid;
        return saved == null ? columns(area) : saved.columns;
    }

    private static int rows(Rect area) {
        return Math.max(1, (area.height - HEADER - 4) / CELL);
    }

    private static int maxScroll(int count, int columns, int rows) {
        return Math.max(0, (count + columns - 1) / columns - rows);
    }

    private static final class Entry {

        final int index;
        final IAEStack<?> stack;

        Entry(int index, IAEStack<?> stack) {
            this.index = index;
            this.stack = stack;
        }
    }
}
