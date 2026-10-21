# Avaritiaddons

**Loaded ID:** `avaritiaddons`. **Status:** compressed-chest adapter registered.

Code owner:
[`AvaritiaddonsCompatibility`](../../src/main/java/dev/gilbe/dynamicinventory/compat/AvaritiaddonsCompatibility.java).
Its existing adapter ID also identifies saved editor layouts; do not rename it
when reorganizing code. Registration remains behind the profile/mod presence gate.

Keep compressed-chest real storage, attached-station presentation and player
inventory distinct. Review infinity-chest or unusually large-stack behavior
separately; ordinary player slots must keep their normal stack limits. Prefer
shared chest geometry and explicit native transfer policies to a mod-specific renderer.

Verify real final storage slots, rectangular padding that is not clickable storage,
scrolling, both transfer directions, full destinations, attached station controls,
editor persistence and oversized native stack opening/refusal behavior.
