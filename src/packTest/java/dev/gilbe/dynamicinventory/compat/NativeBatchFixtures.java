package dev.gilbe.dynamicinventory.compat;

import java.lang.reflect.Method;
import java.nio.file.Paths;
import java.util.zip.ZipFile;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;

import dev.gilbe.dynamicinventory.api.InventoryUi;
import dev.gilbe.dynamicinventory.api.InventoryUiAdapter;
import dev.gilbe.dynamicinventory.compat.extrautils.ExtraUtilitiesItemAccess;

/** Match actual installed class names, exact host gates, and bag/filter owner locks without a client. */
public final class NativeBatchFixtures {

    private NativeBatchFixtures() {}

    public static void main(String[] args) throws Exception {
        EnderIoCompatibility.registerInventoryAdapters();
        ExtraUtilitiesCompatibility.registerInventoryAdapters();
        Method gate = NativePanelHosts.class
            .getDeclaredMethod("reviewedAdapter", InventoryUiAdapter.class, String.class);
        gate.setAccessible(true);
        try (ZipFile eio = new ZipFile(
            Paths.get(args[0])
                .toFile());
            ZipFile xu = new ZipFile(
                Paths.get(args[1])
                    .toFile())) {
            int count = 0;
            for (InventoryUiAdapter adapter : InventoryUi.getRegisteredAdapters()) {
                ZipFile owner = adapter.getId()
                    .startsWith("dynamicinventory:enderio/") ? eio : xu;
                check(
                    owner.getEntry(
                        adapter.getContainerClassName()
                            .replace('.', '/') + ".class")
                        != null,
                    "Real container " + adapter.getId());
                if (!adapter.getGuiClassName()
                    .startsWith("net.minecraft."))
                    check(
                        owner.getEntry(
                            adapter.getGuiClassName()
                                .replace('.', '/') + ".class")
                            != null,
                        "Real GUI " + adapter.getId());
                check(!adapter.isIncludingSubclasses(), "No package/subclass opt-in");
                check(
                    (Boolean) gate.invoke(null, adapter, adapter.getContainerClassName()),
                    "Admitted reviewed panel host");
                check(
                    !(Boolean) gate.invoke(null, adapter, "example.UnrelatedContainer"),
                    "Reject unrelated container");
                count++;
                if (adapter.getId()
                    .endsWith("/golden-bag")
                    || adapter.getId()
                        .equals("dynamicinventory:extrautils/filter")) {
                    FixtureContainer container = new FixtureContainer();
                    InventoryPlayer inventory = new InventoryPlayer(null);
                    Slot held = adapter.getAdditionalSlotFactory()
                        .create(container, inventory, 44);
                    Slot other = adapter.getAdditionalSlotFactory()
                        .create(container, inventory, 45);
                    check(held.inventory == inventory && held.getSlotIndex() == 44, "Canonical owner identity");
                    check(!held.canTakeStack(null) && !held.isItemValid(null), "Expanded owner slot stays locked");
                    check(other.canTakeStack(null) && other.isItemValid(null), "Other player slots stay ordinary");
                }
            }
            check(count == 38, "29 Ender IO and 9 Extra Utilities container families");
        }
        System.out.println("PASS actual installed host names, exact panel gates and expanded bag/filter owner locks");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class FixtureContainer extends Container implements ExtraUtilitiesItemAccess {

        @Override
        public int dynamicinventory$ownerSlot() {
            return 44;
        }

        @Override
        public boolean canInteractWith(EntityPlayer player) {
            return true;
        }
    }
}
