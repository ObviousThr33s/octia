<#
.SYNOPSIS
    Build the mod and prove it works - headless, no account, no window.

.DESCRIPTION
    Two gates, cheapest first:

      1. `gradlew build` - compiles, remaps, and packages. Catches type errors
         and mapping drift.
      2. `gradlew runGametest` - boots a real dedicated server, loads the mod,
         runs every @GameTest, and writes a JUnit XML report.

    This is the modern Fabric answer to what Forge popularised with its own
    test harness: Minecraft's GameTest framework runs the actual game rather
    than a mock of it, needs no display and no Mojang login, and exits non-zero
    on failure. The same command works on a laptop and in CI, which is what
    keeps the mod verifiable by anyone who clones it - not just by whoever has
    the game installed and logged in.

    Nothing here touches your keyboard, your screen, or a live game window.
    Use tools/play.ps1 when you want to drive it yourself.

.PARAMETER SkipBuild
    Run only the gametests, assuming a current build.

.PARAMETER Clean
    Wipe build output first. Slower; use when mappings or Loom look confused.

.EXAMPLE
    .\tools\verify.ps1
#>
[CmdletBinding()]
param(
    [switch]$SkipBuild,
    [switch]$Clean
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$gradlew = Join-Path $repo 'gradlew.bat'
$report = Join-Path $repo 'build\test-results\gametest\report.xml'

function Step($text) {
    Write-Host ""
    Write-Host "==> $text" -ForegroundColor Cyan
}

if (-not (Test-Path $gradlew)) { throw "no gradlew.bat at $gradlew" }

# A stale report would be read as a pass if the run itself died early.
if (Test-Path $report) { Remove-Item $report -Force }

if ($Clean) {
    Step "clean"
    & $gradlew -p $repo clean --console=plain
    if ($LASTEXITCODE -ne 0) { throw "clean failed" }
}

if (-not $SkipBuild) {
    Step "build  (compile, remap, package)"
    & $gradlew -p $repo build --console=plain
    if ($LASTEXITCODE -ne 0) {
        Write-Host "`nBUILD FAILED - gametests not attempted." -ForegroundColor Red
        exit 1
    }
}

Step "gametest  (headless dedicated server, real game)"
& $gradlew -p $repo runGametest --console=plain
$gametestExit = $LASTEXITCODE

# ---- Report ---------------------------------------------------------------
Step "results"

if (-not (Test-Path $report)) {
    Write-Host "No report at $report." -ForegroundColor Red
    Write-Host "The server exited before writing one. Scroll up for the cause -"
    Write-Host "a missing EULA agreement and a mod load failure both look like this."
    exit 1
}

[xml]$xml = Get-Content $report

# Counts come from the testcase nodes, never from testsuite attributes.
# Fabric's SavingXmlReportingTestCompletionListener writes a minimal report:
# a nested <testsuite> carrying no tests/failures/errors/skipped attributes at
# all. Reading those absent attributes yields 0, which turns a clean green run
# into "0 tests ran" and reports a pass as a failure.
$cases = @($xml.SelectNodes('//testcase'))
$tests = $cases.Count
$failures = 0; $errors = 0; $skipped = 0

foreach ($case in $cases) {
    $fail = $case.SelectSingleNode('failure')
    $err  = $case.SelectSingleNode('error')
    $skip = $case.SelectSingleNode('skipped')
    $bad  = if ($fail) { $fail } else { $err }

    if ($fail) { $failures++ }
    elseif ($err) { $errors++ }
    elseif ($skip) { $skipped++ }

    if ($bad) {
        Write-Host ("  FAIL  {0}" -f $case.name) -ForegroundColor Red
        $msg = $bad.message
        if ($msg) { Write-Host ("        {0}" -f $msg) -ForegroundColor DarkRed }
    } elseif ($skip) {
        Write-Host ("  skip  {0}" -f $case.name) -ForegroundColor Yellow
    } else {
        Write-Host ("  pass  {0}" -f $case.name) -ForegroundColor Green
    }
}

Write-Host ""
Write-Host ("  {0} test(s): {1} failed, {2} errored, {3} skipped" -f $tests, $failures, $errors, $skipped)
Write-Host ("  report: {0}" -f $report)

$jar = Get-ChildItem (Join-Path $repo 'build\libs') -Filter '*.jar' -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -notmatch 'sources' } | Select-Object -First 1
if ($jar) { Write-Host ("  jar:    {0} ({1} bytes)" -f $jar.Name, $jar.Length) }

if ($failures -gt 0 -or $errors -gt 0 -or $gametestExit -ne 0) {
    Write-Host "`nVERIFY FAILED" -ForegroundColor Red
    exit 1
}
if ($tests -eq 0) {
    Write-Host "`nNo tests ran. That is a failure, not a pass." -ForegroundColor Yellow
    exit 1
}
Write-Host "`nVERIFY OK" -ForegroundColor Green
exit 0
