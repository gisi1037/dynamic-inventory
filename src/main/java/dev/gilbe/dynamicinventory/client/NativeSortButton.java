package dev.gilbe.dynamicinventory.client;

import com.cleanroommc.bogosorter.common.sort.SlotGroup;

/** Internal bridge that keeps each native Bogo button attached to its actual inventory group. */
public interface NativeSortButton {

    SlotGroup dynamicinventory$sortGroup();
}
