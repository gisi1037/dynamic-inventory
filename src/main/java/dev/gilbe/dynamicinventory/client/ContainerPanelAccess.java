package dev.gilbe.dynamicinventory.client;

public interface ContainerPanelAccess {

    java.util.List<net.minecraft.client.gui.GuiButton> dynamicinventory$buttons();

    NativeInventoryPanel dynamicinventory$panel();

    void dynamicinventory$panel(NativeInventoryPanel panel);

    int dynamicinventory$width();

    int dynamicinventory$height();

    int dynamicinventory$left();

    int dynamicinventory$top();

    void dynamicinventory$bounds(int width, int height, int left, int top);
}
