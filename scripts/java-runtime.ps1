function Get-JavaMajorVersion([string] $Executable, [switch] $Compiler) {
    if (-not (Test-Path -LiteralPath $Executable -PathType Leaf)) {
        throw "Java executable was not found: $Executable"
    }
    $startInfo = New-Object Diagnostics.ProcessStartInfo
    $startInfo.FileName = $Executable
    $startInfo.Arguments = '-version'
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $versionProcess = New-Object Diagnostics.Process
    $versionProcess.StartInfo = $startInfo
    if (-not $versionProcess.Start()) {
        $versionProcess.Dispose()
        throw "Could not start Java executable: $Executable"
    }
    if (-not $versionProcess.WaitForExit(10000)) {
        $versionProcess.Kill()
        $versionProcess.Dispose()
        throw "Timed out reading Java version from $Executable"
    }
    $versionText = ($versionProcess.StandardOutput.ReadToEnd() + $versionProcess.StandardError.ReadToEnd()).Trim()
    $exitCode = $versionProcess.ExitCode
    $versionProcess.Dispose()
    if ($exitCode -ne 0) { throw "Could not read Java version from $Executable" }

    $match = if ($Compiler) {
        [regex]::Match($versionText, '(?m)^javac\s+(\d+)(?:\.(\d+))?')
    } else {
        [regex]::Match($versionText, '(?i)version\s+"?(\d+)(?:\.(\d+))?')
    }
    if (-not $match.Success) { throw "Could not parse Java version from $Executable`: $versionText" }
    $major = [int]$match.Groups[1].Value
    if ($major -eq 1 -and $match.Groups[2].Success) { $major = [int]$match.Groups[2].Value }
    return $major
}

function Resolve-JavaRuntime {
    [CmdletBinding()]
    param([ValidateRange(1,100)] [int] $MinimumMajor = 25)

    $runtimeIsWindows = [IO.Path]::DirectorySeparatorChar -eq '\'
    $javaName = if ($runtimeIsWindows) { 'java.exe' } else { 'java' }
    $javacName = if ($runtimeIsWindows) { 'javac.exe' } else { 'javac' }
    $candidates = [Collections.Generic.List[object]]::new()

    if (-not [string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
        $runtimeHome = [IO.Path]::GetFullPath($env:JAVA_HOME.Trim())
        $candidates.Add([pscustomobject]@{
            Home = $runtimeHome
            Java = Join-Path (Join-Path $runtimeHome 'bin') $javaName
            Javac = Join-Path (Join-Path $runtimeHome 'bin') $javacName
            Source = 'JAVA_HOME'
        })
    } else {
        $javaCommand = Get-Command java -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
        $javacCommand = Get-Command javac -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($null -ne $javaCommand -and $null -ne $javacCommand) {
            $javaBin = Split-Path $javaCommand.Source -Parent
            $javacBin = Split-Path $javacCommand.Source -Parent
            # Oracle's javapath shims can outlive the JDK they target; prefer the
            # documented direct JDK fallback when those wrappers are on PATH.
            if ((Split-Path $javaBin -Leaf) -ine 'javapath' -and (Split-Path $javacBin -Leaf) -ine 'javapath') {
                $javacHome = Split-Path $javacBin -Parent
                $homeJava = Join-Path (Join-Path $javacHome 'bin') $javaName
                $candidates.Add([pscustomobject]@{
                    Home = [IO.Path]::GetFullPath($javacHome)
                    Java = if (Test-Path -LiteralPath $homeJava -PathType Leaf) { $homeJava } else { $javaCommand.Source }
                    Javac = $javacCommand.Source
                    Source = 'PATH'
                })
            }
        }
        # Local fallback for the original developer install location. A JDK on PATH
        # takes precedence when its version satisfies the requested minimum.
        $programFiles = if ($env:ProgramFiles) { $env:ProgramFiles } else { 'C:\Program Files' }
        $localJdkFallbackHome = Join-Path $programFiles 'Java/jdk-25'
        if (Test-Path -LiteralPath (Join-Path (Join-Path $localJdkFallbackHome 'bin') $javaName) -PathType Leaf) {
            $candidates.Add([pscustomobject]@{
                Home = $localJdkFallbackHome
                Java = Join-Path (Join-Path $localJdkFallbackHome 'bin') $javaName
                Javac = Join-Path (Join-Path $localJdkFallbackHome 'bin') $javacName
                Source = 'documented local fallback'
            })
        }
    }

    if ($candidates.Count -eq 0) {
        throw 'A JDK was not found. Set JAVA_HOME to a JDK 25+ installation or add java and javac from that JDK to PATH.'
    }

    $candidateErrors = [Collections.Generic.List[string]]::new()
    foreach ($candidate in $candidates) {
        if (-not (Test-Path -LiteralPath $candidate.Java -PathType Leaf) -or
            -not (Test-Path -LiteralPath $candidate.Javac -PathType Leaf)) {
            $candidateErrors.Add("$($candidate.Source) does not provide both java and javac")
            if ($candidate.Source -eq 'JAVA_HOME') { break }
            continue
        }
        try {
            $javaMajor = Get-JavaMajorVersion $candidate.Java
            $javacMajor = Get-JavaMajorVersion $candidate.Javac -Compiler
            if ($javaMajor -ne $javacMajor) {
                throw "java and javac versions do not match ($javaMajor and $javacMajor)"
            }
            if ($javaMajor -lt $MinimumMajor) {
                throw "JDK $MinimumMajor or newer is required; found version $javaMajor"
            }
            return [pscustomobject]@{
                Home = $candidate.Home
                Java = $candidate.Java
                Javac = $candidate.Javac
                Major = $javaMajor
                Source = $candidate.Source
            }
        } catch {
            $candidateErrors.Add("$($candidate.Source): $($_.Exception.Message)")
            if ($candidate.Source -eq 'JAVA_HOME') { break }
        }
    }

    throw ($candidateErrors -join [Environment]::NewLine)
}
