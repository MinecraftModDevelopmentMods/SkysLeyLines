param(
    [string]$ZigExecutable='D:\MinecraftMineralogy\_native-build-tools\zig-windows-x86_64-0.13.0\zig.exe',
    [string]$SourceArchive='D:\MinecraftMineralogy\glfw-3.4-source.zip',
    [string]$OutputDirectory,
    [switch]$Pristine
)
$ErrorActionPreference='Stop'
$project=Split-Path $PSScriptRoot -Parent
if(!$OutputDirectory){$OutputDirectory=Join-Path $project 'runtime/windows-x86_64'}
$sourceHash='A133DDC3D3C66143EBA9035621DB8E0BCF34DBA1EE9514A9E23E96AFD39FD57A'
if((Get-FileHash -LiteralPath $SourceArchive).Hash -ne $sourceHash){throw 'GLFW source archive differs from the pinned 3.4 input'}
if((& $ZigExecutable version) -ne '0.13.0'){throw 'This native build is qualified with Zig 0.13.0'}
$nativeRoot=Join-Path $project 'build/native'
$work=Join-Path $nativeRoot ('glfw-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $work -Force | Out-Null
Expand-Archive -LiteralPath $SourceArchive -DestinationPath $work
$src=Join-Path $work 'glfw-3.4'
# Restore LWJGL's native extension ABI on the pinned upstream release.
# The patch is a marked backport; it is not claimed to be the lost 3.3.3 CI tree.
$compatibilityPatch=Join-Path $project 'runtime/windows-x86_64/lwjgl-compat.patch'
Push-Location -LiteralPath $src
try {
    & git init --quiet
    if($LASTEXITCODE){throw 'Could not initialize disposable native source tree'}
    & git apply --check $compatibilityPatch
    if($LASTEXITCODE){throw 'LWJGL compatibility patch no longer matches the pinned source'}
    & git apply $compatibilityPatch
    if($LASTEXITCODE){throw 'LWJGL compatibility patch failed'}
} finally {Pop-Location}
$ownershipPatch=Join-Path $project 'runtime/windows-x86_64/owned-window.patch'
if(!$Pristine){
    Push-Location -LiteralPath $src
    try {
        & git apply --check $ownershipPatch
        if($LASTEXITCODE){throw 'Ownership patch no longer matches the compatible GLFW source'}
        & git apply $ownershipPatch
        if($LASTEXITCODE){throw 'Ownership patch failed'}
    } finally {Pop-Location}
}
$names=@('context','init','input','monitor','platform','vulkan','window','egl_context','osmesa_context',
    'null_init','null_monitor','null_window','null_joystick','win32_module','win32_time','win32_thread',
    'win32_init','win32_joystick','win32_monitor','win32_window','wgl_context')
$binary=Join-Path $work 'glfw.dll'
$arguments=@('cc','-shared','-target','x86_64-windows-gnu','-O2','-g0','-s','-DNDEBUG','-D_GLFW_WIN32','-D_GLFW_BUILD_DLL',
    '-DUNICODE','-D_UNICODE','-DWINVER=0x0603','-I',"$src/include",'-I',"$src/src",'-o',$binary)
$arguments+=@($names | ForEach-Object {Join-Path $src "src/$_.c"})
$arguments+=@('-lgdi32','-luser32','-lshell32','-lwinmm','-lole32')
$previousEpoch=$env:SOURCE_DATE_EPOCH
try {
    $env:SOURCE_DATE_EPOCH='1708473600' # Fixed release-day PE timestamp.
    & $ZigExecutable @arguments
    if($LASTEXITCODE){throw "Native compiler failed: $LASTEXITCODE"}
} finally {$env:SOURCE_DATE_EPOCH=$previousEpoch}
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$destination=Join-Path $OutputDirectory 'glfw.dll'
$hash=(Get-FileHash -LiteralPath $binary).Hash
if(Test-Path -LiteralPath $destination){
    if((Get-FileHash -LiteralPath $destination).Hash -ne $hash){throw 'Existing native output differs; choose a new output directory for review'}
} else {Copy-Item -LiteralPath $binary -Destination $destination}
Copy-Item -LiteralPath (Join-Path $src 'LICENSE.md') -Destination (Join-Path $OutputDirectory 'GLFW-LICENSE.md')
[ordered]@{glfw='3.4';source='https://codeload.github.com/glfw/glfw/zip/refs/tags/3.4';sourceSHA256=$sourceHash;
    zig='0.13.0';compilerArchiveSHA256='D859994725EF9402381E557C60BB57497215682E355204D754EE3DF75EE3C158';
    target='x86_64-windows-gnu';lwjglCompatibility='03f63972b7238a179123f4cb3fa2c2539d5a6dea (Windows backport)';
    compatibilityPatchSHA256=(Get-FileHash -LiteralPath $compatibilityPatch).Hash;
    ownershipPatchSHA256=$(if(!$Pristine){(Get-FileHash -LiteralPath $ownershipPatch).Hash}else{$null});
    sourceDateEpoch=1708473600;guarded=(!$Pristine.IsPresent);sha256=$hash} |
    ConvertTo-Json | Set-Content -LiteralPath (Join-Path $OutputDirectory 'build-record.json') -Encoding utf8
Write-Output "GLFW_NATIVE_BUILD_PASS guarded=$(!$Pristine.IsPresent) sha256=$hash"
