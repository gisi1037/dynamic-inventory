package dev.gilbe.dynamicinventory.api;

/** Experimental API v1 immutable layout hints for a native inventory panel. */
public final class NativeUiLayout {

    public static final NativeUiLayout DEFAULT = new Builder().build();
    private final int topPadding;
    private final int minimumHeaderHeight;
    private final int backgroundInset;
    private final int fixedPlayerColumns;
    private final int maximumVisibleRows;
    private final boolean followMachineMode;
    private final int presentationRevision;

    private NativeUiLayout(int topPadding, int minimumHeaderHeight, int backgroundInset, int fixedPlayerColumns,
        boolean followMachineMode, int maximumVisibleRows, int presentationRevision) {
        this.topPadding = topPadding;
        this.minimumHeaderHeight = minimumHeaderHeight;
        this.backgroundInset = backgroundInset;
        this.fixedPlayerColumns = fixedPlayerColumns;
        this.followMachineMode = followMachineMode;
        this.maximumVisibleRows = maximumVisibleRows;
        this.presentationRevision = presentationRevision;
    }

    public static Builder builder() {
        return new Builder();
    }

    public int getTopPadding() {
        return topPadding;
    }

    public int getMinimumHeaderHeight() {
        return minimumHeaderHeight;
    }

    public int getBackgroundInset() {
        return backgroundInset;
    }

    /** Zero allows editor columns; a fixed width keeps item columns stable while visible rows change. */
    public int getFixedPlayerColumns() {
        return fixedPlayerColumns;
    }

    /** Use the existing Machines: Vertical/Scroll preference instead of an editor row count. */
    public boolean followsMachineMode() {
        return followMachineMode;
    }

    /** Zero leaves row fitting unrestricted; a cap retains a compact native host footprint. */
    public int getMaximumVisibleRows() {
        return maximumVisibleRows;
    }

    /** Bump only when saved presentation geometry becomes incompatible, independently of transfers. */
    public int getPresentationRevision() {
        return presentationRevision;
    }

    public static final class Builder {

        private int topPadding;
        private int minimumHeaderHeight;
        private int backgroundInset = 3;
        private int fixedPlayerColumns;
        private int maximumVisibleRows;
        private int presentationRevision = 1;
        // Registered external screens share the same Vertical/Scroll preference by default.
        private boolean followMachineMode = true;

        public Builder topPadding(int value) {
            topPadding = bounded(value, 0, 4096, "topPadding");
            return this;
        }

        public Builder minimumHeaderHeight(int value) {
            minimumHeaderHeight = bounded(value, 0, 4096, "minimumHeaderHeight");
            return this;
        }

        public Builder backgroundInset(int value) {
            backgroundInset = bounded(value, 0, 8, "backgroundInset");
            return this;
        }

        public Builder fixedPlayerColumns(int value) {
            if (value != 0) bounded(value, 3, 18, "fixedPlayerColumns");
            fixedPlayerColumns = value;
            return this;
        }

        public Builder followMachineMode(boolean value) {
            followMachineMode = value;
            return this;
        }

        public Builder maximumVisibleRows(int value) {
            maximumVisibleRows = bounded(value, 0, 12, "maximumVisibleRows");
            return this;
        }

        public Builder presentationRevision(int value) {
            if (value < 1) throw new IllegalArgumentException("presentation revision must be positive");
            presentationRevision = value;
            return this;
        }

        public NativeUiLayout build() {
            return new NativeUiLayout(
                topPadding,
                minimumHeaderHeight,
                backgroundInset,
                fixedPlayerColumns,
                followMachineMode,
                maximumVisibleRows,
                presentationRevision);
        }

        private static int bounded(int value, int min, int max, String name) {
            if (value < min || value > max) throw new IllegalArgumentException(name + " out of range");
            return value;
        }
    }
}
