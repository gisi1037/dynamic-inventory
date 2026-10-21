# Binnie's Mods

**Loaded ID:** `BinnieCore`. **Status:** inspection required; Forestry support
must not implicitly register Binnie screens. Future Java owner: `BinnieCompatibility`.

Audit each relevant Binnie module's machines and storage independently, including
attached-inventory discovery and custom slot widgets. Record the actual module
IDs required by a patch rather than treating `BinnieCore` as proof of every add-on.

Reuse common chest or MUI inventory services where applicable. Preserve native
storage capacities and transfer/filter policies; fix a proven shared layout issue
once in the common layer rather than cloning it into each Binnie screen.

Verify small/large attached inventories, priority when multiple neighbors exist,
final slots, scrolling, both transfer directions, typed NBT and native callbacks.
