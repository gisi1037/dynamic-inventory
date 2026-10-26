package dev.gilbe.dynamicinventory.compat.ae2;

/** The lower native texture moves when AE2 changes its network-browser row count. */
public interface AppliedEnergisticsBrowserAccess {

    int dynamicinventory$browserBottom();

    boolean dynamicinventory$isPinWidget(net.minecraft.client.gui.GuiButton button);

    /** Native header-relative origin of a virtual pattern grid, or null for other hosts. */
    int[] dynamicinventory$virtualCraftingOrigin();
}
