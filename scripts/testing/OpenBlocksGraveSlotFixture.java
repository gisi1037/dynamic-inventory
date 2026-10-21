import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import dev.gilbe.dynamicinventory.compat.openblocks.GraveSlotCodec;

/** Small offline NBT check; deliberately does not start Minecraft or touch a saved world. */
public final class OpenBlocksGraveSlotFixture {
    private static int checks;

    public static void main(String[] args) {
        NBTTagCompound modern = grave(300, false, 0, 127, 128, 255, 256, 299);
        check(GraveSlotCodec.prepareLoad(modern) == modern, "Integer IDs keep origin mappings");
        for (int slot : new int[] {0, 127, 128, 255, 256, 299}) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setInteger("Slot", slot);
            check(GraveSlotCodec.readSlot(entry) == slot, "Integer boundary " + slot);
        }
        NBTTagCompound legacy = grave(256, true, 0, 127, 128, 255);
        check(GraveSlotCodec.prepareLoad(legacy) == legacy, "Unambiguous legacy origins retained");
        check(GraveSlotCodec.readSlot(legacy.getTagList("Items", 10).getCompoundTagAt(2)) == 128, "Unsigned 128");
        check(GraveSlotCodec.readSlot(legacy.getTagList("Items", 10).getCompoundTagAt(3)) == 255, "Unsigned 255");
        // These two byte IDs collide. Both records must survive; neither may overwrite the other.
        verifySalvage(grave(300, true, 0, 127, 128, 255, 256, 299));
        verifySalvage(grave(8, false, 1, 1, 7));
        verifySalvage(grave(8, false, -1, 8));
        verifySalvage(grave(300, true, 42)); // Sparse legacy lists cannot imply original indices.
        check(GraveSlotCodec.prepareLoad(grave(0, false)).getInteger("size") == 0, "Empty grave");
        System.out.println("PASS OpenBlocks grave slot codec: " + checks + " focused checks");
    }

    private static void verifySalvage(NBTTagCompound source) {
        NBTTagCompound before = (NBTTagCompound) source.copy();
        NBTTagCompound result = GraveSlotCodec.prepareLoad(source);
        check(result != source && source.equals(before), "Source is unchanged");
        check(!result.hasKey("SlotOrigins"), "Ambiguous origin hints removed");
        check(result.getString("owner").equals(source.getString("owner")), "Owner retained");
        check(result.getString("Message").equals(source.getString("Message")), "Death message retained");
        NBTTagList items = result.getTagList("Items", 10);
        check(items.tagCount() == source.getTagList("Items", 10).tagCount(), "Every record survives");
        check(result.getInteger("size") == items.tagCount(), "Fresh dense inventory");
        for (int i = 0; i < items.tagCount(); i++) {
            NBTTagCompound entry = items.getCompoundTagAt(i);
            check(entry.hasKey("Slot", 3) && entry.getInteger("Slot") == i, "Unique integer ID");
            NBTTagCompound oldEntry = (NBTTagCompound) source.getTagList("Items", 10).getCompoundTagAt(i).copy();
            oldEntry.setInteger("Slot", i);
            check(entry.equals(oldEntry), "Complete typed item payload retained");
        }
        check(GraveSlotCodec.prepareLoad(result) == result, "Salvage is stable on next load");
    }

    private static NBTTagCompound grave(int size, boolean bytes, int... slots) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("size", size);
        tag.setString("owner", "fixture-owner");
        tag.setString("Message", "fixture-death-message");
        tag.setTag("SlotOrigins", new NBTTagList());
        NBTTagList items = new NBTTagList();
        for (int i = 0; i < slots.length; i++) {
            NBTTagCompound entry = new NBTTagCompound();
            if (bytes) entry.setByte("Slot", (byte) slots[i]);
            else entry.setInteger("Slot", slots[i]);
            entry.setString("id", "minecraft:stone");
            entry.setByte("Count", (byte) (i + 1));
            entry.setShort("Damage", (short) 2);
            NBTTagCompound custom = new NBTTagCompound();
            custom.setLong("typed", 123456789012345L + i);
            custom.setByte("probe", (byte) i);
            entry.setTag("tag", custom);
            items.appendTag(entry);
        }
        tag.setTag("Items", items);
        return tag;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
