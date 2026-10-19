package dev.gilbe.dynamicinventory.api;

/** Experimental API v1 immutable layout hints for a native inventory panel. */
public final class NativeUiLayout {

    public static final NativeUiLayout DEFAULT = new Builder().build();
    private final int topPadding;
    private final int minimumHeaderHeight;
    private final int backgroundInset;

    private NativeUiLayout(int topPadding, int minimumHeaderHeight, int backgroundInset) {
        this.topPadding = topPadding;
        this.minimumHeaderHeight = minimumHeaderHeight;
        this.backgroundInset = backgroundInset;
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

    public static final class Builder {

        private int topPadding;
        private int minimumHeaderHeight;
        private int backgroundInset = 3;

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

        public NativeUiLayout build() {
            return new NativeUiLayout(topPadding, minimumHeaderHeight, backgroundInset);
        }

        private static int bounded(int value, int min, int max, String name) {
            if (value < min || value > max) throw new IllegalArgumentException(name + " out of range");
            return value;
        }
    }
}
