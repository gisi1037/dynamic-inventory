package dev.gilbe.dynamicinventory.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import dev.gilbe.dynamicinventory.DynamicInventory;
import dev.gilbe.dynamicinventory.container.ContainerInventoryRegion;
import dev.gilbe.dynamicinventory.inventory.InventoryLayout;
import dev.gilbe.dynamicinventory.network.ServerInventoryGate;

/**
 * Public native-container integration entry point. Register once during mod initialization on both sides.
 * Only explicitly registered classes are expanded; discovery reports never enable an adapter.
 */
public final class InventoryUi {

    public static final int API_VERSION = 1;
    private static final Map<String, InventoryUiAdapter> ADAPTERS = new LinkedHashMap<>();
    private static final Map<Class<?>, InventoryUiAdapter> RESOLVED = new HashMap<>();
    private static final Map<String, NativeUiLayout> PRESENTATIONS = new HashMap<>();
    private static final Map<Container, String> REFUSED = new WeakHashMap<>();
    private static String compatibilityDigest;

    private InventoryUi() {}

    /** Rejects conflicting registrations instead of silently replacing another mod's integration. */
    public static synchronized void register(InventoryUiAdapter adapter) {
        if (adapter == null) throw new NullPointerException("adapter");
        if (ADAPTERS.containsKey(adapter.getId()))
            throw new IllegalArgumentException("Adapter id already registered: " + adapter.getId());
        for (InventoryUiAdapter existing : ADAPTERS.values()) if (existing.getContainerClassName()
            .equals(adapter.getContainerClassName()))
            throw new IllegalArgumentException("Container already registered by " + existing.getId());
        if (compatibilityDigest != null) throw new IllegalStateException(
            "Register inventory adapters during preInit, init or postInit, before loadComplete");
        ADAPTERS.put(adapter.getId(), adapter);
        RESOLVED.clear();
    }

    /** Internal lifecycle hook: no server/client adapter changes are permitted after startup. */
    public static synchronized void closeRegistration() {
        if (compatibilityDigest == null) compatibilityDigest = InventoryUiProtocol.digest(getRegisteredAdapters());
    }

    public static synchronized String getCompatibilityDigest() {
        if (compatibilityDigest == null)
            throw new IllegalStateException("Inventory adapter startup registration is not closed");
        return compatibilityDigest;
    }

    public static synchronized List<InventoryUiAdapter> getRegisteredAdapters() {
        return Collections.unmodifiableList(new ArrayList<>(ADAPTERS.values()));
    }

    /**
     * Adds a local GUI presentation without changing binding, slots or transfers. Presentation-only registrations
     * remain available after startup and are intentionally excluded from protocol agreement.
     */
    public static synchronized void registerPresentation(String adapterId, String guiClassName, NativeUiLayout layout) {
        InventoryUiAdapter adapter = ADAPTERS.get(adapterId);
        if (adapter == null) throw new IllegalArgumentException("Unknown adapter id: " + adapterId);
        if (layout == null || guiClassName == null
            || !guiClassName.matches("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)+"))
            throw new IllegalArgumentException("A layout and fully qualified GUI class name are required");
        String key = adapterId + "\n" + guiClassName;
        if (PRESENTATIONS.containsKey(key) || guiClassName.equals(adapter.getGuiClassName()))
            throw new IllegalArgumentException("GUI presentation already registered: " + guiClassName);
        PRESENTATIONS.put(key, layout);
    }

    /** Explicit presentations win; the client may approve a narrow storage-only presentation fallback. */
    public static synchronized NativeUiLayout findLayout(Container container, Class<?> guiClass) {
        InventoryUiAdapter adapter = findAdapter(container);
        if (adapter == null || guiClass == null) return null;
        NativeUiLayout override = PRESENTATIONS.get(adapter.getId() + "\n" + guiClass.getName());
        if (override != null) return override;
        if (adapter.matchesGuiClass(guiClass)) return adapter.getLayout();
        return DynamicInventory.proxy == null ? null : DynamicInventory.proxy.automaticLayout(container, guiClass);
    }

    /** Returns the most specific explicit class registration, or null for an unsupported container. */
    public static synchronized InventoryUiAdapter findAdapter(Container container) {
        if (container == null) return null;
        Class<?> type = container.getClass();
        if (RESOLVED.containsKey(type)) return RESOLVED.get(type);
        InventoryUiAdapter best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (InventoryUiAdapter adapter : ADAPTERS.values()) {
            if (!adapter.matchesContainerClass(type)) continue;
            int distance = 0;
            for (Class<?> current = type; current != null; current = current.getSuperclass(), distance++) {
                if (!adapter.getContainerClassName()
                    .equals(current.getName())) continue;
                if (distance < bestDistance) {
                    best = adapter;
                    bestDistance = distance;
                }
                break;
            }
        }
        RESOLVED.put(type, best);
        return best;
    }

    public static PlayerInventoryBinding getBinding(Container container) {
        return container == null ? null : ContainerInventoryRegion.of(container);
    }

    /**
     * Custom actions check this on both peers. Server true means the client accepted the map and initial updates
     * can be released; client true also requires the final ready marker. This is not an ongoing callback audit.
     */
    public static boolean isOpeningReady(EntityPlayer player) {
        if (player == null) return false;
        PlayerInventoryBinding binding = getBinding(player.openContainer);
        if (binding != null && binding.getCapacityRevision() != InventoryLayout.of(player.inventory)
            .revision()) return false;
        if (player instanceof EntityPlayerMP) {
            EntityPlayerMP serverPlayer = (EntityPlayerMP) player;
            // Forge fake players have no play connection or remote peer to negotiate with. Preserve
            // their ordinary local automation path, including unregistered/default player containers.
            return serverPlayer.playerNetServerHandler == null || ServerInventoryGate.of(serverPlayer)
                .allowsInput(serverPlayer, player.openContainer.windowId);
        }
        return DynamicInventory.proxy.isClientOpeningReady(player);
    }

    /**
     * Local automatic-attachment failure, or null if none was recorded. This is diagnostic state, not permission
     * to open or synchronize a window: null also covers unknown containers and attachment not yet attempted.
     * The other peer may have constructed different slots despite matching startup declarations.
     */
    public static synchronized String getRefusalReason(Container container) {
        return REFUSED.get(container);
    }

    /**
     * Explicit early attachment for containers with a custom opening lifecycle. Call after constructing all native
     * slots
     * and before sending slot contents. Ordinary vanilla-style opening uses the registered lifecycle hooks
     * automatically.
     */
    public static PlayerInventoryBinding attach(Container container, InventoryPlayer playerInventory) {
        if (container == null || playerInventory == null) throw new NullPointerException("container/playerInventory");
        PlayerInventoryBinding existing = getBinding(container);
        if (existing != null) {
            if (existing.getPlayerInventory() != playerInventory)
                throw new IllegalArgumentException("Container is bound to a different player inventory");
            return existing;
        }
        InventoryUiAdapter adapter = findAdapter(container);
        if (adapter == null) throw new IllegalArgumentException(
            "No registered adapter for " + container.getClass()
                .getName());
        validate(container, playerInventory, adapter);
        ContainerInventoryRegion.attach(container, playerInventory, adapter.getAdditionalSlotFactory());
        synchronized (InventoryUi.class) {
            // Explicit attachment may succeed after a caller repairs a container refused by automatic attachment.
            REFUSED.remove(container);
        }
        return getBinding(container);
    }

    /** Internal lifecycle entry point. Refusal leaves slots alone; it does not abort the opening protocol. */
    public static PlayerInventoryBinding attachRegistered(Container container, InventoryPlayer playerInventory) {
        if (getBinding(container) != null) return getBinding(container);
        InventoryUiAdapter adapter = findAdapter(container);
        if (adapter == null || playerInventory == null) return null;
        synchronized (InventoryUi.class) {
            if (REFUSED.containsKey(container)) return null;
        }
        try {
            return attach(container, playerInventory);
        } catch (IllegalArgumentException invalid) {
            synchronized (InventoryUi.class) {
                REFUSED.put(container, invalid.getMessage());
            }
            DynamicInventory.LOG.warn(
                "Inventory UI adapter {} refused {} before changing slots: {}",
                adapter.getId(),
                container.getClass()
                    .getName(),
                invalid.getMessage());
            return null;
        }
    }

    /** Constructor-end helper for known containers containing exactly one player inventory. */
    public static PlayerInventoryBinding attachRegistered(Container container) {
        if (getBinding(container) != null) return getBinding(container);
        if (findAdapter(container) == null) return null;
        InventoryPlayer owner = null;
        for (Slot slot : container.inventorySlots) {
            if (!(slot.inventory instanceof InventoryPlayer)) continue;
            InventoryPlayer candidate = (InventoryPlayer) slot.inventory;
            if (owner != null && owner != candidate) return null;
            owner = candidate;
        }
        return owner == null ? null : attachRegistered(container, owner);
    }

    private static void validate(Container container, InventoryPlayer inventory, InventoryUiAdapter adapter) {
        if (adapter.getExpectedNativeSlots() >= 0
            && container.inventorySlots.size() != adapter.getExpectedNativeSlots())
            throw new IllegalArgumentException(
                "Expected " + adapter.getExpectedNativeSlots()
                    + " native slots; found "
                    + container.inventorySlots.size());
        if (inventory.mainInventory.length != InventoryLayout.of(inventory)
            .rawMainSlots())
            throw new IllegalArgumentException("Player backing array differs from its inventory shape");
        boolean[] seen = new boolean[InventoryLayout.of(inventory)
            .mainSlots()];
        for (Slot slot : container.inventorySlots) {
            if (slot.inventory instanceof InventoryPlayer && slot.inventory != inventory)
                throw new IllegalArgumentException("Container contains another player's inventory");
            if (slot.inventory != inventory) continue;
            int logical = InventoryLayout.of(inventory)
                .ordinaryIndex(slot.getSlotIndex());
            if (logical < 0) continue;
            if (seen[logical]) throw new IllegalArgumentException("Duplicate player index " + logical);
            seen[logical] = true;
        }
        for (int logical = 0; logical < InventoryLayout.LEGACY_MAIN_SLOTS; logical++)
            if (!seen[logical]) throw new IllegalArgumentException("Missing player index " + logical);
        int additions = 0;
        for (int logical = InventoryLayout.LEGACY_MAIN_SLOTS; logical < seen.length; logical++)
            if (!seen[logical]) additions++;
        // S30PacketWindowItems serializes the array length as a signed short too.
        if ((long) container.inventorySlots.size() + additions > Short.MAX_VALUE)
            throw new IllegalArgumentException("Expanded container exceeds the 32767-slot wire limit");
    }

    /** True only for an added ordinary player slot handled by an explicit extra-transfer policy. */
    public static boolean handlesAdditionalTransfer(Container container, int slotId) {
        PlayerInventoryBinding binding = getBinding(container);
        InventoryUiAdapter adapter = findAdapter(container);
        return binding != null && adapter != null
            && binding.getCapacityRevision() == InventoryLayout.of(binding.getPlayerInventory())
                .revision()
            && adapter.getTransferPolicy() != null
            && slotId >= binding.getNativeSlotCount()
            && slotId < container.inventorySlots.size()
            && binding.logicalFromContainerId(slotId) >= 0;
    }

    /** Uses the registered policy and the canonical binding; never invents machine-specific destinations. */
    public static ItemStack transferAdditional(Container container, EntityPlayer player, int slotId) {
        if (!handlesAdditionalTransfer(container, slotId) || player == null) return null;
        PlayerInventoryBinding binding = getBinding(container);
        if (binding.getPlayerInventory() != player.inventory) return null;
        return findAdapter(container).getTransferPolicy()
            .transfer(container, player, slotId, binding);
    }
}
