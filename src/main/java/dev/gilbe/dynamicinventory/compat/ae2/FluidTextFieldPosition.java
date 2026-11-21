package dev.gilbe.dynamicinventory.compat.ae2;

/** Move the field's text, border and cached tooltip hitbox as one native control. */
public interface FluidTextFieldPosition {

    int[] dynamicinventory$origin();

    void dynamicinventory$position(int x, int y);
}
