param([string]$RunName='energy-unsupported-r1')
$ErrorActionPreference='Stop'
$project=Split-Path $PSScriptRoot -Parent
if($RunName -notmatch '^[a-z0-9-]+$'){throw 'Invalid fixture name'}
$target=Join-Path $project ('build\validation\'+$RunName)
if(Test-Path -LiteralPath $target){throw 'Existing negative-control evidence is protected'}
$source=Join-Path $project 'build\validation\packaged-ordinary-r2'
New-Item -ItemType Directory -Path (Join-Path $target 'mods') -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $source 'world') -Destination (Join-Path $target 'world') -Recurse
foreach($name in @('eula.txt','server.properties','server.args')){
    Copy-Item -LiteralPath (Join-Path $source $name) -Destination (Join-Path $target $name)
}
$version=(Get-Content -LiteralPath (Join-Path $project 'gradle.properties') | Where-Object {$_ -match '^mod_version='}).Split('=')[1]
Copy-Item -LiteralPath (Join-Path $project "build\libs\SkysLeyLines-$version.jar") -Destination (Join-Path $target 'mods')
$java='C:\Users\johnb\.jdks\temurin-21.0.7+6\bin\java.exe'
$cp=(Get-Content -LiteralPath (Join-Path $project 'build\validation-tools\nbt-classpath.txt') -Raw).Trim()
$data=Join-Path $target 'world\data\skysleylines.dat'
& $java -cp $cp NbtFixtureTool unsupported $data (Join-Path $project 'build\validation')
if($LASTEXITCODE -ne 0){throw 'Fixture editor failed'}
$before=(Get-FileHash -LiteralPath $data -Algorithm SHA256).Hash
$before | Set-Content -LiteralPath (Join-Path $target 'unsupported-before.sha256')
Push-Location -LiteralPath $target
try {
    & $java -Xmx2G ('@'+(Join-Path $target 'server.args')) nogui 2>&1 | Tee-Object -FilePath (Join-Path $target 'expected-rejection.log')
    $code=$LASTEXITCODE
} finally {Pop-Location}
$after=(Get-FileHash -LiteralPath $data -Algorithm SHA256).Hash
$log=Get-Content -LiteralPath (Join-Path $target 'expected-rejection.log') -Raw
# DedicatedServer logs Done before ServerStarting. That line cannot override
# an explicit startup-hook failure; the rejected data itself must remain intact.
if($before -ne $after -or -not $log.Contains('Unsupported ley save or layout version') -or
    -not $log.Contains('Cannot safely read') -or -not $log.Contains('Stopping server')){
    throw 'Unsupported data was not safely rejected/preserved'
}
@{negativeControlPass=$true;expectedStartupRejection=$true;dataUnchanged=$true;
    before=$before;after=$after;exitCode=$code;doneLoggedBeforeStartupHook=$log.Contains('Done (')} |
    ConvertTo-Json | Set-Content -LiteralPath (Join-Path $target 'negative-control.json')
Get-Content -LiteralPath (Join-Path $target 'negative-control.json')
