# Inventory integration contract

This is the starting point for new integrations, whether maintained here or in
another mod. The inventory core is independent of the GUI framework. Prefer MUI2
for a new screen that you own; preserve an external screen's native behavior unless
a reviewed family migration demonstrably reduces maintenance. Follow the
[adapter guide](gui-adapter-guide.md) for registration and runnable examples.

## Ownership and identity

The server's `InventoryPlayer` owns the canonical ordinary inventory. The client
holds its synchronized counterpart. A container's live `Slot` objects reference
that inventory; a different screen must not create a second item store. The
binding belongs to one exact `InventoryPlayer` object, not merely a player name.

Keep these integer namespaces separate:

| Namespace | Meaning | Translation |
| --- | --- | --- |
| Logical ordinary index | Hotbar 0–8, then storage; excludes equipment | `InventoryLayout.rawIndex(logical)` inside this mod |
| Raw player index | Index interpreted by `InventoryPlayer`; Backhand reserves raw 36 | `InventoryLayout.ordinaryIndex(raw)` inside this mod |
| Container slot ID | Position in this particular `Container.inventorySlots` list | Public `PlayerInventoryBinding.getContainerSlotId(logical)` |

For example, with Backhand enabled, ordinary logical 36 maps to raw 37. Neither
number identifies its slot in a chest container. Use the binding for container
IDs; `logicalFromContainerId` returns -1 for a slot outside that binding. Public
additional-slot factories receive the already translated raw index: do not
translate it again. Keep existing native container IDs stable and append missing
ordinary slots only after validation.

Armor, Backhand offhand, Baubles, and Galacticraft equipment retain their own
owners and native rules. Do not identify armor by assuming that raw 36 is the end
of ordinary storage. Do not include equipment in ordinary storage transfers.

## Capacity, layout, and host ownership

Capacity is selected at server startup and agreed during login. The current
default is 72 storage slots plus nine hotbar slots, with supported storage capacity
27–216. This contract does not promise live or per-player capacity changes.

Layout changes positions, viewport size, and visible rows; it does not change
capacity, item identity, slot rules, or server authority. Auto/Compact/Wide/Tall
are presentation choices. A complete rectangular grid requires a column count
that divides the storage count. For example, 72 fits 9×8, 12×6, or 18×4; 73 cannot
form a compact complete rectangle. Use scrolling or an explicitly documented
fallback when a valid rectangle cannot fit. Do not silently add storage or omit
the last slots to make a picture fit. The current attached-chest implementation
selects exact divisors; a common cross-framework policy is not implemented.

The host is the container/screen integration that owns the surrounding controls.
For new integrations it should supply the player region's bounds and say whether
that region may grow or must scroll. Explicit shared host-bounds support is not exposed; existing integrations use framework-specific geometry.
Exactly one native or framework path owns item rendering, hit testing, input, and
synchronization. A decorative overlay must not add a second layer of live slots.

## Slot rules and transfers

Retain insertion and extraction restrictions, slot and item stack limits, dirty
notifications, and pickup callbacks. Additional-slot factories return fresh slots
backed by the supplied inventory and raw index; they must not mutate container
lists or perform unrelated side effects. Preparing slots before appending them
does not make arbitrary factory side effects reversible.

Transfer-policy arguments are container slot IDs. Generic helpers support
ordinary source slots and reviewed destination ranges. They cannot infer machine
priorities, recipe consumption, crafting result callbacks, achievements, or mod
events. Keep specialized result-slot sources on their native transfer path unless
the adapter explicitly reproduces and tests every required callback. GUI layout
code must not perform inventory transfers.

## Unknown, refused, and bound are different

| Observation | Meaning | Required response |
| --- | --- | --- |
| No registered adapter | Unknown container | Leave its slots and transfer paths unaugmented; extra storage remains accessible through a supported player screen |
| Adapter exists, no binding, no recorded refusal | Attachment has not succeeded or has not been attempted | Do not treat this as compatibility approval |
| `getRefusalReason(container)` is non-null | Local automatic attachment failed | Diagnose the failure; do not qualify or ship that integration as safe |
| Binding exists | Local attachment succeeded | Qualify both peers, input, transfers, and native behavior; this alone does not prove runtime agreement |

`attach` throws on an invalid explicit attachment. The player-specific
`attachRegistered` overload records an automatic validation failure and returns
null. The constructor-end overload can also return null without recording a reason
when it cannot identify exactly one player inventory. Refusal is local diagnostic
state, **not a multiplayer opening rejection**. Repeated automatic attempts keep
the refusal; a successful explicit retry that creates a binding clears it. F9 audit reports expose the
reason and do not mark a refused container as a structural candidate.

The registered native opening path now adds a separate runtime agreement. The
server attaches before listener registration, offers a fingerprint, and buffers
ordinary contents/slot/property/transaction updates until the client accepts and
the server commits. A final ready marker follows the buffered initial updates so
client prediction stays locked until the initial cursor/slot batch is complete.
Both peers block window clicks while pending. A refused
binding, differing slot map, malformed exchange, timeout, or buffer overflow closes
the affected window and discards its pending updates. It does not disconnect the
player or change canonical capacity. Startup protocol 3 rejects older peers that
lack this gate.

Server `EntityPlayerMP` actors with a null play network handler, including Forge's
standard fake players, have no remote GUI to negotiate. They retain their native
local container actions; `InventoryUi.isOpeningReady` returns true for them. The
gate must not dereference a missing handler or require a client acknowledgment
from these actors. Other automation implementations still need their own review.

In the pinned RC-2 integration, pending native windows also freeze GUI mouse/keyboard
actions (Escape can still close them). BogoSorter's sort, shortcut, hotbar-swap and
debug slot-action requests check the shared opening permission before their direct
slot writes. CraftingTweaks 1.0.3 checks it in all five scheduled game-thread action
callbacks: rotate, balance/spread, clear, compress/decompress and transfer. These
explicit integrations leave unrelated mod traffic and unknown containers on their
native paths; they do not provide a universal custom-packet firewall.

The fingerprint covers the adapter/revision, exact container class, native/total
slot counts, live slot IDs/classes/raw indices, backing-inventory alias groups,
and logical-to-container mapping. Item contents, coordinates, GUI class, and theme
are excluded. Server/client dummy backing inventories may differ in Java class,
but their alias grouping must agree. This is intentionally conservative: a host
using a different client wrapper topology may need an explicit integration change.
Hashing the mapping does not prove callback behavior; compatibility revisions and
behavioral tests remain required.

Each connection owns its own state. A monotonic opening nonce prevents reused
window IDs or delayed replies from approving another opening. Pending agreement
expires after 100 player ticks and retains at most 128 packets plus the container's slot
count, with an absolute 4 MiB serialized-update limit. The slot allowance accommodates
vanilla's initial full snapshot followed by one delta per occupied slot. Close,
replacement, and disconnect release pending state. Commit precedes
contents in the same ordered play-packet stream, and ready follows them. The persistent
player-inventory window 0 and unknown containers retain their existing path. A registered
custom client container can temporarily have ID 0 during construction; it stays locked
until its open handler assigns the real window ID and agreement completes. Registered
custom containers must use standard nonzero window IDs, rather than taking ownership
of the persistent player window.

The pinned Tinkers crafting-station integration defers its oversized-stack custom
snapshot while pending, including its native synchronization-cache updates. After
releasing standard packets, the gate performs one native change-detection pass before
ready, so that custom counts above 127 correct the vanilla byte-sized snapshot before
client prediction unlocks. This is an explicit hook for that supported native path;
other custom synchronization still needs its own reviewed integration.

Custom opening protocols must attach before constructing a contents packet. The
boundary guard also covers standard packets from listener overrides; a short
packet constructed before attachment is refused. Custom click/button/transfer
handlers must check `InventoryUi.isOpeningReady(player)` on the game thread,
immediately before any mutation. Checking a network receiver before scheduling
its work is insufficient: the active window can change before that work executes.
Entirely custom synchronization packets, direct backing-inventory writes, and
unregistered MUI1/MUI2 screens are outside this native gate. They need their own
reviewed framework integration; do not claim universal interception.

An unknown GUI also does not isolate the rest of its mod from the globally enlarged
player inventory. A known incompatible serializer, grave, refill, or armor consumer
needs a bridge or a refused unsupported profile; a GUI fallback cannot contain it.

## Qualification before registration becomes support

1. Inspect the exact container, GUI, slot owners, index mappings, transfer dispatch,
   result callbacks, and optional dependency versions. F9/scaffold output is evidence
   for review, never automatic activation.
2. Use an explicit registration or the shared framework binding/factory. Register
   compatible declarations on both peers before load-complete; bump the adapter's
   compatibility revision when binding or transfer behavior changes.
3. Test pickup/place, shift-click both ways, scroll moves, filtered/output slots,
   final/offscreen storage, cursor and nested NBT conservation, and GUI resizing.
4. Test integrated and dedicated server behavior, including a deliberately refused
   opening on either peer. A local refusal test does not prove safe network abort.
5. Retain exact artifact/profile evidence for compatibility or release claims.
   Early visual previews remain explicitly unqualified.
