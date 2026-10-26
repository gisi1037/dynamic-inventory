package dev.gilbe.dynamicinventory.compat.ae2;

import net.minecraft.client.gui.GuiButton;

/** Buttons whose native terminal recalculates their positions when a processing pattern is inverted. */
public interface AppliedEnergisticsMovingButtons {

    GuiButton[] dynamicinventory$movingButtons();
}
