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

# Say out loud which checkout this is.
#
# $repo comes from $PSScriptRoot, so this script always drives the tree it lives
# in and cannot be pointed at another by the working directory. That is correct
# by construction and completely invisible, which is not the same as obvious -
# there is a second checkout on this machine at D:\Serenity\Octioid from before
# the rename, and an orphaned worktree under .claude\worktrees with its own
# build output. Neither can be launched from here, but "which one am I actually
# testing" is a fair question to be able to answer by looking.
function Show-Provenance {
    $props = Join-Path $repo 'gradle.properties'
    $id = (Select-String -LiteralPath $props -Pattern '^mod_id=(.+)$').Matches.Groups[1].Value
    $ver = (Select-String -LiteralPath $props -Pattern '^mod_version=(.+)$').Matches.Groups[1].Value
    $mc = (Select-String -LiteralPath $props -Pattern '^minecraft_version=(.+)$').Matches.Groups[1].Value

    Write-Host ""
    Write-Host "  repo    : $repo" -ForegroundColor Green
    Write-Host "  mod     : $id $ver  (Minecraft $mc)"

    $jar = Join-Path $repo "build\libs\$id-$ver.jar"
    if (Test-Path $jar) {
        Write-Host "  jar     : built $((Get-Item $jar).LastWriteTime)"
    } else {
        Write-Host "  jar     : not built yet"
    }
    Write-Host "  worlds  : $(Join-Path $repo 'run\saves')"
}

Show-Provenance

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
    & $gradlew -p $repo runServer --console=plain
    $code = $LASTEXITCODE
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

& $gradlew -p $repo runClient --console=plain
$code = $LASTEXITCODE
exit $code
