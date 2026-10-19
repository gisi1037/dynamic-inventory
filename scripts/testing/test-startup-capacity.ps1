param(
    [int[]] $Capacities = @(72,27,73,216,72),
    [string] $EvidenceDirectory = 'docs/qualification/startup-capacity/matrix',
    [switch] $IncludeUiApi
)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent)
$java='C:/Program Files/Java/jdk-25/bin/java.exe'
$game='run/rc2-integration/client-developer'
$evidence=$EvidenceDirectory
$world='DI-Baseline-Capacity-Matrix-Qualification'
New-Item -ItemType Directory -Force -Path $evidence | Out-Null
. (Join-Path $PSScriptRoot 'qualification-evidence.ps1')
@{success=$false;status='running';capturedAtUtc=[datetime]::UtcNow.ToString('o')} |
    ConvertTo-Json | Set-Content (Join-Path $evidence 'verification.json')
$checks=[Collections.Generic.List[string]]::new()
$launchIds=[Collections.Generic.List[string]]::new()
$matrixArtifact=$null
$matrixDevelopmentArtifact=$null
function Harness {
    $out=& $java --add-modules jdk.jdi '-Ddinv.port=5007' -cp .setup StorageHarness @args
    if($LASTEXITCODE -ne 0){throw "Harness failed: $args"}
    $out | ForEach-Object { Write-Host $_ }
    return ($out -join "`n")
}
function Report([string] $Command, [string] $Name) {
    $since=[datetime]::UtcNow
    Harness command $Command | Out-Null
    $path=Join-Path $game "diagnostics/dynamicinventory/qualification/Developer-$Name.json"
    $deadline=[datetime]::UtcNow.AddSeconds(20)
    do {
        if((Test-Path $path) -and (Get-Item $path).LastWriteTimeUtc -gt $since){
            $report=Get-Content $path -Raw | ConvertFrom-Json
            if(!$report.success){throw "Failed $Command : $($report.failure)"}
            return $report
        }
        Start-Sleep -Milliseconds 150
    } while([datetime]::UtcNow -lt $deadline)
    throw "No fresh report: $Command"
}
function UiReport([string] $Command, [string] $FileName) {
    $since=[datetime]::UtcNow
    Harness command $Command | Out-Null
    $path=Join-Path $game "diagnostics/dynamicinventory/$FileName.json"
    $deadline=[datetime]::UtcNow.AddSeconds(20)
    do {
        if((Test-Path $path) -and (Get-Item $path).LastWriteTimeUtc -gt $since){
            $report=Get-Content $path -Raw | ConvertFrom-Json
            if(!$report.success){throw "Failed $Command : $($report.failure)"}
            return $report
        }
        Start-Sleep -Milliseconds 100
    } while([datetime]::UtcNow -lt $deadline)
    throw "No fresh report: $Command"
}
$step=0
foreach($storage in $Capacities){
    $metadata=Get-Content .setup/peer-Developer.json -Raw | ConvertFrom-Json
    if(Get-Process -Id $metadata.pid -ErrorAction SilentlyContinue){throw 'Close the fixture before changing startup capacity'}
    $config=Join-Path $game 'config/dynamicinventory.cfg'
    $text=Get-Content $config -Raw
    if($text -notmatch 'I:storageSlots=\d+'){throw 'Expected generated startup capacity setting'}
    $text=[regex]::Replace($text,'I:storageSlots=\d+',"I:storageSlots=$storage")
    Set-Content $config $text
    & ./scripts/testing/start-peer-client.ps1 -PlayerName Developer -Profile rc2-integration -DebugPort 5007
    $deadline=[datetime]::UtcNow.AddSeconds(90)
    while(!(Select-String -Path .setup/peer-Developer.log -Pattern 'Forge Mod Loader has successfully loaded' -Quiet)){
        if([datetime]::UtcNow -gt $deadline){throw 'Client startup timed out'}
        Start-Sleep -Milliseconds 500
    }
    # Every cold boot must execute the same immutable code/fixture snapshot family.
    # A current build/libs hash alone cannot identify what an earlier JVM loaded.
    $bootEvidence=Join-Path $evidence "launch-$step-$storage"
    $launch=Start-QualificationEvidence -ProjectRoot (Get-Location).Path -Port 5007 -GameDirectory $game `
        -Profile rc2-integration -EvidenceDirectory $bootEvidence
    if($null -eq $matrixArtifact){
        $matrixArtifact=$launch.artifactSha256
        $matrixDevelopmentArtifact=$launch.developmentArtifactSha256
    } elseif($matrixArtifact -ne $launch.artifactSha256 -or $matrixDevelopmentArtifact -ne $launch.developmentArtifactSha256){
        throw 'The code artifacts changed between capacity cold boots'
    }
    $launchIds.Add($launch.launchId)
    $target=if($storage -eq 72){54}else{72}
    Harness handshake-check $target | Out-Null
    $checks.Add("$storage startup: outbound advertisement, read-only ping, pre-play negotiation, missing metadata rejection")
    $info=Harness capacity-info
    if($info -notmatch "configuredStorage=$storage storage=$storage ordinary=$($storage+9)"){throw 'Startup capacity mismatch'}
    Harness background | Out-Null
    Harness world $world | Out-Null
    $deadline=[datetime]::UtcNow.AddSeconds(40)
    do {
        $status=Harness screen-status
        if($status -notmatch 'health=none'){break}
        if([datetime]::UtcNow -gt $deadline){throw 'Player did not join fixture'}
        Start-Sleep -Milliseconds 300
    }while($true)
    Harness command '/difficulty peaceful' | Out-Null
    Harness command '/gamerule doMobSpawning false' | Out-Null
    $capacity=Report '/dinvcapacitytest' capacity
    if($capacity.storageSlots -ne $storage -or $capacity.inventory.mainSlots -ne $storage+10){throw 'Backing inventory mismatch'}
    if($step -eq 0 -and $capacity.inventory.mainInventory[81].item -ne 'minecraft:diamond'){
        $capacity=Report '/dinvcapacitytest seed' capacity
    }
    if($storage -lt 72){
        if($capacity.overflowItems -ne 1){throw 'Shrinking did not preserve exactly one overflow stack'}
    } else {
        $stack=$capacity.inventory.mainInventory[81]
        if($stack.item -ne 'minecraft:diamond' -or $stack.count -ne 7 -or $stack.nbtCanonical -notmatch 'persistent final slot' -or $capacity.overflowItems -ne 0){throw 'Growing lost or duplicated the persistent payload'}
    }
    $capacity | ConvertTo-Json -Depth 32 | Set-Content "$evidence/$step-storage-$storage.json"
    $adapters=Report '/dinvadapters selftest' adapters-selftest
    $adapters | ConvertTo-Json -Depth 32 | Set-Content "$evidence/$step-storage-$storage-adapters.json"
    $checks.Add("$storage storage: canonical allocation, migration, persistence and $($adapters.passed.Count) native transfer assertions")
    if ($IncludeUiApi) {
        $uiApi=UiReport '/dinvuiapi selftest' 'ui-api-selftest'
        $uiApi | ConvertTo-Json -Depth 12 | Set-Content "$evidence/$step-storage-$storage-ui-api.json"
        $muiApi=UiReport '/dinvmuiapi selftest' 'mui2-api-selftest'
        $muiApi | ConvertTo-Json -Depth 12 | Set-Content "$evidence/$step-storage-$storage-mui2-api.json"
        Harness resume | Out-Null
        $after=Report '/dinvcapacitytest' capacity
        if ($after.overflowItems -ne $capacity.overflowItems) { throw 'UI API checks changed quarantined overflow items' }
        if (($after.inventory.mainInventory | ConvertTo-Json -Depth 20 -Compress) -ne ($capacity.inventory.mainInventory | ConvertTo-Json -Depth 20 -Compress)) {
            throw 'UI API checks changed canonical saved inventory contents'
        }
        $checks.Add("$storage storage: public native API $($uiApi.checkCount) checks and MUI2 factory $($muiApi.checks) assertions preserve inventory and overflow")
    }
    Harness disconnect-safe $world | Out-Null
    Harness quit | Out-Null
    $fixturePid=(Get-Content .setup/peer-Developer.json -Raw | ConvertFrom-Json).pid
    Wait-Process -Id $fixturePid -Timeout 40 -ErrorAction SilentlyContinue
    if(Get-Process -Id $fixturePid -ErrorAction SilentlyContinue){throw 'Fixture JVM did not exit'}
    $bootReport=@{success=$true;scope='source-capacity-cold-boot';storageSlots=$storage}
    foreach($key in $launch.Keys){$bootReport[$key]=$launch[$key]}
    $bootReport | ConvertTo-Json -Depth 8 | Set-Content (Join-Path $bootEvidence 'verification.json')
    $step++
}
@{success=$true;prototype='0.8.0-dev';capacities=$Capacities;checks=$checks;includesUiApi=[bool]$IncludeUiApi;
    jarSha256=$matrixArtifact;developmentArtifactSha256=$matrixDevelopmentArtifact;launchIds=@($launchIds)} |
    ConvertTo-Json -Depth 10 | Set-Content "$evidence/verification.json"
Write-Host "PASS startup capacity matrix: $($checks.Count) checks"
