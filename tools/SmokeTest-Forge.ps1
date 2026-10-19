param(
    [string]$ModJar = '',
    [string]$JavaHome = 'C:\Users\Owner\.gradle\jdks\jdk8u504-b01',
    [string]$MinecraftInstall = 'C:\Users\Owner\curseforge\minecraft\Install',
    [string]$ServerJar = 'C:\Users\Owner\.gradle\caches\forge_gradle\mcp_repo\de\oceanlabs\mcp\mcp_config\1.12.2\joined\downloadServer\server.jar',
    [string]$RLCraftInstance = 'C:\Users\Owner\curseforge\minecraft\Instances\RLCraft',
    [switch]$WithRLCraft,
    [ValidateRange(10, 120)][int]$TimeoutSeconds = 120
)

# This leaves eula=false and does not launch MinecraftServer.main or a server world.
# A test-only LaunchWrapper target invokes Forge's production mod-loading lifecycle.
# No server-starting events, listening ports or gameplay are exercised.
# All writable state is isolated beneath this project's build directory.
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
if ([string]::IsNullOrWhiteSpace($ModJar)) {
    $ModJar = Join-Path $projectRoot 'build\libs\Settlements-0.1.0.jar'
}
$ModJar = (Resolve-Path -LiteralPath $ModJar).Path
$java = Join-Path $JavaHome 'bin\java.exe'
$profilePath = Join-Path $MinecraftInstall 'versions\forge-14.23.5.2860\forge-14.23.5.2860.json'
foreach ($required in @($java, $profilePath, $ServerJar, $ModJar)) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) { throw "Missing cached input: $required" }
}
$versionInfo = [Diagnostics.ProcessStartInfo]::new()
$versionInfo.FileName = $java
$versionInfo.Arguments = '-version'
$versionInfo.UseShellExecute = $false
$versionInfo.CreateNoWindow = $true
$versionInfo.RedirectStandardError = $true
$versionInfo.RedirectStandardOutput = $true
$versionProcess = [Diagnostics.Process]::Start($versionInfo)
$javaVersion = $versionProcess.StandardError.ReadToEnd() + $versionProcess.StandardOutput.ReadToEnd()
$versionProcess.WaitForExit()
$versionProcess.Dispose()
if ($javaVersion -notmatch 'version "1\.8\.') { throw "This smoke test requires Java 8. Selected runtime: $javaVersion" }
if ($ModJar -match '(?i)-(sources|javadoc)\.jar$') { throw 'Supply the production reobfuscated mod jar, not a sources or javadoc jar.' }

Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::OpenRead($ModJar)
try {
    foreach ($entry in @('mcmod.info', 'com/mechgaming/settlements/Settlements.class')) {
        if ($null -eq $archive.GetEntry($entry)) { throw "The supplied jar is missing $entry" }
    }
} finally { $archive.Dispose() }

$profile = Get-Content -LiteralPath $profilePath -Raw | ConvertFrom-Json
$classPath = [Collections.Generic.List[string]]::new()
foreach ($library in $profile.libraries) {
    $coordinate = $library.name.Split(':')
    if ($coordinate.Length -lt 3) { throw "Unsupported library coordinate: $($library.name)" }
    $fileName = "$($coordinate[1])-$($coordinate[2])"
    if ($coordinate.Length -gt 3) { $fileName += "-$($coordinate[3])" }
    $relativePath = ($coordinate[0].Replace('.', '\') + '\' + $coordinate[1] + '\' + $coordinate[2] + '\' + $fileName + '.jar')
    $libraryPath = Join-Path (Join-Path $MinecraftInstall 'libraries') $relativePath
    if (-not (Test-Path -LiteralPath $libraryPath -PathType Leaf)) { throw "Missing installed Forge dependency: $libraryPath" }
    $classPath.Add($libraryPath)
}
$classPath.Add((Resolve-Path -LiteralPath $ServerJar).Path)

$baseName = if ($WithRLCraft) { 'smoke-rlcraft2860' } else { 'smoke-forge2860' }
$runName = (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + ([Guid]::NewGuid().ToString('N').Substring(0, 8))
$testRoot = [IO.Path]::GetFullPath((Join-Path (Join-Path (Join-Path $projectRoot 'build') $baseName) $runName))
$workspacePrefix = $projectRoot.TrimEnd('\') + '\'
if (-not $testRoot.StartsWith($workspacePrefix, [StringComparison]::OrdinalIgnoreCase)) { throw 'Resolved smoke directory escaped the project.' }
if ($WithRLCraft) {
    $sourceInstance = (Resolve-Path -LiteralPath $RLCraftInstance).Path
    if (-not (Test-Path -LiteralPath (Join-Path $sourceInstance 'mods') -PathType Container)) { throw 'The selected RLCraft instance has no mods folder.' }
    if ($testRoot.StartsWith($sourceInstance.TrimEnd('\') + '\', [StringComparison]::OrdinalIgnoreCase)) { throw 'Smoke directory must not be inside the real RLCraft instance.' }
}
New-Item -ItemType Directory -Path (Join-Path $testRoot 'mods') -Force | Out-Null
if ($WithRLCraft) {
    foreach ($folder in @('mods', 'config', 'scripts', 'resources', 'structures', 'datapacks')) {
        $source = Join-Path $sourceInstance $folder
        if (-not (Test-Path -LiteralPath $source -PathType Container)) { continue }
        $destination = Join-Path $testRoot $folder
        New-Item -ItemType Directory -Path $destination -Force | Out-Null
        foreach ($child in Get-ChildItem -LiteralPath $source -Force) {
            # Copy bytes; never link pack files to the writable smoke directory.
            Copy-Item -LiteralPath $child.FullName -Destination $destination -Recurse -Force
        }
    }
}
Copy-Item -LiteralPath $ModJar -Destination (Join-Path $testRoot 'mods\Settlements-smoke.jar') -Force
Set-Content -LiteralPath (Join-Path $testRoot 'eula.txt') -Value 'eula=false' -Encoding ASCII
$helperClasses = Join-Path $testRoot 'lifecycle-helper'
New-Item -ItemType Directory -Path $helperClasses -Force | Out-Null
$helperSources = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'smoke') -Filter '*.java' -File | ForEach-Object FullName)
& (Join-Path $JavaHome 'bin\javac.exe') '-source' '8' '-target' '8' '-classpath' ($classPath -join ';') '-d' $helperClasses @helperSources
if ($LASTEXITCODE -ne 0) { throw 'Could not compile the test-only Forge lifecycle launcher.' }
$classPath.Insert(0, $helperClasses)
$stdout = Join-Path $testRoot 'process-stdout.log'
$stderr = Join-Path $testRoot 'process-stderr.log'
$heap = if ($WithRLCraft) { '-Xmx4096M' } else { '-Xmx2048M' }
$launchArguments = @('-Xms256M', $heap, '-Djava.awt.headless=true', '-Dlog4j2.formatMsgNoLookups=true', '-cp', ('"' + ($classPath -join ';') + '"'), 'net.minecraft.launchwrapper.Launch', '--tweakClass', 'com.mechgaming.settlements.smoke.LifecycleSmokeTweaker')
$process = Start-Process -FilePath $java -ArgumentList $launchArguments -WorkingDirectory $testRoot -RedirectStandardOutput $stdout -RedirectStandardError $stderr -WindowStyle Hidden -PassThru
$timedOut = -not $process.WaitForExit($TimeoutSeconds * 1000)
if ($timedOut) {
    # Stop only the process created by this invocation, never another user's game.
    if (-not $process.HasExited) { Stop-Process -Id $process.Id -Force }
    $process.WaitForExit()
}
$process.Refresh()
$logs = @($stdout, $stderr, (Join-Path $testRoot 'logs\latest.log'), (Join-Path $testRoot 'logs\fml-server-latest.log'))
$combined = ($logs | Where-Object { Test-Path -LiteralPath $_ } | ForEach-Object { Get-Content -LiteralPath $_ -Raw }) -join "`n"
$lifecycleComplete = $combined -match 'SETTLEMENTS_LIFECYCLE_SMOKE_OK'
$modSeen = $combined -match '(?i)(settlements|Settlements-smoke\.jar)'
$modConfigWritten = Test-Path -LiteralPath (Join-Path $testRoot 'config\settlements.cfg') -PathType Leaf
$npcDefinitionWritten = Test-Path -LiteralPath (Join-Path $testRoot 'config\settlements\npcs\settler.json') -PathType Leaf
# Legacy Forge can log caught LoaderException traces when skipping library
# module-info classes. A completed lifecycle plus exit 0 distinguishes those
# diagnostics from an initialization failure.
$failure = $combined -match '(?i)(Unable to launch|There was a critical exception handling a packet|Caught exception from Settlements)'
$eulaStillFalse = (Get-Content -LiteralPath (Join-Path $testRoot 'eula.txt') -Raw) -match '(?m)^eula=false\s*$'
$passed = (-not $timedOut) -and ($process.ExitCode -eq 0) -and $lifecycleComplete -and $modSeen -and $modConfigWritten -and $npcDefinitionWritten -and (-not $failure) -and $eulaStillFalse
$result = [ordered]@{
    passed = $passed
    scope = 'Forge 14.23.5.2860 dedicated-side CONSTRUCT/PREINIT/INIT/POSTINIT production lifecycle harness; no server-starting events, world, listener or gameplay'
    withRLCraft = [bool]$WithRLCraft
    testDirectory = $testRoot
    modJar = $ModJar
    java = $java
    timedOut = $timedOut
    exitCode = $process.ExitCode
    lifecycleComplete = $lifecycleComplete
    eulaStillFalse = $eulaStillFalse
    modSeen = $modSeen
    modConfigWritten = $modConfigWritten
    npcDefinitionWritten = $npcDefinitionWritten
    detectedFailure = $failure
}
$result | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $testRoot 'smoke-result.json') -Encoding UTF8
[pscustomobject]$result | Format-List
if (-not $passed) { throw "Smoke test did not pass. Inspect logs in $testRoot" }
