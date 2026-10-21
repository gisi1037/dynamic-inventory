# Tinkers' Construct

**Loaded ID:** `TConstruct`. **Status:** crafting station integrated; Furnace Slab
adapter added for `1.14.119-GTNH`. Other Tinkers screens are not covered merely by
sharing this mod ID. See the [native machine contract](native-machines.md).

Registration owner:
[`TinkersConstructCompatibility`](../../src/main/java/dev/gilbe/dynamicinventory/compat/TinkersConstructCompatibility.java).
Native station slot, drawing and recipe-position exceptions remain in the existing
`CraftingStationInventoryMixin`, `CraftingStationGuiMixin` and `TinkerRecipePositionMixin`.

Keep station and attached-inventory geometry in shared layout services. Add other
Tinkers families to this compatibility owner only after inspecting their slot roles
and callbacks; prefer API registrations over another rendering implementation.
Keep Traveler's Belt equipment/hotbar behavior separate from storage expansion.

Verify small/large attached chests, both transfer directions, crafting callbacks,
recipe controls, consistent panel width/style, editor save/cancel, and belt swaps.
Preserve the existing adapter ID and revision when only moving its declaration.
