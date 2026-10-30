# Glass UI resource-pack integration

Dynamic Inventory can read an optional color theme from the [Glass UI fork branch](https://github.com/gisi1037/Glass-UI/tree/codex/dynamic-inventory). The fork adapts Sampsa's GTNH Glass UI, based on Ranzu's [Shadow-UI](https://github.com/Ranzuu/Shadow-UI), for GTNH 2.9.0-RC-2 and Dynamic Inventory 0.8.0. The resource pack adds a small theme JSON file; its original raster artwork remains unchanged. Attribution and the upstream CC BY-NC-SA 4.0 license are retained in the fork.

The resource-pack fork is a separate repository. If cloned at `resourcepacks/Glass-UI/`, that directory is ignored by the mod repository. From a PowerShell terminal in the fork directory, run `./build-pack.ps1` to produce:

```text
resourcepacks/Glass-UI/dist/Glass-UI-Dynamic-Inventory.zip
```

Copy that ZIP into the active Minecraft instance's `resourcepacks` folder, then select it under **Options → Resource Packs**. The client reads the theme when Minecraft reloads resources, including when the resource pack selection changes. If the theme file is missing or invalid, Dynamic Inventory samples the active inventory texture for its basic panel palette. The built-in vanilla palette is the fallback if that texture cannot be read.

## Theme format

The resource location is `assets/dynamicinventory/inventory_theme.json`. The current format requires `schemaVersion: 1`, a non-empty `name`, and a `colors` object. Color values accept `#RRGGBB` or `#AARRGGBB`; the Glass UI fork uses the eight-digit alpha-first form so transparent fills and translucent scroll tracks can be represented. Missing or invalid individual colors use the active inventory texture palette, with vanilla defaults for settings text and controls.

The schema 1 color keys are:

| Key | Draws |
| --- | --- |
| `panelOuter`, `panelShadow`, `panelHighlight`, `panelFill` | Main panel border, depth edge, highlight, and fill |
| `playerFrame`, `playerFill` | Player model/preview frame and fill |
| `arrow`, `label` | Layout arrows and labels |
| `scrollTrack`, `scrollThumb` | Scrollbar track and handle |
| `settingsOuter`, `settingsFill`, `settingsText` | Layout settings controls |

The fork's complete palette is in [inventory_theme.json](https://github.com/gisi1037/Glass-UI/blob/codex/dynamic-inventory/assets/dynamicinventory/inventory_theme.json). Transparent panel centers reveal the world behind the screen. Ordinary inventory slot artwork continues to come from the active pack's vanilla inventory atlas; `playerFrame` and `playerFill` style the player model/preview frame.

The theme only changes client presentation. It does not change storage capacity, item positions, save data, or client/server inventory synchronization. Inventory borders are drawn as separate edges, so transparent panel centers and variable layouts do not require editing or stretching the original raster art. The Tinkers station view clips the obsolete fixed player-grid region from its existing background, including when a tall attached chest extends above the station's usual storage area. In short windows, the station reserves space for the player panel before choosing its chest layout, allowing oversized attached inventories such as compressed chests to use Tinkers' native scrolling layout. Native chest-slot coordinates are restored before each station draw and refreshed afterward to prevent frame-to-frame drift. Runtime qualification of these cases is pending.

Attached layouts remain the default. Panel-specific layout and style controls now
live in the inventory panel menu; the old Full box/Compact and Vertical/Scroll
settings have been removed. Transparent fills continue to reveal the world behind
the interface without changing inventory contents.

Earlier development checks exercised resource-pack frames, GUI scales and selected
container transfers. Their raw captures are local development records. See
[testing](testing.md) and the [changelog](../CHANGELOG.md) for current release
coverage; arbitrary resource packs and machine screens still need focused checks.

## Detached panels and existing packs

Detached panels keep slot art on the normal
`assets/minecraft/textures/gui/container/inventory.png` resource. In their panel
menu, **Style > Frame: Original** also uses that sheet for the fill, edges and
corners. A nine-slice keeps the corners intact while the panel changes size.
UVs remain in vanilla logical coordinates, so HD versions of that sheet work.
Resource reload updates textures and the sampled palette. No Dynamic Inventory
specific theme file is required for this basic support.

Unified flat/bevel styles remain available, using sampled colors unless a pack
supplies an explicit theme. The existing unified-bevel default is retained; choose
Original globally or per panel to prioritize native pack artwork. Original owns
its border/corners, so custom bevel controls are disabled. Machine content still
uses native rendering and resource locations. Packs that rearrange vanilla texture
regions may need a compatibility theme or adapter; sampling cannot infer a
different texture layout.
