package dev.gilbe.dynamicinventory.client;

import java.awt.Point;

/** Internal bridge for native buttons that recalculate their position from a relative offset. */
public interface NativeButtonOffset {

    Point dynamicinventory$buttonOffset();
}
