param([int]$Port=5015,[string]$GameDirectory='run/rc2-integration/client-developer',[string]$PlayerName='Developer',[string]$WorldName='DI-Baseline-Upgrades-RC2-Qualification',[string]$EvidenceDirectory='.setup/upgrades/runtime')
$ErrorActionPreference='Stop'
Set-Location (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent)
New-Item -ItemType Directory -Force $EvidenceDirectory | Out-Null
. (Join-Path (Split-Path $PSScriptRoot -Parent) 'java-runtime.ps1')
$java=(Resolve-JavaRuntime -MinimumMajor 25).Java
$checks=[Collections.Generic.List[string]]::new()
function Harness { $result=& $java --add-modules jdk.jdi "-Ddinv.port=$Port" -cp .setup StorageHarness @args; if($LASTEXITCODE){throw "Harness failed: $args"}; $result | Add-Content "$EvidenceDirectory/harness.txt" }
function Check([bool]$ok,[string]$label){if(!$ok){throw $label};$checks.Add($label);Write-Host "PASS $label"}
function Report([string]$action='state'){
    $path=Join-Path $GameDirectory "diagnostics/dynamicinventory/qualification/$PlayerName-upgrades-$action.json"
    $since=[datetime]::UtcNow; Harness command "/dinvupgradetest $action"
    $until=[datetime]::UtcNow.AddSeconds(15)
    do{if((Test-Path $path) -and (Get-Item $path).LastWriteTimeUtc -ge $since){$r=Get-Content $path -Raw|ConvertFrom-Json;break};Start-Sleep -Milliseconds 100}while([datetime]::UtcNow -lt $until)
    if(!$r -or !$r.success){throw "Missing/failed upgrade fixture $action $($r.failure)"}
    return $r
}
function State([int]$storage,[string]$label){
    Start-Sleep -Milliseconds 700
    Harness resize-state $storage
    $r=Report
    Check ($r.inventory.ordinarySlots -eq $storage+9) "Server capacity $label"
    Copy-Item (Join-Path $GameDirectory "diagnostics/dynamicinventory/qualification/$PlayerName-upgrades-state.json") "$EvidenceDirectory/$label.json" -Force
    return $r
}
Harness require-qualification $WorldName
Harness background
Harness command '/difficulty peaceful'
Harness command '/gamerule doMobSpawning false'
Harness command '/gamerule keepInventory true'
Harness command '/clear @p'
Harness hotbar-select 0
$start=State 72 initial
Check ($start.savedUpgrades -eq '{}') 'Fresh player has no upgrade account'
Harness command '/give @p dynamicinventory:capacity_module 3'
Harness command '/give @p minecraft:cobblestone 1'
Harness capture refused-first-purchase
Harness click 37 0 0
Harness use-held
$unpaid=State 72 refused-first-purchase
Check ($unpaid.savedUpgrades -eq '{}' -and $unpaid.inventory.mainInventory[0].count -eq 3) 'Refused first purchase creates no permanent metadata and consumes nothing'
Harness click 37 0 0
Harness resume
Harness command '/clear @p minecraft:cobblestone'
Harness use-held
$first=State 81 first-module
Check ($first.inventory.mainInventory[0].count -eq 2) 'One module consumed after successful resize'
Check ($first.savedUpgrades -match 'baseSlots:81' -and $first.savedUpgrades -match 'modulesUsed:1') 'Permanent base and consumption recorded'
Report equip | Out-Null
$equipped=State 90 belt-equipped
Check ($equipped.savedUpgrades -match 'baseSlots:81') 'Belt leaves permanent base unchanged'
# Actual mouse/packet placement in the new final slot, then native unequip guard.
Harness command '/give @p minecraft:diamond 7 0 {upgrade_fixture:"tail"}'
Harness capture upgrade-tail
Harness click 37 0 0
Harness click 108 0 0
$tail=State 90 occupied-tail
Check ($tail.inventory.mainInventory[-1].item -eq 'minecraft:diamond' -and $tail.inventory.mainInventory[-1].count -eq 7) 'Tagged tail stack placed in added capacity'
$r=Report remove
Check (!$r.canRemove) 'Normal belt removal refused while removed slot is occupied'
State 90 blocked-removal | Out-Null
Report force-remove | Out-Null
$forced=State 90 forced-removal
Check ($forced.inventory.mainInventory[-1].count -eq 7) 'Forced removal retains occupied capacity and items'
Harness capture clear-tail
Harness click 108 0 0
Harness click 37 0 0
State 81 deferred-shrink | Out-Null
# Cursor refusal consumes no module.
Harness capture cursor-refusal
Harness click 37 0 0
Harness use-held
$refused=State 81 cursor-refusal
Check ($refused.inventory.mainInventory[0].count -eq 2 -and $refused.savedUpgrades -match 'modulesUsed:1') 'Refused upgrade does not consume or grant'
Harness click 37 0 0
Harness resume
Harness use-held
$second=State 90 second-module
Check ($second.inventory.mainInventory[0].count -eq 1 -and $second.savedUpgrades -match 'modulesUsed:2') 'Second module commits once'
Report equip | Out-Null
State 99 belt-after-module | Out-Null
# Unloading below performs an actual integrated-server save.
Harness disconnect-safe $WorldName
Harness world $WorldName
$reload=State 99 reload
Check ($reload.savedUpgrades -match 'baseSlots:90' -and $reload.savedUpgrades -match 'modulesUsed:2') 'Reload restores base and bonus without double application'
Harness command '/kill @p'
Start-Sleep -Milliseconds 700
Harness respawn
$respawn=State 99 respawn
Check ($respawn.savedUpgrades -match 'baseSlots:90' -and $respawn.savedUpgrades -match 'modulesUsed:2') 'Keep-inventory respawn preserves permanent progress and one bonus'
Report remove | Out-Null
State 90 unequipped | Out-Null
# Administrative changes update the permanent base instead of fighting the next equipment poll.
Harness command '/dinvsize @p 207'
State 207 admin-base | Out-Null
Report equip | Out-Null
State 216 capped-belt | Out-Null
Harness use-held
$limit=State 216 no-usable-gain
Check ($limit.inventory.mainInventory[0].count -eq 1 -and $limit.savedUpgrades -match 'modulesUsed:2') 'At-cap module refused without consumption'
Report remove | Out-Null
State 207 cap-removal | Out-Null
Harness command '/dinvsize @p 216'
State 216 maximum-base | Out-Null
Report equip | Out-Null
State 216 capped-with-maximum-base | Out-Null
$rejected=Report admin-refusal
Check $rejected.adminRefusalPreservesBase 'Rejected same-total administrative command preserves permanent base'
State 216 after-admin-refusal | Out-Null
Report remove | Out-Null
State 216 maximum-base-after-removal | Out-Null
Harness command '/dinvsize @p 207'
State 207 ordinary-death-base | Out-Null
Report equip | Out-Null
State 216 ordinary-death-equipped | Out-Null
Harness command '/gamerule keepInventory false'
Harness command '/kill @p'
Start-Sleep -Milliseconds 700
Harness respawn
$death=State 207 ordinary-death
Check ($death.savedUpgrades -match 'baseSlots:207' -and $death.savedUpgrades -match 'modulesUsed:2') 'Ordinary death removes gear bonus and preserves permanent progress'
Harness command '/dinvsize @p 72'
State 72 restored | Out-Null
Harness disconnect-safe $WorldName
# Unloading below performs an actual integrated-server save.
@{success=$true;scope='offline RC-2 item and Baubles runtime';fullPackQualified=$false;checks=$checks.ToArray();completedAtUtc=[datetime]::UtcNow.ToString('o')}|ConvertTo-Json -Depth 6|Set-Content "$EvidenceDirectory/verification.json"
