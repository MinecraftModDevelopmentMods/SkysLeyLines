param(
    [string]$RunName='packaged-client',
    [string]$AssetsDirectory
)
$ErrorActionPreference = 'Stop'
if($RunName -notmatch '^[a-z0-9-]+$'){throw 'Invalid validation runtime name'}

$runtimeRoot = 'D:\MinecraftMineralogy\_validation\os1211neo-41-packaged-client'
$fallbackRoot = 'D:\MinecraftMineralogy\packaged-runtime-cache\forge-client-1.21.1-52.1.0'
$java = 'C:\Users\johnb\.jdks\temurin-21.0.7+6\bin\java.exe'
$gameDir = Join-Path (Split-Path $PSScriptRoot -Parent) ('build\validation\'+$RunName)
$libraryDir = Join-Path $runtimeRoot 'libraries'
$baseJson = Get-Content -Raw (Join-Path $runtimeRoot 'versions\1.21.1\1.21.1.json') | ConvertFrom-Json
$neoJson = Get-Content -Raw (Join-Path $runtimeRoot 'versions\neoforge-21.1.247\neoforge-21.1.247.json') | ConvertFrom-Json
$nativesDir = Join-Path $fallbackRoot 'natives\1.21.1-forge-52.1.0'
$assetsDir = if ($AssetsDirectory) {
    (Resolve-Path -LiteralPath $AssetsDirectory).Path
} else {
    Join-Path $fallbackRoot 'assets'
}
$index = Join-Path $assetsDir ('indexes\' + $baseJson.assetIndex.id + '.json')
if (!(Test-Path -LiteralPath $index -PathType Leaf) -or
        (Get-FileHash -LiteralPath $index -Algorithm SHA1).Hash -ne $baseJson.assetIndex.sha1) {
    throw 'The vanilla asset index is missing or differs from the launcher metadata. Supply the matching assets with -AssetsDirectory.'
}

function Test-RuleSet($rules) {
    if (-not $rules) { return $true }
    $allowed = $false
    foreach ($rule in $rules) {
        $matches = $true
        if ($rule.os) {
            if ($rule.os.name -and $rule.os.name -ne 'windows') { $matches = $false }
            if ($rule.os.arch -and $rule.os.arch -ne 'x86_64') { $matches = $false }
        }
        if ($rule.features) { $matches = $false }
        if ($matches) { $allowed = $rule.action -eq 'allow' }
    }
    return $allowed
}

function Expand-Arguments($items, $values) {
    $result = [System.Collections.Generic.List[string]]::new()
    foreach ($item in $items) {
        if ($item -is [string]) {
            $entries = @($item)
        } elseif (Test-RuleSet $item.rules) {
            $entries = @($item.value)
        } else {
            continue
        }
        foreach ($entry in $entries) {
            $expanded = [string]$entry
            foreach ($key in $values.Keys) {
                $expanded = $expanded.Replace('${' + $key + '}', [string]$values[$key])
            }
            $result.Add($expanded)
        }
    }
    return $result
}

$childKeys = @{}
foreach ($library in $neoJson.libraries) {
    if ($library.downloads.artifact) {
        $parts = $library.name.Split(':')
        $childKeys[$parts[0] + ':' + $parts[1]] = $true
    }
}

$classpath = [System.Collections.Generic.List[string]]::new()
foreach ($library in $baseJson.libraries) {
    if (-not (Test-RuleSet $library.rules) -or -not $library.downloads.artifact) { continue }
    $parts = $library.name.Split(':')
    if ($childKeys.ContainsKey($parts[0] + ':' + $parts[1])) { continue }
    $relative = $library.downloads.artifact.path.Replace('/', '\')
    $candidate = Join-Path $libraryDir $relative
    if (-not (Test-Path -LiteralPath $candidate)) { $candidate = Join-Path (Join-Path $fallbackRoot 'libraries') $relative }
    if (-not (Test-Path -LiteralPath $candidate)) { throw "Missing base library: $relative" }
    $classpath.Add($candidate)
}
foreach ($library in $neoJson.libraries) {
    if (-not (Test-RuleSet $library.rules) -or -not $library.downloads.artifact) { continue }
    $relative = $library.downloads.artifact.path.Replace('/', '\')
    $candidate = Join-Path $libraryDir $relative
    if (-not (Test-Path -LiteralPath $candidate)) { throw "Missing NeoForge library: $relative" }
    $classpath.Add($candidate)
}
$classpath = @($classpath | Select-Object -Unique) -join [IO.Path]::PathSeparator

$values = @{
    natives_directory = $nativesDir
    launcher_name = 'SkysLeyLinesLocalQualification'
    launcher_version = '1'
    classpath = $classpath
    classpath_separator = [IO.Path]::PathSeparator
    library_directory = $libraryDir
    version_name = 'neoforge-21.1.247'
    auth_player_name = 'LeyLinesProbe'
    game_directory = $gameDir
    assets_root = $assetsDir
    assets_index_name = $baseJson.assetIndex.id
    auth_uuid = '00000000000000000000000000000001'
    auth_access_token = '0'
    clientid = ''
    auth_xuid = ''
    user_type = 'msa'
    version_type = 'release'
}

$jvmArgs = [System.Collections.Generic.List[string]]::new()
$jvmArgs.AddRange([string[]](Expand-Arguments $baseJson.arguments.jvm $values))
$jvmArgs.AddRange([string[]](Expand-Arguments $neoJson.arguments.jvm $values))

$jvmArgs.Add('-Xmx3G')
$guardedGlfw = & (Join-Path $PSScriptRoot 'Get-GlfwRuntime.ps1')
$jvmArgs.Add('-Dorg.lwjgl.glfw.libname='+$guardedGlfw)

$gameArgs = [System.Collections.Generic.List[string]]::new()
$gameArgs.AddRange([string[]](Expand-Arguments $baseJson.arguments.game $values))
$gameArgs.AddRange([string[]](Expand-Arguments $neoJson.arguments.game $values))
$gameArgs.Add('--width')
$gameArgs.Add('960')
$gameArgs.Add('--height')
$gameArgs.Add('540')

$gameArgs.Add('--quickPlaySingleplayer')
$gameArgs.Add('Ley Lines Visual')
Push-Location -LiteralPath $gameDir
try { & $java @jvmArgs $neoJson.mainClass @gameArgs } finally { Pop-Location }
exit $LASTEXITCODE
