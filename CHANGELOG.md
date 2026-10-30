# Changelog

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
