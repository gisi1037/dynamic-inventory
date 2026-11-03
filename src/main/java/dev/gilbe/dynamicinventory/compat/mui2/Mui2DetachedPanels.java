package dev.gilbe.dynamicinventory.compat.mui2;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.resources.I18n;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.factory.PlayerInventoryGuiData;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularContainer;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.utils.item.PlayerMainInvWrapper;
import com.cleanroommc.modularui.widget.sizer.Area;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;

import dev.gilbe.dynamicinventory.client.ContainerPanelAccess;
import dev.gilbe.dynamicinventory.client.DetachedChestPanel;
import dev.gilbe.dynamicinventory.client.InventoryPanelLayout.Rect;
import dev.gilbe.dynamicinventory.client.PanelSettingsOverlay;
import dev.gilbe.dynamicinventory.client.PlayerPanelProfiles;
import dev.gilbe.dynamicinventory.inventory.InventoryLayout;
import dev.gilbe.dynamicinventory.inventory.InventoryShape;

/**
 * Projects the existing MUI2 player widgets into shared inventory panels. Nothing is reparented,
 * registered, copied or disabled: the same ItemSlotSH, ModularSlot and native transfer group survive.
 */
public final class Mui2DetachedPanels {

    private final Map<IWidget, Area> originalAreas = new IdentityHashMap<>();
    private PlayerInventoryWidgetGroup group;
    private ModularPanel owner;
    private GuiContainer host;
    private DetachedChestPanel controller;
    private final List<ItemSlot> widgets = new ArrayList<>();
    private int[] slotIds;
    private int screenWidth, screenHeight;
    private boolean updating;

    /** Undo only presentation overrides before the framework runs its own layout or update. */
    public void beforeLayout() {
        restoreAreas();
    }

    private void restoreAreas() {
        for (Map.Entry<IWidget, Area> entry : originalAreas.entrySet()) {
            if (!entry.getKey()
                .isValid()) continue;
            Area target = entry.getKey()
                .getArea(), saved = entry.getValue();
            target.set(saved);
            target.rx = saved.rx;
            target.ry = saved.ry;
        }
        originalAreas.clear();
    }

    public void close() {
        restoreAreas();
        if (group != null) group.setDetachedPresentation(false);
        if (controller != null) controller.cancel();
        if (host != null && ((ContainerPanelAccess) host).dynamicinventory$floating() == controller)
            ((ContainerPanelAccess) host).dynamicinventory$floating(null);
        controller = null;
        group = null;
        owner = null;
        host = null;
        widgets.clear();
        slotIds = null;
    }

    /** Called after native resizing, before hover calculation, rendering and mouse dispatch. */
    public void update(ModularScreen screen) {
        if (updating || screen.isOverlay()
            || screen.getScreenWrapper() == null
            || !(screen.getScreenWrapper()
                .getGuiScreen() instanceof GuiContainer gui)
            || !(gui.inventorySlots instanceof ModularContainer)) return;
        updating = true;
        try {
            restoreAreas();
            PlayerInventoryWidgetGroup candidate = findGroup(screen);
            if (candidate == null) {
                close();
                return;
            }
            if (host != gui || group != candidate
                || screenWidth != gui.width
                || screenHeight != gui.height
                || !validSlots()) {
                close();
                host = gui;
                group = candidate;
                owner = group.getPanel();
                if (!bindSlots()) {
                    close();
                    return;
                }
                screenWidth = gui.width;
                screenHeight = gui.height;
                String profileKey = profileKey(screen);
                if (PlayerPanelProfiles.resolve(profileKey).detached) compactMachineSiblings();
                Rect machine = machineBounds();
                controller = new DetachedChestPanel(
                    host,
                    machine,
                    attachedBounds(),
                    2,
                    2,
                    0,
                    2,
                    auxiliary(screen),
                    slotIds,
                    profileKey);
                ((ContainerPanelAccess) host).dynamicinventory$floating(controller);
            }
            if (controller.displayedProfile(dev.gilbe.dynamicinventory.client.PlayerPanelSet.MAIN).detached)
                compactMachineSiblings();
            Rect machine = machineBounds();
            controller.hostBounds(machine, attachedBounds(), auxiliary(screen));
            controller.attachedControl(controlBounds(screen, machine));
            boolean detached = controller.detached();
            group.setDetachedPresentation(detached);
            if (detached) {
                remember(owner);
                owner.getArea().height = machine.height;
                for (IWidget ancestor = group.getParent(); ancestor != null
                    && ancestor != owner; ancestor = ancestor.getParent()) {
                    Area area = ancestor.getArea();
                    if (area.y + area.height > machine.bottom()) {
                        remember(ancestor);
                        area.height = Math.max(1, machine.bottom() - area.y);
                    }
                }
                for (IWidget label : inventoryLabels()) hide(label);
                // Native slots receive display coordinates only; their IDs and enabled state are untouched.
                controller.position();
                dev.gilbe.dynamicinventory.client.HotbarBankPanel.layout(host);
                ContainerPanelAccess access = (ContainerPanelAccess) host;
                for (ItemSlot widget : widgets) {
                    Slot slot = widget.getSlot();
                    move(
                        widget,
                        access.dynamicinventory$left() + slot.xDisplayPosition - 1,
                        access.dynamicinventory$top() + slot.yDisplayPosition - 1);
                }
            }
            screen.getScreenWrapper()
                .updateGuiArea(
                    screen.getMainPanel()
                        .getArea());
        } finally {
            updating = false;
        }
    }

    private PlayerInventoryWidgetGroup findGroup(ModularScreen screen) {
        for (ModularPanel panel : screen.getPanelManager()
            .getOpenPanels()) {
            ArrayDeque<IWidget> pending = new ArrayDeque<>();
            pending.add(panel);
            while (!pending.isEmpty()) {
                IWidget widget = pending.removeFirst();
                if (widget instanceof PlayerInventoryWidgetGroup player && player.isValid()) return player;
                pending.addAll(widget.getChildren());
            }
        }
        return null;
    }

    private boolean bindSlots() {
        InventoryShape shape = InventoryLayout.of(Minecraft.getMinecraft().thePlayer.inventory);
        slotIds = new int[shape.mainSlots()];
        Arrays.fill(slotIds, -1);
        ArrayDeque<IWidget> pending = new ArrayDeque<>();
        pending.add(group);
        while (!pending.isEmpty()) {
            IWidget widget = pending.removeFirst();
            if (widget instanceof ItemSlot item && item.isSynced()) {
                Slot slot = item.getSlot();
                if (!(item.getSlot()
                    .getItemHandler() instanceof PlayerMainInvWrapper inventory)
                    || inventory.getInventoryPlayer() != Minecraft.getMinecraft().thePlayer.inventory) return false;
                int logical = shape.ordinaryIndex(slot.getSlotIndex());
                if (logical < 0 || slotIds[logical] >= 0
                    || slot.slotNumber < 0
                    || slot.slotNumber >= host.inventorySlots.inventorySlots.size()
                    || host.inventorySlots.getSlot(slot.slotNumber) != slot) return false;
                slotIds[logical] = slot.slotNumber;
                widgets.add(item);
            }
            pending.addAll(widget.getChildren());
        }
        for (int id : slotIds) if (id < 0) return false;
        return true;
    }

    private boolean validSlots() {
        if (host == null || slotIds == null
            || slotIds.length != InventoryLayout.of(Minecraft.getMinecraft().thePlayer.inventory)
                .mainSlots())
            return false;
        for (ItemSlot widget : widgets) if (!widget.isValid() || widget.getSlot().slotNumber < 0
            || widget.getSlot().slotNumber >= host.inventorySlots.inventorySlots.size()
            || host.inventorySlots.getSlot(widget.getSlot().slotNumber) != widget.getSlot()) return false;
        return true;
    }

    private Rect attachedBounds() {
        Area area = group.getArea();
        return new Rect(area.x - 7, area.y - 14, area.width + 14, area.height + 21);
    }

    private List<IWidget> inventoryLabels() {
        List<IWidget> result = new ArrayList<>();
        ArrayDeque<IWidget> pending = new ArrayDeque<>();
        pending.add(owner);
        while (!pending.isEmpty()) {
            IWidget widget = pending.removeFirst();
            if (widget == group) continue;
            if (widget instanceof TextWidget<?>text && text.getKey()
                .getFormatted()
                .equals(I18n.format("container.inventory"))
                && widget.getArea().y >= group.getArea().y - 18
                && widget.getArea().y < group.getArea().y) result.add(widget);
            pending.addAll(widget.getChildren());
        }
        return result;
    }

    private Rect machineBounds() {
        Area area = owner.getArea();
        int bottom = Math.max(area.y + 24, group.getArea().y - 6);
        List<IWidget> labels = inventoryLabels();
        ArrayDeque<IWidget> pending = new ArrayDeque<>();
        pending.addAll(owner.getChildren());
        while (!pending.isEmpty()) {
            IWidget widget = pending.removeFirst();
            if (widget == group || labels.contains(widget) || !widget.isEnabled()) continue;
            if (widget.getChildren()
                .isEmpty()) bottom = Math.max(bottom, widget.getArea().y + widget.getArea().height + 5);
            else pending.addAll(widget.getChildren());
        }
        return new Rect(area.x, area.y, area.width, Math.min(area.height, Math.max(24, bottom - area.y)));
    }

    /** Inventory-row siblings such as multiblock controller buttons must lose the inventory's added empty space. */
    private void compactMachineSiblings() {
        Area inventory = group.getArea();
        IWidget branch = group;
        for (IWidget parent = group.getParent(); parent != null; parent = parent.getParent()) {
            for (IWidget sibling : parent.getChildren()) {
                if (sibling == branch || !sibling.isEnabled()) continue;
                Area area = sibling.getArea();
                if (area.x < inventory.x + inventory.width - 2 && area.x + area.width > inventory.x + 2) continue;
                int first = Integer.MAX_VALUE, last = Integer.MIN_VALUE;
                ArrayDeque<IWidget> pending = new ArrayDeque<>();
                pending.add(sibling);
                List<IWidget> descendants = new ArrayList<>();
                while (!pending.isEmpty()) {
                    IWidget widget = pending.removeFirst();
                    descendants.add(widget);
                    if (!widget.isEnabled()) continue;
                    if (widget.getChildren()
                        .isEmpty()) {
                        first = Math.min(first, widget.getArea().y);
                        last = Math.max(last, widget.getArea().y + widget.getArea().height);
                    } else pending.addAll(widget.getChildren());
                }
                if (first == Integer.MAX_VALUE || first <= inventory.y || last > inventory.y + inventory.height + 4)
                    continue;
                int delta = inventory.y - first;
                int top = Math.min(area.y, inventory.y);
                move(sibling, area.x, top);
                area.height = Math.max(1, last + delta - top);
                for (IWidget child : descendants)
                    if (child != sibling) move(child, child.getArea().x, child.getArea().y + delta);
            }
            if (parent == owner) break;
            branch = parent;
        }
    }

    private List<Rect> auxiliary(ModularScreen screen) {
        List<Rect> result = new ArrayList<>();
        for (ModularPanel panel : screen.getPanelManager()
            .getOpenPanels()) if (panel != owner) {
                Area area = panel.getArea();
                result.add(new Rect(area.x, area.y, area.width, area.height));
            }
        return result;
    }

    private Rect controlBounds(ModularScreen screen, Rect machine) {
        List<Rect> occupied = auxiliary(screen);
        Area frame = owner.getArea();
        occupied.add(new Rect(frame.x, frame.y, frame.width, frame.height));
        occupied.addAll(controller.bounds());
        ArrayDeque<IWidget> pending = new ArrayDeque<>();
        pending.add(owner);
        while (!pending.isEmpty()) {
            IWidget widget = pending.removeFirst();
            if (!widget.isEnabled() || widget == group) continue;
            if (widget.getChildren()
                .isEmpty()) {
                Area area = widget.getArea();
                occupied.add(new Rect(area.x, area.y, area.width, area.height));
            } else pending.addAll(widget.getChildren());
        }
        // Keep the split button outside the machine's control strip; prefer its upper right edge.
        for (int y = Math.max(8, machine.y + 3); y + 12 <= Math.min(host.height - 28, machine.bottom()); y += 14) {
            for (int x : new int[] { frame.x + frame.width + 3, frame.x - 15 }) {
                Rect candidate = new Rect(x, y, 12, 12);
                if (clearControl(candidate, occupied)) return candidate;
            }
        }
        for (int y : new int[] { frame.y - 15, frame.y + frame.height + 3 })
            for (int x = frame.x + frame.width - 12; x >= frame.x; x -= 14) {
                Rect candidate = new Rect(x, y, 12, 12);
                if (clearControl(candidate, occupied)) return candidate;
            }
        return new Rect(Math.max(0, Math.min(host.width - 12, frame.x + frame.width + 3)), 8, 12, 12);
    }

    private boolean clearControl(Rect candidate, List<Rect> occupied) {
        if (candidate.x < 0 || candidate.y < 8
            || candidate.right() > host.width
            || candidate.bottom() > host.height - 28) return false;
        for (Rect obstacle : occupied) if (candidate.intersects(obstacle)) return false;
        return true;
    }

    private void remember(IWidget widget) {
        originalAreas.computeIfAbsent(widget, ignored -> new Area(widget.getArea()));
    }

    private void move(IWidget widget, int x, int y) {
        remember(widget);
        Area area = widget.getArea(), parent = widget.getParent()
            .getArea();
        area.x = x;
        area.y = y;
        area.rx = x - parent.x;
        area.ry = y - parent.y;
    }

    private void hide(IWidget widget) {
        move(widget, -10000, -10000);
    }

    public void drawBackground(ModularScreen screen) {
        update(screen);
        if (controller == null) return;
        controller.updateDragFrame();
        if (controller.detached()) {
            java.util.function.Consumer<Rect> frame = bounds -> Mui2PanelFrame.draw(owner, screen.getContext(), bounds);
            controller.drawPanels(false, frame);
            update(screen);
            dev.gilbe.dynamicinventory.client.HotbarBankPanel
                .drawFramework(host, frame, Mui2PanelFrame.textColor(owner));
        }
    }

    public void drawForeground(ModularScreen screen) {
        if (controller == null) return;
        if (PanelSettingsOverlay.isOpen(host)) {
            PanelSettingsOverlay.draw(
                host,
                screen.getContext()
                    .getAbsMouseX(),
                screen.getContext()
                    .getAbsMouseY(),
                screen.getContext()
                    .getPartialTicks());
        } else controller.drawControl(Mui2PanelFrame.textColor(owner));
    }

    public static String profileKey(ModularScreen screen) {
        GuiContainer gui = (GuiContainer) screen.getScreenWrapper()
            .getGuiScreen();
        GuiData data = ((ModularContainer) gui.inventorySlots).getGuiData();
        String type = gui.inventorySlots.getClass()
            .getName(), kind = "";
        if (data instanceof PosGuiData pos) {
            TileEntity tile = pos.getTileEntity();
            if (tile != null) {
                type = tile.getClass()
                    .getName();
                kind = "/" + data.getWorld()
                    .getBlockMetadata(pos.getX(), pos.getY(), pos.getZ());
                try {
                    Object machine = tile.getClass()
                        .getMethod("getMetaTileEntity")
                        .invoke(tile);
                    if (machine != null) type = machine.getClass()
                        .getName();
                    kind += "/" + tile.getClass()
                        .getMethod("getMetaTileID")
                        .invoke(tile);
                } catch (ReflectiveOperationException ignored) {}
            }
        } else if (data instanceof PlayerInventoryGuiData inventory) {
            ItemStack stack = inventory.getUsedItemStack();
            if (stack != null) {
                type = stack.getItem()
                    .getClass()
                    .getName();
                kind = "/" + stack.getItemDamage();
            }
        }
        return PlayerPanelProfiles.key(
            "dynamicinventory:mui2/" + safe(screen.getOwner()) + "/" + safe(screen.getName()) + kind,
            type,
            gui.getClass()
                .getName());
    }

    private static String safe(String value) {
        return value.toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9_./-]", "_");
    }
}
