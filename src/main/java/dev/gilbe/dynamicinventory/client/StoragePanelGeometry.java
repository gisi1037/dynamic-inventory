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
        int enderIoBottom = switch (screen) {
            case "crazypants.enderio.machine.killera.GuiKillerJoe" -> 88;
            case "crazypants.enderio.machine.vacuum.GuiVacuumChest" -> 125;
            case "crazypants.enderio.machine.invpanel.GuiInventoryPanel" -> 187;
            case "crazypants.enderio.machine.capbank.GuiCapBank" -> Math.max(83, contentBottom + 6);
            case "crazypants.enderio.conduit.gui.GuiExternalConnection" -> storageTop + 4;
            case "crazypants.enderio.machine.crafter.GuiCrafter", "crazypants.enderio.machine.spawner.GuiPoweredSpawner", "crazypants.enderio.machine.soul.GuiSoulBinder", "crazypants.enderio.machine.slicensplice.GuiSliceAndSplice", "crazypants.enderio.machine.tank.GuiTank", "crazypants.enderio.machine.buffer.GuiBuffer" -> storageTop
                + 2;
            default -> 0;
        };
        if (enderIoBottom > 0) {
            int right = screen.equals("crazypants.enderio.machine.capbank.GuiCapBank") ? width - 21 : width;
            return new StoragePanelGeometry(0, right, enderIoBottom, enderIoBottom, true);
        }
        int draconicHeight = switch (screen) {
            case "com.brandon3055.draconicevolution.client.gui.GUIDraconiumChest" -> 256;
            case "com.brandon3055.draconicevolution.client.gui.GUIUpgradeModifier" -> 190;
            case "com.brandon3055.draconicevolution.client.gui.componentguis.GUIReactor" -> 222;
            case "com.brandon3055.draconicevolution.client.gui.componentguis.GUIToolInventory" -> 130;
            default -> 0;
        };
        if (draconicHeight > 0)
            // Controls extend beside the old player inventory and need the full native sheet.
            return new StoragePanelGeometry(
                0,
                width,
                draconicHeight,
                screen.endsWith(".GUIDraconiumChest") || screen.endsWith(".GUIUpgradeModifier") ? draconicHeight - 3
                    : draconicHeight,
                true);
        if (screen.equals("com.brandon3055.draconicevolution.client.gui.GUIWeatherController"))
            return new StoragePanelGeometry(0, width, storageTop, storageTop, true);
        if (screen.equals("ganymedes01.etfuturum.client.gui.inventory.GuiBarrel") && slots == 27 && width == 184)
            // The 184px Barrel draws the 176px vanilla chest sheet, including its old right border.
            return new StoragePanelGeometry(0, 176, contentBottom + 8, contentBottom + 2, true);
        int thaumcraftCrop = switch (screen) {
            case "thaumcraft.client.gui.GuiFocalManipulator" -> 143;
            case "thaumcraft.client.gui.GuiResearchTable" -> 167;
            case "thaumcraft.client.gui.GuiAlchemyFurnace", "thaumcraft.client.gui.GuiThaumatorium" -> 76;
            default -> 0;
        };
        if (thaumcraftCrop > 0)
            // These sheets draw the workstation and the old inventory frame as separate pieces.
            return new StoragePanelGeometry(0, width, thaumcraftCrop + 6, thaumcraftCrop, true);
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
