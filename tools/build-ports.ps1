param(
    [ValidateSet('1.12.2','1.16.5','1.18.2','1.19.2','1.20.1','all')][string]$Target = 'all',
    [string]$Java8Home = $env:MODHUB_JAVA8_HOME,
    [string]$Java17Home = $env:MODHUB_JAVA17_HOME,
    [string[]]$Tasks = @('build')
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot
function Find-Jdk([string]$explicitPath,[string]$major) {
    if ($explicitPath -and (Test-Path -LiteralPath (Join-Path $explicitPath 'bin/javac.exe'))) { return $explicitPath }
    if ($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'release')) -and ((Get-Content (Join-Path $env:JAVA_HOME 'release') -Raw) -match ('JAVA_VERSION="(?:1\.)?' + $major + '\.'))) { return $env:JAVA_HOME }
    if ($IsWindows -or $env:OS -eq 'Windows_NT') {
        foreach ($jdkPath in @(Get-ChildItem (Join-Path $env:ProgramFiles 'Eclipse Adoptium') -Directory -ErrorAction SilentlyContinue)) {
            if ((Test-Path -LiteralPath (Join-Path $jdkPath.FullName 'release')) -and ((Get-Content (Join-Path $jdkPath.FullName 'release') -Raw) -match ('JAVA_VERSION="(?:1\.)?' + $major + '\.'))) { return $jdkPath.FullName }
        }
    }
    throw "Configure MODHUB_JAVA${major}_HOME o pase la ruta del JDK $major."
}
$savedJavaHome = $env:JAVA_HOME
$savedGradleHome = $env:GRADLE_USER_HOME
$savedPath = $env:PATH
try {
    $env:GRADLE_USER_HOME = Join-Path $projectRoot '.gradle-cache'
    $targets = if ($Target -eq 'all') { @('1.20.1','1.12.2','1.16.5','1.18.2','1.19.2') } else { @($Target) }
    foreach ($portTarget in $targets) {
        $jdkMajor = if ($portTarget -eq '1.12.2') { '8' } else { '17' }
        $env:JAVA_HOME = Find-Jdk $(if ($jdkMajor -eq '8') { $Java8Home } else { $Java17Home }) $jdkMajor
        $env:PATH = (Join-Path $env:JAVA_HOME 'bin') + [IO.Path]::PathSeparator + $savedPath
        $portFolder = if ($portTarget -eq '1.20.1') { $projectRoot } else { Join-Path $projectRoot "adapters/forge-$portTarget" }
        Push-Location $portFolder
        try {
            $extra = @()
            if ($portTarget -eq '1.16.5') { $extra += "-Porg.gradle.java.installations.paths=$(Find-Jdk $Java8Home '8')" }
            & .\gradlew.bat @extra @Tasks
            if ($LASTEXITCODE -ne 0) { throw "Falló la compilación del destino $portTarget." }
        } finally { Pop-Location }
    }
} finally { $env:JAVA_HOME=$savedJavaHome; $env:GRADLE_USER_HOME=$savedGradleHome; $env:PATH=$savedPath }

