package dev.gilbe.dynamicinventory.qualification;

import dev.gilbe.dynamicinventory.client.StoragePanelGeometry;

/** Runs without Minecraft; visual checks still cover the resource-pack artwork itself. */
public final class StoragePanelGeometryFixtures {

    private StoragePanelGeometryFixtures() {}

    public static void main(String[] args) {
        for (int columns : new int[] { 9, 12, 15 }) for (int rows : new int[] { 5, 6, 8, 9 }) {
            int width = columns * 18 + 22, bottom = rows * 18 + 6;
            StoragePanelGeometry geometry = StoragePanelGeometry
                .of("cpw.mods.ironchest.client.GUIChest", width, bottom, bottom, columns * rows);
            check(12 - geometry.left == 8, "left grid padding");
            check(geometry.right - (12 + (columns - 1) * 18 + 16) == 8, "right grid padding");
            check(geometry.bottom - bottom == 8, "bottom grid padding");
            check(geometry.cropBottom >= bottom + 1, "last slot bevel retained");
        }
        StoragePanelGeometry baby = StoragePanelGeometry
            .of("com.dreammaster.modbabychest.GuiBabyChest", 184, 98, 60, 1);
        check(
            baby.fillBackground && baby.cropBottom >= 66 && baby.cropBottom < 94,
            "Baby illustration retained without the old player frame or transparent fill hole");
        StoragePanelGeometry dirt = StoragePanelGeometry.of("cpw.mods.ironchest.client.GUIChest", 184, 98, 60, 1);
        check(dirt.left == 0 && dirt.right == 184, "Dirt Chest decoration keeps its native centering");
        StoragePanelGeometry compressed = StoragePanelGeometry
            .of("wanion.avaritiaddons.block.chest.compressed.GuiCompressedChest", 500, 190, 178, 243);
        check(
            compressed.cropBottom == 180 && compressed.bottom == 186,
            "Compressed Chest retains all rows and excludes the old notched frame");
        check(
            StoragePanelGeometry.of("net.minecraft.client.gui.inventory.GuiChest", 176, 80, 70, 27) == null,
            "unrelated native screens retain their geometry");
        for (String type : new String[] { "Churn", "Oven", "Quern", "PamAnimalTrap", "PamFishTrap", "PamApiary",
            "PamGrinder", "PamPresser" }) {
            StoragePanelGeometry machine = StoragePanelGeometry.of("com.pam.harvestcraft.Gui" + type, 176, 80, 69, 3);
            check(
                machine.cropBottom == 71 && machine.bottom == 77,
                "HarvestCraft retains slot bevels and excludes the old label plaque");
            int transparentTop = type.equals("PamPresser") ? 6
                : type.equals("PamAnimalTrap") || type.equals("PamFishTrap") || type.equals("PamApiary") ? 8 : 0;
            check(
                machine.backgroundTop == transparentTop && !machine.fillBackground,
                "only the transparent HarvestCraft header strip receives frame backing");
        }
        System.out
            .println("PASS storage crops, illustration fill, equal Iron Chest padding and HarvestCraft header crops");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
