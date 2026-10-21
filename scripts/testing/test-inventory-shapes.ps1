param([string] $EvidenceDirectory = '.setup/inventory-shape-checks')
$ErrorActionPreference='Stop'
$projectRoot=Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
. (Join-Path (Split-Path $PSScriptRoot -Parent) 'java-runtime.ps1')
$runtime=Resolve-JavaRuntime -MinimumMajor 25
$classes=Join-Path $projectRoot '.setup/inventory-shape-classes'
New-Item -ItemType Directory -Force -Path $classes,$EvidenceDirectory | Out-Null
Push-Location $projectRoot
try {
    # JDK-only by design: this validates planning, not network/container gameplay.
    & (Join-Path $runtime.Home 'bin/javac.exe') --release 8 -Xlint:-options -d $classes `
        src/main/java/dev/gilbe/dynamicinventory/inventory/InventoryShape.java `
        src/main/java/dev/gilbe/dynamicinventory/inventory/InventoryResizePlan.java `
        src/main/java/dev/gilbe/dynamicinventory/upgrades/UpgradeCapacity.java `
        src/packTest/java/dev/gilbe/dynamicinventory/qualification/InventoryShapeFixtures.java `
        src/packTest/java/dev/gilbe/dynamicinventory/qualification/UpgradeCapacityFixtures.java
    if($LASTEXITCODE -ne 0){throw 'Could not compile inventory shape fixtures'}
    $result=& $runtime.Java -cp $classes dev.gilbe.dynamicinventory.qualification.InventoryShapeFixtures
    if($LASTEXITCODE -ne 0){throw 'Inventory shape matrix failed'}
    $upgrades=& $runtime.Java -cp $classes dev.gilbe.dynamicinventory.qualification.UpgradeCapacityFixtures
    if($LASTEXITCODE -ne 0){throw 'Inventory upgrade policy matrix failed'}
    $result | Set-Content (Join-Path $EvidenceDirectory 'fixture-output.txt')
    @{success=$true;scope='pure shape and preflight matrix';fullPackQualified=$false;
      java8Compiled=$true;result=$result;upgradeResult=$upgrades;capturedAtUtc=[datetime]::UtcNow.ToString('o')} |
        ConvertTo-Json | Set-Content (Join-Path $EvidenceDirectory 'verification.json')
    Write-Host $result
    Write-Host $upgrades
} finally { Pop-Location }
