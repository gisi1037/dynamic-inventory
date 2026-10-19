package dev.gilbe.dynamicinventory.api;

import java.util.regex.Pattern;

/** Experimental API v1 declarative binding between a container class and the dynamic inventory UI. */
public final class InventoryUiAdapter {

    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Pattern CLASS_NAME = Pattern
        .compile("[A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*(?:\\$[A-Za-z_$][A-Za-z0-9_$]*)*");

    private final String id;
    private final String containerClassName;
    private final String guiClassName;
    private final boolean includeSubclasses;
    private final int expectedNativeSlots;
    private final NativeUiLayout layout;
    private final InventoryTransferPolicy transferPolicy;
    private final PlayerInventorySlotFactory additionalSlotFactory;
    private final boolean customAdditionalSlots;
    private final int compatibilityRevision;

    private InventoryUiAdapter(Builder builder) {
        id = builder.id;
        containerClassName = builder.containerClassName;
        guiClassName = builder.guiClassName;
        includeSubclasses = builder.includeSubclasses;
        expectedNativeSlots = builder.expectedNativeSlots;
        layout = builder.layout;
        transferPolicy = builder.transferPolicy;
        additionalSlotFactory = builder.additionalSlotFactory;
        customAdditionalSlots = builder.customAdditionalSlots;
        compatibilityRevision = builder.compatibilityRevision;
    }

    public static Builder builder(String id, String containerFQCN) {
        return new Builder(id, containerFQCN);
    }

    public String getId() {
        return id;
    }

    public String getContainerClassName() {
        return containerClassName;
    }

    public String getGuiClassName() {
        return guiClassName;
    }

    public boolean isIncludingSubclasses() {
        return includeSubclasses;
    }

    public int getExpectedNativeSlots() {
        return expectedNativeSlots;
    }

    public NativeUiLayout getLayout() {
        return layout;
    }

    /** Returns null when nativeTransfers() was selected. */
    public InventoryTransferPolicy getTransferPolicy() {
        return transferPolicy;
    }

    public PlayerInventorySlotFactory getAdditionalSlotFactory() {
        return additionalSlotFactory;
    }

    public boolean hasCustomAdditionalSlots() {
        return customAdditionalSlots;
    }

    public int getCompatibilityRevision() {
        return compatibilityRevision;
    }

    /** Matches by class name only; it never loads classes reflectively. */
    public boolean matchesContainerClass(Class<?> type) {
        if (type == null) return false;
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            if (containerClassName.equals(current.getName())) return true;
            if (!includeSubclasses) break;
        }
        return false;
    }

    /** GUI matching is deliberately exact, including when container subclass matching is enabled. */
    public boolean matchesGuiClass(Class<?> type) {
        return type != null && guiClassName != null && guiClassName.equals(type.getName());
    }

    public static final class Builder {

        private final String id;
        private final String containerClassName;
        private String guiClassName;
        private boolean includeSubclasses;
        private int expectedNativeSlots = -1;
        private NativeUiLayout layout = NativeUiLayout.DEFAULT;
        private boolean transferChoiceMade;
        private InventoryTransferPolicy transferPolicy;
        private PlayerInventorySlotFactory additionalSlotFactory = PlayerInventorySlotFactory.plainSlot();
        private boolean customAdditionalSlots;
        private int compatibilityRevision = 1;

        private Builder(String id, String containerFQCN) {
            if (id == null || !ID.matcher(id)
                .matches() || id.contains("..")) throw new IllegalArgumentException("Invalid adapter id");
            if (containerFQCN == null || !CLASS_NAME.matcher(containerFQCN)
                .matches()) throw new IllegalArgumentException("Invalid container class name");
            this.id = id;
            this.containerClassName = containerFQCN;
        }

        public Builder gui(String guiFQCN) {
            if (guiFQCN == null || !CLASS_NAME.matcher(guiFQCN)
                .matches()) throw new IllegalArgumentException("Invalid GUI class name");
            guiClassName = guiFQCN;
            return this;
        }

        public Builder includeSubclasses() {
            includeSubclasses = true;
            return this;
        }

        public Builder expectedNativeSlots(int value) {
            if (value < 36 || value > 32767) throw new IllegalArgumentException("expectedNativeSlots out of range");
            expectedNativeSlots = value;
            return this;
        }

        public Builder layout(NativeUiLayout value) {
            if (value == null) throw new IllegalArgumentException("layout must not be null");
            layout = value;
            return this;
        }

        public Builder extraTransfers(InventoryTransferPolicy policy) {
            if (policy == null) throw new IllegalArgumentException("policy must not be null");
            chooseTransfer(policy);
            return this;
        }

        public Builder nativeTransfers() {
            chooseTransfer(null);
            return this;
        }

        /** Preserve custom native player-slot rules for added slots; defaults to ordinary vanilla slots. */
        public Builder additionalSlots(PlayerInventorySlotFactory factory) {
            if (factory == null) throw new IllegalArgumentException("additional slot factory must not be null");
            additionalSlotFactory = factory;
            customAdditionalSlots = true;
            return this;
        }

        /** Bump when binding, added-slot or transfer behavior changes. Both sides must declare the same revision. */
        public Builder compatibilityRevision(int value) {
            if (value < 1) throw new IllegalArgumentException("compatibility revision must be positive");
            compatibilityRevision = value;
            return this;
        }

        private void chooseTransfer(InventoryTransferPolicy policy) {
            if (transferChoiceMade) throw new IllegalStateException("Transfer policy choice was already made");
            transferChoiceMade = true;
            transferPolicy = policy;
        }

        public InventoryUiAdapter build() {
            if (!transferChoiceMade)
                throw new IllegalStateException("Choose nativeTransfers() or extraTransfers(policy)");
            return new InventoryUiAdapter(this);
        }
    }
}
