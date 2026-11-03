# Changelog

## 0.8.0 Alpha 3 — 2026-11-02

Targets Minecraft 1.7.10 / GTNH 2.9.0. Install matching versions on client and
server and select `-Ddynamicinventory.profile=gtnh-2.9.0` on both sides.

- Added reviewed modular panel hosts for Iron Chests, Baby Chest, Compressed Chest,
  Forestry and HarvestCraft machines, AE2 machines and terminal/tool families,
  Et Futurum, Ender IO and Extra Utilities. Unsupported subclasses remain excluded.
- Added detached player panels to shared MUI2 interfaces, including machine-themed
  frames and slots. Corrected controller layouts and split-button placement.
- Corrected chest margins and texture crops, HarvestCraft header gaps, Forestry
  attach/detach spacing, GUI-scale anchors, and inventory placement after reopening.
  Automatic layouts keep player storage below machine/player sections.
- Kept compact and wide player sections vertically aligned and added dragging of
  the character/crafting section. Global attachment settings persist across hosts.
- Moved AE2 pins, wireless controls and Network Tool side panels away from player
  inventory panels; retained native upgrade slots, terminal switches and tooltips.
- Ground pickups and container-to-player transfers target Main and the active
  hotbar while detached, including offscreen Main rows. Attached mode restores
  normal automatic destinations across ordinary storage. Manual panel placement
  remains available.
- Scoped Main sorts to Main, protected saved hotbar rows from native BogoSorter
  buttons/hotkeys, and added the opposite-half Main fallback when the hotbar is full.
- Fixed AE2 Interface Terminal pattern extraction using raw inventory destinations
  that could select Backhand. It now uses canonical expanded player slots and
  inserts only when the whole pattern stack fits.
- Added immediate client hotbar swaps with ordered server confirmation, replay of
  pending swaps, and recovery after refused swaps. Older servers retain the
  response-based path until updated.
- Fixed panel settings remaining blocked by a stale modal after closing another
  native screen. Added build/install-without-launch tooling and offline test labs;
  Prism retains control of game Java selection.

Validation includes the production build and Checkstyle, focused headless panel,
Main-routing, hotbar prediction, packet, slot-identity and menu-lifecycle checks.
Manual visual acceptance was reported for chest, Forestry and the previously
supported AE2 interfaces. Newly added families still require gameplay testing.

Known issues: intermittent wireless terminal openings can still be refused when
AE2 constructs an empty placeholder instead of the expected terminal. A client
hang was also observed while returning from AE2 crafting confirmation during
debugger tracing; the available records do not establish its cause. These issues
are not claimed fixed by this release. Voluntary inventory-size reduction is not
included.

## 0.8.0 Alpha 2 — 2026-10-30

Updated GitHub prerelease, targeting Minecraft 1.7.10 / GTNH
2.9.0-nightly-2026-10-19, build 795. The development branch since the original
repository overview is included in this release.

- Named inventory panels with independent allocation, placement, resizing,
  visibility and styling; global profiles, interface overrides and scoped resets.
  Attached inventory remains the default.
- Modular inventory controls on all eleven vanilla container families, including
  double/ender/trapped chests, horse inventories, villagers and container minecarts,
  plus the declared Et Futurum replacements for vanilla screens.
- Attached inventory frames reserve scrollbar padding only when storage overflows.
  Shared chest/crafting layouts keep their native column budget stable while fitting.
- Corrected subpanel transfers with a full hotbar, including Mouse Tweaks' held-Shift
  path, and stable slot allocation when reserving swappable hotbar rows from Main.
- Direct hotbar-row bindings, a cycle binding and a rearrangement panel for reserved rows.
- Tiered belts with replacement textures and a UHV Creative Belt with configurable
  capacity; legacy creative-controller items retain a conversion path.
- Improved Baubles anchoring, panel clearance, dragging, edge snapping and weak alignment.
- Native/MUI integration hooks, compatibility profiles and developer layout audit/template tools.
- The Super Factory Manager MUI2 manager retains its compact machine footprint while
  extra player storage scrolls; unrelated MUI2 panels keep adaptive expansion.
- Reusable vanilla UI lab setup and focused native-screen, geometry, transfer and packet fixtures.

Focused development checks include thirteen original vanilla screen variants,
nineteen nightly block/entity openings, 3,380 attached-chest geometry cases, and
nightly chest/crafting-station border checks. These runtime checks were performed
on the development builds described in the qualification records; full gameplay,
multiplayer and arbitrary resource-pack qualification remains pending.

The release contains the production mod, development and source JARs. Local Minecraft instances,
world saves, screenshots, downloaded tools and raw qualification captures are not included.
