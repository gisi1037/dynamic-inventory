package dev.gilbe.dynamicinventory.client;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.ContainerDispenser;
import net.minecraft.inventory.ContainerHopper;
import net.minecraft.inventory.Slot;

import dev.gilbe.dynamicinventory.DynamicInventory;
import dev.gilbe.dynamicinventory.api.InventoryUi;
import dev.gilbe.dynamicinventory.api.NativeUiLayout;
import dev.gilbe.dynamicinventory.api.PlayerInventoryBinding;
import dev.gilbe.dynamicinventory.compat.PackCompatibility;

/**
 * Presentation fallback, deliberately NOT an automatic container adapter. Only an already registered,
 * bound vanilla storage host qualifies. Unknown container code still needs a reviewed adapter, because
 * a familiar-looking grid says nothing about custom packets, shift-click or output callbacks.
 */
public final class AutomaticScrollPresentation {

    private static final NativeUiLayout LAYOUT = NativeUiLayout.builder()
        .fixedPlayerColumns(9)
        .maximumVisibleRows(3)
        .followMachineMode(false)
        .backgroundInset(4)
        .build();
    // Per-container approval: one safe instance must not approve every future instance of a GUI class.
    // Values contain only a name, so this weak map does not keep closed containers alive.
    private static final Map<Container, String> APPROVED = new WeakHashMap<>();

    private AutomaticScrollPresentation() {}

    /** Called after native init and before decoration, while slot coordinates are still native. */
    public static void prepare(GuiContainer gui) {
        APPROVED.remove(gui.inventorySlots);
        // Explicit adapter/presentation declarations always take priority, including saved layouts.
        if (!InventoryViewSettings.automaticStorageScroll
            || InventoryUi.findLayout(gui.inventorySlots, gui.getClass()) != null) return;
        PlayerInventoryBinding binding = InventoryUi.getBinding(gui.inventorySlots);
        if (InventoryUi.findAdapter(gui.inventorySlots) == null) return;
        Class<?> host = gui.inventorySlots.getClass();
        // Exact classes only. Subclasses can override transfer, click or synchronization behavior.
        if (binding == null
            || (host != ContainerChest.class && host != ContainerDispenser.class && host != ContainerHopper.class))
            return;
        if (!isDrawingOnly(gui.getClass(), GuiContainer.class)) return;
        ContainerPanelAccess access = (ContainerPanelAccess) gui;
        for (GuiButton button : access.dynamicinventory$buttons()) {
            // Bogo adds these four shared controls to an otherwise drawing-only GUI. Their
            // canonical groups, opening guard and positioning already have a reviewed bridge.
            // Do not broaden this exception to arbitrary buttons or subclasses.
            if (!PackCompatibility.enabled() || !(button instanceof NativeSortButton)
                || !button.getClass()
                    .getName()
                    .equals("com.cleanroommc.bogosorter.common.sort.ButtonHandler$SortButton"))
                return;
        }
        if (!standardSlots(gui, binding, access.dynamicinventory$width(), access.dynamicinventory$height())) return;
        APPROVED.put(
            gui.inventorySlots,
            gui.getClass()
                .getName());
        DynamicInventory.LOG.info(
            "Automatic storage scroll presentation: {}",
            gui.getClass()
                .getName());
    }

    /** Read-only lookup used by the existing presentation/editor pipeline; never attaches slots. */
    public static NativeUiLayout layoutFor(Container container, Class<?> guiClass) {
        return InventoryViewSettings.automaticStorageScroll && guiClass != null
            && guiClass.getName()
                .equals(APPROVED.get(container)) ? LAYOUT : null;
    }

    /**
     * Inspect every superclass, not only the leaf. Constructors are not reflection Methods. Any
     * declared input, init, update, overlay, or custom helper method sends the screen to manual review.
     * Checking both names supports the development and SRG runtime environments without loading mods.
     */
    public static boolean isDrawingOnly(Class<?> type, Class<?> base) {
        if (type == null || type == base || !base.isAssignableFrom(type)) return false;
        try {
            for (Class<?> current = type; current != base; current = current.getSuperclass()) {
                if (current.getInterfaces().length != 0) return false;
                for (Method method : current.getDeclaredMethods()) {
                    String name = method.getName();
                    Class<?>[] args = method.getParameterTypes();
                    boolean foreground = (name.equals("drawGuiContainerForegroundLayer")
                        || name.equals("func_146979_b"))
                        && java.util.Arrays.equals(args, new Class<?>[] { int.class, int.class });
                    boolean background = (name.equals("drawGuiContainerBackgroundLayer")
                        || name.equals("func_146976_a"))
                        && java.util.Arrays.equals(args, new Class<?>[] { float.class, int.class, int.class });
                    if (Modifier.isStatic(method.getModifiers()) || method.getReturnType() != void.class
                        || (!foreground && !background)) return false;
                }
            }
            return true;
        } catch (LinkageError | SecurityException unavailable) {
            // A missing optional type is a refusal, never a reason to load a partial integration.
            return false;
        }
    }

    private static boolean standardSlots(GuiContainer gui, PlayerInventoryBinding binding, int width, int height) {
        Container host = gui.inventorySlots;
        int count = binding.getNativeSlotCount();
        if (width != 176 || count <= 36 || height < 100 || height > 512) return false;
        if (binding.getOrdinarySlotCount() < 36) return false;
        if (host.inventorySlots.size() != count + binding.getOrdinarySlotCount() - 36) return false;
        // Refuse a stale binding before dereferencing any of its IDs. Other mods may have
        // changed a container between construction and GUI initialization.
        for (int logical = 0; logical < binding.getOrdinarySlotCount(); logical++) {
            int id = binding.getContainerSlotId(logical);
            if (id < 0 || id >= host.inventorySlots.size()) return false;
            Slot slot = host.getSlot(id);
            if (slot == null || slot.slotNumber != id
                || slot.getClass() != Slot.class
                || slot.inventory != binding.getPlayerInventory()
                || slot.getSlotIndex()
                    != dev.gilbe.dynamicinventory.inventory.InventoryLayout.of(binding.getPlayerInventory())
                        .rawIndex(logical))
                return false;
        }
        Slot first = host.getSlot(binding.getContainerSlotId(9));
        int left = first.xDisplayPosition, top = first.yDisplayPosition;
        // This fallback preserves the native width and header. Custom player offsets need an adapter.
        if (left != 8 || top < 26 || top + 76 > height) return false;
        boolean[] seen = new boolean[36];
        for (int id = 0; id < count; id++) {
            Slot slot = host.getSlot(id);
            if (slot == null || slot.slotNumber != id || slot.getClass() != Slot.class) return false;
            int logical = binding.logicalFromContainerId(id);
            if (logical >= 0) {
                if (logical >= 36 || seen[logical] || slot.inventory != binding.getPlayerInventory()) return false;
                seen[logical] = true;
                if (!standardPlayerPosition(logical, slot.xDisplayPosition, slot.yDisplayPosition, left, top))
                    return false;
            } else {
                if (slot.inventory == null || slot.inventory == binding.getPlayerInventory()) return false;
                // Only the upper storage grid may survive the native-background clipping boundary.
                if (slot.xDisplayPosition < 8 || slot.xDisplayPosition + 16 > width - 8
                    || slot.yDisplayPosition < 8
                    || slot.yDisplayPosition + 16 > top - 4) return false;
            }
        }
        for (boolean present : seen) if (!present) return false;
        return true;
    }

    /** Logical slots keep their identity; scrolling changes coordinates only. */
    public static boolean standardPlayerPosition(int logical, int x, int y, int left, int top) {
        if (logical < 0 || logical >= 36) return false;
        int index = logical < 9 ? logical : logical - 9;
        return x == left + index % 9 * 18 && y == top + (logical < 9 ? 58 : index / 9 * 18);
    }
}
