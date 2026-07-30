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
    $eula = Join-Path $repo 'run\server\eula.txt'
    if (-not (Test-Path $eula) -or -not (Select-String -Path $eula -Pattern 'eula\s*=\s*true' -Quiet)) {
        Write-Host "`nMinecraft's EULA has not been accepted for this server." -ForegroundColor Yellow
        Write-Host "That is yours to accept, not mine. The server will write"
        Write-Host "  $eula"
        Write-Host "on first run; set eula=true there yourself, then rerun."
        Write-Host "Terms: https://aka.ms/MinecraftEULA"
    }
    Write-Host "`n==> runServer" -ForegroundColor Cyan
    & $gradlew -p $repo runServer --console=plain
    exit $LASTEXITCODE
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

& $gradlew -p $repo runClient --console=plain
exit $LASTEXITCODE
