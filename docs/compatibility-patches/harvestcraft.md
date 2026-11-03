# HarvestCraft

Reviewed version: `1.3.15-GTNH`. Java owner: `HarvestCraftCompatibility`.

Exact native adapters and modular panels cover Churn, Oven, Quern, Animal Trap,
Fish Trap, Apiary, Grinder and Presser. They keep native recipe/bait routing,
filtered and output slots, callbacks and synchronization. The shared panel-host
policy admits only these exact registered GUI/container pairs at revision 3.
Open containers use HarvestCraft's native Shift-click path even when their player
inventory is split into panels.
Another version skips the optional integration. Market, cutting board and pot
remain separate contracts.

Attached frames back only the transparent top strip in Animal Trap, Fish Trap,
Apiary and Presser. Native artwork keeps its UVs and transparency elsewhere.
Attach/detach preserves global inheritance as a temporary view choice. Custom
layouts save fitted bounds in their current interface reference and reload those
profiles immediately, so toggling does not introduce a gap before reopening.

In shared nightly 803, `enablemeatGrinder=false` disables the Grinder block.
The lab leaves that pack setting intact and provides seven enabled stations plus
a disabled marker, behind the original chest stations. Fish Trap has a small
water surround; the test world does not change production recipes or capacity.

Run `scripts/testing/add-harvestcraft-panel-lab.ps1` once in the existing storage
qualification world, then `scripts/testing/test-harvestcraft-panel-lab.ps1`.
The setup checks that its area is empty and refuses to repeat installation.
The smoke exercises real block opening, canonical player mappings, panel creation,
allocation, dragging, persistence and live slot/container identity. Native
processing recipes still need manual gameplay review.
