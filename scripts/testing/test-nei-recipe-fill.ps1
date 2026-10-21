param([Parameter(Mandatory)][string]$GameDirectory,[int]$Port=5006,[string]$EvidenceDirectory='.setup/nei-editor-smoke/recipes',[string]$WorldName='DI-Baseline-Nei-Editor-Smoke-Qualification',[switch]$UseOpenQualificationWorld)
$ErrorActionPreference='Stop'
Set-Location -LiteralPath (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent)
$world=$WorldName
$java='C:/Program Files/Java/jdk-25/bin/java.exe'
$diagnostics=Join-Path $GameDirectory 'diagnostics/dynamicinventory'
New-Item -ItemType Directory -Force -Path $EvidenceDirectory | Out-Null
$log=Join-Path $EvidenceDirectory 'harness.txt'
$checks=[Collections.Generic.List[string]]::new()
$report=[ordered]@{success=$false;world=$world;startedAtUtc=[datetime]::UtcNow.ToString('o');scope='focused-nei-fill-and-editor'}
function Harness {
    $result=& $java --add-modules jdk.jdi "-Ddinv.port=$Port" '-Ddinv.srgMapping=.setup/mcp-srg.srg' '-Ddinv.packInput=true' -cp .setup StorageHarness @args 2>&1
    $result | Add-Content -LiteralPath $log
    if($LASTEXITCODE -ne 0){throw "Harness failed: $args`n$($result -join "`n")"}
    return ($result -join "`n")
}
function Check([bool]$Condition,[string]$Message){if(!$Condition){throw $Message};$checks.Add($Message);Write-Host "PASS $Message"}
function Contents($state){return (@($state.activeContainer.slots | Select-Object containerSlot,item,count,damage,nbtCanonical) | ConvertTo-Json -Depth 10 -Compress)}
function Fresh([string]$Side,[datetime]$Since){
    $until=[datetime]::UtcNow.AddSeconds(10)
    do{$file=Get-ChildItem -LiteralPath $diagnostics -Filter "$Side-*.json" | Where-Object LastWriteTimeUtc -ge $Since | Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
        if($file){return $file};Start-Sleep -Milliseconds 100
    }while([datetime]::UtcNow -lt $until)
    throw "Missing $Side snapshot"
}
function Snapshot([string]$Label){
    $since=[datetime]::UtcNow;Harness command '/dinvbaseline' | Out-Null;$serverFile=Fresh server $since
    $since=[datetime]::UtcNow;Harness snapshot $Label | Out-Null;$clientFile=Fresh client $since
    $server=Get-Content -LiteralPath $serverFile.FullName -Raw | ConvertFrom-Json
    $client=Get-Content -LiteralPath $clientFile.FullName -Raw | ConvertFrom-Json
    Copy-Item -LiteralPath $serverFile.FullName -Destination (Join-Path $EvidenceDirectory "$Label-server.json") -Force
    Copy-Item -LiteralPath $clientFile.FullName -Destination (Join-Path $EvidenceDirectory "$Label-client.json") -Force
    Check ((Contents $server) -eq (Contents $client)) "Exact peer contents: $Label"
    return $client
}
try{
    if($UseOpenQualificationWorld) {
        if($world -notmatch '^DI-Baseline-Upgrades-.+-Qualification$'){throw 'Named disposable upgrade world required for a combined qualification run'}
    } else {
        if($world -notmatch '^DI-Baseline-Nei-Editor-Smoke(?:-[0-9]+)?-Qualification$'){throw 'Dedicated disposable NEI smoke world required'}
        if(Test-Path -LiteralPath (Join-Path $GameDirectory "saves/$world")){throw 'Use a fresh disposable NEI smoke world; this script does not clear an existing world.'}
        Harness world $world | Out-Null
    }
    Harness require-qualification $world | Out-Null
    Harness background | Out-Null
    Harness command '/gamerule doDaylightCycle false' | Out-Null
    Harness command '/time set 1000' | Out-Null
    Harness capture nei-editor-start | Out-Null
    Harness window 1920 1080 | Out-Null
    Harness scale 2 | Out-Null
    Harness resume | Out-Null
    $cases=@(@{name='workbench';x=2;block='minecraft:crafting_table'},@{name='station';x=8;block='TConstruct:CraftingStation'},@{name='attached';x=14;block='TConstruct:CraftingStation'})
    foreach($case in $cases){
        if($UseOpenQualificationWorld) {
            # Reuse only explicitly authorized disposable fixture coordinates, after loading their chunk.
            Harness command "/tp @p $($case.x + 0.5) 4 2.5" | Out-Null
            Harness command "/setblock $($case.x) 4 0 minecraft:air" | Out-Null
            if($case.name -eq 'attached'){Harness command '/setblock 13 4 0 minecraft:air' | Out-Null}
        }
        Harness command "/setblock $($case.x) 4 0 $($case.block) 0 keep" | Out-Null
        if($case.name -eq 'attached'){Harness command '/setblock 13 4 0 avaritiaddons:CompressedChest 0 keep' | Out-Null}
        Harness command '/clear @p Railcraft:slab' | Out-Null
        Harness command '/clear @p minecraft:diamond_block' | Out-Null
        Harness command '/give @p Railcraft:slab 64 8' | Out-Null
        Harness command "/tp @p $($case.x + 0.5) 4 2.5" | Out-Null
        Harness block-open $case.x 4 0 | Out-Null
        Harness gate-state ready | Out-Null
        $before=Snapshot "$($case.name)-before"
        Harness nei-open-recipe minecraft:diamond_block | Out-Null
        Harness gate-state detour | Out-Null
        Check $true "$($case.name): approved host retained; placeholder input blocked"
        Harness nei-fill-recipe | Out-Null
        Harness gate-state ready | Out-Null
        $filled=Snapshot "$($case.name)-filled"
        $grid=@($filled.activeContainer.slots | Where-Object { $_.containerSlot -ge 1 -and $_.containerSlot -le 9 -and $_.item -eq 'Railcraft:slab' })
        Check ($grid.Count -eq 2) "$($case.name): actual NEI overlay filled both enabled recipe inputs"
        $raw=@($filled.mainInventory | Where-Object item -eq 'Railcraft:slab' | Measure-Object count -Sum)[0].Sum
        $inGrid=@($grid | Measure-Object count -Sum)[0].Sum
        Check (($raw+$inGrid) -eq 64 -and !$filled.cursorOccupied) "$($case.name): all 64 ingredients conserved; cursor empty"
        Harness click 0 0 1 | Out-Null
        $crafted=Snapshot "$($case.name)-crafted"
        $slabs=@($crafted.mainInventory | Where-Object item -eq 'Railcraft:slab' | Measure-Object count -Sum)[0].Sum
        $blocks=@($crafted.mainInventory | Where-Object item -eq 'minecraft:diamond_block' | Measure-Object count -Sum)[0].Sum
        Check ($blocks -gt 0 -and ($slabs+2*$blocks) -eq 64 -and !$crafted.cursorOccupied) "$($case.name): result shift-click works after NEI; exact ingredient value retained"
        Harness nei-open-recipe minecraft:diamond_block | Out-Null
        Harness gate-state detour | Out-Null
        Harness nei-return | Out-Null
        Harness gate-state ready | Out-Null
        Check $true "$($case.name): Escape recipe return stays unlocked"
        Harness resume | Out-Null
    }
    $report.success=$true
}catch{$report.error=$_.Exception.Message;throw}finally{
    $report.checks=@($checks.ToArray());$report.checkCount=$checks.Count;$report.finishedAtUtc=[datetime]::UtcNow.ToString('o')
    $report | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $EvidenceDirectory 'verification.json')
}
