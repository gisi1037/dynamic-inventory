param(
    [int] $Port=5007,
    [string] $GameDirectory='run/rc2-integration/client-developer',
    [string] $ServerGameDirectory='',
    [string] $WorldName='DI-Baseline-Containers-RC2-Qualification',
    [string] $EvidenceDirectory='docs/qualification/opening-gate/integrated',
    [string] $PeerMetadataPath='',
    [string] $ServerLaunchManifest='',
    [ValidateSet('rc2-integration','daily-2026-10-19')] [string] $Profile='rc2-integration'
)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
Set-Location -LiteralPath $projectRoot
. (Join-Path $PSScriptRoot 'qualification-evidence.ps1')
New-Item -ItemType Directory -Force -Path $EvidenceDirectory | Out-Null
$launchEvidence=Start-QualificationEvidence -ProjectRoot $projectRoot -Port $Port -GameDirectory $GameDirectory `
    -Profile $Profile -EvidenceDirectory $EvidenceDirectory -PeerMetadataPath $PeerMetadataPath `
    -ServerGameDirectory $ServerGameDirectory -ServerLaunchManifest $ServerLaunchManifest
$clientDiagnostics=Join-Path $GameDirectory 'diagnostics/dynamicinventory'
$serverDiagnostics=if($ServerGameDirectory){Join-Path $ServerGameDirectory 'diagnostics/dynamicinventory'}else{$clientDiagnostics}
$checks=[Collections.Generic.List[string]]::new()
function Harness {
    $result=& 'C:/Program Files/Java/jdk-25/bin/java.exe' --add-modules jdk.jdi "-Ddinv.port=$Port" -cp .setup StorageHarness @args
    if($LASTEXITCODE -ne 0){throw "Harness failed: $args"}
    $result | ForEach-Object {Write-Host $_}
}
function Check([bool]$Pass,[string]$Description){
    if(!$Pass){throw $Description}
    $checks.Add($Description)
}
function Read-Server {
    $since=[datetime]::UtcNow
    Harness command '/dinvgate status'
    $path=Join-Path $serverDiagnostics 'opening-server.json'
    $deadline=[datetime]::UtcNow.AddSeconds(15)
    while(!(Test-Path $path) -or (Get-Item $path).LastWriteTimeUtc -lt $since){
        if([datetime]::UtcNow -gt $deadline){throw 'No fresh server gate report'}
        Start-Sleep -Milliseconds 100
    }
    return Get-Content $path -Raw | ConvertFrom-Json
}
function Capture-Inventory([string]$Label){
    $since=[datetime]::UtcNow
    Harness capture $Label
    $path=Get-ChildItem "$clientDiagnostics/client-*.json" | Where-Object LastWriteTimeUtc -ge $since | Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
    if(!$path){throw 'Missing fresh inventory snapshot'}
    return Get-Content $path.FullName -Raw | ConvertFrom-Json
}
function Signature($State){return ($State.mainInventory | Select-Object inventoryIndex,item,count,damage,nbtCanonical | ConvertTo-Json -Depth 12 -Compress)}
if($ServerGameDirectory){Harness require-dedicated}
else{Harness require-qualification $WorldName}
Harness background
$before=Capture-Inventory 'gate-before'
Check (!$before.cursorOccupied) 'Test starts with an empty cursor'
$caseNames=@('match','server-refusal','client-refusal','mapping','timeout','malformed','budget','late-binding','populated','tinkers-oversized','actions')
$runtime=Get-Content (Join-Path $clientDiagnostics 'runtime.json') -Raw | ConvertFrom-Json
$cases=@($caseNames | Where-Object {$_ -ne 'tinkers-oversized' -or @($runtime.mods | Where-Object id -eq 'TConstruct').Count -gt 0})+@('match')
for($caseIndex=0;$caseIndex -lt $cases.Count;$caseIndex++){
    $case=$cases[$caseIndex]
    $expectedMode=[array]::IndexOf($caseNames,$case)
    $successCase=$case -in @('match','populated','tinkers-oversized','actions')
    Harness resume
    Harness command "/dinvgate $case"
    $deadline=[datetime]::UtcNow.AddSeconds(20)
    do {
        Harness opening-report
        $client=Get-Content (Join-Path $clientDiagnostics 'opening-client.json') -Raw | ConvertFrom-Json
        $settled=$client.mode -eq $expectedMode -and $(if($successCase){$client.open -and $client.ready -and $client.machineCount -eq $(if($case -eq 'tinkers-oversized'){300}else{7})}else{!$client.open})
        if(!$settled){Start-Sleep -Milliseconds 100}
    }while(!$settled -and [datetime]::UtcNow -lt $deadline)
    Check $settled "$caseIndex/$case opening settles in the expected state"
    $server=Read-Server
    Check ($server.clicks -eq 0 -and $client.clicks -eq 0) "$caseIndex/$case processes no pre-commit click on either peer"
    Check (!$server.cursorOccupied -and !$client.cursorOccupied) "$caseIndex/$case leaves both cursors empty"
    if($successCase){
        Check ($server.open -and $server.ready -and $server.machineCount -eq $client.machineCount) "$caseIndex/$case commits and delivers real contents"
        if($case -eq 'tinkers-oversized'){
            Check ($client.machineCount -eq 300) "$caseIndex/$case preserves the actual Tinkers custom snapshot after the byte-sized vanilla batch"
        }else{
            Check ($client.probed -and $client.predictionBlocked -and $client.forgedCommitBlocked) "$caseIndex/$case blocks prediction and rejects forged commit before acceptance"
            Check ($client.pendingInputBlocked -and $client.inputReady) "$caseIndex/$case freezes pending mouse and keyboard events and restores input after READY"
            if($case -eq 'actions'){
                Check ($server.actionsExpected -ge 4 -and $server.pendingActionsPassed -eq $server.actionsExpected) "$caseIndex/$case blocks every real installed Bogo/CraftingTweaks action before agreement"
                Harness command '/dinvgate actions-ready'
                $approved=Read-Server
                Check ($approved.readyActionsPassed -eq $approved.actionsExpected) "$caseIndex/$case positive controls prove every native action mutates after agreement"
                Harness opening-report
            }
            if($case -eq 'populated'){
                Check ($client.machineOccupiedSlots -eq 144 -and $client.machineTotalCount -eq 1008 -and $server.machineTotalCount -eq 1008) "$caseIndex/$case delivers every one of 144 occupied slots without exhausting the initial-sync budget"
            }
            # BogoSorter enables native extraction only inside its real mouse-event path.
            Harness input-click 0
            Harness opening-report
            $picked=Get-Content (Join-Path $clientDiagnostics 'opening-client.json') -Raw | ConvertFrom-Json
            $pickedServer=Read-Server
            Check ($picked.cursorOccupied -and $pickedServer.cursorOccupied -and $pickedServer.machineCount -eq 0) "$caseIndex/$case allows real clicks after commit"
            Harness input-click 0
        }
    }else{
        Check (!$server.open -and $client.machineUpdates -eq 0) "$caseIndex/$case closes both peers without applying any machine contents"
        Check ($server.machineCount -eq 7) "$caseIndex/$case preserves the server's seven-item payload"
    }
    Copy-Item (Join-Path $clientDiagnostics 'opening-client.json') (Join-Path $EvidenceDirectory "$caseIndex-$case-client.json") -Force
    Copy-Item (Join-Path $serverDiagnostics 'opening-server.json') (Join-Path $EvidenceDirectory "$caseIndex-$case-server.json") -Force
    Harness resume
    $after=Capture-Inventory "gate-after-$caseIndex"
    Check ((Signature $before) -eq (Signature $after) -and !$after.cursorOccupied) "$caseIndex/$case conserves canonical inventory and nested NBT"
}
Harness resume
$report=@{success=$true;scope=$(if($ServerGameDirectory){'source-dedicated'}else{'source-integrated'});fullPackQualified=$false;
    checkCount=$checks.Count;checks=@($checks);timeoutTest='Pending deadline advanced in disposable fixture; no production test flags';
    capturedAtUtc=[datetime]::UtcNow.ToString('o')}
foreach($key in $launchEvidence.Keys){$report[$key]=$launchEvidence[$key]}
$report | ConvertTo-Json -Depth 8 | Set-Content (Join-Path $EvidenceDirectory 'verification.json') -Encoding UTF8
Write-Host "PASS inventory opening gate: $($checks.Count) checks"
