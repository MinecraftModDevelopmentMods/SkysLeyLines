param(
    [string]$PristineDll,
    [string]$EvidenceDirectory
)
$ErrorActionPreference='Stop'
if(![OperatingSystem]::IsWindows() -or [Runtime.InteropServices.RuntimeInformation]::ProcessArchitecture -ne 'X64'){
    throw 'The native regression requires Windows x64 and the .NET 10 SDK'
}
$project=Split-Path $PSScriptRoot -Parent
if(!$EvidenceDirectory){$EvidenceDirectory=Join-Path $project ('build/validation/glfw-'+[guid]::NewGuid().ToString('N'))}
if(Test-Path -LiteralPath $EvidenceDirectory){throw 'Choose a new evidence directory; earlier results are preserved'}
New-Item -ItemType Directory -Path $EvidenceDirectory | Out-Null
$fixed=& (Join-Path $PSScriptRoot 'Get-GlfwRuntime.ps1')
$output=Join-Path $project 'build/validation-tools/glfw-repro'
& dotnet build (Join-Path $PSScriptRoot 'glfw-repro/GlfwRepro.csproj') -c Release -o $output *> (Join-Path $EvidenceDirectory 'build.log')
if($LASTEXITCODE){throw 'Native regression fixture build failed; inspect build.log'}
$fixture=Join-Path $output 'GlfwRepro.dll'
$cases=@(
    @{name='owned';dll=$fixed;mode='owned';expected=0},
    @{name='external';dll=$fixed;mode='external';expected=0},
    @{name='foreign';dll=$fixed;mode='10000';expected=0}
)
if($PristineDll){
    $cases+=@{name='pristine-negative';dll=(Resolve-Path -LiteralPath $PristineDll).Path;mode='1';expected=-1073741819}
}
$results=foreach($case in $cases){
    & dotnet $fixture $case.dll (Join-Path $EvidenceDirectory ($case.name+'.json')) $case.mode *> (Join-Path $EvidenceDirectory ($case.name+'.log'))
    $code=$LASTEXITCODE
    [ordered]@{name=$case.name;dllSHA256=(Get-FileHash -LiteralPath $case.dll).Hash;exit=$code;expected=$case.expected;passed=($code -eq $case.expected)}
}
$results | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $EvidenceDirectory 'results.json') -Encoding utf8
if(@($results | Where-Object {!$_.passed}).Count){throw 'Native regression failed; inspect results.json'}
Write-Output "GLFW_NATIVE_REGRESSION_PASS $EvidenceDirectory"
