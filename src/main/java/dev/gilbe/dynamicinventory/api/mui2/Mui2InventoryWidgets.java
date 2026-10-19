package dev.gilbe.dynamicinventory.api.mui2;

import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;

import dev.gilbe.dynamicinventory.compat.mui2.InventoryScrollWidget;
import dev.gilbe.dynamicinventory.compat.mui2.PlayerInventoryWidgetGroup;
import dev.gilbe.dynamicinventory.inventory.InventoryLayout;

/**
 * Factories for Dynamic Inventory's MUI2 player inventory widgets.
 *
 * <p>
 * The default factory preserves the standard nine-column player layout while
 * exposing additional storage through the dynamic inventory viewport.
 * </p>
 */
public final class Mui2InventoryWidgets {

    private static final int COLUMNS = 9;
    private static final int VISIBLE_ROWS = 3;

    private Mui2InventoryWidgets() {}

    /**
     * Creates the standard dynamic player inventory group.
     *
     * <p>
     * The first nine children are the hotbar slots, followed by a vertically
     * scrollable storage viewport. Consumers customize each {@link ItemSlot};
     * the returned widgets retain MUI2's {@code player} sync keys and
     * {@code slot_i} names.
     * </p>
     *
     * @param slotConsumer MUI2 slot factory/decoration hook
     * @return the standard responsive player inventory group
     */
    public static SlotGroupWidget playerInventory(SlotGroupWidget.SlotConsumer slotConsumer) {
        if (slotConsumer == null) throw new NullPointerException("slotConsumer");
        return create(slotConsumer, COLUMNS, VISIBLE_ROWS, true, true);
    }

    /**
     * Creates a fixed-column player inventory group for a new MUI2 GUI.
     *
     * <p>
     * The visible row count is limited to the number needed to display the
     * configured storage capacity. Unlike the standard factory, this viewport
     * does not participate in Dynamic Inventory's responsive MUI2 screen resize;
     * its requested layout remains owned by the caller.
     * </p>
     *
     * @param slotConsumer MUI2 slot factory/decoration hook
     * @param columns      storage grid columns, from 3 through 18
     * @param visibleRows  requested visible storage rows, from 1 through 12
     * @return a fixed-layout player inventory group
     */
    public static SlotGroupWidget playerInventory(SlotGroupWidget.SlotConsumer slotConsumer, int columns,
        int visibleRows) {
        if (slotConsumer == null) throw new NullPointerException("slotConsumer");
        if (columns < 3 || columns > 18) throw new IllegalArgumentException("columns must be between 3 and 18");
        if (visibleRows < 1 || visibleRows > 12)
            throw new IllegalArgumentException("visibleRows must be between 1 and 12");
        return create(slotConsumer, columns, visibleRows, false, false);
    }

    private static SlotGroupWidget create(SlotGroupWidget.SlotConsumer slotConsumer, int columns, int visibleRows,
        boolean adaptive, boolean stockCoordinates) {
        int storageRows = (InventoryLayout.storageSlots() + columns - 1) / columns;
        int rows = Math.min(visibleRows, storageRows);
        int groupWidth = Math.max(COLUMNS * ItemSlot.SIZE + 6, columns * ItemSlot.SIZE + 6);
        int viewportHeight = rows * ItemSlot.SIZE;
        int hotbarX = stockCoordinates ? 0 : (groupWidth - InventoryLayout.HOTBAR_SLOTS * ItemSlot.SIZE) / 2;
        InventoryScrollWidget viewport = adaptive ? new InventoryScrollWidget() : new InventoryScrollWidget(columns);
        PlayerInventoryWidgetGroup group = new PlayerInventoryWidgetGroup(viewport, rows);
        group.name("player_inventory")
            .size(groupWidth, viewportHeight + 22);
        for (int i = 0; i < InventoryLayout.HOTBAR_SLOTS; i++) group.addHotbarSlot(
            slotConsumer.apply(i, new ItemSlot())
                .syncHandler("player", i)
                .pos(hotbarX + i * ItemSlot.SIZE, viewportHeight + 4)
                .name("slot_" + i));

        viewport.size(groupWidth, viewportHeight);
        for (int i = InventoryLayout.HOTBAR_SLOTS; i < InventoryLayout.mainSlots(); i++) viewport.child(
            slotConsumer.apply(i, new ItemSlot())
                .syncHandler("player", i)
                .pos(
                    (i - InventoryLayout.HOTBAR_SLOTS) % columns * ItemSlot.SIZE,
                    (i - InventoryLayout.HOTBAR_SLOTS) / columns * ItemSlot.SIZE)
                .name("slot_" + i));
        group.child(viewport);
        return group;
    }
}
