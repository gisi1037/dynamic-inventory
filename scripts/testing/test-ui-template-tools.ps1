param([string] $EvidencePath = 'docs/qualification/ui-api/template-tooling.json')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
Set-Location -LiteralPath $projectRoot
$runtimeIsWindows = [IO.Path]::DirectorySeparatorChar -eq '\'
if (-not $runtimeIsWindows) { throw 'The template-tool regression requires Windows PowerShell 5.1 and PowerShell 7.' }
$windowsPowerShell = Get-Command powershell.exe -CommandType Application -ErrorAction Stop | Select-Object -First 1
$powerShell7 = Get-Command pwsh.exe -CommandType Application -ErrorAction Stop | Select-Object -First 1
$javaRuntimePath = Join-Path $projectRoot 'scripts/java-runtime.ps1'
. $javaRuntimePath
$javaRuntime = Resolve-JavaRuntime -MinimumMajor 25
$launchPath = Join-Path $projectRoot '.setup/rc2-integration-offline-client-launch.json'
$productionJar = Join-Path $projectRoot 'build/libs/dynamicinventory-0.8.0-dev.jar'
$bootstrapTasks = [Collections.Generic.List[string]]::new()
if (-not (Test-Path -LiteralPath $productionJar -PathType Leaf)) { $bootstrapTasks.Add('build') }
if (-not (Test-Path -LiteralPath $launchPath -PathType Leaf)) { $bootstrapTasks.Add('writeFixtureClientLaunch') }
if ($bootstrapTasks.Count -gt 0) {
    $bootstrapTasks.Add('packTestClasses')
    $bootstrapTasks.Add('-PinventoryProfile=rc2-integration')
    $bootstrapTasks.Add('-PinventoryStorageTests')
    $bootstrapTasks.Add('--no-daemon')
    $bootstrapTasks.Add('--no-configuration-cache')
    $gradle = Join-Path $projectRoot 'scripts/gradle.ps1'
    & $gradle @($bootstrapTasks.ToArray())
    if ($LASTEXITCODE -ne 0) { throw 'Could not prepare the RC-2 template regression classpath/artifact.' }
}
$launch = Get-Content -LiteralPath $launchPath -Raw | ConvertFrom-Json
if ($launch.profile -ne 'rc2-integration' -or !$launch.offlineOnly -or $launch.classpath -notlike "*$projectRoot*") {
    throw 'The template regression requires the generated offline RC-2 integration classpath.'
}
$root = Join-Path $projectRoot ('.setup/template-tools-' + [guid]::NewGuid().ToString('N'))
$audits = Join-Path $root 'audits'
$output = Join-Path $root 'output'
New-Item -ItemType Directory -Force -Path $audits | Out-Null
$watcher = Join-Path $projectRoot 'scripts/tools/Watch-InventoryUiTemplates.ps1'
$generator = Join-Path $projectRoot 'scripts/tools/New-InventoryUiAdapter.ps1'
$checks = [Collections.Generic.List[string]]::new()
function Check([bool] $Condition, [string] $Message) {
    if (!$Condition) { throw $Message }
    $checks.Add($Message)
}
function Bundles([string] $Directory) {
    return @(Get-ChildItem -LiteralPath $Directory -Filter template-export.json -Recurse -File -ErrorAction SilentlyContinue)
}
function Signature([string] $Directory) {
    return @(Get-ChildItem -LiteralPath $Directory -Recurse -File | Where-Object Name -ne '.watcher.lock' |
        ForEach-Object { "$($_.FullName):$((Get-FileHash $_.FullName -Algorithm SHA256).Hash)" })
}
$nativeText = Get-Content docs/qualification/ui-api/integrated/native-audit.json -Raw
$muiText = Get-Content docs/qualification/ui-api/gt-mui2/gt-audit.json -Raw
# New captures include refusal diagnostics; historical schema-1 captures may omit them.
$refused = $nativeText | ConvertFrom-Json
$refused | Add-Member -NotePropertyName refusalReason -NotePropertyValue 'Expected 63 native slots; found 62' -Force
$refused.candidate = $false
$refusedPath = Join-Path $root 'refused.json'
$refusedOutput = Join-Path $root 'refused-output'
$refused | ConvertTo-Json -Depth 12 | Set-Content $refusedPath -Encoding UTF8
& $generator -AuditPath $refusedPath -OutputDirectory $refusedOutput | Out-Null
$refusedSummary = Get-ChildItem $refusedOutput -Filter '*-audit-summary.json' | Get-Content -Raw | ConvertFrom-Json
Check (!$refusedSummary.candidate -and $refusedSummary.refusalReason -eq $refused.refusalReason) 'Refused audit exports an inactive scaffold with its diagnostic reason preserved'
foreach ($invalidReason in @('candidate', 'type')) {
    $invalid = Get-Content $refusedPath -Raw | ConvertFrom-Json
    if ($invalidReason -eq 'candidate') { $invalid.candidate = $true }
    else { $invalid.refusalReason = 42 }
    $invalidPath = Join-Path $root "invalid-refusal-$invalidReason.json"
    $invalid | ConvertTo-Json -Depth 12 | Set-Content $invalidPath -Encoding UTF8
    $rejected = $false
    try { & $generator -AuditPath $invalidPath -OutputDirectory $refusedOutput -Force | Out-Null }
    catch { $rejected = $true }
    Check $rejected "Refusal diagnostic rejects inconsistent $invalidReason"
}
$mui1 = $muiText | ConvertFrom-Json
$mui1.framework = 'MUI1'
Set-Content (Join-Path $audits 'ui-audit-native.json') $nativeText -Encoding UTF8
Set-Content (Join-Path $audits 'ui-audit-mui2.json') $muiText -Encoding UTF8
$mui1 | ConvertTo-Json -Depth 12 | Set-Content (Join-Path $audits 'ui-audit-mui1.json') -Encoding UTF8
Set-Content (Join-Path $audits 'ui-audit-invalid.json') '{"schemaVersion":999}' -Encoding UTF8
# Exercise both Windows PowerShell 5.1 and PowerShell 7, the supported local shells.
$firstLog = & $windowsPowerShell.Source -NoProfile -ExecutionPolicy Bypass -File $watcher -AuditDirectory $audits -OutputDirectory $output -Once 2>&1
if ($LASTEXITCODE -ne 0) { throw 'Windows PowerShell 5.1 template export failed' }
$receipts = Bundles $output
Check ($receipts.Count -eq 3) 'Native, MUI2, and MUI1 captures export; invalid schema does not complete a bundle'
$java = @(Get-ChildItem -LiteralPath $output -Filter '*.java' -Recurse -File)
Check ($java.Count -eq 2) 'Native registration and MUI2 player-section Java templates are generated; MUI1 gets review notes'
$nativeJava = $java | Where-Object Name -like '*NativeGui*'
$muiJava = $java | Where-Object Name -like '*GuiContainerWrapper*'
Check ((Get-Content $nativeJava.FullName -Raw) -match '// InventoryUi.register\(') 'Native registration stays commented and inactive'
Check ((Get-Content $muiJava.FullName -Raw) -match 'Mui2InventoryWidgets.playerInventory' -and
    (Get-Content $muiJava.FullName -Raw) -match 'sync.bindPlayerInventory') 'MUI2 template uses the public live-slot factory and matching binding'
Check (@($receipts | Where-Object { (Get-Content $_.FullName -Raw | ConvertFrom-Json).activated }).Count -eq 0) 'Every exported bundle is explicitly inactive'
$signature = Signature $output
& $windowsPowerShell.Source -NoProfile -ExecutionPolicy Bypass -File $watcher -AuditDirectory $audits -OutputDirectory $output -Once | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Windows PowerShell 5.1 repeated export failed' }
$secondSignature = Signature $output
Check (($signature -join "`n") -eq ($secondSignature -join "`n")) 'Repeated Windows PowerShell 5.1 export preserves completed templates'

$powerShell7Output = Join-Path $root 'output-pwsh'
New-Item -ItemType Directory -Force -Path $powerShell7Output | Out-Null
& $powerShell7.Source -NoProfile -ExecutionPolicy Bypass -File $watcher -AuditDirectory $audits -OutputDirectory $powerShell7Output -Once | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'PowerShell 7 template export failed' }
$powerShell7Receipts = Bundles $powerShell7Output
Check ($powerShell7Receipts.Count -eq 3) 'PowerShell 7 exports native, MUI2, and MUI1 captures without completing invalid schema'
$powerShell7Java = @(Get-ChildItem -LiteralPath $powerShell7Output -Filter '*.java' -Recurse -File)
Check ($powerShell7Java.Count -eq 2) 'PowerShell 7 generates native registration and MUI2 player-section templates'
$powerShell7Signature = Signature $powerShell7Output
& $powerShell7.Source -NoProfile -ExecutionPolicy Bypass -File $watcher -AuditDirectory $audits -OutputDirectory $powerShell7Output -Once | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'PowerShell 7 repeated export failed' }
$secondPowerShell7Signature = Signature $powerShell7Output
Check (($powerShell7Signature -join "`n") -eq ($secondPowerShell7Signature -join "`n")) 'Repeated PowerShell 7 export preserves completed templates'

$classes = Join-Path $root 'classes'
New-Item -ItemType Directory -Force -Path $classes | Out-Null
& $javaRuntime.Javac --release 8 -proc:none -cp $launch.classpath -d $classes @($java.FullName)
Check ($LASTEXITCODE -eq 0) 'Windows PowerShell generated templates compile for Java 8 against the current public API'
$classesPwsh = Join-Path $root 'classes-pwsh'
New-Item -ItemType Directory -Force -Path $classesPwsh | Out-Null
& $javaRuntime.Javac --release 8 -proc:none -cp $launch.classpath -d $classesPwsh @($powerShell7Java.FullName)
Check ($LASTEXITCODE -eq 0) 'PowerShell 7 generated templates compile for Java 8 against the current public API'

# The exported native scaffold intentionally contains no live registration. Compile
# a separate activated copy too, otherwise javac would only check an empty method
# and miss stale builder names or missing imports in the actual generated example.
$activatedDirectory = Join-Path $root 'activated-native-fixture'
New-Item -ItemType Directory -Force -Path $activatedDirectory | Out-Null
$nativeSource = Get-Content -LiteralPath $nativeJava.FullName -Raw
$expression = [regex]::Match($nativeSource, '(?ms)^\s*// InventoryUi\.register\(.*?//\s*\.build\(\)\);')
Check $expression.Success 'Native scaffold contains the expected complete registration expression'
$activatedExpression = [regex]::Replace($expression.Value, '(?m)^(\s*)// ?', '$1')
$activatedExpression = [regex]::Replace($activatedExpression, '(?m)^(\s*)//\s*\.nativeTransfers\(\)', '$1.nativeTransfers()')
$activated = $nativeSource.Replace($expression.Value, $activatedExpression)
$activated = [regex]::Replace($activated, '(?m)^(package [^;]+;)', '$1' + "`nimport dev.gilbe.dynamicinventory.api.*;")
$activatedPath = Join-Path $activatedDirectory $nativeJava.Name
[IO.File]::WriteAllText($activatedPath, $activated, [Text.UTF8Encoding]::new($false))
& $javaRuntime.Javac --release 8 -proc:none -cp $launch.classpath -d $classes $activatedPath
Check ($LASTEXITCODE -eq 0) 'Activated native registration expression compiles with an explicit transfer choice'

$liveAudits = Join-Path $root 'live-audits'
$liveOutput = Join-Path $root 'live-output'
New-Item -ItemType Directory -Force -Path $liveAudits | Out-Null
Set-Content (Join-Path $liveAudits 'ui-audit-preexisting.json') $nativeText -Encoding UTF8
$job = Start-Job -ScriptBlock {
    param($Script, $InputPath, $OutputPath)
    & $Script -AuditDirectory $InputPath -OutputDirectory $OutputPath -RunForSeconds 7 -PollMilliseconds 100
} -ArgumentList $watcher, $liveAudits, $liveOutput
try {
    $deadline = [datetime]::UtcNow.AddSeconds(15)
    while (!(Test-Path (Join-Path $liveOutput '.watcher.lock')) -and [datetime]::UtcNow -lt $deadline) {
        Start-Sleep -Milliseconds 100
    }
    Check (Test-Path (Join-Path $liveOutput '.watcher.lock')) 'Background watcher starts before the next F9-style capture'
    Start-Sleep -Milliseconds 400
    # A partially written JSON must not kill the watcher or block a subsequent completed capture.
    $partial = Join-Path $liveAudits 'ui-audit-partial.json'
    Set-Content $partial '{"schemaVersion":' -Encoding UTF8
    Start-Sleep -Milliseconds 400
    Set-Content $partial $muiText -Encoding UTF8
    Set-Content (Join-Path $liveAudits 'ui-audit-new-native.json') $nativeText -Encoding UTF8
    $lockedPath = Join-Path $liveAudits 'ui-audit-temporarily-locked.json'
    Set-Content $lockedPath $nativeText -Encoding UTF8
    $heldFile = [IO.File]::Open($lockedPath, 'Open', 'ReadWrite', 'None')
    try { Start-Sleep -Milliseconds 1500 } finally { $heldFile.Dispose() }
    $job | Wait-Job -Timeout 15 | Out-Null
    Check ($job.State -eq 'Completed') 'Watcher survives partial JSON and exits at its bounded test deadline'
    $liveLog = Receive-Job $job 2>&1
    Check ((Bundles $liveOutput).Count -eq 3) 'Live mode ignores preexisting captures and exports newly completed captures'
    Check (@(Get-ChildItem -LiteralPath $liveOutput -Directory | Where-Object Name -like 'ui-audit-temporarily-locked*').Count -eq 1) 'Transient read failures retry successfully without changing the audit file'
    Check (@(Get-ChildItem -LiteralPath $liveOutput -Directory | Where-Object Name -like '.pending-*').Count -eq 0) 'Completed and rejected exports leave no partial staging bundles'
    $liveLog | Out-String | Set-Content (Join-Path $root 'watcher.log')
} finally {
    if ($job.State -eq 'Running') { Stop-Job $job }
    Remove-Job $job -Force
}

$blocked = $false
try { & $watcher -AuditDirectory $audits -OutputDirectory (Join-Path $projectRoot 'src/generated-template-test') -Once }
catch { $blocked = $true }
Check $blocked 'Watcher rejects output under the production source tree'
$parseErrors = @()
foreach ($file in @($watcher, $generator, $PSCommandPath)) {
    $tokens=$null; $errors=$null
    [Management.Automation.Language.Parser]::ParseFile($file, [ref]$tokens, [ref]$errors) | Out-Null
    $parseErrors += @($errors)
}
Check ($parseErrors.Count -eq 0) 'Watcher, generator, and regression script parse successfully'
@{success=$true; scope='Developer template tooling only; no production Java or inventory behavior changed';
    capturedAtUtc=[datetime]::UtcNow.ToString('o'); checkCount=$checks.Count; checks=@($checks);
    testedShells=@('Windows PowerShell 5.1', 'PowerShell 7');
    productionArtifactSha256=(Get-FileHash build/libs/dynamicinventory-0.8.0-dev.jar -Algorithm SHA256).Hash;
    watcherSha256=(Get-FileHash $watcher -Algorithm SHA256).Hash;
    generatorSha256=(Get-FileHash $generator -Algorithm SHA256).Hash} |
    ConvertTo-Json -Depth 7 | Set-Content -LiteralPath $EvidencePath -Encoding UTF8
Write-Host "PASS GUI template tooling: $($checks.Count) checks"
