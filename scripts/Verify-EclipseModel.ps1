param(
    [string]$GradleUserHome='D:\MinecraftMineralogy\.gradle-verify-cache',
    [string]$JavaHome='C:\Users\johnb\.jdks\temurin-21.0.7+6'
)
$ErrorActionPreference='Stop'
$project=Split-Path $PSScriptRoot -Parent
$wrapper=Get-Content -Raw -LiteralPath (Join-Path $project 'gradle\wrapper\gradle-wrapper.properties')
if($wrapper -notmatch 'gradle-([0-9.]+)-bin.zip'){throw 'Cannot identify wrapper version'}
$version=$Matches[1]
$distRoot=Join-Path $GradleUserHome "wrapper\dists\gradle-$version-bin"
$installed=@(Get-ChildItem -LiteralPath $distRoot -Directory | ForEach-Object {
    $homePath=Join-Path $_.FullName "gradle-$version"
    if(Test-Path -LiteralPath (Join-Path $homePath "lib\gradle-tooling-api-$version.jar")){$homePath}
})
if($installed.Count -ne 1){throw 'Run the Gradle wrapper once in the chosen cache before this check'}
$gradleHome=$installed[0]
$output=Join-Path $project 'build\validation-tools\eclipse-tooling'
New-Item -ItemType Directory -Path $output -Force | Out-Null
& (Join-Path $JavaHome 'bin\javac.exe') -proc:none -cp (Join-Path $gradleHome 'lib\*') -d $output (Join-Path $PSScriptRoot 'VerifyEclipseModel.java')
if($LASTEXITCODE -ne 0){throw 'Eclipse tooling check did not compile'}
& (Join-Path $JavaHome 'bin\java.exe') -cp ((Join-Path $gradleHome 'lib\*')+';'+$output) VerifyEclipseModel $project $GradleUserHome $JavaHome $gradleHome
if($LASTEXITCODE -ne 0){throw 'Eclipse tooling model check failed'}
if([OperatingSystem]::IsWindows() -and [Runtime.InteropServices.RuntimeInformation]::ProcessArchitecture -eq 'X64'){
    $native=& (Join-Path $PSScriptRoot 'Get-GlfwRuntime.ps1')
    [xml]$launch=Get-Content -LiteralPath (Join-Path $project '.eclipse/configurations/Run SkysLeyLines - Client.launch')
    $vm=($launch.launchConfiguration.stringAttribute | Where-Object key -eq 'org.eclipse.jdt.launching.VM_ARGUMENTS').value
    # NeoGradle escapes Windows separators in the generated VM argument text.
    $vm=$vm -replace '\\{2,}', '\'
    $argument='-Dorg.lwjgl.glfw.libname='+$native
    if(!$vm.Contains($argument) -or ($native.Contains(' ') -and !$vm.Contains('"'+$argument+'"'))){
        throw 'Generated Eclipse client launch is missing the quoted project GLFW runtime argument'
    }
    Write-Output 'ECLIPSE_GLFW_RUNTIME_PASS'
}
