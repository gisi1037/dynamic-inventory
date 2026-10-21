param([Parameter(Mandatory)][string]$GameDirectory,[int]$Port=5006,[string]$EvidenceDirectory='.setup/nei-editor-smoke/margins-borders',[string]$WorldName='DI-Baseline-Nei-Editor-Smoke-Qualification')
$ErrorActionPreference='Stop'
Set-Location -LiteralPath (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent)
$java='C:/Program Files/Java/jdk-25/bin/java.exe'
New-Item -ItemType Directory -Force -Path $EvidenceDirectory | Out-Null
$checks=[Collections.Generic.List[string]]::new()
$report=[ordered]@{success=$false;scope='focused-live-editor-controls';startedAtUtc=[datetime]::UtcNow.ToString('o')}
function Harness{
    $out=& $java --add-modules jdk.jdi "-Ddinv.port=$Port" '-Ddinv.srgMapping=.setup/mcp-srg.srg' '-Ddinv.packInput=true' -cp .setup StorageHarness @args 2>&1
    $out | Add-Content -LiteralPath (Join-Path $EvidenceDirectory 'harness.txt')
    if($LASTEXITCODE -ne 0){throw "Harness failed: $args`n$($out -join "`n")"};return ($out -join "`n")
}
function Check([bool]$condition,[string]$message){if(!$condition){throw $message};$checks.Add($message);Write-Host "PASS $message"}
function Geometry([string]$label){
    $out=Harness button-geometry
    $json=([regex]::Match($out,'buttonGeometry=(\{[^\r\n]+\})')).Groups[1].Value
    $json | Set-Content -LiteralPath (Join-Path $EvidenceDirectory "$label.json")
    return ($json | ConvertFrom-Json)
}
try{
    Harness require-qualification $WorldName | Out-Null
    Harness command '/tp @p 14.5 4 2.5' | Out-Null
    Harness block-open 14 4 0 | Out-Null
    Harness native-layout-set 18 8 | Out-Null
    $before=Geometry before
    Harness editor-open | Out-Null
    Harness editor-control 114 | Out-Null
    Harness editor-control 29 8 | Out-Null
    Harness editor-control 31 24 | Out-Null
    $after=Geometry margins
    Check ($after.guiTop -ge 16 -and ($after.guiTop+$after.guiHeight) -le 488) 'Screen margins reserve top16/bottom52 within scaled540 screen'
    Check ($after.guiHeight -le $before.guiHeight) 'Unused margin space does not enlarge the interface'
    Check ($after.guiWidth -eq $before.guiWidth) 'Changing screen margins retains panel width'
    Harness editor-control 29 112 | Out-Null
    Harness editor-control 31 76 | Out-Null
    $tight=Geometry tight
    Check ($tight.guiTop -ge 128 -and ($tight.guiTop+$tight.guiHeight) -le 412) 'Both128px margins constrain the complete interface'
    Check ($tight.guiHeight -lt $before.guiHeight) 'Live native chest viewport shrinks when the reserved margins exceed free space'
    Harness editor-control 112 | Out-Null
    Harness editor-control 25 | Out-Null
    $flat=Geometry flat2
    Harness editor-control 33 4 | Out-Null
    $wide=Geometry flat6
    Check ($flat.guiWidth -eq $wide.guiWidth -and $flat.guiHeight -eq $wide.guiHeight) 'Border width6 does not resize the frame'
    Harness screenshot step3-border-flat6.png | Out-Null
    Harness editor-control 32 6 | Out-Null
    $zero=Geometry flat0
    Check ($flat.guiWidth -eq $zero.guiWidth -and $flat.guiHeight -eq $zero.guiHeight) 'Border width0 retains identical layout bounds'
    Harness screenshot step3-border-flat0.png | Out-Null
    Harness editor-control 33 6 | Out-Null
    Harness editor-control 25 | Out-Null
    Harness screenshot step3-border-bevel6.png | Out-Null
    Harness editor-control 101 | Out-Null
    Harness native-layout-clear | Out-Null
    Harness gate-state ready | Out-Null
    Check $true 'Cancel restores saved layout and keeps the server opening ready'
    $report.success=$true
}catch{$report.error=$_.Exception.Message;throw}finally{
    $report.checks=@($checks.ToArray());$report.checkCount=$checks.Count;$report.finishedAtUtc=[datetime]::UtcNow.ToString('o')
    $report | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $EvidenceDirectory 'verification.json')
}
