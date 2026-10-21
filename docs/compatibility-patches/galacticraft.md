# Galacticraft

**Loaded ID:** `GalacticraftCore`. **Status:** native equipment screen integrated;
other machines and Galacticraft add-ons need their own review.

Registration owner:
[`GalacticraftCompatibility`](../../src/main/java/dev/gilbe/dynamicinventory/compat/GalacticraftCompatibility.java).
The existing `GalacticraftInventoryMixin` handles its native container exception.
String GUI targets keep client classes out of common registration code.

Retain separate equipment and ordinary-player roles, native tab/header space and
equipment transfer restrictions. Reuse general layout/editor controls for geometry.
Audit additional machines through their actual native/MUI builders before adding
any Galacticraft-specific hook.

Verify both tabs, header/crop at multiple GUI scales, equipment acceptance/removal,
ordinary/offhand boundaries, shift-click/wheel in both directions, save/reload and
matching server/client maps. Test add-ons separately from Galacticraft Core.
