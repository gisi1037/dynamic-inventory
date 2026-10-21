# In-game GUI editor (first development version)

Open a supported **native** container GUI, put down any item held by the cursor, and press
**F10** (Controls → Dynamic Inventory Diagnostics → Edit inventory GUI layout).
The current chest, crafting-station or other registered native adapter must already be working.
This editor changes its presentation; it does not register an unsupported container.

## Controls

The toolbar has **Layout**, **Sections**, **Style**, **Advanced**, and **Screen** pages.

- **Columns / Visible rows:** preferred player-inventory viewport dimensions. The existing layout
  engine fits them to the scaled window; the toolbar shows the resulting dimensions. Rows beyond
  that viewport retain the existing scrolling behavior. Player grids use rectangular divisors,
  so their fitted width can differ from the requested width. An attached chest instead fills
  the available panel width; Tinkers draws inactive padding if its last row is incomplete.
- **Panel X / Y:** offsets from the centered position, in scaled GUI pixels. Drag anywhere inside
  the blue outline outside the toolbar to move the whole panel. Clicks on slots are drag handles
  during editing and cannot pick up their items.
- **Section gap (Layout):** `Auto` keeps the adapter's original spacing; zero removes extra spacing after
  the native content. On a crafting station with an attached chest, it controls the space between its two bands.
- **Content gap (Sections):** spacing between player and crafting sections in a compound screen.
  It controls horizontal spacing side by side, or vertical spacing when stacked on small screens.
  It remains effective when the chest above is wider than both sections combined. It is disabled
  for adapters without a separate side-content section.
- **Player X / Y (Sections):** move the player grid, hotbar, inventory caption and sort controls as one section.
  Negative Y moves it up, independently of the chest/machine contents.
- **Native X / Y (Sections):** move the native chest/machine section independently. The compound Tinkers
  attached-chest adapter currently owns this group's placement, so these controls are disabled there.
- **Drag: Panel / Player / Native:** choose the group to drag; the blue outline marks that group.
- **Frame style (Style):** Original retains the native border and current global frame preference.
  Unified flat uses matching square edges; Unified bevel uses matching stepped corners and an outer
  outline. Both unified styles crop the original sheet's border by at least four pixels and draw the
  shared frame using the active resource-pack palette. They join the normal native/player frame even
  when the global preference is Compact; attached-chest screens retain their two separate panels.
  Original restores the ordinary Texture crop behavior. Style is a per-GUI presentation preference
  and follows the same preview, Save, Reset and Cancel rules as positioning.
- **Border width (Style):** zero to six scaled pixels for either unified frame style. Zero keeps
  the fill without an outline. Original uses the resource pack's border and disables this control.
- **Top margin / Bottom margin (Screen):** reserve space outside the complete interface, for example
  for NEI's search field. Defaults are eight and 28 scaled pixels. Increasing a margin reduces
  visible inventory rows and enables scrolling as needed, including the attached chest during live
  preview. These are screen limits; Top padding instead adds space inside the native header.
  Fixed native controls still keep their minimum size if the screen cannot fit both margins.
- **Top padding:** room above the native section, such as inventory tabs.
- **Minimum header (Advanced):** a minimum height, not an offset. Auto uses measured native geometry;
  the first increase starts at that height and has a visible effect. Use Section gap to reduce spacing.
- **Texture crop (Advanced):** crop the native sheet's edges, including Tessellator-rendered compressed-chest
  frames. Split stations also allow cropping their crafting sheet; the attached storage frame and scrollbar
  keep their own geometry. Cropping is disabled for compact single-frame modes that own their border styling. Hover controls
  to see explanations. Top padding and minimum header are also disabled in the attached-chest layout.
  Furnaces and vanilla crafting tables default to crop 4 and fixed nine-column player storage.
  Saved crop overrides remain intact; Reset previews the adapter defaults.
- **Mode: Vertical / Scroll (toolbar):** preview either player-inventory presentation on any registered
  native screen. Vertical grows to fit the available height; Scroll shows up to three rows. Both scroll
  when needed and keep the same column preference. The switch is temporary: Save/Cancel restores the global
  **Machines: Vertical/Scroll** preference. Geometry edits are shared by both modes; there are no separate
  saved copies. MUI1/MUI2 already follow the global preference; this editor still edits native screens only.
- **Shift + the +/- buttons:** move/resize padding by eight pixels per click.
- **Hide controls / F10:** preview with the toolbar collapsed. This keeps the editor modal.
- **Save:** persist the layout and return to ordinary GUI interaction.
- **Cancel / Escape:** discard the preview and return to the previously saved layout.
- **Reset:** preview the adapter's hints and the external baseline: nine columns, up to eight rows.
  Personal player-menu Wide/Compact preferences do not affect external screens. Save commits that
  reset; Cancel still restores the earlier saved layout.

Both the toolbar and preview stay over the real screen. The editor does not replace the screen,
reopen its container, reconstruct its slots, change capacity, or send inventory clicks.
If the real screen/container/slot identities change, its draft is discarded. A pending opening
or cursor-held item pauses editing. Server-driven item updates can still arrive normally.

## Saved patches

Files are local to the client under:

```text
<Minecraft game directory>/config/dynamicinventory/ui-layouts/<target-sha256>.json
```

Each schema-5 JSON file contains a `target` and `layout`. Original schema-1/2/3/4 layouts still load;
missing newer fields receive neutral offsets, automatic spacing, Original styling and an eight-pixel
content gap. Missing screen margins use eight/28 pixels; missing border width uses two pixels.
The target records adapter ID, presentation revision, exact container/screen classes and ordinary
player capacity. Native chest slot count is no longer part of the presentation target: edits saved
to a Tinkers station apply to its ordinary and compressed chest variants. Different adapters,
player capacities, presentation revisions or screen classes retain separate files. Transfer-only
compatibility revision changes no longer invalidate saved geometry. No inventory contents are saved.
Server opening and slot checks remain independent. Invalid/oversized files are ignored with a warning.

If no current file exists, compatible legacy geometry is read while presentation revision is 1.
A shared family edit takes precedence over an older exact chest variant; other variants do not
match. Conflicting legacy edits at the same priority are left for manual selection, with a warning,
rather than picking a file arbitrarily. Save promotes a migrated edit to schema 5. Old files remain intact.
Save replaces the file atomically where the filesystem supports it.

Each launcher instance has its own game directory. RC-2 layouts do not automatically appear
in a separate nightly instance. When transferring them, match presentation revision and player
capacity, back up both folders, and choose which instance's settings take precedence.

To share a layout, copy its JSON file into another client's same folder with the same tested
adapter and capacity, then restart that client. Files are cached during a session. To revert,
delete its file while Minecraft is closed, or use Reset followed by Save in game. These files are
not the Java adapter scaffolds produced by the F9 audit/template workflow.

## Scope and next steps

This first slice supports the shared native decorator, including attached ordinary/compressed
chests in the Tinkers crafting station. Its controls affect the player viewport, group placement and
section spacing. Native section offsets move its slots, artwork and controls together; they do not
change how those widgets work. The station's chest sizing uses the same saved player dimensions
when opened and during live preview. The native chest recalculates positions and visible rows
without replacing its slots or their contents.

The lower player/content layout sets the preferred width of both station panels. Larger attached
inventories add rows within that width and use the native vertical scrollbar when height runs out.
Chest capacity does not choose a narrower divisor or expand the lower frame. Live previews refit the native
viewport as well as the player section; scrolling retains access to the complete stored inventory.

Crafting Tweaks buttons follow the crafting grid as a group, and NEI's invisible Recipes hotspot
follows the native header/arrow. This uses section anchors rather than a separate widget editor.

MUI1/MUI2 widget editors, individual widget dragging, named presets, undo/redo and Java/API export
are not implemented yet. The custom player menu also has its own layout engine and is not part of
this first editor. F9 remains the discovery tool for an unsupported interface. A layout file cannot
bypass adapter registration or the client/server opening safety gate.

## Focused development checks

From the repository root, prepare the fixtures and run their headless check with one command:

```powershell
./scripts/testing/test-native-editor.ps1 -Profile rc2-integration -Build
```

The nightly source profile is also explicit:

```powershell
./scripts/testing/test-native-editor.ps1 -Profile daily-2026-10-19 -Build
```

This uses the configured JDK 25+ (`JAVA_HOME`, otherwise `java` and `javac` on `PATH`, then
the shared runtime helper's local fallback). `-Build` calls the existing Gradle
`writeFixtureClientLaunch` task with `inventoryStorageTests`; it resolves the selected profile's
dependencies, builds the development JAR and `packTest` classes, and exports their offline
classpath. Close running fixture clients before using `-Build`, since they can hold that JAR open.
Dependency downloads may require network access on a fresh checkout.

Omit `-Build` to repeat the check using already prepared outputs. By default it reads
`.setup/<profile>-offline-client-launch.json`; `-LaunchManifest` accepts another export with the
same profile and this workspace's `packTest` classes. Rebuild after source or dependency changes:
an existing classpath export does not prove its compiled outputs are current.

The script runs the plain Java main
`dev.gilbe.dynamicinventory.qualification.NativeEditorFixtures`, which checks strict JSON/target
rejection, immutable integer bounds, live resize budgets and the attached-chest geometry matrix.
It starts no Minecraft client/server and requires no world, display or harness connection. Its
output and `verification.json` go to `.setup/native-editor-checks/<profile>` (override with
`-EvidenceDirectory`); a failure replaces the prior success status. These are source checks,
with `fullPackQualified=false`; they do not certify rendered UI, mouse input, persistence in a
running game, the installed mod JAR, or nightly full-pack qualification.

For the separate live check, in an isolated disposable world, open a registered native GUI
with an occupied visible slot and run the harness action
`editor-fixtures`. That client fixture exercises previews, drag, modal input, Cancel, Save/cache
reload and Reset/Cancel, while checking that slot identities and stack contents survive.
Fixture classes are excluded from the shipped mod JAR.

## Useful manual checks

These complement automated slot, transfer and persistence checks:

- Switch Original, Unified flat and Unified bevel. Check texture brightness, borders, transparency,
  native artwork, and whether player/crafting sections still look like one consistent interface.
- Resize the window and try your usual GUI scales, including a small window. Check tabs, captions,
  NEI panels, scrollbars and small buttons for clipping or overlap.
- Compare baby, normal, double and compressed chests, both alone and beside a crafting station.
  Scroll to the final row and check that inactive padding looks intentional and cannot hold items.
- Drag each editor section near the screen edges. Check for jumps, drift, confusing hit areas,
  unreadable toolbar placement, and offsets that make the interface awkward to use.
- Save a distinctive layout, reopen the screen, and restart the same instance. Check Cancel and
  Reset separately. A different launcher instance uses a separate set of saved patches.
- Hold items while hovering equipment slots and buttons. Watch for flicker, disappearing icons,
  tooltip obstruction, or a pointer location that does not match its highlighted slot.
- Use normal NEI recipe/overlay navigation, Baubles and Galacticraft tabs, Traveler's Belt swaps,
  and your usual sorting shortcuts. Check that focus returns to the expected screen afterward.
- Try real crafting and machine workflows with your normal modded items, filters and NBT-bearing
  tools. Report any surprising behavior even if simple diamonds or cobblestone work correctly.

For a report, include the block/GUI, pack build, resource pack, resolution, GUI scale, frame style,
whether the editor is open, the action that caused the issue, and a screenshot when useful.
