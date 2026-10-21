package dev.gilbe.dynamicinventory.api;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import dev.gilbe.dynamicinventory.Tags;
import dev.gilbe.dynamicinventory.inventory.InventoryCapacity;

/** Startup declaration agreement, not an exchange of each runtime container's slot layout. */
public final class InventoryUiProtocol {

    public static final int WIRE_VERSION = 4;

    private InventoryUiProtocol() {}

    /** Callback implementation names are unstable; integrations must bump their declared compatibility revision. */
    public static String digest(List<InventoryUiAdapter> adapters) {
        List<String> declarations = new ArrayList<>();
        for (InventoryUiAdapter adapter : adapters) declarations.add(
            adapter.getId() + "\n"
                + adapter.getContainerClassName()
                + "\n"
                + adapter.isIncludingSubclasses()
                + "\n"
                + adapter.getExpectedNativeSlots()
                + "\n"
                + adapter.getCompatibilityRevision()
                + "\n"
                + (adapter.getTransferPolicy() != null)
                + "\n"
                + adapter.hasCustomAdditionalSlots()
                + "\n");
        Collections.sort(declarations);
        try {
            MessageDigest hash = MessageDigest.getInstance("SHA-256");
            for (String declaration : declarations) hash.update(declaration.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte value : hash.digest()) {
                hex.append("0123456789abcdef".charAt((value & 255) >>> 4));
                hex.append("0123456789abcdef".charAt(value & 15));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 is required for inventory protocol agreement", unavailable);
        }
    }

    public static String wireVersion(int storage, String digest) {
        return Tags.VERSION + ";protocol=" + WIRE_VERSION + ";storage=" + storage + ";adapters=" + digest;
    }

    /** Pure parser used for probes and login. Only a complete matching protocol declaration is negotiable. */
    public static int readStorage(String version, String expectedDigest) {
        if (version == null || expectedDigest == null) return -1;
        String[] fields = version.split(";", -1);
        if (fields.length != 4 || !Tags.VERSION.equals(fields[0])
            || !("protocol=" + WIRE_VERSION).equals(fields[1])
            || !fields[2].startsWith("storage=")
            || !("adapters=" + expectedDigest).equals(fields[3])
            || !expectedDigest.matches("[0-9a-f]{64}")) return -1;
        String raw = fields[2].substring("storage=".length());
        if (!raw.matches("[0-9]+")) return -1;
        try {
            int value = Integer.parseInt(raw);
            return value >= InventoryCapacity.MIN_STORAGE && value <= InventoryCapacity.MAX_STORAGE ? value : -1;
        } catch (NumberFormatException invalid) {
            return -1;
        }
    }
}
