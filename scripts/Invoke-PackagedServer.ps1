param(
    [Parameter(Mandatory=$true)][string]$RunName,
    [ValidateSet('fresh','reload')][string]$Phase='fresh',
    [switch]$Probe,
    [switch]$MigrateRates,
    [string]$CandidateJar,
    [string]$AdditionalMod,
    [string]$InstalledRuntime='D:\MinecraftMineralogy\_validation\os1211neo-41-packaged-server',
    [string]$Java='C:\Users\johnb\.jdks\temurin-21.0.7+6\bin\java.exe'
)
$ErrorActionPreference='Stop'
$project=Split-Path $PSScriptRoot -Parent
$build=[IO.Path]::GetFullPath((Join-Path $project 'build'))
if($RunName -notmatch '^[a-z0-9-]+$'){throw 'RunName must be a simple validation directory name'}
$run=[IO.Path]::GetFullPath((Join-Path $build ('validation\'+$RunName)))
if(-not $run.StartsWith($build+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Runtime escaped build root'}
$version=(Get-Content -LiteralPath (Join-Path $project 'gradle.properties') | Where-Object {$_ -match '^mod_version='}).Split('=')[1]
$jar=if($CandidateJar){[IO.Path]::GetFullPath($CandidateJar)}else{Join-Path $project "build\libs\SkysLeyLines-$version.jar"}
if($Phase -eq 'fresh') {
    if(Test-Path -LiteralPath $run){throw 'Use a new run name; existing validation evidence is protected'}
    New-Item -ItemType Directory -Path (Join-Path $run 'mods') -Force | Out-Null
    Copy-Item -LiteralPath $jar -Destination (Join-Path $run 'mods')
    if($Probe){Copy-Item -LiteralPath (Join-Path $project "build\validation-tools\SkysLeyLines-$version-runtime-probe.jar") -Destination (Join-Path $run 'mods')}
    if($AdditionalMod){Copy-Item -LiteralPath $AdditionalMod -Destination (Join-Path $run 'mods')}
    'eula=true' | Set-Content -LiteralPath (Join-Path $run 'eula.txt')
    @('level-seed=-4965128775892001975','level-name=world','online-mode=false','server-ip=127.0.0.1',
        'server-port=0','view-distance=2','simulation-distance=2','spawn-protection=0',
        'gamemode=creative','difficulty=peaceful','max-players=1','sync-chunk-writes=true') |
        Set-Content -LiteralPath (Join-Path $run 'server.properties')
} else {
    $installedJar=Join-Path (Join-Path $run 'mods') (Split-Path $jar -Leaf)
    if((Get-FileHash -LiteralPath $jar).Hash -ne (Get-FileHash -LiteralPath $installedJar).Hash){throw 'Reload jar differs from fresh-start candidate'}
    $previous=Join-Path $run 'logs'
    if(Test-Path -LiteralPath $previous){Copy-Item -LiteralPath $previous -Destination (Join-Path $run 'fresh-logs') -Recurse}
}
$libraryRoot=([IO.Path]::GetFullPath((Join-Path $InstalledRuntime 'libraries'))).Replace('\','/')
$source=Join-Path $InstalledRuntime 'libraries\net\neoforged\neoforge\21.1.247\win_args.txt'
$lines=Get-Content -LiteralPath $source
$arguments=foreach($line in $lines){
    $expanded=$line.Replace('libraries/',$libraryRoot+'/').Replace('-DlibraryDirectory=libraries','-DlibraryDirectory='+$libraryRoot)
    if($expanded -match '^(-p|--module-path|--class-path|-cp)\s+(.+)$'){$Matches[1]; '"'+$Matches[2]+'"'}
    elseif($expanded.StartsWith('-D') -and $expanded.Contains(' ')){'"'+$expanded+'"'}
    else{$expanded}
}
$argsPath=Join-Path $run 'server.args'
$arguments | Set-Content -LiteralPath $argsPath -Encoding utf8
$flags=@('-Xmx2G')
if($Probe){$flags+=@('-Dskysleylines.runtimeProbe=true',('-Dskysleylines.probePhase='+$Phase))}
if($MigrateRates){$flags+=@('-Dskysleylines.probeMigrate=true','-Dskysleylines.probeRate=2000')}
Get-ChildItem -LiteralPath (Join-Path $run 'mods') -File | Get-FileHash -Algorithm SHA256 |
    Select-Object Path,Hash | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $run ('candidate-'+$Phase+'.json'))
Push-Location -LiteralPath $run
try {
    & $Java @flags ('@'+$argsPath) nogui 2>&1 | Tee-Object -FilePath ('console-'+$Phase+'.log')
    $code=$LASTEXITCODE
    if($code -ne 0){throw "Packaged server exited $code"}
    if($Probe -and -not (Test-Path -LiteralPath ('ley-probe-'+$Phase+'.properties'))){throw 'Runtime probe did not produce its PASS receipt'}
} finally {Pop-Location}
