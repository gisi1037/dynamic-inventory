package dev.gilbe.dynamicinventory.client;

/** Header positions share the same scaled-pixel coordinate system as the real slots. */
public final class PlayerHeaderLayout {

    public static final int ARMOR_X = 8;
    public static final int PLAYER_X = 52;
    public static final int OFFHAND_X = 80;
    public static final int OFFHAND_Y = 62;
    public static final int BAUBLES_X = 100;
    public static final int BAUBLES_ROWS = 4;
    public final int craftingX;
    public final int resultX;
    public final int layoutButtonX;
    public final int baublesButtonX;
    public final int baublesColumns;
    public final boolean inlineBaublesFits;

    public PlayerHeaderLayout(int width, int baublesSlots) {
        craftingX = width - 88;
        resultX = width - 28;
        layoutButtonX = width - 22;
        baublesButtonX = Math.min(OFFHAND_X, craftingX - 24);
        int availableColumns = Math.max(0, (craftingX - 8 - BAUBLES_X) / 18);
        baublesColumns = (baublesSlots + BAUBLES_ROWS - 1) / BAUBLES_ROWS;
        inlineBaublesFits = baublesSlots > 0 && baublesColumns <= availableColumns;
    }
}
