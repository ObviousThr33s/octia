<#
.SYNOPSIS
    Generate a world headlessly, then catalogue it.

.DESCRIPTION
    Boots the dedicated server against its own run directory, lets it write the
    world, moves the finished save into run/saves so the client can open it, and
    appends a register entry in house notation.

    Why this exists: every question worth asking about worldgen - does the near
    spawn derelict land, is the density right, does a ruin read as a ruin - is a
    question about a real world, and until now answering one cost a human loading
    the client. This closes the loop so tuning is a script instead of an evening.

    Nothing here accepts Minecraft's EULA. The first run writes
    run/worldgen/eula.txt with eula=false and stops; setting it to true is yours
    to do, and it is asked for exactly once.

    The server stops itself once the work is done - see HeadlessRun - rather than
    being killed. Killing a Minecraft server races its final chunk save, which is
    the one thing a world generator must never do.

    It is also shut to the outside world: bound to loopback, no player slots, no
    status ping, no query, no rcon, and Mojang authentication left on. It draws
    terrain for a minute with nobody connected and has no business being
    reachable from anywhere.

.PARAMETER Seed
    World seed. Defaults to a fresh one from the clock.

.PARAMETER Name
    Full world name. Defaults to the next free SERENITY_[a.b.c.S.A.F.E].project.

.PARAMETER Type
    Overworld preset: normal, amplified, large_biomes, flat. Default normal.
    The three existing saves deliberately run three different presets - see
    docs/WORLDS.md - and a placement rule that behaves in all of them is a rule.

.PARAMETER Chunks
    Radius in chunks to generate around spawn, for measuring ruin density over a
    real area. 0 (the default) generates the spawn region only. Cost is
    quadratic: 32 is 4225 chunks and takes minutes.

.EXAMPLE
    .\tools\new-world.ps1
    .\tools\new-world.ps1 -Seed 1 -Type amplified -Chunks 32
#>
[CmdletBinding()]
param(
    [long]$Seed = 0,
    [string]$Name = '',
    [ValidateSet('normal', 'amplified', 'large_biomes', 'flat')]
    [string]$Type = 'normal',
    [int]$Chunks = 0
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$gradlew = Join-Path $repo 'gradlew.bat'
$runDir = Join-Path $repo 'run\worldgen'
$saves = Join-Path $repo 'run\saves'

function Step($text) {
    Write-Host ""
    Write-Host "==> $text" -ForegroundColor Cyan
}

if (-not (Test-Path $gradlew)) { throw "no gradlew.bat at $gradlew" }
New-Item -ItemType Directory -Force -Path $runDir | Out-Null
New-Item -ItemType Directory -Force -Path $saves | Out-Null

# ---- The EULA, asked for once and never on your behalf --------------------

$eula = Join-Path $runDir 'eula.txt'
if (-not (Test-Path -LiteralPath $eula)) {
    # Written as false. This is the file appearing, not the terms being agreed.
    @(
        '# Minecraft requires agreement to its EULA before a dedicated server',
        '# will start: https://aka.ms/MinecraftEULA',
        '#',
        "# $modName will not set this for you. Change false to true yourself if you",
        '# agree, and tools/new-world.ps1 will work from then on.',
        'eula=false'
    ) | Set-Content -LiteralPath $eula -Encoding ascii
}

if (-not (Select-String -LiteralPath $eula -Pattern '^\s*eula\s*=\s*true' -Quiet)) {
    Write-Host ""
    Write-Host "Minecraft's EULA has not been accepted for this server." -ForegroundColor Yellow
    Write-Host "That is yours to accept, not mine. Set eula=true in:"
    Write-Host "  $eula"
    Write-Host "then run this again. Terms: https://aka.ms/MinecraftEULA"
    Write-Host ""
    exit 2
}

# ---- Naming, in house style ------------------------------------------------

# The register and the three existing saves use SERENITY_[a.b.c.S.A.F.E].project
# with the folder carrying underscores and level.dat carrying the dots. A
# dedicated server takes one string for both, so the dotted form goes in
# server.properties (which is what shows in game and in the register) and the
# folder is renamed to the underscore form afterwards. Minecraft does not mind:
# the client lists saves by folder and reads the name out of level.dat.

function Next-WorldName {
    $best = @(0, 0, 0)
    foreach ($dir in Get-ChildItem -LiteralPath $saves -Directory -ErrorAction SilentlyContinue) {
        if ($dir.Name -match 'SERENITY_\[(\d+)_(\d+)_(\d+)_S_A_F_E\]_project') {
            $found = @([int]$Matches[1], [int]$Matches[2], [int]$Matches[3])
            for ($i = 0; $i -lt 3; $i++) {
                if ($found[$i] -gt $best[$i]) { $best = $found; break }
                if ($found[$i] -lt $best[$i]) { break }
            }
        }
    }
    $best[2] = $best[2] + 1
    return "SERENITY_[$($best[0]).$($best[1]).$($best[2]).S.A.F.E].project"
}

if ([string]::IsNullOrWhiteSpace($Name)) { $Name = Next-WorldName }
if ($Seed -eq 0) { $Seed = [long]((Get-Date).Ticks % 2147483647) }

$folderDotted = Join-Path $runDir $Name
$folderFinal = $Name -replace '\.', '_'
$destination = Join-Path $saves $folderFinal

if (Test-Path -LiteralPath $destination) {
    throw "a save already exists at $destination"
}

Step "world  $Name"
Write-Host "  seed    : $Seed"
Write-Host "  terrain : minecraft:$Type"
Write-Host "  into    : $destination"

# ---- server.properties -----------------------------------------------------

# Shut to the outside world, on purpose and by several independent means.
#
# A default dedicated server binds 0.0.0.0:25565 and answers anyone who can
# reach the machine - the log says "Starting Minecraft server on *:25565". This
# one exists to draw terrain for about a minute with nobody connected, so it has
# no business being reachable at all:
#
#   server-ip=127.0.0.1   binds loopback only. Nothing off this machine can open
#                         a socket to it, which is the setting that actually
#                         matters; everything below is belt and braces.
#   max-players=0         no slots to take even from loopback.
#   enable-status=false   does not answer server-list pings, so it does not
#                         announce itself to a LAN scan.
#   query / rcon          both off. Neither is wanted and both are extra ports.
#   online-mode=true      Mojang authentication left ON. Turning it off is what
#                         printed the SERVER IS RUNNING IN OFFLINE/INSECURE MODE
#                         warning, and an unauthenticated server is exactly the
#                         thing not to leave listening. No login happens here.
#   white-list            on and enforced, with an empty whitelist.
$levelType = "minecraft\:$Type"
@(
    "level-name=$Name",
    "level-seed=$Seed",
    "level-type=$levelType",
    'server-ip=127.0.0.1',
    'max-players=0',
    'online-mode=true',
    'enable-status=false',
    'enable-query=false',
    'enable-rcon=false',
    'white-list=true',
    'enforce-whitelist=true',
    'spawn-protection=0',
    'view-distance=10',
    'sync-chunk-writes=true',
    'max-tick-time=-1',
    "motd=$modName worldgen (local only)"
) | Set-Content -LiteralPath (Join-Path $runDir 'server.properties') -Encoding ascii

$log = Join-Path $runDir 'logs\latest.log'
if (Test-Path -LiteralPath $log) { Remove-Item -LiteralPath $log -Force }

# ---- Boot, wait for spawn, stop cleanly ------------------------------------

Step 'generating  (dedicated server, headless)'

# The server generates and then halts itself; see HeadlessRun. Nothing is
# written to its console, because that path put a byte-order mark in front of
# the word `stop` twice and left a finished world locked behind a server that
# had ignored it. A JVM property crosses PowerShell, Gradle and the forked JVM
# with nothing in the middle to encode it.
#
# The flag names are built from mod_id rather than written out, and so is the
# log filter further down. This script is the third corner of a coupling with
# build.gradle.kts and Octia.property(String), and it is the corner a rename
# script will never find because it is not Java and not JSON. Read the id from
# the one place that owns it.
$modId = (Select-String -Path (Join-Path $repo 'gradle.properties') `
    -Pattern '^\s*mod_id\s*=\s*(.+?)\s*$').Matches[0].Groups[1].Value
if (-not $modId) { throw "mod_id not found in gradle.properties" }

# The display name too, because the log lines this script gates on are written
# by Java as prose - "Octia worldgen: done, stopping" - and the rename script
# rewrites the bare class name wherever it appears, prose included. So the
# success gate below moves under a rename whether or not anybody remembers it.
$modName = (Select-String -Path (Join-Path $repo 'gradle.properties') `
    -Pattern '^\s*mod_name\s*=\s*(.+?)\s*$').Matches[0].Groups[1].Value
if (-not $modName) { throw "mod_name not found in gradle.properties" }

$radiusArg = if ($Chunks -gt 0) { " -P$($modId)Radius=$Chunks" } else { '' }

# Killing gradle leaves the server it forked running: with --no-daemon gradle is
# the parent and the JVM is a child that does not die with it. Every failure
# path has to sweep, or the next run meets a world directory still held open.
function Stop-Orphans {
    Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction SilentlyContinue |
        Where-Object { $_.CommandLine -like '*runWorldgen*' } |
        ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
}

$proc = Start-Process -FilePath $gradlew -PassThru -NoNewWindow -WorkingDirectory $repo `
    -ArgumentList "-p `"$repo`" runWorldgen -P$($modId)Exit$radiusArg --console=plain --no-daemon"

# Generous: a large radius is genuinely slow, and the server says so as it goes.
$budget = 600 + ($Chunks * $Chunks * 2)
if (-not $proc.WaitForExit($budget * 1000)) {
    $proc.Kill()
    Stop-Orphans
    throw "the server did not finish within $budget seconds; see $log"
}
Stop-Orphans

if (-not (Select-String -LiteralPath $log -Pattern "$modName worldgen: done, stopping" -Quiet)) {
    throw "the server exited without reporting a finished run; see $log"
}
foreach ($line in (Select-String -LiteralPath $log -Pattern "$modName (worldgen|: beacon|: derelict)")) {
    Write-Host "  $($line.Line -replace "^\[[^]]*\] \[[^]]*\] \($modId\) ", '')"
}

# ---- Move it where the client can open it ----------------------------------

if (-not (Test-Path -LiteralPath $folderDotted)) {
    throw "the server did not write a world at $folderDotted"
}

Move-Item -LiteralPath $folderDotted -Destination $destination
# session.lock is the server's, not this save's. A stale one makes the client
# refuse to open the world - the same trap backup-world.ps1 documents.
$lock = Join-Path $destination 'session.lock'
if (Test-Path -LiteralPath $lock) { Remove-Item -LiteralPath $lock -Force }

Step 'cataloguing'
& python (Join-Path $PSScriptRoot 'world-report.py') --catalogue $destination

Write-Host ""
Write-Host "WORLD READY  $folderFinal" -ForegroundColor Green
Write-Host "  open it with .\tools\play.ps1, or read it with:"
Write-Host "  python tools\world-report.py --ruins `"$destination`""
Write-Host ""
