param(
    [string] $GameDirectory = '',
    [string] $AuditDirectory = '',
    [string] $OutputDirectory = '.setup/generated-ui-adapters',
    [switch] $IncludeExisting,
    [switch] $Once,
    [switch] $InsertIntoSource,
    [ValidateRange(100, 10000)] [int] $PollMilliseconds = 500,
    [ValidateRange(0, 86400)] [int] $RunForSeconds = 0
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
if (!$AuditDirectory) {
    if (!$GameDirectory) {
        if (!$env:APPDATA) { throw 'Pass -GameDirectory or -AuditDirectory for this computer.' }
        $pack = Get-Content -LiteralPath (Join-Path $projectRoot 'pack-baseline.json') -Raw | ConvertFrom-Json
        $GameDirectory = Join-Path $env:APPDATA "PrismLauncher/instances/$($pack.instanceId)/.minecraft"
    }
    $AuditDirectory = Join-Path $GameDirectory 'diagnostics/dynamicinventory'
}
if (![IO.Path]::IsPathRooted($AuditDirectory)) { $AuditDirectory = Join-Path $projectRoot $AuditDirectory }
$auditRoot = [IO.Path]::GetFullPath($AuditDirectory)
if (![IO.Path]::IsPathRooted($OutputDirectory)) { $OutputDirectory = Join-Path $projectRoot $OutputDirectory }
$outputRoot = [IO.Path]::GetFullPath($OutputDirectory)
$outputPrefix = $outputRoot.TrimEnd([char[]]@('\', '/')) + [IO.Path]::DirectorySeparatorChar
$sourceRoot = [IO.Path]::GetFullPath((Join-Path $projectRoot 'src'))
$sourcePrefix = $sourceRoot.TrimEnd([char[]]@('\', '/')) + [IO.Path]::DirectorySeparatorChar
if ($outputRoot.Equals($sourceRoot, [StringComparison]::OrdinalIgnoreCase) -or
    $outputRoot.StartsWith($sourcePrefix, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'OutputDirectory must remain outside src; templates are never enabled automatically.'
}
if ($outputRoot.Equals($auditRoot, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Use separate audit and template output directories.'
}
New-Item -ItemType Directory -Force -Path $outputRoot | Out-Null
$lock = $null
try {
    try {
        $lock = [IO.File]::Open((Join-Path $outputRoot '.watcher.lock'), 'OpenOrCreate', 'ReadWrite', 'None')
    } catch {
        throw "Another template watcher may already own $outputRoot. Stop it or choose another output directory."
    }
    $seen = @{}
    $pending = @{}
    $failures = @{}
    $started = [datetime]::UtcNow
    if (!$IncludeExisting -and !$Once -and (Test-Path -LiteralPath $auditRoot)) {
        foreach ($file in Get-ChildItem -LiteralPath $auditRoot -Filter 'ui-audit-*.json' -File) {
            $seen[$file.FullName] = "$($file.Length):$($file.LastWriteTimeUtc.Ticks)"
        }
    }
    Write-Host "Watching inventory UI audits: $auditRoot"
    Write-Host "Templates: $outputRoot"
    Write-Host 'Open a GUI and press Inventory UI audit (F9 by default). Stop this task with Ctrl+C.'
    do {
        if (Test-Path -LiteralPath $auditRoot) {
            foreach ($file in Get-ChildItem -LiteralPath $auditRoot -Filter 'ui-audit-*.json' -File | Sort-Object Name) {
                $fingerprint = "$($file.Length):$($file.LastWriteTimeUtc.Ticks)"
                if ($seen[$file.FullName] -eq $fingerprint) { continue }
                # Wait for two equal observations so F9 can finish closing its JSON writer.
                if (!$Once -and $pending[$file.FullName] -ne $fingerprint) {
                    $pending[$file.FullName] = $fingerprint
                    continue
                }
                $failure = $failures[$file.FullName]
                if (!$Once -and $failure -and $failure.Fingerprint -eq $fingerprint -and
                    [datetime]::UtcNow -lt $failure.RetryAt) { continue }
                $staging = $null
                try {
                    $bytes = [IO.File]::ReadAllBytes($file.FullName)
                    $sha = [Security.Cryptography.SHA256]::Create()
                    try { $digest = ([BitConverter]::ToString($sha.ComputeHash($bytes))).Replace('-', '').ToLowerInvariant() }
                    finally { $sha.Dispose() }
                    $bundle = Join-Path $outputRoot ($file.BaseName + '-' + $digest.Substring(0, 12))
                    $receipt = Join-Path $bundle 'template-export.json'
                    if (Test-Path -LiteralPath $receipt) {
                        $existing = Get-Content -LiteralPath $receipt -Raw | ConvertFrom-Json
                        if ($existing.auditSha256 -ne $digest -or $existing.activated -ne $false) {
                            throw 'Existing bundle receipt does not match this inactive capture; choose another output directory.'
                        }
                    } else {
                        if (Test-Path -LiteralPath $bundle) {
                            throw 'An incomplete or manually created bundle already exists; choose another output directory.'
                        }
                        # Publish the directory only after every generated file and receipt is complete.
                        $staging = Join-Path $outputRoot ('.pending-' + [guid]::NewGuid().ToString('N'))
                        New-Item -ItemType Directory -Path $staging | Out-Null
                        $snapshot = Join-Path $staging 'audit.json'
                        [IO.File]::WriteAllBytes($snapshot, $bytes)
                        & (Join-Path $PSScriptRoot 'New-InventoryUiAdapter.ps1') -AuditPath $snapshot -OutputDirectory $staging -InsertIntoSource:$InsertIntoSource |
                            ForEach-Object { if ($_ -match 'inserted:|already contains|builder integration') { Write-Host $_ } }
                        @{schemaVersion=1; sourceAudit=$file.FullName; auditSha256=$digest;
                            generatedAtUtc=[datetime]::UtcNow.ToString('o'); activated=$false} |
                            ConvertTo-Json | Set-Content -LiteralPath (Join-Path $staging 'template-export.json') -Encoding UTF8
                        foreach ($child in @($staging, $bundle)) {
                            if (![IO.Path]::GetFullPath($child).StartsWith($outputPrefix, [StringComparison]::OrdinalIgnoreCase)) {
                                throw 'Template publication escaped the intended output directory.'
                            }
                        }
                        [IO.Directory]::Move($staging, $bundle)
                        $staging = $null
                        Write-Host "Template ready: $bundle"
                    }
                    $seen[$file.FullName] = $fingerprint
                    $pending.Remove($file.FullName)
                    $failures.Remove($file.FullName)
                } catch {
                    # Retry unchanged captures after transient IO errors too, without repeated warnings.
                    $attempt = if ($failure -and $failure.Fingerprint -eq $fingerprint) { $failure.Attempt + 1 } else { 1 }
                    $delay = [math]::Min(30, [math]::Pow(2, [math]::Min($attempt - 1, 5)))
                    $failures[$file.FullName] = @{Fingerprint=$fingerprint; Attempt=$attempt; RetryAt=[datetime]::UtcNow.AddSeconds($delay)}
                    if ($attempt -eq 1) { Write-Warning "Template not generated for $($file.Name); will retry: $($_.Exception.Message)" }
                } finally {
                    if ($staging -and (Test-Path -LiteralPath $staging)) {
                        $resolvedStaging = (Resolve-Path -LiteralPath $staging).Path
                        if (!$resolvedStaging.StartsWith($outputPrefix, [StringComparison]::OrdinalIgnoreCase) -or
                            [IO.Path]::GetFileName($resolvedStaging) -notmatch '^\.pending-[a-f0-9]{32}$') {
                            throw 'Refusing cleanup outside the generated staging directory.'
                        }
                        Remove-Item -LiteralPath $resolvedStaging -Recurse -Force
                    }
                }
            }
        }
        if ($Once -or ($RunForSeconds -gt 0 -and ([datetime]::UtcNow - $started).TotalSeconds -ge $RunForSeconds)) { break }
        Start-Sleep -Milliseconds $PollMilliseconds
    } while ($true)
} finally {
    if ($null -ne $lock) { $lock.Dispose() }
}
