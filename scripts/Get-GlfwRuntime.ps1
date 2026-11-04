$ErrorActionPreference='Stop'
$project=Split-Path $PSScriptRoot -Parent
$directory=Join-Path $project 'runtime/windows-x86_64'
$library=Join-Path $directory 'glfw.dll'
$expected=(Get-Content -LiteralPath (Join-Path $directory 'glfw.sha256') -Raw).Trim()
if($expected -notmatch '^[A-F0-9]{64}$' -or (Get-FileHash -LiteralPath $library).Hash -ne $expected){
    throw 'Project GLFW runtime is missing or changed. Restore runtime/windows-x86_64 or rebuild it with Build-GlfwRuntime.ps1.'
}
(Resolve-Path -LiteralPath $library).Path
