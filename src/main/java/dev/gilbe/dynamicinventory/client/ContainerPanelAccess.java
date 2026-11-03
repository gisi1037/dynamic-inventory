package dev.gilbe.dynamicinventory.client;

public interface ContainerPanelAccess {

    /** Framework-owned screens can supply the shared controller without a native background decorator. */
    DetachedChestPanel dynamicinventory$floating();

    void dynamicinventory$floating(DetachedChestPanel panel);

    java.util.List<net.minecraft.client.gui.GuiButton> dynamicinventory$buttons();

    NativeInventoryPanel dynamicinventory$panel();

    void dynamicinventory$panel(NativeInventoryPanel panel);

    int dynamicinventory$width();

    int dynamicinventory$height();

    int dynamicinventory$left();

    int dynamicinventory$top();

    boolean dynamicinventory$draggingItems();

    void dynamicinventory$bounds(int width, int height, int left, int top);
}
