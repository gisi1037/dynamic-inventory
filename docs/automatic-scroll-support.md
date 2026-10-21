# Conservative automatic scroll support

The first fallback is a **client presentation fallback for an already integrated
container**. It does not automatically expand unknown containers or infer their
transfer rules. Existing adapters and explicit presentations always win. The
normal server/client opening agreement still protects every expanded slot.

## Current contract

After native GUI initialization, the fallback checks all of the following:

- An existing canonical player binding and registered adapter are present.
- The host is exactly vanilla `ContainerChest`, `ContainerDispenser`, or
  `ContainerHopper`. Subclasses require review.
- Every GUI superclass up to `GuiContainer` declares only the standard background
  and foreground drawing methods. Custom controls, initialization, input,
  updates, helper methods, and overlay interfaces require manual integration.
- The initialized button list is empty apart from Bogo Sorter's exact bridged
  sort/config button class in a supported integration profile. Other controls
  require review. The frame is the standard 176 pixels wide.
- The original player slots are plain `Slot` objects in a complete nine-column,
  three-row grid, with a separate nine-slot hotbar. Storage slots must fit above
  that player section, and every added slot must retain the canonical owner.

Approval belongs to the individual container and is rechecked on GUI
initialization. The fallback uses nine columns, up to three visible storage rows,
texture crop 4, and the existing scrollbar and layout editor. It does not change
slot IDs, stack limits, item order, transfer policies, container construction,
packets, or the opening protocol. Standard native-host NEI transfers remain in use.

Disable it with `layout.automaticStorageScroll=false` in
`config/dynamicinventory-client.cfg`, then restart the client. Explicit integrations
continue to work independently of this setting. Explicit adapters remain free to
use Vertical/Scroll modes; this fallback stays compact.

## Catalog shortlist, 2026-10-20

Follow-up: the [reviewed native machine adapters](compatibility-patches/native-machines.md)
now implement the grate, Avaritia and Flood Lights candidates below, plus ordinary
furnaces and processing machines. Those are explicit per-mod integrations;
the automatic presentation fallback's narrow contract above is unchanged.
The table below preserves the initial source-triage findings. Use the live
catalog's integration/verification columns for current implementation progress.

The live catalog contained 773 non-MUI2 inventory GUI classes, with no manual
results entered when read. Container references in the catalog are class
references, **not verified runtime pairings**. These recommendations are source
triage, not proof of reachability or full-pack qualification.

| GUI | Mod | Record ID | Recommendation |
| --- | --- | --- | --- |
| Magic Box | Thaumcraft | GUI-D884BF37B4 | Excluded after handler review: client uses `ContainerChest`, server uses custom `ContainerMagicBox`; needs a paired integration |
| Container Chest Grate | Alchemical Grate | GUI-EC0C4846A5 | Best next adapter candidate: plain slots and dynamic native transfer bounds; not automatically enabled |
| Compressor | Avaritia | GUI-18613C12AF | Manual adapter: furnace output callback and fixed player-range branches |
| Neutron | Avaritia | GUI-A3931F3BA9 | Manual adapter: furnace output callback; added source IDs fall outside native branches |
| Carbon Floodlight | Flood Lights | GUI-7924A49A71 | Manual adapter: custom fuel slot; native `>=36` source check mistakes appended player slots for machine slots |
| Electric Floodlight | Flood Lights | GUI-C32474216F | Manual adapter: custom electric slot; same fixed machine-source boundary |
| Sound Recorder | RandomThings | GUI-8825BF9DE3 | No inventory adapter needed: compiled constructor adds zero slots |
| Soulforge | ThaumicHorizons | GUI-2BD1DF1CAF | No inventory adapter needed for this class: compiled constructor adds zero slots |
| Item Brew Bag GUI | Witchery | GUI-5909DE57AD | Review held-item restrictions, inventory identity, and transfers |

HEE's Void Chest is excluded from automatic support because it overrides
`transferStackInSlot`, despite inheriting vanilla `ContainerChest`. Other screens
with custom synchronization or input remain manual. MUI1 uses its existing bridge;
MUI2 is outside this catalog and fallback.

The separate CSV recommendation report uses the catalog's Record IDs so it can
be compared with reachability notes without overwriting the user's spreadsheet.
The initial triage found 25 existing/separate-scope entries, one presentation
candidate, eight prioritized reviews, and 739 entries requiring investigation.
The report now incorporates a follow-up inspection of the eight actual
container constructors/transfers, which ruled out Sound Recorder and Soulforge as
inventory integrations: neither constructor adds any slots. This does not prove
whether the screens themselves are reachable. The remaining candidates stay
behind manual adapter review; no class-name or method-count heuristic enables
their slots.
Handler review also disqualified the one presentation candidate: Magic Box's
server host differs from the class constructed by its client GUI. Thus no new
natural pack GUI is claimed as supported by this first narrow fallback yet.
Class references alone must never stand in for server/client host pairing.

## Checks and next step

Headless fixtures check drawing-only classes, inherited input rejection, wrong
signatures, overlay interfaces, player-grid coordinates, and layout caps. The
existing editor/geometry fixtures also run. These checks do not qualify every
listed GUI or prove actual mod reachability.

The full-pack smoke fixture can reuse Magic Box's drawing-only GUI over an
already opened vanilla chest (`StorageHarness automatic-scroll-screen`). This
retains the negotiated container identity on both peers and tests presentation
only. It does not qualify the mod's natural Magic Box opening.

This focused nightly smoke passed: nine columns, three visible storage rows,
81 canonical player slots, final expanded slot reachable, one tagged seven-item
stack shifted from that slot into the chest and back, authoritative server/client
count and NBT agreement, opening gate ready, and editor Cancel preserving the
binding. The 3,380 existing headless geometry cases and artifact checks also passed.
This is narrow presentation evidence, not full-pack or natural Magic Box qualification.

With development fixtures loaded, open `/dinvuiapi auto-scroll` in a disposable
`DI-Baseline-...Qualification` world. Its unregistered GUI uses vanilla
`ContainerChest`; verify the three-row viewport, final storage row, ordinary
clicks, shift-click, NEI transfers, resize/reopen, and editor Save/Cancel. A new
unknown container must still use an explicit reviewed adapter before receiving
any extra slots. Broader automation should add small audited behavior families,
not infer compatibility from the presence of 36 player slots.
