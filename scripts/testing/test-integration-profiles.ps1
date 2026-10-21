param(
    [string] $EvidenceDirectory='.setup/integration-profile-checks',
    [string] $LaunchManifest=''
)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
Set-Location -LiteralPath $projectRoot
. (Join-Path (Split-Path $PSScriptRoot -Parent) 'java-runtime.ps1')
$runtime=Resolve-JavaRuntime -MinimumMajor 25
$bin=Split-Path $runtime.Java -Parent
$classes=Join-Path $projectRoot '.setup/integration-profile-tests'
New-Item -ItemType Directory -Force $classes,$EvidenceDirectory | Out-Null
$report=Join-Path $EvidenceDirectory 'verification.json'
@{success=$false;status='running'} | ConvertTo-Json | Set-Content $report
# Deliberately omit every mod/library from this classpath. Early selection must be JDK-only.
& (Join-Path $bin 'javac.exe') --release 8 -Xlint:-options -d $classes `
    src/main/java/dev/gilbe/dynamicinventory/compat/IntegrationProfile.java `
    src/main/java/dev/gilbe/dynamicinventory/compat/BackhandCompatibility.java `
    src/packTest/java/dev/gilbe/dynamicinventory/qualification/IntegrationProfileFixtures.java
if($LASTEXITCODE -ne 0){throw 'Could not compile early profile fixtures'}
Copy-Item src/main/resources/integration-profiles.properties $classes -Force
$cases=@(
    @('isolated','2.9.0-RC-2','1.8.16','false','false'),
    @('rc2-mui2','2.9.0-RC-2','1.8.16','false','false'),
    @('rc2-backhand','2.9.0-RC-2','1.8.16','false','true'),
    @('rc2-integration','2.9.0-RC-2','1.8.16','true','true'),
    @('daily-2026-10-19','2.9.0-nightly-2026-10-19','1.8.17','true','true'))
foreach($case in $cases){
    & $runtime.Java "-Ddynamicinventory.profile=$($case[0])" -cp $classes `
        dev.gilbe.dynamicinventory.qualification.IntegrationProfileFixtures @case
    if($LASTEXITCODE -ne 0){throw "Early profile fixture failed: $($case[0])"}
}
# A bad launch selection must fail before the first fixture assertion, rather than
# silently falling back to isolated storage with a different canonical mapping.
$previousErrorPreference = $ErrorActionPreference
try {
    # Windows PowerShell turns native stderr into ErrorRecords. This failure is
    # intentional; capture it for the assertions instead of terminating the script.
    $ErrorActionPreference = 'Continue'
    $failure=& $runtime.Java '-Ddynamicinventory.profile=unknown' -cp $classes `
        dev.gilbe.dynamicinventory.qualification.IntegrationProfileFixtures 2>&1
    $failureExitCode = $LASTEXITCODE
} finally { $ErrorActionPreference = $previousErrorPreference }
if($failureExitCode -eq 0 -or ($failure -join "`n") -notmatch 'Unknown Dynamic Inventory integration profile'){
    throw 'Unknown startup profile did not fail closed'
}
# Exercise the real runtime guard without booting Forge or loading pack JARs.
# Keep Loader doubles on a separate classpath so early selection remains JDK-only.
$guardClasses=Join-Path $classes 'pack-versions'
$guardStubs=Join-Path $guardClasses 'src/cpw/mods/fml/common'
New-Item -ItemType Directory -Force $guardStubs | Out-Null
@'
package cpw.mods.fml.common;
public interface ModContainer { String getVersion(); }
'@ | Set-Content (Join-Path $guardStubs 'ModContainer.java')
@'
package cpw.mods.fml.common;
import java.util.HashMap;
import java.util.Map;
public final class Loader {
    private static final Loader INSTANCE = new Loader();
    private final Map<String, ModContainer> mods = new HashMap<>();
    public static Loader instance() { return INSTANCE; }
    public static boolean isModLoaded(String id) { return INSTANCE.mods.containsKey(id); }
    public Map<String, ModContainer> getIndexedModList() { return mods; }
}
'@ | Set-Content (Join-Path $guardStubs 'Loader.java')
& (Join-Path $bin 'javac.exe') --release 8 -Xlint:-options -d $guardClasses `
    (Join-Path $guardStubs 'ModContainer.java') (Join-Path $guardStubs 'Loader.java') `
    src/main/java/dev/gilbe/dynamicinventory/compat/IntegrationProfile.java `
    src/main/java/dev/gilbe/dynamicinventory/compat/PackCompatibility.java `
    src/packTest/java/dev/gilbe/dynamicinventory/qualification/PackCompatibilityFixtures.java
if($LASTEXITCODE -ne 0){throw 'Could not compile optional pack-version fixtures'}
Copy-Item src/main/resources/integration-profiles.properties $guardClasses -Force
$guardCases=@(
    @('rc2-integration','1.9.6-GTNH','2.9.84','1.12.22-GTNH','6.1.13','2.6.60-GTNH'),
    @('daily-2026-10-19','1.9.6-GTNH','2.9.93','1.12.22-GTNH','6.1.13','2.6.64-GTNH'))
foreach($case in $guardCases){
    & $runtime.Java "-Ddynamicinventory.profile=$($case[0])" -cp $guardClasses `
        dev.gilbe.dynamicinventory.qualification.PackCompatibilityFixtures $case[1] $case[2] $case[3] $case[4] $case[5]
    if($LASTEXITCODE -ne 0){throw "Optional pack-version checks failed: $($case[0])"}
}
$networkAssertions=0
if($LaunchManifest){
    # Optional Forge classpath, exported with packTestClasses. This probe invokes the
    # real NetworkCheckHandler in a fresh JVM before any adapter registration closes.
    $launch=Get-Content -LiteralPath $LaunchManifest -Raw | ConvertFrom-Json
    if(!$launch.offlineOnly -or [string]$launch.classpath -notlike "*$projectRoot*"){
        throw 'Expected this workspace offline fixture classpath'
    }
    foreach($profile in @('rc2-integration','daily-2026-10-19')){
        & $runtime.Java "-Ddynamicinventory.profile=$profile" -cp $launch.classpath `
            dev.gilbe.dynamicinventory.qualification.EarlyNetworkFixtures
        if($LASTEXITCODE -ne 0){throw "Early network checks failed: $profile"}
        $networkAssertions+=7
    }
}
@{success=$true;checks=106;profiles=@($cases|ForEach-Object{$_[0]});java8Compiled=$true;
    optionalPackVersionAssertions=50;
    earlyNetworkAssertions=$networkAssertions;
    pinsSha256=(Get-FileHash src/main/resources/integration-profiles.properties).Hash;
    capturedAtUtc=[datetime]::UtcNow.ToString('o')} | ConvertTo-Json | Set-Content $report
Write-Host 'PASS: 56 early profile-selection and 50 optional pack-version checks'
