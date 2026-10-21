package dev.gilbe.dynamicinventory.inventory;

/**
 * Immutable capacity/index snapshot belonging to one inventory. No process-wide state,
 * Minecraft classes, or UI preferences are needed to interpret it. A live resize
 * replaces the snapshot and backing array together; existing bindings retain their old map.
 */
public final class InventoryShape {

    public static final int MIN_STORAGE = 27;
    public static final int MAX_STORAGE = 216;
    public static final int HOTBAR_SLOTS = 9;
    public static final int LEGACY_MAIN_SLOTS = 36;
    public static final int ARMOR_SLOTS = 4;
    public static final int OFFHAND_INDEX = 36;

    private final int storageSlots;
    private final boolean offhand;
    private final long revision;

    public InventoryShape(int storageSlots, boolean offhand, long revision) {
        if (storageSlots < MIN_STORAGE || storageSlots > MAX_STORAGE || revision < 0)
            throw new IllegalArgumentException("Invalid inventory shape");
        this.storageSlots = storageSlots;
        this.offhand = offhand;
        this.revision = revision;
    }

    public int storageSlots() {
        return storageSlots;
    }

    public int mainSlots() {
        return storageSlots + HOTBAR_SLOTS;
    }

    public int rawMainSlots() {
        return mainSlots() + (offhand ? 1 : 0);
    }

    public boolean hasOffhand() {
        return offhand;
    }

    public long revision() {
        return revision;
    }

    /** A capacity revision never changes the hotbar, equipment mode, or existing logical identities. */
    public InventoryShape resized(int storage) {
        return new InventoryShape(storage, offhand, Math.addExact(revision, 1));
    }

    public int rawIndex(int ordinary) {
        if (ordinary < 0 || ordinary >= mainSlots()) throw new IllegalArgumentException("ordinary index");
        return ordinary + (offhand && ordinary >= OFFHAND_INDEX ? 1 : 0);
    }

    public int ordinaryIndex(int raw) {
        if (raw < 0 || raw >= rawMainSlots() || (offhand && raw == OFFHAND_INDEX)) return -1;
        return raw - (offhand && raw > OFFHAND_INDEX ? 1 : 0);
    }

    public boolean isExtra(int ordinary) {
        return ordinary >= LEGACY_MAIN_SLOTS && ordinary < mainSlots();
    }
}
