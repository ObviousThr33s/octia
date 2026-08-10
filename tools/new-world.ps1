<#
.SYNOPSIS
    Generate a world headlessly, then catalogue it.

.DESCRIPTION
    Boots the dedicated server against its own run directory, lets it write the
    spawn region, tells it to stop, moves the finished save into run/saves so the
    client can open it, and appends a register entry in house notation.

    Why this exists: every question worth asking about worldgen - does the near
    spawn derelict land, is the density right, does a ruin read as a ruin - is a
    question about a real world, and until now answering one cost a human loading
    the client. This closes the loop so tuning is a script instead of an evening.

    Nothing here accepts Minecraft's EULA. The first run writes
    run/worldgen/eula.txt with eula=false and stops; setting it to true is yours
    to do, and it is asked for exactly once.

    The server is asked to stop rather than killed. Killing a Minecraft server
    races its final chunk save, which is the one thing a world generator must
    never do. That is also why the gradle invocation passes --no-daemon: with the
    daemon in the way, the run task's stdin is the daemon's, not this script's,
    and the stop would go nowhere.

.PARAMETER Seed
    World seed. Defaults to a fresh one from the clock.

.PARAMETER Name
    Full world name. Defaults to the next free SERENITY_[a.b.c.S.A.F.E].project.

.PARAMETER Type
    Overworld preset: normal, amplified, large_biomes, flat. Default normal.
    The three existing saves deliberately run three different presets - see
    docs/WORLDS.md - and a placement rule that behaves in all of them is a rule.

.PARAMETER Chunks
    Extra radius in chunks to force-load past the spawn region, for measuring
    ruin density over a real area. 0 (the default) generates spawn only.

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
        '# Octia will not set this for you. Change false to true yourself if you',
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

$levelType = "minecraft\:$Type"
@(
    "level-name=$Name",
    "level-seed=$Seed",
    "level-type=$levelType",
    'online-mode=false',
    'spawn-protection=0',
    'view-distance=10',
    'sync-chunk-writes=true',
    'max-tick-time=-1',
    'motd=Octia worldgen'
) | Set-Content -LiteralPath (Join-Path $runDir 'server.properties') -Encoding ascii

$log = Join-Path $runDir 'logs\latest.log'
if (Test-Path -LiteralPath $log) { Remove-Item -LiteralPath $log -Force }

# ---- Boot, wait for spawn, stop cleanly ------------------------------------

Step 'generating  (dedicated server, headless)'

$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = $gradlew
# -PoctiaStdin is what connects the server's stdin so `stop` can be sent; see
# build.gradle.kts for why it is a flag and not the default. --no-daemon because
# with the daemon in the way the run task's stdin is the daemon's, not ours.
$psi.Arguments = "-p `"$repo`" runWorldgen -PoctiaStdin --console=plain --no-daemon"
$psi.WorkingDirectory = $repo
$psi.UseShellExecute = $false
$psi.RedirectStandardInput = $true

$proc = [System.Diagnostics.Process]::Start($psi)

function Wait-ForLog([string]$pattern, [int]$seconds) {
    $deadline = (Get-Date).AddSeconds($seconds)
    while ((Get-Date) -lt $deadline) {
        if ($proc.HasExited) { return $false }
        if ((Test-Path -LiteralPath $log) -and
            (Select-String -LiteralPath $log -Pattern $pattern -Quiet -ErrorAction SilentlyContinue)) {
            return $true
        }
        Start-Sleep -Seconds 2
    }
    return $false
}

if (-not (Wait-ForLog 'Done \(' 420)) {
    if (-not $proc.HasExited) { $proc.Kill() }
    throw "the server never finished starting; see $log"
}
Write-Host "  spawn region written"

# Force-load past the spawn region when a density sample was asked for. Done in
# 16x16 chunk tiles because forceload refuses more than 256 chunks at a time.
if ($Chunks -gt 0) {
    Step "force-loading  ${Chunks}-chunk radius"
    for ($cx = -$Chunks; $cx -lt $Chunks; $cx += 16) {
        for ($cz = -$Chunks; $cz -lt $Chunks; $cz += 16) {
            $x1 = $cx * 16
            $z1 = $cz * 16
            $x2 = ([Math]::Min($cx + 15, $Chunks - 1)) * 16
            $z2 = ([Math]::Min($cz + 15, $Chunks - 1)) * 16
            $proc.StandardInput.WriteLine("forceload add $x1 $z1 $x2 $z2")
            $proc.StandardInput.Flush()
            Start-Sleep -Seconds 3
        }
    }
    $proc.StandardInput.WriteLine('forceload remove all')
    $proc.StandardInput.Flush()
    Start-Sleep -Seconds 5
}

Step 'stopping'
$proc.StandardInput.WriteLine('stop')
$proc.StandardInput.Flush()

if (-not $proc.WaitForExit(180000)) {
    $proc.Kill()
    throw "the server did not stop when asked; the save at $folderDotted may be short of its last chunks"
}
Write-Host "  stopped cleanly (exit $($proc.ExitCode))"

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
