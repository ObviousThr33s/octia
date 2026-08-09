<#
.SYNOPSIS
    Open the OCTIA front door, and put it on your desktop.

.DESCRIPTION
    The front door is a standalone Swing window - the design project's obelisk
    icon and Nocturne palette, rendered rather than themed. It is not part of
    the mod: it does not build against Loom, it does not know what Minecraft is,
    and nothing here can reach the game. It is the thing you double-click first.

    Why it lives outside src/ and outside Gradle: the mod builds on a
    provisioned JDK 21 against the Fabric toolchain, and a desktop shell has no
    business in that source set. This compiles with whatever JDK is on PATH and
    caches the jar under tools/frontdoor/build, so opening the door does not
    wake Gradle.

.PARAMETER Install
    Write the icon and drop a shortcut on the desktop, then exit without
    opening the window. It writes exactly two paths: the .lnk on your desktop
    and the .ico beside the jar.

    (Since 2026-07-31 there is one other write outside the repo: if the estate
    ledger file exists - helios_system_developer_ops\ledger\pulse.jsonl - a
    plain open appends one wake line of JSON there. -Install does not pulse,
    and deleting pulse.jsonl turns the pulse off entirely.)

.PARAMETER Rebuild
    Recompile even when the jar looks current.

.PARAMETER DesktopPath
    Where the shortcut goes. Defaults to your real desktop folder.

.EXAMPLE
    .\tools\frontdoor.ps1

.EXAMPLE
    .\tools\frontdoor.ps1 -Install
#>
[CmdletBinding()]
param(
    [switch]$Install,
    [switch]$Rebuild,
    [string]$DesktopPath = [Environment]::GetFolderPath('Desktop')
)

$ErrorActionPreference = 'Stop'

# pulse v0 (2026-07-31): see the -Install note. The lever writes, never the
# door itself. A failed pulse never blocks the door.
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

$repo    = Split-Path -Parent $PSScriptRoot
$src     = Join-Path $PSScriptRoot 'frontdoor'
$out     = Join-Path $src 'build'
$classes = Join-Path $out 'classes'
$jar     = Join-Path $out 'frontdoor.jar'
$ico     = Join-Path $out 'obelisk.ico'

function Step($text) {
    Write-Host ""
    Write-Host "==> $text" -ForegroundColor Cyan
}

# ---- toolchain -------------------------------------------------------------
# Finding a JDK on Windows is not "look on PATH". Oracle's installer puts a
# shim directory on PATH - C:\Program Files\Common Files\Oracle\Java\javapath -
# which carries java, javaw and javac but NOT jar. Trusting PATH therefore
# compiles fine and then dies on the packaging step, which is exactly what it
# did here. So: collect every plausible bin directory, and accept only one that
# has javac and jar together.

function Find-JdkBin {
    $seen = New-Object System.Collections.Generic.List[string]

    if ($env:JAVA_HOME) { $seen.Add((Join-Path $env:JAVA_HOME 'bin')) }

    $onPath = Get-Command javac.exe -ErrorAction SilentlyContinue
    if ($onPath) { $seen.Add((Split-Path -Parent $onPath.Source)) }

    # The registry knows where the real install went, shim or no shim.
    foreach ($key in @('HKLM:\SOFTWARE\JavaSoft\JDK',
                       'HKLM:\SOFTWARE\JavaSoft\Java Development Kit')) {
        try {
            $current = (Get-ItemProperty -Path $key -ErrorAction Stop).CurrentVersion
            if ($current) {
                $jdkHome = (Get-ItemProperty -Path (Join-Path $key $current) `
                    -ErrorAction Stop).JavaHome
                if ($jdkHome) { $seen.Add((Join-Path $jdkHome 'bin')) }
            }
        } catch { }
    }

    foreach ($root in @((Join-Path $env:ProgramFiles 'Java'),
                        (Join-Path $env:ProgramFiles 'Eclipse Adoptium'),
                        (Join-Path $env:ProgramFiles 'Microsoft'),
                        (Join-Path $env:ProgramFiles 'Amazon Corretto'),
                        (Join-Path $env:ProgramFiles 'BellSoft'),
                        (Join-Path $env:ProgramFiles 'Zulu'))) {
        if (Test-Path $root) {
            Get-ChildItem $root -Directory -ErrorAction SilentlyContinue |
                Sort-Object Name -Descending |
                ForEach-Object { $seen.Add((Join-Path $_.FullName 'bin')) }
        }
    }

    foreach ($candidate in $seen) {
        if ((Test-Path (Join-Path $candidate 'javac.exe')) -and
            (Test-Path (Join-Path $candidate 'jar.exe'))) {
            return $candidate
        }
    }
    return $null
}

$bin = Find-JdkBin
if ($bin) {
    $useJar = $true
} else {
    # No complete JDK found. A lone javac is still enough to run the door from
    # loose classes - it only costs the single-file jar.
    $onPath = Get-Command javac.exe -ErrorAction SilentlyContinue
    if (-not $onPath) {
        throw "no JDK found. The front door needs a JDK (17 or newer) on PATH or in JAVA_HOME."
    }
    $bin = Split-Path -Parent $onPath.Source
    $useJar = $false
    Write-Host "no jar.exe alongside javac; running from classes instead of a jar." `
        -ForegroundColor DarkYellow
}

$javac = Join-Path $bin 'javac.exe'
$javaw = Join-Path $bin 'javaw.exe'
$java  = Join-Path $bin 'java.exe'
$jarx  = Join-Path $bin 'jar.exe'
Write-Host "jdk: $bin" -ForegroundColor DarkGray

# ---- build -----------------------------------------------------------------

$sources = @(Get-ChildItem -Path (Join-Path $src 'com') -Recurse -Filter *.java)
if ($sources.Count -eq 0) { throw "no sources under $src" }

$newest = ($sources | Sort-Object LastWriteTimeUtc -Descending |
    Select-Object -First 1).LastWriteTimeUtc

# What "already built" means depends on whether we can package.
if ($useJar) {
    $built = $jar
} else {
    $built = Join-Path $classes 'com\serenity\frontdoor\FrontDoor.class'
}
$stale = $Rebuild -or (-not (Test-Path $built)) -or
    ((Get-Item $built).LastWriteTimeUtc -lt $newest)

if ($stale) {
    Step "compile  ($($sources.Count) sources)"
    if (Test-Path $classes) { Remove-Item $classes -Recurse -Force }
    New-Item -ItemType Directory -Path $classes -Force | Out-Null

    & $javac -d $classes -encoding UTF-8 $sources.FullName
    if ($LASTEXITCODE -ne 0) { throw "compile failed" }

    if ($useJar) {
        & $jarx --create --file $jar --main-class com.serenity.frontdoor.FrontDoor -C $classes .
        if ($LASTEXITCODE -ne 0) { throw "jar failed" }
    }
    Write-Host "  $built"
} else {
    Write-Host "front door is current: $built" -ForegroundColor DarkGray
}

# One place decides how the program is named on a command line, so the icon
# render, the shortcut and the plain open can never disagree about it.
if ($useJar) {
    $runPrefix = @('-jar', $jar)
} else {
    $runPrefix = @('-cp', $classes, 'com.serenity.frontdoor.FrontDoor')
}

# ---- icon ------------------------------------------------------------------
# Rendered by the same code that draws the window, so the icon on your desktop
# and the obelisk beyond the door can never be two different drawings.

if ($Install -or -not (Test-Path $ico) -or $stale) {
    Step "icon"
    & $java @runPrefix --write-icon $ico
    if ($LASTEXITCODE -ne 0) { throw "icon render failed" }
}

# ---- install or open -------------------------------------------------------

if ($Install) {
    Step "shortcut"
    if (-not (Test-Path $DesktopPath)) { throw "no such desktop folder: $DesktopPath" }

    # Quote every path: Program Files has a space in it and so might the repo.
    $quoted = ($runPrefix | ForEach-Object {
        if ($_ -match '[\\/ ]') { '"{0}"' -f $_ } else { $_ }
    }) -join ' '

    # Must match the name days.ps1's $SkipNames protects. The daily sweep moves
    # anything at the desktop root it does not recognise into a dated folder, so
    # a shortcut installed under a name that list does not carry gets carried
    # off the first time the sweep runs. Renamed from 'Octioid Front Door.lnk'
    # with the folder, 2026-08-09; the two names move together or not at all.
    $lnkPath = Join-Path $DesktopPath 'Octia Front Door.lnk'
    $shell = New-Object -ComObject WScript.Shell
    $lnk = $shell.CreateShortcut($lnkPath)
    $lnk.TargetPath        = $javaw
    $lnk.Arguments         = ('{0} "{1}"' -f $quoted, $repo)
    $lnk.WorkingDirectory  = $repo
    $lnk.IconLocation      = ('{0},0' -f $ico)
    $lnk.Description       = 'OCTIA front door - SEEK KEG |ALL|'
    $lnk.Save()

    Write-Host "  $lnkPath"
    Write-Host "  icon: $ico"
    Write-Host ""
    Write-Host "Double-click it. javaw means no console window behind the door." -ForegroundColor DarkGray
    exit 0
}

Step "open"
$proc = Start-Process -FilePath $javaw -ArgumentList ($runPrefix + $repo) -WorkingDirectory $repo -PassThru
# wake only: javaw detaches and the door outlives this lever, so its pid is
# the honest one to record and no sleep line is observable from here.
Write-Pulse @{ frame = 'OCTIA'; event = 'wake'; door = 'frontdoor.ps1'; pid = $proc.Id }
Write-Host "  the door is open." -ForegroundColor DarkGray
