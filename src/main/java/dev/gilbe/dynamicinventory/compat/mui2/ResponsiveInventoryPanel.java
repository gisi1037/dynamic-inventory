package dev.gilbe.dynamicinventory.compat.mui2;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.widget.WidgetTree;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;

import dev.gilbe.dynamicinventory.client.InventoryViewSettings;
import dev.gilbe.dynamicinventory.client.InventoryViewportLayout;

/** Resize live player-inventory branches and their ancestors; machine controls keep native sizes. */
public final class ResponsiveInventoryPanel {

    private final Map<IWidget, Height> heights = new IdentityHashMap<>();
    private final Map<PlayerInventoryWidgetGroup, Integer> rows = new IdentityHashMap<>();

    public boolean resize(ModularScreen screen, int height) {
        // New dynamic children may be initialized but still have zero-sized areas.
        // Apply their pending native layout before recording responsive baselines.
        for (ModularPanel panel : screen.getPanelManager()
            .getOpenPanels()) {
            ArrayDeque<IWidget> pending = new ArrayDeque<>();
            pending.add(panel);
            while (!pending.isEmpty()) {
                IWidget widget = pending.removeFirst();
                if (widget.requiresResize()) {
                    WidgetTree.resizeInternal(panel.resizer(), false);
                    break;
                }
                pending.addAll(widget.getChildren());
            }
        }
        return resizePanels(
            screen.getPanelManager()
                .getOpenPanels(),
            height);
    }

    /** The panel snapshot is supplied separately so secondary and dynamically replaced panels use the same path. */
    public boolean resizePanels(Iterable<ModularPanel> panels, int height) {
        Map<IWidget, IWidget> parents = new IdentityHashMap<>();
        Map<IWidget, ModularPanel> owners = new IdentityHashMap<>();
        List<PlayerInventoryWidgetGroup> groups = new ArrayList<>();
        for (ModularPanel panel : panels) {
            ArrayDeque<IWidget> pending = new ArrayDeque<>();
            pending.add(panel);
            parents.put(panel, null);
            while (!pending.isEmpty()) {
                IWidget widget = pending.removeFirst();
                owners.put(widget, panel);
                if (widget instanceof PlayerInventoryWidgetGroup) groups.add((PlayerInventoryWidgetGroup) widget);
                for (IWidget child : widget.getChildren()) {
                    parents.put(child, widget);
                    pending.addLast(child);
                }
            }
        }

        // A dynamic rebuild may assign a new native height to an otherwise unchanged panel/ancestor.
        for (Map.Entry<IWidget, Height> entry : heights.entrySet()) {
            if (owners.containsKey(entry.getKey())) entry.getValue()
                .refresh(
                    entry.getKey()
                        .getArea().height);
        }

        boolean changed = false;
        Map<IWidget, Integer> deltas = new IdentityHashMap<>();
        Map<PlayerInventoryWidgetGroup, Integer> activeRows = new IdentityHashMap<>();
        for (PlayerInventoryWidgetGroup group : groups) {
            InventoryScrollWidget viewport = group.getInventoryViewport();
            if (!viewport.isAdaptive() || parents.get(viewport) != group) continue;
            ModularPanel panel = owners.get(group);
            int nextRows = InventoryViewportLayout.rows(
                viewport.getStorageSlots(),
                baseline(panel),
                height,
                InventoryViewSettings.expandMachineInventory);
            int previousRows = rows.containsKey(group) ? rows.get(group) : group.getInitialRows();
            activeRows.put(group, nextRows);
            if (previousRows != nextRows) {
                viewport.height(nextRows * 18);
                viewport.getScrollArea()
                    .getScrollY()
                    .scrollTo(viewport.getScrollArea(), 0);
                for (ItemSlot hotbar : group.getHotbarSlots()) {
                    if (parents.get(hotbar) == group) hotbar.resizer()
                        .top(nextRows * 18 + 4);
                }
                changed = true;
            }
            int delta = (nextRows - group.getInitialRows()) * 18;
            for (IWidget ancestor = group; ancestor != null; ancestor = parents.get(ancestor)) {
                // Sibling inventory branches share the extra space instead of growing their panel twice.
                deltas.put(ancestor, Math.max(deltas.getOrDefault(ancestor, 0), delta));
            }
        }
        for (PlayerInventoryWidgetGroup previous : rows.keySet()) {
            if (activeRows.containsKey(previous)) continue;
            // Cached panels/groups may be reopened with the same identity. Undo our layout
            // before releasing references, so the next admission starts from native geometry.
            int initialRows = previous.getInitialRows();
            previous.getInventoryViewport()
                .height(initialRows * 18);
            for (ItemSlot hotbar : previous.getHotbarSlots()) hotbar.resizer()
                .top(initialRows * 18 + 4);
        }
        rows.clear();
        rows.putAll(activeRows);

        // Removed branches must release their baselines and cannot keep detached widgets alive.
        Iterator<Map.Entry<IWidget, Height>> old = heights.entrySet()
            .iterator();
        while (old.hasNext()) {
            Map.Entry<IWidget, Height> entry = old.next();
            if (deltas.containsKey(entry.getKey())) continue;
            if (entry.getValue().delta != 0) {
                entry.getKey()
                    .resizer()
                    .height(entry.getValue().baseline);
                changed = true;
            }
            old.remove();
        }
        for (Map.Entry<IWidget, Integer> entry : deltas.entrySet()) {
            IWidget widget = entry.getKey();
            baseline(widget);
            Height state = heights.get(widget);
            if (state.delta == entry.getValue()) continue;
            state.delta = entry.getValue();
            widget.resizer()
                .height(state.baseline + state.delta);
            changed = true;
        }
        return changed;
    }

    private int baseline(IWidget widget) {
        Height state = heights.get(widget);
        if (state == null) {
            state = new Height(widget.getArea().height);
            heights.put(widget, state);
        }
        return state.baseline;
    }

    private static final class Height {

        int baseline;
        int delta;

        Height(int baseline) {
            this.baseline = baseline;
        }

        void refresh(int measuredHeight) {
            if (measuredHeight != baseline + delta) {
                baseline = measuredHeight;
                delta = 0;
            }
        }
    }
}
