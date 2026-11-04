param([Parameter(Mandatory=$true)][string]$RunDirectory)
$ErrorActionPreference='Stop'
$project=[IO.Path]::GetFullPath((Split-Path $PSScriptRoot -Parent))
$run=[IO.Path]::GetFullPath($RunDirectory)
if(-not ($run.StartsWith($project+'\run\',[StringComparison]::OrdinalIgnoreCase) -or
    $run.StartsWith($project+'\build\validation\',[StringComparison]::OrdinalIgnoreCase))){throw 'RCON is confined to this project validation runtimes'}
$settings=@{}
foreach($line in Get-Content -LiteralPath (Join-Path $run 'server.properties')){
    if($line -match '^([^#=]+)=(.*)$'){$settings[$Matches[1]]=$Matches[2]}
}
if($settings['server-ip'] -ne '127.0.0.1' -or $settings['enable-rcon'] -ne 'true'){throw 'Expected isolated loopback validation server'}
$client=[Net.Sockets.TcpClient]::new('127.0.0.1',[int]$settings['rcon.port'])
$stream=$client.GetStream(); $stream.ReadTimeout=10000
function Write-Packet([int]$id,[int]$type,[string]$text){
    $payload=[Text.Encoding]::UTF8.GetBytes($text)
    $packet=[byte[]]::new($payload.Length+14)
    [BitConverter]::GetBytes($payload.Length+10).CopyTo($packet,0)
    [BitConverter]::GetBytes($id).CopyTo($packet,4)
    [BitConverter]::GetBytes($type).CopyTo($packet,8)
    $payload.CopyTo($packet,12)
    $stream.Write($packet,0,$packet.Length)
}
function Read-Exact([int]$count){
    $buffer=[byte[]]::new($count); $offset=0
    while($offset -lt $count){$read=$stream.Read($buffer,$offset,$count-$offset);if($read -eq 0){throw 'RCON connection closed'};$offset+=$read}
    return ,$buffer
}
function Read-Packet {
    $length=[BitConverter]::ToInt32((Read-Exact 4),0)
    if($length -lt 10 -or $length -gt 1048576){throw 'Invalid RCON packet'}
    $body=Read-Exact $length
    return @{id=[BitConverter]::ToInt32($body,0);type=[BitConverter]::ToInt32($body,4);text=[Text.Encoding]::UTF8.GetString($body,8,$length-10)}
}
try {
    Write-Packet 1 3 $settings['rcon.password']
    do {$reply=Read-Packet; if($reply.id -eq -1){throw 'RCON authentication failed'}} while($reply.type -ne 2)
    foreach($command in @('skysleylines sample 1166 49 -743','save-all flush','stop')){
        Write-Packet 2 2 $command
        $reply=Read-Packet
        if($reply.id -ne 2){throw 'Unexpected RCON response identity'}
        Write-Output $reply.text
    }
} finally {$client.Dispose()}
