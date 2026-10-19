package dev.gilbe.dynamicinventory.api.client;

/**
 * Optional client bridge for GUIs that cache absolute header/widget origins. Values are x/y pairs.
 * Capture returns a fresh array; positioning receives a fresh translated copy, preserving the original native geometry.
 */
public interface NativeUiAnchors {

    int[] captureHeaderOrigins();

    void positionHeaderOrigins(int[] origins);
}
