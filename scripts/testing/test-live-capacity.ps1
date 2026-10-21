param(
    [Parameter(Mandatory=$true)] [int] $DeveloperPort,
    [Parameter(Mandatory=$true)] [int] $ObserverPort,
    [ValidateSet('rc2-integration','daily-2026-10-19')] [string] $Profile='rc2-integration',
    [string] $EvidenceDirectory='.setup/live-resize/runtime'
)
# Destructive fixture operations below are confined to the named disposable world.
# Start the two offline fixture clients and server separately; this script never drives a pack instance.
$ErrorActionPreference='Stop'
$root=Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
Set-Location -LiteralPath $root
. (Join-Path (Split-Path $PSScriptRoot -Parent) 'java-runtime.ps1')
$resizeRuntime=Resolve-JavaRuntime -MinimumMajor 25
$Java=$resizeRuntime.Java
$serverRoot=Join-Path $root "run/$Profile/server"
$properties=Get-Content (Join-Path $serverRoot 'server.properties') -Raw
if($properties -notmatch '(?m)^level-name=DI-Baseline-Live-Resize-Qualification\s*$') {
    throw 'Requires the dedicated DI-Baseline-Live-Resize-Qualification world.'
}
foreach($peer in @(@{name='Developer';port=$DeveloperPort},@{name='Observer';port=$ObserverPort})) {
    $metadata=Get-Content (Join-Path $root ".setup/peer-$($peer.name).json") -Raw | ConvertFrom-Json
    if($metadata.profile -ne $Profile -or $metadata.debugPort -ne $peer.port) {
        throw 'Ports must match the explicitly launched offline fixture peers for this profile.'
    }
}
& (Join-Path (Split-Path $Java -Parent) 'javac.exe') --add-modules jdk.jdi -d .setup scripts/testing/StorageHarness.java
if($LASTEXITCODE -ne 0){throw 'Could not compile the current client harness.'}
$evidence=Join-Path $root $EvidenceDirectory
New-Item -ItemType Directory -Force $evidence | Out-Null
$script:step=0
function Invoke-Harness([int]$Port,[string[]]$Action) {
    & $Java --add-modules jdk.jdi "-Ddinv.port=$Port" '-Ddinv.waitMs=20000' -cp .setup StorageHarness @Action
    if ($LASTEXITCODE -ne 0) { throw "Harness failed: $Port $Action" }
}
function Report([int]$Port,[string]$Player,[string]$Action) {
    $name=$Action.Split(' ')[0]
    $file=Join-Path $root "run/$Profile/server/diagnostics/dynamicinventory/qualification/$Player-server-$name.json"
    $before=if(Test-Path $file){(Get-Item $file).LastWriteTimeUtc.Ticks}else{0}
    Invoke-Harness $Port @('command',"/dinvqualify $Action")
    $deadline=[datetime]::UtcNow.AddSeconds(20)
    $result=$null
    while([datetime]::UtcNow -lt $deadline) {
        if((Test-Path $file) -and (Get-Item $file).LastWriteTimeUtc.Ticks -gt $before) {
            try { $result=Get-Content $file -Raw | ConvertFrom-Json } catch { $result=$null }
            if($null -ne $result){break}
        }
        Start-Sleep -Milliseconds 200
    }
    if($null -eq $result){throw "Missing fresh report $Action"}
    $script:step++
    Copy-Item $file (Join-Path $evidence "$script:step-$Player-$name.json")
    if(!$result.success){throw "Fixture failed $Action : $($result.failure)"}
    Write-Host "PASS server $Player $Action"
}
function State([int]$Port,[string]$Player,[int]$Storage) {
    Start-Sleep -Milliseconds 500
    Invoke-Harness $Port @('resize-state',"$Storage")
    Report $Port $Player "resize-check $Storage"
}
Invoke-Harness $DeveloperPort @('require-dedicated')
Invoke-Harness $ObserverPort @('require-dedicated')
State $DeveloperPort Developer 72
State $ObserverPort Observer 72
Report $DeveloperPort Developer seed
Report $ObserverPort Observer seed
Invoke-Harness $DeveloperPort @('command','/dinvsize Developer 90')
State $DeveloperPort Developer 90
Report $DeveloperPort Developer verify
State $ObserverPort Observer 72
Report $ObserverPort Observer verify
Report $DeveloperPort Developer 'resize-refuse 27'
State $DeveloperPort Developer 90
Report $DeveloperPort Developer chest
Start-Sleep -Milliseconds 600
Invoke-Harness $DeveloperPort @('gate-state','ready')
Invoke-Harness $ObserverPort @('command','/dinvsize Developer 108')
State $DeveloperPort Developer 108
Report $DeveloperPort Developer verify
Invoke-Harness $DeveloperPort @('command','/dinvsize Developer 72')
State $DeveloperPort Developer 72
Report $DeveloperPort Developer verify
Invoke-Harness $DeveloperPort @('command','/dinvsize Developer 90')
State $DeveloperPort Developer 90
Invoke-Harness $DeveloperPort @('disconnect')
Invoke-Harness $DeveloperPort @('connect','25566')
Start-Sleep -Seconds 2
State $DeveloperPort Developer 90
Report $DeveloperPort Developer verify
Report $DeveloperPort Developer 'kill-keep'
Invoke-Harness $DeveloperPort @('respawn')
Start-Sleep -Seconds 1
State $DeveloperPort Developer 90
Report $DeveloperPort Developer verify
Report $DeveloperPort Developer nether
State $DeveloperPort Developer 90
Report $DeveloperPort Developer verify
Report $DeveloperPort Developer overworld
State $DeveloperPort Developer 90
Report $DeveloperPort Developer verify
State $ObserverPort Observer 72
Report $ObserverPort Observer verify
Invoke-Harness $DeveloperPort @('command','/clear Developer')
Start-Sleep -Milliseconds 500
Invoke-Harness $DeveloperPort @('command','/dinvsize Developer 27')
State $DeveloperPort Developer 27
Invoke-Harness $DeveloperPort @('command','/dinvsize Developer 216')
State $DeveloperPort Developer 216
Invoke-Harness $DeveloperPort @('command','/dinvsize Developer 72')
State $DeveloperPort Developer 72
@{success=$true;scope="local $Profile fixtures, two players";serverChecks=$script:step;fullPackQualified=$false;completedAtUtc=[datetime]::UtcNow.ToString('o')} | ConvertTo-Json | Set-Content (Join-Path $evidence 'verification.json')
