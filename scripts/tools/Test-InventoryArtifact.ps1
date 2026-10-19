param(
    [string] $ProductionJar = 'build/libs/dynamicinventory-0.8.0-dev.jar',
    [string] $DevelopmentJar = 'build/libs/dynamicinventory-0.8.0-dev-dev.jar',
    [string] $EvidencePath = 'docs/qualification/stabilization/artifact-verification.json'
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$evidenceFullPath = if ([IO.Path]::IsPathRooted($EvidencePath)) { [IO.Path]::GetFullPath($EvidencePath) }
    else { [IO.Path]::GetFullPath((Join-Path $projectRoot $EvidencePath)) }
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $evidenceFullPath) | Out-Null
@{ success = $false; status = 'running'; fullPackQualified = $false; capturedAtUtc = [datetime]::UtcNow.ToString('o') } |
    ConvertTo-Json | Set-Content -LiteralPath $evidenceFullPath -Encoding UTF8

function Resolve-ArtifactPath([string] $Path) {
    if ([IO.Path]::IsPathRooted($Path)) { return [IO.Path]::GetFullPath($Path) }
    return [IO.Path]::GetFullPath((Join-Path $projectRoot $Path))
}

$fixtureClasses = [Collections.Generic.List[string]]::new()
$nonJava8Classes = [Collections.Generic.List[object]]::new()
$classCount = 0
$publicApiClassCount = 0
$craftingStationForegroundRefmap = $null
$productionPath = $null
$developmentPath = $null
try {
    Add-Type -AssemblyName System.IO.Compression
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $productionPath = Resolve-ArtifactPath $ProductionJar
    $developmentPath = Resolve-ArtifactPath $DevelopmentJar
    if (-not (Test-Path -LiteralPath $productionPath -PathType Leaf)) { throw "Production JAR is missing: $productionPath" }
    if (-not (Test-Path -LiteralPath $developmentPath -PathType Leaf)) { throw "Development JAR is missing: $developmentPath" }

    $archive = [IO.Compression.ZipFile]::OpenRead($productionPath)
    try {
        $entries = @($archive.Entries)
        $entryNames = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
        foreach ($entry in $entries) { [void]$entryNames.Add($entry.FullName) }
        $requiredResources = @(
            'mcmod.info',
            'mixins.dynamicinventory.early.json',
            'mixins.dynamicinventory.json',
            'mixins.dynamicinventory.late.json',
            'mixins.dynamicinventory.refmap.json'
        )
        foreach ($resource in $requiredResources) {
            if (-not $entryNames.Contains($resource)) { throw "Production JAR is missing required resource: $resource" }
        }
        $refmapEntry = $archive.GetEntry('mixins.dynamicinventory.refmap.json')
        $reader = New-Object IO.StreamReader($refmapEntry.Open())
        try { $refmap = $reader.ReadToEnd() | ConvertFrom-Json }
        catch { throw "Production JAR refmap is not valid JSON: $($_.Exception.Message)" }
        finally { $reader.Dispose() }
        $guiMixin = $refmap.mappings.PSObject.Properties['dev/gilbe/dynamicinventory/mixins/CraftingStationGuiMixin']
        $foregroundSelector = if ($null -ne $guiMixin) { $guiMixin.Value.PSObject.Properties['drawGuiContainerForegroundLayer'] } else { $null }
        $expectedForegroundMapping = 'Ltconstruct/tools/gui/CraftingStationGui;func_146979_b(II)V'
        if ($null -eq $foregroundSelector -or [string]$foregroundSelector.Value -cne $expectedForegroundMapping) {
            throw "Tinkers CraftingStationGui foreground selector must map to $expectedForegroundMapping."
        }
        $craftingStationForegroundRefmap = [ordered]@{
            mixin = 'dev/gilbe/dynamicinventory/mixins/CraftingStationGuiMixin'
            selector = 'drawGuiContainerForegroundLayer'
            target = [string]$foregroundSelector.Value
            passed = $true
        }

        $pickBlockIncluded = $false
        foreach ($entry in $entries) {
            if (-not $entry.FullName.EndsWith('.class', [StringComparison]::Ordinal)) { continue }
            $classCount++
            if ($entry.FullName.StartsWith('dev/gilbe/dynamicinventory/api/', [StringComparison]::Ordinal)) {
                $publicApiClassCount++
            }
            if ($entry.FullName -match '(?i)(^|/)(fixtures?|qualification|storageTest|packTest|backhandTest|tests?)(/|$)|(?:Test|Tests|Fixture|Fixtures|Qualification)(?:\$[^/]*)?\.class$|(?:StorageTestCommand|CapacityTestCommand|QualificationCommand)\.class$') {
                $fixtureClasses.Add($entry.FullName)
            }
            if ($entry.FullName -eq 'dev/gilbe/dynamicinventory/mixins/PickBlockSlotMixin.class') { $pickBlockIncluded = $true }
            $stream = $entry.Open()
            try {
                $bytes = New-Object byte[] 8
                $offset = 0
                while ($offset -lt $bytes.Length) {
                    $read = $stream.Read($bytes, $offset, $bytes.Length - $offset)
                    if ($read -le 0) { break }
                    $offset += $read
                }
                if ($offset -lt 8 -or $bytes[0] -ne 0xCA -or $bytes[1] -ne 0xFE -or $bytes[2] -ne 0xBA -or $bytes[3] -ne 0xBE) {
                    throw "Class entry has an invalid or truncated class-file header: $($entry.FullName)"
                }
                $major = ([int]$bytes[6] * 256) + [int]$bytes[7]
                if ($major -ne 52) { $nonJava8Classes.Add([pscustomobject]@{ path = $entry.FullName; majorVersion = $major }) }
            } finally { $stream.Dispose() }
        }
        if ($classCount -eq 0) { throw 'Production JAR contains no class files.' }
        if ($publicApiClassCount -eq 0) { throw 'Production JAR contains no public API package classes.' }
        if (-not $pickBlockIncluded) { throw 'Production JAR is missing mixins/PickBlockSlotMixin.class.' }
        if ($fixtureClasses.Count -gt 0) { throw "Production JAR contains fixture/test classes: $($fixtureClasses -join ', ')" }
        if ($nonJava8Classes.Count -gt 0) {
            $badClasses = @($nonJava8Classes | ForEach-Object { "$($_.path) (major $($_.majorVersion))" }) -join ', '
            throw "Production JAR contains classes outside Java 8 bytecode: $badClasses"
        }
    } finally { $archive.Dispose() }

    $report = [ordered]@{
        success = $true
        status = 'passed'
        fullPackQualified = $false
        productionJar = $productionPath
        developmentJar = $developmentPath
        artifactSha256 = (Get-FileHash -LiteralPath $productionPath -Algorithm SHA256).Hash.ToLowerInvariant()
        developmentArtifactSha256 = (Get-FileHash -LiteralPath $developmentPath -Algorithm SHA256).Hash.ToLowerInvariant()
        classCount = $classCount
        publicApiClassCount = $publicApiClassCount
        fixtureClasses = @($fixtureClasses.ToArray())
        nonJava8Classes = @($nonJava8Classes.ToArray())
        requiredResources = $requiredResources
        pickBlockSlotMixinIncluded = $true
        craftingStationForegroundRefmap = $craftingStationForegroundRefmap
        capturedAtUtc = [datetime]::UtcNow.ToString('o')
    }
    $report | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $evidenceFullPath -Encoding UTF8
    Write-Host "PASS: scanned $classCount production classes ($publicApiClassCount under the public API prefix); no test classes or non-Java-8 bytecode found."
    return $report
} catch {
    $failure = [ordered]@{
        success = $false
        status = 'failed'
        fullPackQualified = $false
        productionJar = $productionPath
        developmentJar = $developmentPath
        artifactSha256 = if ($productionPath -and (Test-Path -LiteralPath $productionPath -PathType Leaf)) { (Get-FileHash -LiteralPath $productionPath -Algorithm SHA256).Hash.ToLowerInvariant() } else { $null }
        developmentArtifactSha256 = if ($developmentPath -and (Test-Path -LiteralPath $developmentPath -PathType Leaf)) { (Get-FileHash -LiteralPath $developmentPath -Algorithm SHA256).Hash.ToLowerInvariant() } else { $null }
        classCount = $classCount
        publicApiClassCount = $publicApiClassCount
        fixtureClasses = @($fixtureClasses.ToArray())
        nonJava8Classes = @($nonJava8Classes.ToArray())
        craftingStationForegroundRefmap = $craftingStationForegroundRefmap
        failure = $_.Exception.Message
        capturedAtUtc = [datetime]::UtcNow.ToString('o')
    }
    $failure | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $evidenceFullPath -Encoding UTF8
    throw
}
