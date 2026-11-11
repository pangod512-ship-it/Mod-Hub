param(
    [string]$Minecraft = '1.20',
    [ValidateSet('forge', 'neoforge')][string]$Loader = 'forge',
    [string]$JavaHome,
    [string[]]$Tasks = @('build')
)
$ErrorActionPreference = 'Stop'
$workspace = Split-Path $PSScriptRoot
$catalog = Get-Content -LiteralPath (Join-Path $workspace 'adapters/modern-targets.json') -Raw | ConvertFrom-Json
$target = $catalog | Where-Object minecraft -EQ $Minecraft
if (-not $target -or -not $target.$Loader) { throw "No existe $Loader para Minecraft $Minecraft en el catálogo." }
$major = [string]$target.java
if (-not $JavaHome) { $JavaHome = [Environment]::GetEnvironmentVariable("MODHUB_JAVA${major}_HOME") }
if (-not $JavaHome) {
    $candidates = @($env:JAVA_HOME)
    foreach ($vendor in @('Eclipse Adoptium', 'Java')) {
        $candidates += @(Get-ChildItem -LiteralPath (Join-Path $env:ProgramFiles $vendor) -Directory -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName)
    }
    foreach ($candidate in $candidates) {
        if (-not $candidate) { continue }
        $releaseFile = Join-Path $candidate 'release'
        if ((Test-Path -LiteralPath $releaseFile) -and ((Get-Content -LiteralPath $releaseFile -Raw) -match ('JAVA_VERSION="' + $major + '\.'))) {
            $JavaHome = $candidate
            break
        }
    }
}
if (-not $JavaHome -or -not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin/javac.exe'))) { throw "Configura MODHUB_JAVA${major}_HOME con un JDK $major." }
$savedJava = $env:JAVA_HOME
$savedGradle = $env:GRADLE_USER_HOME
try {
    $env:JAVA_HOME = $JavaHome
    $env:GRADLE_USER_HOME = Join-Path $workspace '.gradle-cache'
    $module = if ($Loader -eq 'neoforge' -and $Minecraft -ne '1.20.1') { 'adapters/neoforge' } else { 'adapters/modern' }
    if ($Loader -eq 'neoforge' -and $Minecraft -in @('1.20.2', '1.20.3', '1.20.5')) { $module = 'adapters/neoforge-legacy' }
    if ($Loader -eq 'forge' -and [version]$Minecraft -ge [version]'1.20.6') { $module = 'adapters/forge-next' }
    $probe = ($Tasks -contains '-PportProbe') -and ($Tasks -contains 'runClient')
    $buildContext = if ($module -like 'adapters/neoforge*') { $Minecraft } else { "$Minecraft-$Loader" }
    $probeResult = Join-Path $workspace "$module/build/$buildContext/runtime-probe/results.txt"
    if ($probe -and (Test-Path -LiteralPath $probeResult)) { Remove-Item -LiteralPath $probeResult }
    $wrapper = if ($module -eq 'adapters/forge-next') { Join-Path $workspace "$module/gradlew.bat" } else { Join-Path $workspace 'gradlew.bat' }
    $targetGradleState = Join-Path $workspace "$module/build/$buildContext/gradle-state"
    & $wrapper -p (Join-Path $workspace $module) '--project-cache-dir' $targetGradleState '--max-workers=2' "-PmcVersion=$Minecraft" "-PmodLoader=$Loader" @Tasks
    if ($LASTEXITCODE -ne 0) { throw "Falló $Minecraft / $Loader; no se puede declarar compatible." }
    if ($probe -and (-not (Test-Path -LiteralPath $probeResult) -or (Get-Content -LiteralPath $probeResult -Raw) -notmatch '^PASSED:')) {
        throw "La prueba dentro del juego falló o no produjo resultado: $probeResult"
    }
    if ($probe -and ($Tasks -contains 'build')) {
        $artifact = Join-Path $workspace "$module/build/$buildContext/libs/modhub-1.1.1-mc$Minecraft-$Loader.jar"
        if (-not (Test-Path -LiteralPath $artifact -PathType Leaf)) { throw "Falta el JAR compilado: $artifact" }
        $nativeResult = (Get-Content -LiteralPath $probeResult -Raw).Trim()
        $evidence = [ordered]@{
            minecraft = $Minecraft
            loader = $Loader
            checkedAtUtc = [DateTime]::UtcNow.ToString('o')
            file = Split-Path -Leaf $artifact
            sha256 = (Get-FileHash -LiteralPath $artifact -Algorithm SHA256).Hash.ToLowerInvariant()
            nativeResult = $nativeResult
        }
        $evidence | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $workspace "$module/build/$buildContext/runtime-probe/artifact.json") -Encoding utf8
    }
} finally {
    $env:JAVA_HOME = $savedJava
    $env:GRADLE_USER_HOME = $savedGradle
}
