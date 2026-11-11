param(
    [Parameter(Mandatory)][string]$SourceInstance,
    [Parameter(Mandatory)][string]$DestinationInstance,
    [Parameter(Mandatory)][string]$ModJar,
    [Parameter(Mandatory)][string]$ProbeJar,
    [Parameter(Mandatory)][string]$Minecraft,
    [Parameter(Mandatory)][string]$JavaHome
)
$ErrorActionPreference = 'Stop'
$source = (Resolve-Path -LiteralPath $SourceInstance).Path
$destination = [IO.Path]::GetFullPath($DestinationInstance)
if (Test-Path -LiteralPath $destination) { throw "El destino ya existe: $destination" }
foreach ($required in @($ModJar, $ProbeJar, (Join-Path $JavaHome 'bin/javaw.exe'), (Join-Path $source 'mmc-pack.json'))) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) { throw "Falta: $required" }
}
$gameName = if (Test-Path -LiteralPath (Join-Path $source '.minecraft')) { '.minecraft' } else { 'minecraft' }
$game = Join-Path $source $gameName
if (-not (Test-Path -LiteralPath (Join-Path $game 'mods'))) { throw 'La instancia no contiene mods.' }
$targetGame = Join-Path $destination $gameName
New-Item -ItemType Directory -Path (Join-Path $targetGame 'mods') -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $source 'mmc-pack.json') -Destination $destination
if (Test-Path -LiteralPath (Join-Path $source 'patches')) {
    Copy-Item -LiteralPath (Join-Path $source 'patches') -Destination $destination -Recurse
}
foreach ($folder in @('config','coremods','scripts','resources','structures','defaultconfigs','global_packs','resourcepacks','fancymenu_assets','fancymenu_data','addons','simple-rpc')) {
    $item = Join-Path $game $folder
    if (Test-Path -LiteralPath $item) { Copy-Item -LiteralPath $item -Destination $targetGame -Recurse }
}
Get-ChildItem -LiteralPath (Join-Path $game 'mods') | Where-Object Name -NotLike 'modhub*' | ForEach-Object {
    Copy-Item -LiteralPath $_.FullName -Destination (Join-Path $targetGame 'mods') -Recurse
}
Copy-Item -LiteralPath $ModJar,$ProbeJar -Destination (Join-Path $targetGame 'mods')
$java = (Join-Path $JavaHome 'bin/javaw.exe').Replace('\','/')
$instanceName = Split-Path -Leaf $destination
$configuration = @"
InstanceType=OneSix
name=$instanceName
OverrideJavaLocation=true
JavaPath=$java
OverrideJavaArgs=true
JvmArgs=-Dmodhub.probe.output=../build/runtime-probe/results.txt -Dmodhub.probe.mc=$Minecraft -Dmodhub.probe.loader=forge
OverrideMemory=true
MinMemAlloc=512
MaxMemAlloc=4096
OverrideCommands=true
PreLaunchCommand=
PostExitCommand=
WrapperCommand=
JoinServerOnLaunch=false
ManagedPack=false
"@
[IO.File]::WriteAllText((Join-Path $destination 'instance.cfg'), $configuration, [Text.UTF8Encoding]::new($false))
Write-Output "Copia de prueba preparada: $destination"
Write-Output "Mods: $(@(Get-ChildItem -LiteralPath (Join-Path $targetGame 'mods') -File).Count)"
