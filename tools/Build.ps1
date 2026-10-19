param(
    [string[]]$Tasks = @('build'),
    [string]$JavaHome = ''
)
$ErrorActionPreference = 'Stop'
$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
if (-not $JavaHome) {
    $candidates = @($env:JAVA_HOME, (Join-Path $env:USERPROFILE '.gradle\jdks\jdk8u504-b01'))
    $candidates += @(Get-ChildItem -LiteralPath (Join-Path $env:USERPROFILE '.gradle\jdks') -Directory -ErrorAction SilentlyContinue | Where-Object Name -Match 'jdk8|java.?8|1\.8' | ForEach-Object FullName)
    foreach ($candidate in $candidates) {
        if ($candidate -and (Test-Path -LiteralPath (Join-Path $candidate 'release'))) {
            $release = Get-Content -LiteralPath (Join-Path $candidate 'release') -Raw
            if ($release -match 'JAVA_VERSION="1\.8\.') { $JavaHome = $candidate; break }
        }
    }
}
if (-not $JavaHome -or -not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin\javac.exe'))) {
    throw 'A Java 8 JDK is required. Run .\tools\Build.ps1 -JavaHome "C:\path\to\jdk8".'
}
$release = Get-Content -LiteralPath (Join-Path $JavaHome 'release') -Raw
if ($release -notmatch 'JAVA_VERSION="1\.8\.') { throw 'Select Java 8 for this Forge 1.12.2 project.' }
$previousJavaHome = $env:JAVA_HOME
$previousGradleHome = $env:GRADLE_USER_HOME
try {
    $env:JAVA_HOME = $JavaHome
    if (-not $env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME = Join-Path $env:USERPROFILE '.gradle' }
    Push-Location -LiteralPath $projectRoot
    try {
        & (Join-Path $projectRoot 'gradlew.bat') @Tasks '--console=plain'
        if ($LASTEXITCODE -ne 0) { throw "Gradle failed with exit code $LASTEXITCODE." }
    } finally { Pop-Location }
} finally {
    $env:JAVA_HOME = $previousJavaHome
    $env:GRADLE_USER_HOME = $previousGradleHome
}
