param([Parameter(Mandatory)][string]$GameDirectory,[int]$Port=5006,[string]$EvidenceDirectory='.setup/vanilla-gui-smoke',[switch]$Resume,[int]$StartAt=0,[int]$EndAt=14)
$ErrorActionPreference='Stop'
Set-Location -LiteralPath (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent)
$world='DI-Baseline-Vanilla-Guis-Qualification'
$java='C:/Program Files/Java/jdk-25/bin/java.exe'
$diagnostics=Join-Path $GameDirectory 'diagnostics/dynamicinventory'
New-Item -ItemType Directory -Force -Path $EvidenceDirectory | Out-Null
$checks=[Collections.Generic.List[string]]::new()
$nonce=[guid]::NewGuid().ToString('N')
$report=[ordered]@{success=$false;fullPackQualified=$false;scope='vanilla native opening and appended-slot shift transfers';world=$world;cases=@();startedAtUtc=[datetime]::UtcNow.ToString('o')}
function Harness{
    $out=& $java --add-modules jdk.jdi "-Ddinv.port=$Port" '-Ddinv.srgMapping=.setup/mcp-srg.srg' '-Ddinv.packInput=true' -cp .setup StorageHarness @args 2>&1
    $out | Add-Content -LiteralPath (Join-Path $EvidenceDirectory 'harness.txt')
    if($LASTEXITCODE -ne 0){throw "Harness failed: $args`n$($out -join "`n")"};return ($out -join "`n")
}
function Check([bool]$condition,[string]$message){if(!$condition){throw $message};$checks.Add($message);Write-Host "PASS $message"}
function Snapshot([string]$label){
    $since=[datetime]::UtcNow
    Harness command '/dinvbaseline' | Out-Null
    $until=[datetime]::UtcNow.AddSeconds(10)
    do{$serverFile=Get-ChildItem -LiteralPath $diagnostics -Filter server-*.json | Where-Object LastWriteTimeUtc -ge $since | Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
        if(!$serverFile){Start-Sleep -Milliseconds 100}
    }while(!$serverFile -and [datetime]::UtcNow -lt $until)
    if(!$serverFile){throw 'Missing server snapshot'}
    $since=[datetime]::UtcNow
    Harness snapshot $label | Out-Null
    $clientFile=Get-ChildItem -LiteralPath $diagnostics -Filter client-*.json | Where-Object LastWriteTimeUtc -ge $since | Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
    if(!$clientFile){throw 'Missing client snapshot'}
    $server=Get-Content -LiteralPath $serverFile.FullName -Raw | ConvertFrom-Json
    $client=Get-Content -LiteralPath $clientFile.FullName -Raw | ConvertFrom-Json
    Copy-Item -LiteralPath $serverFile.FullName -Destination (Join-Path $EvidenceDirectory "$label-server.json") -Force
    Copy-Item -LiteralPath $clientFile.FullName -Destination (Join-Path $EvidenceDirectory "$label-client.json") -Force
    $serverItems=@($server.activeContainer.slots | Select-Object containerSlot,item,count,damage,nbtCanonical) | ConvertTo-Json -Depth 10 -Compress
    $clientItems=@($client.activeContainer.slots | Select-Object containerSlot,item,count,damage,nbtCanonical) | ConvertTo-Json -Depth 10 -Compress
    Check ($serverItems -eq $clientItems -and !$server.cursorOccupied -and !$client.cursorOccupied) "Exact peer items and empty cursor: $label"
    return $client
}
function Probe($slot){return $slot.nbtCanonical -and $slot.nbtCanonical.Contains($nonce)}
try{
    if((Test-Path -LiteralPath (Join-Path $GameDirectory "saves/$world")) -and !$Resume){throw 'This smoke requires a fresh dedicated world unless explicitly resuming its fixtures'}
    if($Resume){
        # Reuse only this exact disposable world; world itself rejects replacing any open world.
        try{Harness require-qualification $world | Out-Null}catch{Harness world $world | Out-Null}
    }else{Harness world $world | Out-Null}
    Harness require-qualification $world | Out-Null
    Harness background | Out-Null
    Harness command '/gamerule doDaylightCycle false' | Out-Null
    Harness command '/gamerule doMobSpawning false' | Out-Null
    Harness command '/time set 1000' | Out-Null
    Harness capture vanilla-start | Out-Null
    Harness window 1920 1080 | Out-Null
    Harness scale 2 | Out-Null
    Harness resume | Out-Null
    $cases=@(
        @{name='chest';block='chest';id='chest'},@{name='ender-chest';block='ender_chest';id='chest'},
        @{name='workbench';block='crafting_table';id='workbench'},@{name='furnace';block='furnace';id='furnace'},
        @{name='dispenser';block='dispenser';id='dispenser';probe='cobblestone';count=16;dest=0},
        @{name='dropper';block='dropper';id='dispenser'},
        @{name='hopper';block='hopper';id='hopper';probe='cobblestone';count=16;dest=0},
        @{name='brewing';block='brewing_stand';id='etfuturum/brewing-stand';probe='nether_wart';count=16;dest=3},
        @{name='enchantment';block='enchanting_table';id='etfuturum/enchantment';probe='iron_sword';count=1;dest=0},
        @{name='anvil';block='anvil';id='anvil';probe='iron_sword';count=1;dest=0},
        @{name='beacon';block='beacon';id='beacon';probe='emerald';count=1;dest=0},
        @{name='merchant';entity='Villager';selector='DI-Villager';id='merchant';probe='dirt';count=16;dest=-1;nbt='Profession:0,Offers:{Recipes:[{buy:{id:388,Count:1b,Damage:0s},sell:{id:297,Count:1b,Damage:0s},maxUses:999,uses:0}]}'},
        @{name='horse';entity='EntityHorse';selector='DI-Horse';id='horse';probe='saddle';count=1;dest=0;sneak=$true;nbt='Type:0,Tame:1b,Age:0'},
        @{name='donkey';entity='EntityHorse';selector='DI-Donkey';id='horse';probe='dirt';count=16;dest=2;sneak=$true;nbt='Type:1,Tame:1b,ChestedHorse:1b,Age:0'},
        @{name='mule';entity='EntityHorse';selector='DI-Mule';id='horse';sneak=$true;nbt='Type:2,Tame:1b,ChestedHorse:1b,Age:0'}
    )
    for($index=$StartAt;$index -lt $cases.Count -and $index -le $EndAt;$index++){
        $nonce=[guid]::NewGuid().ToString('N');$case=$cases[$index];$name=$case.name;$x=2+4*($index%6);$z=6*[math]::Floor($index/6)
        $case.x=$x;$case.z=$z
        Harness resume | Out-Null
        if($case.block){Harness command "/setblock $x 4 $z minecraft:$($case.block) 0 keep" | Out-Null}
        else{Harness command ("/summon $($case.entity) $($x+0.5) 4 $($z+0.5) {CustomName:`"$($case.selector)`",PersistenceRequired:1b,Attributes:[{Name:generic.movementSpeed,Base:0.0d}],$($case.nbt)}") | Out-Null}
        if($case.probe){Harness command ("/give @p minecraft:$($case.probe) $($case.count) 0 {dinv_vanilla:`"$nonce`"}") | Out-Null}
        Harness command "/tp @p $($x+0.5) 4 $($z+2.5)" | Out-Null
        if($case.entity){Harness sneak ([string][bool]$case.sneak).ToLowerInvariant() | Out-Null;Harness entity-open $case.selector | Out-Null;Harness sneak false | Out-Null}
        else{Harness block-open $x 4 $z | Out-Null}
        Harness gate-state ready | Out-Null
        $binding=Harness ui-binding
        Check ($binding -match "adapter=dynamicinventory:$($case.id) native=([0-9]+) ordinary=81") "${name}: exact adapter and expanded canonical binding"
        Harness editor-open | Out-Null
        Harness editor-control 101 | Out-Null
        if($case.probe){
            $before=Snapshot "$name-before"
            $source=@($before.activeContainer.slots | Where-Object {Probe $_})
            Check ($source.Count -eq 1 -and $source[0].count -eq $case.count) "${name}: unique tagged source stack"
            $extra=@($before.activeContainer.slots | Where-Object {$_.role -eq 'additional-storage'})[0].containerSlot
            Harness click $source[0].containerSlot 0 0 | Out-Null
            Harness click $extra 0 0 | Out-Null
            Harness click $extra 0 1 | Out-Null
            $sent=Snapshot "$name-sent"
            $dest=@($sent.activeContainer.slots | Where-Object {Probe $_})
            Check ($dest.Count -eq 1 -and $dest[0].count -eq $case.count -and $dest[0].containerSlot -ne $extra) "${name}: appended-source shift-click moves complete tagged stack"
            if($case.dest -ge 0){
                Check ($dest[0].containerSlot -eq $case.dest) "${name}: native input/equipment priority preserved"
                Harness click $case.dest 0 1 | Out-Null
                $returned=Snapshot "$name-returned"
                $back=@($returned.mainInventory | Where-Object {Probe $_})
                Check ($back.Count -eq 1 -and $back[0].count -eq $case.count) "${name}: native-to-player shift-click restores tagged stack"
            }
        }
        if($case.name -in @('enchantment','anvil','beacon','merchant','horse')){Harness screenshot "step3-vanilla-$name.png" | Out-Null}
        $report.cases+=@{name=$name;adapter=$case.id;x=$x;y=4;z=$z;testedTransfer=[bool]$case.probe}
    }
    Harness resume | Out-Null
    Harness command '/tp @p 10 4 6' | Out-Null
    $report.success=$true
}catch{$report.error=$_.Exception.Message;throw}finally{
    $report.checks=@($checks.ToArray());$report.checkCount=$checks.Count;$report.finishedAtUtc=[datetime]::UtcNow.ToString('o')
    $report | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath (Join-Path $EvidenceDirectory 'verification.json')
}
