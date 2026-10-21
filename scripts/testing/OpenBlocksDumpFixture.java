import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

/** Offline NBT-only negative dumps. Does not initialize Minecraft or decode any ItemStack. */
public final class OpenBlocksDumpFixture {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Expected native dump path and current API size 86");
        Path original = Path.of(args[0]).toRealPath();
        int current = Integer.parseInt(args[1]);
        String world = original.getParent().getParent().getFileName().toString();
        if (current != 86 || !original.getParent().getFileName().toString().equals("data")
            || !world.startsWith("DI-Baseline-") || !world.endsWith("Qualification")
            || !original.getFileName().toString().startsWith("inventory-")
            || !original.getFileName().toString().endsWith(".dat"))
            throw new IllegalArgumentException("Requires native dump in disposable qualification world and API size 86");
        byte[] originalBytes = Files.readAllBytes(original);
        NBTTagCompound baseline = read(original);
        if (!baseline.hasKey("Inventory", 10) || !baseline.getCompoundTag("Inventory").hasKey("size", 3)
            || baseline.getCompoundTag("Inventory").getInteger("size") != current)
            throw new IllegalArgumentException("Native OpenMods Inventory.size must be integer 86");
        String nonce = UUID.randomUUID().toString();
        for (int size : new int[] {41, current + 1}) {
            NBTTagCompound changed = (NBTTagCompound) baseline.copy();
            changed.getCompoundTag("Inventory").setInteger("size", size);
            Path output = original.resolveSibling("inventory-dinv-negative-" + nonce + "-" + size + ".dat");
            try (OutputStream stream = Files.newOutputStream(output, StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE)) {
                CompressedStreamTools.writeCompressed(changed, stream);
            }
            NBTTagCompound verified = read(output);
            if (verified.getCompoundTag("Inventory").getInteger("size") != size)
                throw new IllegalStateException("Negative dump size was not persisted");
            verified.getCompoundTag("Inventory").setInteger("size", current);
            if (!verified.equals(baseline)) throw new IllegalStateException("Negative dump changed unrelated NBT");
            System.out.println(size + "\t" + output.getFileName());
        }
        if (!java.util.Arrays.equals(originalBytes, Files.readAllBytes(original)))
            throw new IllegalStateException("Original backup was changed");
    }

    private static NBTTagCompound read(Path path) throws Exception {
        try (InputStream stream = Files.newInputStream(path)) {
            return CompressedStreamTools.readCompressed(stream);
        }
    }
}
