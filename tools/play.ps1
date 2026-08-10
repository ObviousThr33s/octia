<#
.SYNOPSIS
    Build the mod and launch Minecraft with it loaded, for you to play.

.DESCRIPTION
    Runs `gradlew runClient` - Loom's development client. It launches the real
    game with the mod on the classpath and no packaging step in between, so
    what you test is what you just compiled.

    Why this and not the vanilla launcher:
      * No jar copying. Edit, rerun, the change is there.
      * No Mojang login needed - the dev client runs offline as "Player".
      * Your real .minecraft saves are untouched. The dev world lives in
        run/ inside this repo, so nothing here can damage Landoak or Survil.

    The script hands you the game and gets out of the way. It does not send
    input, does not drive the window, and does not touch your keyboard - the
    session is yours alone.

    Pulse (added 2026-07-31): if the estate ledger file exists
    (helios_system_developer_ops\ledger\pulse.jsonl), this lever appends one
    wake line when the game starts and one sleep line when it ends. That is
    the only write outside this repo, it is one line of JSON each way, and
    deleting pulse.jsonl turns it off entirely.

.PARAMETER SkipBuild
    Launch without rebuilding first.

.PARAMETER Server
    Launch a dedicated server instead of the client. Requires that you accept
    Mojang's EULA yourself - see the note the script prints.

.EXAMPLE
    .\tools\play.ps1
#>
[CmdletBinding()]
param(
    [switch]$SkipBuild,
    [switch]$Server
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$gradlew = Join-Path $repo 'gradlew.bat'

# pulse v0 (2026-07-31): see the header note. The lever writes, never the
# game. A failed pulse never blocks the door.
function Write-Pulse([hashtable]$line) {
    try {
        $desk = $env:HELIOS_DESK
        if (-not $desk) { $desk = 'C:\Users\kfman\Desktop\all_projects\work_projects\Ultraviolet Systems LLC\Ultra Violet System Domains\helios_system_developer_ops' }
        $ledger = Join-Path $desk 'ledger\pulse.jsonl'
        if (Test-Path -LiteralPath $ledger -PathType Leaf) {
            $o = [ordered]@{ t = (Get-Date).ToString('yyyy-MM-ddTHH:mm:sszzz') }
            foreach ($k in @('frame','event','door','mode','exit','pid')) {
                if ($line.Contains($k)) { $o[$k] = $line[$k] }
            }
            # .NET append: UTF-8 without BOM even on a fresh file, which
            # Add-Content -Encoding UTF8 cannot promise on PS 5.1.
            [System.IO.File]::AppendAllText($ledger, (ConvertTo-Json -InputObject $o -Compress) + [Environment]::NewLine)
        }
    } catch { }
}

if (-not (Test-Path $gradlew)) { throw "no gradlew.bat at $gradlew" }

if (-not $SkipBuild) {
    Write-Host "`n==> build" -ForegroundColor Cyan
    & $gradlew -p $repo build --console=plain
    if ($LASTEXITCODE -ne 0) {
        Write-Host "`nBUILD FAILED - not launching." -ForegroundColor Red
        exit 1
    }
}

if ($Server) {
    # run/, not run/server/. Loom gives runServer the default run directory, so
    # the path this used to print was one the server would never create - the
    # message sent you to look for a file that could not appear.
    $eula = Join-Path $repo 'run\eula.txt'
    if (-not (Test-Path $eula) -or -not (Select-String -Path $eula -Pattern 'eula\s*=\s*true' -Quiet)) {
        Write-Host "`nMinecraft's EULA has not been accepted for this server." -ForegroundColor Yellow
        Write-Host "That is yours to accept, not mine. The server will write"
        Write-Host "  $eula"
        Write-Host "on first run; set eula=true there yourself, then rerun."
        Write-Host "Terms: https://aka.ms/MinecraftEULA"
    }
    Write-Host "`n==> runServer" -ForegroundColor Cyan
    Write-Pulse @{ frame = 'OCTIA'; event = 'wake'; door = 'play.ps1'; mode = 'server'; pid = $PID }
    & $gradlew -p $repo runServer --console=plain
    $code = $LASTEXITCODE
    Write-Pulse @{ frame = 'OCTIA'; event = 'sleep'; door = 'play.ps1'; mode = 'server'; exit = $code; pid = $PID }
    exit $code
}

Write-Host "`n==> runClient" -ForegroundColor Cyan
Write-Host @"

  The dev world lives in $repo\run - your real saves are not touched.
  First launch downloads game assets and is slow; later launches are not.

  To try the block:
    /gamemode creative
    Search the Building Blocks tab for "Andesite Frame Panel"
    Place it, then right-click: dark -> generic (light 7) -> styled (light 15)

  To run the in-world tests from inside the game:
    /test runall

"@ -ForegroundColor DarkGray

Write-Pulse @{ frame = 'OCTIA'; event = 'wake'; door = 'play.ps1'; mode = 'client'; pid = $PID }
& $gradlew -p $repo runClient --console=plain
$code = $LASTEXITCODE
Write-Pulse @{ frame = 'OCTIA'; event = 'sleep'; door = 'play.ps1'; mode = 'client'; exit = $code; pid = $PID }
exit $code
