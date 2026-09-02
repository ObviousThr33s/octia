<#
.SYNOPSIS
    Build the mod and prove it works - headless, no account, no window.

.DESCRIPTION
    Three gates, cheapest first:

      1. `atlas.py --check` - proves the art grids, the one tensor, and every
         shipped texture are in step. Milliseconds; runs before the build so a
         stale texture does not cost a compile.
      2. `gradlew build` - compiles, remaps, and packages. Catches type errors
         and mapping drift.
      3. `gradlew runGametest` - boots a real dedicated server, loads the mod,
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

.PARAMETER Bench
    Declare that a local model endpoint IS running, and hold the crew bench to
    it. Without this the run declares octia.crew.network=absent and asserts the
    offline tender contract instead.

    The default is deliberate. CrewBenchGameTest defaults to "required" on its
    own, which is right at a desk with LM Studio open and wrong for a gate:
    OCTIA.md standing order 2 says the gates run with no display, no account and
    no secrets, and something listening on 127.0.0.1:1234 is the same class of
    dependency. A gate that cannot go green on a clean checkout is not a gate.

    Use -Bench when you have started LM Studio, Ollama, or anything else on
    1234 / 11434 / 8080 and you want the online half checked.

.EXAMPLE
    .\tools\verify.ps1

.EXAMPLE
    .\tools\verify.ps1 -Bench
#>
[CmdletBinding()]
param(
    [switch]$SkipBuild,
    [switch]$Clean,
    [switch]$Bench
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

# Which checkout this is. $repo comes from $PSScriptRoot so it cannot be the
# wrong one, but a green run is only reassuring if you can see what it was green
# about - and there is more than one octia tree on this machine.
Write-Host ""
Write-Host "  repo: $repo" -ForegroundColor Green

# A stale report would be read as a pass if the run itself died early.
if (Test-Path $report) { Remove-Item $report -Force }

# ---- Atlas ----------------------------------------------------------------
# Every texture in the mod compiles into one tensor, art/atlas.safetensors, and
# every shipped PNG is derived from it. This checks the three are in step: the
# art grids, the tensor, and the textures on disk. It is the cheapest gate here
# by orders of magnitude, so it runs first.
#
# Failing hard when python is missing is deliberate. BENCH.md settles the same
# question for the crew bench - a gate declares its dependency and never skips -
# and a texture gate that quietly does nothing is worse than no texture gate,
# because a green run then reads as proof the textures are current.
Step "atlas  (art grids, tensor, and textures in step)"
$python = Get-Command python -ErrorAction SilentlyContinue
if (-not $python) {
    throw "no python on PATH, so the atlas gate cannot run. See docs/PALETTE.md."
}
& $python.Source (Join-Path $repo 'tools\atlas.py') --check
if ($LASTEXITCODE -ne 0) {
    Write-Host "`nATLAS GATE FAILED - build not attempted." -ForegroundColor Red
    Write-Host "Run: python tools\atlas.py --build" -ForegroundColor Yellow
    exit 1
}

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

# A gametest server from an earlier run can still be alive, because Minecraft's
# shutdown occasionally spins forever draining chunks - see docs/ROADMAP.md. It
# holds build/gametest/session.lock while it does, and the next run then dies at
# startup with "another process has locked a portion of the file", which looks
# like a mod failure and is not one. Sweep first.
#
# STALE means old, and the age test is not decoration. A first version killed
# every runGametest process it could see, which murdered a concurrent run the
# moment two verifies overlapped - and then reported "No report", which reads
# like the suite failed rather than like it was shot. A whole run is about a
# minute; anything still alive after five is not working.
$cutoff = (Get-Date).AddMinutes(-5)
$stale = @(Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction SilentlyContinue |
    Where-Object { $_.CommandLine -like '*runGametest*' } |
    Where-Object { $_.CreationDate -lt $cutoff })
if ($stale.Count -gt 0) {
    Write-Host "  clearing $($stale.Count) stale gametest server(s) holding the world lock" -ForegroundColor Yellow
    $stale | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
    Start-Sleep -Seconds 2
}

Step "gametest  (headless dedicated server, real game)"

# Said out loud, both ways. "absent" is a real assertion - it holds the crew to
# the offline tender - and a reader who does not know that would otherwise read
# a green run as proof the bench works. It is proof the bench's absence works.
if ($Bench) {
    Write-Host "  crew bench: required (an endpoint must answer)" -ForegroundColor Yellow
    & $gradlew -p $repo runGametest --console=plain -PoctiaBench=required
} else {
    Write-Host "  crew bench: absent (asserting the offline tender; -Bench checks the other half)" -ForegroundColor DarkGray
    & $gradlew -p $repo runGametest --console=plain
}
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

# Newest first, and the sort is the whole point. This took whatever came back
# first, which is alphabetical - so with an octia-0.1.0.jar left over beside the
# octia-0.2.0-alpha.1.jar that had just been built and tested, the receipt named
# the old one. A line that says which artifact a green run was green about must
# not name a different artifact; that is worse than printing nothing.
$jar = Get-ChildItem (Join-Path $repo 'build\libs') -Filter '*.jar' -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -notmatch 'sources' } |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
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
