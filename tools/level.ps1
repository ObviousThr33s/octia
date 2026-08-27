<#
.SYNOPSIS
    Say what this machine is qualified to do, by measuring it.

.DESCRIPTION
    There are four things a person can be to this repo, and each one is a set of
    capabilities rather than a title:

      Whimsered   plays a build                    needs a Java 21 runtime
      Prefect     makes and catalogues worlds      + gradle wrapper, EULA accepted
      Mage        reads saves without the game     + Python 3
      Sorcerer    runs the gates, commits, tags    + git identity and a remote

    The levels are cumulative: a Mage can do everything a Prefect can. Your level
    is the highest one whose requirements are ALL met.

    Why this is measured and never declared. A level kept in a config file is a
    claim, and a claim is exactly the thing this repo does not accept anywhere
    else: chunk-probe exists because reading level.dat only proves what a save
    was ASKED for, and reading the blocks proves what it GOT. The same rule
    applies to the operator. Nothing here reads a profile, a variable, or a
    setting a person could edit to promote themselves - it looks for the tools
    and reports what it found. A rank you can lie about is not a rank.

    Nothing here writes, installs, downloads, or accepts anything on your behalf.
    Every check is a read, and -Deep is the only one that touches the network.

.PARAMETER Require
    Exit non-zero unless the machine is at this level or above. This is the form
    other scripts should use as a gate:

        pwsh tools/level.ps1 -Require Mage
        if ($LASTEXITCODE -ne 0) { throw 'need a Mage for this' }

.PARAMETER Deep
    Also test push access by asking the remote, which needs the network. Without
    it, Sorcerer is judged on having an identity and a remote configured, which
    is the most that can be known offline.

.PARAMETER Json
    Machine-readable. For tools that gate on a level rather than print one.

.EXAMPLE
    .\tools\level.ps1

.EXAMPLE
    .\tools\level.ps1 -Require Mage -Json
#>
[CmdletBinding()]
param(
    [ValidateSet('Whimsered', 'Prefect', 'Mage', 'Sorcerer')]
    [string]$Require,
    [switch]$Deep,
    [switch]$Json
)

$ErrorActionPreference = 'Stop'

# $PSScriptRoot, not the working directory, so this reports on the checkout it
# lives in and cannot be pointed at another one. Same reason play.ps1 does it.
$repo = Split-Path -Parent $PSScriptRoot

function Test-Tool {
    <#
      Native version banners are read from STDOUT only. `java -version` writes to
      stderr, and redirecting a native command's stderr in Windows PowerShell 5.1
      wraps every line in an ErrorRecord and sets $? to false even on success -
      so the modern `--version` form is used instead, which Java has had since 9.
      A JRE too old to know `--version` reads here as absent, which is correct:
      it is too old for 1.21.1 anyway.
    #>
    param([string]$Exe, [string[]]$VersionArgs)
    $cmd = Get-Command $Exe -ErrorAction SilentlyContinue
    if (-not $cmd) { return $null }
    try { $out = & $Exe @VersionArgs } catch { return $null }
    if (-not $out) { return $null }
    return (($out | Out-String) -split "`n")[0].Trim()
}

# ---- the measurements -----------------------------------------------------

$facts = [ordered]@{}

$javaLine = Test-Tool 'java' @('--version')
$javaMajor = 0
if ($javaLine -and $javaLine -match '(\d+)') { $javaMajor = [int]$Matches[1] }
$facts['java'] = @{
    ok = ($javaMajor -ge 21); detail = if ($javaLine) { $javaLine } else { 'not on PATH' }
}

$wrapper = Join-Path $repo 'gradlew.bat'
$facts['gradle wrapper'] = @{
    ok = (Test-Path -LiteralPath $wrapper); detail = $wrapper
}

# The EULA is the user's to accept and this only ever reads it - see the note in
# new-world.ps1, which writes eula=false once and stops rather than deciding.
$eulaOk = $false
$eulaWhere = 'not accepted'
foreach ($p in @('run\eula.txt', 'run\worldgen\eula.txt')) {
    $full = Join-Path $repo $p
    if (Test-Path -LiteralPath $full) {
        if ((Get-Content -LiteralPath $full -Raw) -match '(?im)^\s*eula\s*=\s*true') {
            $eulaOk = $true; $eulaWhere = $p; break
        }
        $eulaWhere = "$p says false"
    }
}
$facts['EULA'] = @{ ok = $eulaOk; detail = $eulaWhere }

$pyLine = Test-Tool 'python' @('--version')
$pyOk = ($pyLine -match 'Python\s+3\.')
$facts['python 3'] = @{
    ok = $pyOk; detail = if ($pyLine) { $pyLine } else { 'not on PATH' }
}

$gitName = ''
$gitRemote = ''
try { $gitName = (& git -C $repo config user.name) } catch { }
try { $gitRemote = (& git -C $repo remote get-url origin) } catch { }
$facts['git identity'] = @{
    ok = [bool]$gitName; detail = if ($gitName) { $gitName } else { 'user.name unset' }
}
$facts['git remote'] = @{
    ok = [bool]$gitRemote; detail = if ($gitRemote) { $gitRemote } else { 'no origin' }
}

if ($Deep -and $gitRemote) {
    $pushOk = $false
    try { & git -C $repo push --dry-run origin HEAD | Out-Null; $pushOk = ($LASTEXITCODE -eq 0) } catch { }
    $facts['push access'] = @{
        ok = $pushOk; detail = if ($pushOk) { 'dry-run accepted' } else { 'remote refused a dry run' }
    }
}

# ---- the ladder -----------------------------------------------------------

$ladder = [ordered]@{
    'Whimsered' = @('java')
    'Prefect'   = @('java', 'gradle wrapper', 'EULA')
    'Mage'      = @('java', 'gradle wrapper', 'EULA', 'python 3')
    'Sorcerer'  = @('java', 'gradle wrapper', 'EULA', 'python 3', 'git identity', 'git remote')
}
if ($Deep) { $ladder['Sorcerer'] += 'push access' }

$level = 'none'
$blockers = @()
foreach ($tier in $ladder.Keys) {
    $missing = @($ladder[$tier] | Where-Object { -not $facts[$_].ok })
    if ($missing.Count -eq 0) { $level = $tier }
    elseif ($blockers.Count -eq 0) { $blockers = $missing; $nextTier = $tier }
}

if ($Json) {
    $out = [ordered]@{
        level    = $level
        next     = if ($blockers.Count) { $nextTier } else { $null }
        blockers = $blockers
        facts    = [ordered]@{}
    }
    foreach ($k in $facts.Keys) { $out.facts[$k] = $facts[$k] }
    $out | ConvertTo-Json -Depth 4
} else {
    Write-Output ''
    Write-Output '  == octia - operator level'
    Write-Output ''
    foreach ($k in $facts.Keys) {
        $mark = if ($facts[$k].ok) { 'yes' } else { ' - ' }
        Write-Output ("     {0,-4} {1,-15} {2}" -f $mark, $k, $facts[$k].detail)
    }
    Write-Output ''
    foreach ($tier in $ladder.Keys) {
        $missing = @($ladder[$tier] | Where-Object { -not $facts[$_].ok })
        $state = if ($missing.Count -eq 0) { 'met' } else { 'needs ' + ($missing -join ', ') }
        Write-Output ("     {0,-11} {1}" -f $tier, $state)
    }
    Write-Output ''
    if ($level -eq 'none') {
        Write-Output '     YOU ARE: unranked - no Java 21 runtime found.'
    } else {
        Write-Output ("     YOU ARE: {0}" -f $level)
    }
    if ($blockers.Count) {
        Write-Output ("     To reach {0}: {1}" -f $nextTier, ($blockers -join ', '))
    }
    if (-not $Deep) {
        Write-Output '     (push access not tested - pass -Deep to ask the remote)'
    }
    Write-Output ''
}

if ($Require) {
    $order = @('none', 'Whimsered', 'Prefect', 'Mage', 'Sorcerer')
    if ($order.IndexOf($level) -lt $order.IndexOf($Require)) {
        Write-Error ("this machine is {0}; {1} required" -f $level, $Require)
        exit 1
    }
}
exit 0
