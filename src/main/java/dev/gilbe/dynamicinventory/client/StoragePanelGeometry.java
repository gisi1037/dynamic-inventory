package dev.gilbe.dynamicinventory.client;

/** Reviewed storage sheets keep their native UVs; the old player band's frame is never part of the header. */
public final class StoragePanelGeometry {

    public final int left, right, bottom, cropBottom, backgroundTop;
    public final boolean fillBackground;

    private StoragePanelGeometry(int left, int right, int bottom, int cropBottom, boolean fillBackground) {
        this(left, right, bottom, cropBottom, fillBackground, 0);
    }

    private StoragePanelGeometry(int left, int right, int bottom, int cropBottom, boolean fillBackground,
        int backgroundTop) {
        this.left = left;
        this.right = right;
        this.bottom = bottom;
        this.cropBottom = cropBottom;
        this.fillBackground = fillBackground;
        this.backgroundTop = backgroundTop;
    }

    public static StoragePanelGeometry of(String screen, int width, int storageTop, int contentBottom, int slots) {
        if (screen.equals("ganymedes01.etfuturum.client.gui.inventory.GuiBarrel")
            || screen.equals("ganymedes01.etfuturum.client.gui.inventory.GuiShulkerBox")) {
            int inset = slots > 27 ? 4 : 0;
            return new StoragePanelGeometry(inset, width - inset, contentBottom + 8, contentBottom + 2, false);
        }
        if (screen.equals("com.rwtema.extrautils.gui.GuiFilingCabinet"))
            return new StoragePanelGeometry(0, width, 142, 140, false);
        if (screen.equals("com.dreammaster.modbabychest.GuiBabyChest"))
            // The upper sheet is transparent apart from the chest illustration. Its lower frame starts at y=94.
            return new StoragePanelGeometry(0, width, storageTop + 2, contentBottom + 8, true);
        if (screen.equals("cpw.mods.ironchest.client.GUIChest")) {
            // Regular tiers place their grid at x=12. Remove four surplus pixels on each side,
            // and give the last row the same eight-pixel padding. Dirt Chest keeps its centered decoration.
            int inset = slots > 1 ? 4 : 0;
            return new StoragePanelGeometry(
                inset,
                width - inset,
                slots > 1 ? contentBottom + 8 : storageTop + 2,
                contentBottom + (slots > 1 ? 2 : 8),
                false);
        }
        if (screen.equals("wanion.avaritiaddons.block.chest.compressed.GuiCompressedChest"))
            return new StoragePanelGeometry(0, width, contentBottom + 8, contentBottom + 2, false);
        for (String type : new String[] {
            "Churn", "Oven", "Quern", "PamAnimalTrap", "PamFishTrap", "PamApiary", "PamGrinder", "PamPresser" })
            if (screen.equals("com.pam.harvestcraft.Gui" + type))
                // Exclude the old Inventory plaque. Trap/Apiary/Presser art also starts below
                // a transparent strip; back only that strip, not the resource pack's whole sheet.
                return new StoragePanelGeometry(
                    0,
                    width,
                    contentBottom + 8,
                    contentBottom + 2,
                    false,
                    type.equals("PamPresser") ? 6
                        : type.equals("PamAnimalTrap") || type.equals("PamFishTrap") || type.equals("PamApiary") ? 8
                            : 0);
        return null;
    }
}
