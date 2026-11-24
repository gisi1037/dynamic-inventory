package dev.gilbe.dynamicinventory.compat.ae2;

import java.util.Arrays;
import java.util.TreeSet;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagByteArray;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.nbt.NBTTagList;

import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;

import appeng.api.storage.data.IAEStack;

/** Binds a gesture to the stack displayed when it was made, independent of count and craftability. */
public final class PinIdentity {

    public static final int BYTES = 32;

    private PinIdentity() {}

    public static byte[] of(IAEStack<?> stack) {
        if (stack == null) return new byte[BYTES];
        Hasher hash = Hashing.sha256()
            .newHasher();
        var normalized = new NBTTagCompound();
        stack.copy()
            .reset()
            .writeToNBTGeneric(normalized);
        append(hash, normalized);
        return hash.hash()
            .asBytes();
    }

    public static boolean matches(byte[] expected, IAEStack<?> current) {
        return current != null && Arrays.equals(expected, of(current));
    }

    private static void append(Hasher hash, NBTBase tag) {
        hash.putByte(tag.getId());
        if (tag instanceof NBTTagCompound compound) {
            var keys = new TreeSet<String>(compound.func_150296_c());
            hash.putInt(keys.size());
            for (String key : keys) {
                text(hash, key);
                append(hash, compound.getTag(key));
            }
        } else if (tag instanceof NBTTagList list) {
            NBTTagList copy = (NBTTagList) list.copy();
            hash.putInt(copy.tagCount())
                .putByte((byte) copy.func_150303_d());
            // Reverse traversal preserves order using the public 1.7.10 API without shifting the list.
            for (int i = copy.tagCount() - 1; i >= 0; i--) append(hash, copy.removeTag(i));
        } else if (tag instanceof NBTTagByteArray array) {
            byte[] values = array.func_150292_c();
            hash.putInt(values.length)
                .putBytes(values);
        } else if (tag instanceof NBTTagIntArray array) {
            int[] values = array.func_150302_c();
            hash.putInt(values.length);
            for (int value : values) hash.putInt(value);
        } else text(hash, tag.toString());
    }

    private static void text(Hasher hash, String value) {
        hash.putInt(value.length())
            .putUnencodedChars(value);
    }
}
