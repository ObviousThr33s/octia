<#
.SYNOPSIS
    Bundle a play session into something reviewable.

.DESCRIPTION
    There are three jobs now, and docs/DEVOPS.md already names the first two:

      play.ps1      hands you the game and gets out of the way
      verify.ps1    proves the mod works headless, with nobody watching
      playtest.ps1  records what happened when a person played

    The third exists because of what the last session cost. The signal that
    mattered - "not very bindle like", "cool but too dark!" - was typed into
    chat, and the evidence for it was 89 screenshots. Matching one to the other
    was done by hand, by reading a log beside a folder of PNGs and lining up
    clock times. Nothing about that is hard; it is just work, and the log
    already holds every fact needed to do it.

    Minecraft writes BOTH halves to the same file, in order:

      [20:27:50] ... [CHAT] Saved screenshot as 2026-08-24_20.27.49.png
      [20:31:02] ... [CHAT] <Player> not very bindle like

    So a session is a TIMELINE, and this builds it: every typed note and every
    screenshot, interleaved, in the order they happened. A note lands next to
    the picture that prompted it because the clock says it did.

    This reads and copies. It does not launch the game, drive a window, send
    input, or touch a save. Every original stays where it was.

    WHAT IT REFUSES. A bundle with no notes and no screenshots is not a
    playtest, and producing an empty folder that looks like one is worse than
    failing. It exits non-zero instead - the same rule verify.ps1 applies to a
    report with zero testcase nodes, for the same reason.

.PARAMETER Log
    Which log to read. Defaults to run/logs/latest.log.

.PARAMETER World
    Which save under run/saves the session was played in. Defaults to the most
    recently modified one.

    Not derived from the log, deliberately. The log names the LEVEL - it says
    ServerLevel[[0.6.9]] - and the folder on disk is [0_6_9]; the sanitising
    rule between them is Minecraft's and is not something to reimplement from
    memory. So the bundle records the log's level name AND the save's own
    level.dat name side by side, and a mismatch is visible rather than silent.

.PARAMETER Out
    Where to write the bundle. Defaults to playtests/<stamp>_<world>.

.PARAMETER Zip
    Also pack the bundle into a single .zip beside it, for handing over.

.PARAMETER SkipProbe
    Skip world-report and build-probe. Faster; loses the register block and the
    list of what the player actually built.

.EXAMPLE
    .\tools\playtest.ps1

.EXAMPLE
    .\tools\playtest.ps1 -World '[0_6_8]' -Zip
#>
[CmdletBinding()]
param(
    [string]$Log,
    [string]$World,
    [string]$Out,
    [switch]$Zip,
    [switch]$SkipProbe
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot

# The probes are Python, so this needs a Mage. Gating here rather than inside
# build-probe is deliberate: build-probe IS Python, so a level check inside it
# could never fail and would be theatre. A wrapper is the honest place for it.
if (-not $SkipProbe) {
    & (Join-Path $PSScriptRoot 'level.ps1') -Require Mage | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw 'playtest needs a Mage (Python 3). Run tools/level.ps1 to see what is missing, or pass -SkipProbe.'
    }
}

# ---- the log --------------------------------------------------------------

if (-not $Log) { $Log = Join-Path $repo 'run\logs\latest.log' }
if (-not (Test-Path -LiteralPath $Log)) { throw "no log at $Log" }
$lines = Get-Content -LiteralPath $Log

$levelName = ''
$mcVersion = ''
$loaderLine = ($lines | Select-String -Pattern 'Loading Minecraft (\S+) with Fabric Loader (\S+)' | Select-Object -First 1)
if ($loaderLine -and $loaderLine.Line -match 'Loading Minecraft (\S+) with Fabric Loader (\S+)') {
    $mcVersion = $Matches[1]
}
# The log writes it as   Saving chunks for level 'ServerLevel[[0.6.9]]'/minecraft:overworld
# so the closing bracket is followed by an apostrophe before the slash. Leaving
# the '? out matched nothing and reported an empty level name, silently.
$lvl = ($lines | Select-String -Pattern "ServerLevel\[(.+?)\]'?/" | Select-Object -First 1)
if ($lvl -and $lvl.Line -match "ServerLevel\[(.+?)\]'?/") { $levelName = $Matches[1] }

# ---- the timeline ---------------------------------------------------------
#
# Three kinds of entry, all from [CHAT]. A typed note carries <Name>; a
# screenshot announces itself by filename; the rest are state changes worth
# keeping for context but not worth treating as direction.

$timeline = @()
foreach ($line in $lines) {
    if ($line -notmatch '^\[(\d\d:\d\d:\d\d)\]') { continue }
    $at = $Matches[1]
    if ($line -match '\[CHAT\] Saved screenshot as (\S+\.png)') {
        $timeline += [PSCustomObject]@{ At = $at; Kind = 'shot'; Text = $Matches[1] }
    }
    elseif ($line -match '\[CHAT\] <([^>]+)> (.+)$') {
        $timeline += [PSCustomObject]@{ At = $at; Kind = 'NOTE'; Text = ("{0}: {1}" -f $Matches[1], $Matches[2].Trim()) }
    }
    elseif ($line -match '\[CHAT\] (Set own game mode to .+|.+ was .+|.+ fell .+|.+ drowned.*|.+ died.*)$') {
        $timeline += [PSCustomObject]@{ At = $at; Kind = 'event'; Text = $Matches[1].Trim() }
    }
}

$notes = @($timeline | Where-Object { $_.Kind -eq 'NOTE' })
$shots = @($timeline | Where-Object { $_.Kind -eq 'shot' })

if ($notes.Count -eq 0 -and $shots.Count -eq 0) {
    Write-Error ("{0} holds no typed notes and no screenshots - that is not a playtest." -f (Split-Path $Log -Leaf))
    exit 1
}

# ---- the world ------------------------------------------------------------

$savesDir = Join-Path $repo 'run\saves'
if ($World) {
    $save = Join-Path $savesDir $World
    if (-not (Test-Path -LiteralPath $save)) { throw "no save named $World under $savesDir" }
} else {
    $newest = Get-ChildItem -LiteralPath $savesDir -Directory -Force -ErrorAction SilentlyContinue |
              Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $newest) { throw "no saves under $savesDir" }
    $save = $newest.FullName
}
$worldFolder = Split-Path $save -Leaf

# ---- the bundle -----------------------------------------------------------

$stamp = (Get-Item -LiteralPath $Log).LastWriteTime.ToString('yyyy-MM-dd_HHmm')
if (-not $Out) { $Out = Join-Path $repo ("playtests\{0}_{1}" -f $stamp, ($worldFolder -replace '[^\w\.\[\]-]', '_')) }
New-Item -ItemType Directory -Force -Path $Out | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $Out 'screenshots') | Out-Null

Copy-Item -LiteralPath $Log -Destination (Join-Path $Out 'session.log') -Force

# Only the screenshots this session announced - not the whole folder. A
# screenshots directory accumulates across every session ever played, and a
# bundle that swept it up would attribute months of pictures to one evening.
$shotSrc = Join-Path $repo 'run\screenshots'
$copied = 0
$missing = @()
foreach ($s in $shots) {
    $from = Join-Path $shotSrc $s.Text
    if (Test-Path -LiteralPath $from) {
        Copy-Item -LiteralPath $from -Destination (Join-Path $Out ('screenshots\' + $s.Text)) -Force
        $copied++
    } else { $missing += $s.Text }
}

$version = ''
$gp = Join-Path $repo 'gradle.properties'
if (Test-Path -LiteralPath $gp) {
    $m = Select-String -LiteralPath $gp -Pattern '^mod_version\s*=\s*(.+)$' | Select-Object -First 1
    if ($m) { $version = $m.Matches[0].Groups[1].Value.Trim() }
}
$commit = ''
try { $commit = (& git -C $repo rev-parse --short HEAD) } catch { }

if (-not $SkipProbe) {
    Write-Output '  cataloguing the world...'
    & python (Join-Path $PSScriptRoot 'world-report.py') --catalogue $save 2>&1 |
        Out-File -LiteralPath (Join-Path $Out 'catalogue.md') -Encoding utf8
    Write-Output '  probing for handiwork...'
    & python (Join-Path $PSScriptRoot 'build-probe.py') $save 2>&1 |
        Out-File -LiteralPath (Join-Path $Out 'builds.txt') -Encoding utf8
}

# ---- NOTES.md -------------------------------------------------------------
#
# The typed notes go FIRST and unabridged. They are the direction; everything
# else in the bundle is evidence for them.

$md = New-Object System.Collections.Generic.List[string]
$md.Add("# Playtest $stamp")
$md.Add('')
$md.Add('| | |')
$md.Add('|---|---|')
$md.Add("| Save folder | ``$worldFolder`` |")
$md.Add("| Level name in log | ``$levelName`` |")
$md.Add("| Minecraft | $mcVersion |")
$md.Add("| mod_version | $version |")
$md.Add("| commit | $commit |")
$md.Add("| Notes typed | $($notes.Count) |")
$md.Add("| Screenshots | $($shots.Count) announced, $copied copied |")
$md.Add('')
if ($missing.Count) {
    $md.Add("> $($missing.Count) screenshot(s) named in the log were not on disk: " + ($missing -join ', '))
    $md.Add('')
}
$md.Add('## What the player said')
$md.Add('')
if ($notes.Count) {
    foreach ($n in $notes) { $md.Add("- [ ] **$($n.At)** - $($n.Text)") }
} else {
    $md.Add('_Nothing typed this session._')
}
$md.Add('')
$md.Add('## Timeline')
$md.Add('')
$md.Add('Notes and screenshots in the order they happened, so a verdict sits next to')
$md.Add('the picture that prompted it.')
$md.Add('')
foreach ($t in $timeline) {
    switch ($t.Kind) {
        'NOTE'  { $md.Add("- **$($t.At)  NOTE**  $($t.Text)") }
        'shot'  { $md.Add("- $($t.At)  shot  ``screenshots/$($t.Text)``") }
        default { $md.Add("- $($t.At)  .     $($t.Text)") }
    }
}
$md.Add('')
if (-not $SkipProbe) {
    $md.Add('## Also in this bundle')
    $md.Add('')
    $md.Add('- `catalogue.md` - the register block for docs/WORLDS.md, in house notation')
    $md.Add('- `builds.txt` - where the player actually built, from tools/build-probe.py')
    $md.Add('- `session.log` - the log this was read from, verbatim')
}
$md | Out-File -LiteralPath (Join-Path $Out 'NOTES.md') -Encoding utf8

if ($Zip) {
    $zipPath = "$Out.zip"
    if (Test-Path -LiteralPath $zipPath) { Remove-Item -LiteralPath $zipPath -Force }
    Compress-Archive -Path (Join-Path $Out '*') -DestinationPath $zipPath
    Write-Output "  packed  $zipPath"
}

Write-Output ''
Write-Output "  bundle   $Out"
Write-Output "  world    $worldFolder   (log said '$levelName')"
Write-Output "  notes    $($notes.Count) typed"
Write-Output "  shots    $copied copied of $($shots.Count) announced"
Write-Output ''
exit 0
