<#
.SYNOPSIS
    Snapshot a dev world before anything changes it.

.DESCRIPTION
    Copies a world out of the dev client's run/saves into
    D:\Serenity\world-backups, timestamped and labelled. Backups live outside
    the repo on purpose: nothing gradle does, and nothing git does, can reach
    them.

    Two things this handles that a plain Copy-Item does not:

    session.lock is deliberately excluded. Minecraft holds it open while the
    world is loaded, so copying it fails - and a stale lock restored into a
    world makes Minecraft refuse to open it with "world is in use".

    An open world is reported, not silently copied. Minecraft keeps chunks in
    memory and flushes on autosave or on quit-to-title. A backup taken while
    the world is open can be missing the last few minutes of building, which
    is exactly the kind of quiet loss a backup is supposed to prevent.

.PARAMETER World
    World folder name. Omit to list what is available.

.PARAMETER Label
    Short suffix for the backup name, e.g. "before-worldedit". Defaults to
    "snapshot".

.PARAMETER Restore
    Path to a backup to copy back. Refuses to run while the world is open.

.EXAMPLE
    .\tools\backup-world.ps1

.EXAMPLE
    .\tools\backup-world.ps1 -World "SERENITY_[0_0_0_S_A_F_E]_project" -Label before-worldedit
#>
[CmdletBinding()]
param(
    [string]$World,
    [string]$Label = 'snapshot',
    [string]$Restore
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$savesRoot  = Join-Path $repo 'run\saves'
$backupRoot = 'D:\Serenity\world-backups'

function Get-Size($path) {
    $f = Get-ChildItem -LiteralPath $path -Recurse -File -ErrorAction SilentlyContinue
    [PSCustomObject]@{ Files = $f.Count; Bytes = ($f | Measure-Object Length -Sum).Sum }
}

function Test-WorldOpen($path) {
    $lock = Join-Path $path 'session.lock'
    if (-not (Test-Path -LiteralPath $lock)) { return $false }
    # A lock file exists even after a clean exit. The real test is whether it
    # is still held: an open handle makes the file unopenable for writing.
    try {
        $fs = [System.IO.File]::Open($lock, 'Open', 'Write', 'None')
        $fs.Close()
        return $false
    } catch {
        return $true
    }
}

# ---- Restore ---------------------------------------------------------------
if ($Restore) {
    if (-not (Test-Path -LiteralPath $Restore)) { throw "no backup at $Restore" }
    if (-not $World) { throw "-Restore also needs -World (the target folder name)." }
    $target = Join-Path $savesRoot $World
    if ((Test-Path -LiteralPath $target) -and (Test-WorldOpen $target)) {
        throw "'$World' is open in Minecraft. Quit to title first - restoring under a running game corrupts it."
    }
    # The world being replaced is itself worth keeping.
    if (Test-Path -LiteralPath $target) {
        $stamp = Get-Date -Format 'yyyy-MM-dd_HHmm'
        $aside = Join-Path $backupRoot ("$World`__$stamp`__replaced")
        Copy-Item -LiteralPath $target -Destination $aside -Recurse -Force -Exclude 'session.lock'
        Write-Host "  set aside the current world: $aside"
        Remove-Item -LiteralPath $target -Recurse -Force
    }
    Copy-Item -LiteralPath $Restore -Destination $target -Recurse -Force
    Remove-Item -LiteralPath (Join-Path $target 'session.lock') -Force -ErrorAction SilentlyContinue
    $r = Get-Size $target
    Write-Host "`nRESTORED $Restore"
    Write-Host ("  -> {0}  ({1} files, {2} MB)" -f $target, $r.Files, [math]::Round($r.Bytes / 1MB, 2))
    return
}

# ---- List ------------------------------------------------------------------
if (-not (Test-Path $savesRoot)) {
    Write-Host "No dev saves yet at $savesRoot - create a world in the dev client first."
    return
}

if (-not $World) {
    Write-Host "`nDev worlds in $savesRoot`:`n"
    foreach ($d in Get-ChildItem $savesRoot -Directory) {
        $s = Get-Size $d.FullName
        $open = if (Test-WorldOpen $d.FullName) { '  [OPEN IN MINECRAFT]' } else { '' }
        Write-Host ("  {0,-40} {1,8} MB   {2}{3}" -f $d.Name, [math]::Round($s.Bytes / 1MB, 2), $d.LastWriteTime, $open)
    }
    Write-Host "`nBackups in $backupRoot`:`n"
    if (Test-Path $backupRoot) {
        foreach ($d in Get-ChildItem $backupRoot -Directory | Sort-Object Name -Descending) {
            $s = Get-Size $d.FullName
            Write-Host ("  {0,-60} {1,8} MB" -f $d.Name, [math]::Round($s.Bytes / 1MB, 2))
        }
    } else { Write-Host "  (none yet)" }
    Write-Host "`nBack one up:  .\tools\backup-world.ps1 -World '<name>' -Label before-something`n"
    return
}

# ---- Back up ---------------------------------------------------------------
$src = Join-Path $savesRoot $World
if (-not (Test-Path -LiteralPath $src)) { throw "no world '$World' in $savesRoot" }

if (Test-WorldOpen $src) {
    Write-Host "`n  '$World' is OPEN in Minecraft." -ForegroundColor Yellow
    Write-Host "  Chunks are held in memory until an autosave or quit-to-title, so this"
    Write-Host "  copy may be missing your most recent building. For a certain snapshot,"
    Write-Host "  quit to the title screen and run this again.`n"
}

$stamp = Get-Date -Format 'yyyy-MM-dd_HHmm'
$safeLabel = ($Label -replace '[^A-Za-z0-9_.-]', '-')
$dst = Join-Path $backupRoot ("$World`__$stamp`__$safeLabel")

if (Test-Path -LiteralPath $dst) { throw "backup already exists: $dst" }
New-Item -ItemType Directory -Force -Path $backupRoot | Out-Null

# -Exclude on a recursive Copy-Item is unreliable, so copy then drop the lock.
Copy-Item -LiteralPath $src -Destination $dst -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item -LiteralPath (Join-Path $dst 'session.lock') -Force -ErrorAction SilentlyContinue

# ---- Verify ----------------------------------------------------------------
$sFiles = Get-ChildItem -LiteralPath $src -Recurse -File -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -ne 'session.lock' } | ForEach-Object { $_.FullName.Substring($src.Length + 1) }
$dFiles = Get-ChildItem -LiteralPath $dst -Recurse -File -ErrorAction SilentlyContinue |
    ForEach-Object { $_.FullName.Substring($dst.Length + 1) }
$missing = @(Compare-Object $sFiles $dFiles | Where-Object SideIndicator -eq '<=' | ForEach-Object InputObject)

$size = Get-Size $dst
$regions = @(Get-ChildItem -LiteralPath (Join-Path $dst 'region') -File -ErrorAction SilentlyContinue |
    Where-Object Length -gt 0)

Write-Host ""
Write-Host "  backup: $dst"
Write-Host ("  {0} files, {1} MB, {2} region file(s) with terrain" -f $size.Files, [math]::Round($size.Bytes / 1MB, 2), $regions.Count)

if ($missing.Count) {
    Write-Host "  INCOMPLETE - missing: $($missing -join ', ')" -ForegroundColor Red
    exit 1
}
if ($regions.Count -eq 0) {
    Write-Host "  WARNING: no terrain in this copy. Nothing has been flushed to disk yet." -ForegroundColor Yellow
    exit 1
}
Write-Host "  OK - every file copied (session.lock excluded by design)" -ForegroundColor Green
exit 0
