param([string] $ClasspathManifest = '.setup/inventory-example-classpath.json')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
Push-Location -LiteralPath $projectRoot
try {
    . (Join-Path (Split-Path $PSScriptRoot -Parent) 'java-runtime.ps1')
    $runtime = Resolve-JavaRuntime -MinimumMajor 25
    if (!(Test-Path -LiteralPath $ClasspathManifest)) {
        throw 'First run scripts/gradle.ps1 writeInventoryExampleClasspath -PinventoryProfile=rc2-integration'
    }
    $classpath = (Get-Content -LiteralPath $ClasspathManifest -Raw | ConvertFrom-Json).classpath
    $classes = Join-Path $projectRoot '.setup/openblocks-grave-slot-tests'
    New-Item -ItemType Directory -Force -Path $classes | Out-Null
    # Compile the actual NBT-only codec. No item registration, game launch, or world access.
    & (Join-Path (Split-Path $runtime.Java -Parent) 'javac.exe') --release 8 -proc:none `
        -cp $classpath -d $classes `
        src/main/java/dev/gilbe/dynamicinventory/compat/openblocks/GraveSlotCodec.java `
        scripts/testing/OpenBlocksGraveSlotFixture.java
    if ($LASTEXITCODE -ne 0) { throw 'Grave slot fixture compilation failed' }
    & $runtime.Java -cp "$classes$([IO.Path]::PathSeparator)$classpath" OpenBlocksGraveSlotFixture
    if ($LASTEXITCODE -ne 0) { throw 'Grave slot fixture failed' }

    # A successful Java build alone does not prove the inherited tile method was remapped.
    $refmap = Get-Content build/tmp/mixins/mixins.dynamicinventory.refmap.json -Raw | ConvertFrom-Json
    $selector = $refmap.mappings.'dev/gilbe/dynamicinventory/mixins/openblocks/OpenBlocksGraveMixin'.readFromNBT
    if ($selector -ne 'Lopenblocks/common/tileentity/TileEntityGrave;func_145839_a(Lnet/minecraft/nbt/NBTTagCompound;)V') {
        throw 'Missing or incorrect OpenBlocks grave readFromNBT mapping; rebuild first'
    }
    Write-Host 'PASS grave tile readFromNBT mapping. Full grave lifecycle testing remains pending.'
} finally {
    Pop-Location
}
