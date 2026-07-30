<#
.SYNOPSIS
    Renames the mod: id, namespace, Java package, resource trees, and the
    single Java constant - in one pass.

.DESCRIPTION
    The mod's name is expected to change as goals, milestones, and metrics
    change. This script is the sanctioned way to do it, so a rename stays a
    mechanical operation rather than a repo-wide find-and-replace that misses
    a directory name and produces a mod that builds but resolves nothing.

    It touches exactly the places the name is allowed to live:
      gradle.properties          mod_id / mod_name / mod_package / mod_main_class
      settings.gradle.kts        rootProject.name
      src/main/resources/assets/<id>/
      src/main/resources/data/<id>/
      src/main/java/<package path>/
      the MOD_ID constant and package/import lines in the Java sources

    Everything else (fabric.mod.json, translation keys, archivesName) is
    generated or derived and needs no edit.

    Afterwards it greps for the old id and reports any survivor, which is
    almost always a hardcoded "oldid:something" string literal - the one
    thing a rename cannot find on its own. Fix those by routing them
    through the id(String) helper.

.PARAMETER NewModId
    The new ResourceLocation namespace. Fabric requires ^[a-z][a-z0-9_-]{1,63}$.

.PARAMETER NewModName
    Human-readable display name. Defaults to NewModId, Title Cased.

.PARAMETER NewPackage
    New Java package. Defaults to the current maven_group + "." + NewModId.

.PARAMETER NewMainClass
    New entrypoint class name. Defaults to NewModName with non-letters stripped.

.PARAMETER DryRun
    Print what would change; touch nothing.

.EXAMPLE
    .\tools\rename-mod.ps1 -NewModId serenity_class -NewModName "Serenity Class"

.EXAMPLE
    .\tools\rename-mod.ps1 -NewModId octioid_mk2 -DryRun
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$NewModId,
    [string]$NewModName,
    [string]$NewPackage,
    [string]$NewMainClass,
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
Set-Location $repo

# ---- Validate against Fabric's actual rule, not a guess ----------------
if ($NewModId -cnotmatch '^[a-z][a-z0-9_-]{1,63}$') {
    throw "Invalid mod id '$NewModId'. Fabric requires ^[a-z][a-z0-9_-]{1,63}`$ - lowercase, starts with a letter, 2-64 chars, only a-z 0-9 _ and -."
}

$propsPath = Join-Path $repo 'gradle.properties'
$props = @{}
foreach ($line in Get-Content $propsPath) {
    if ($line -match '^\s*([A-Za-z0-9_.]+)\s*=\s*(.*)$') { $props[$Matches[1]] = $Matches[2] }
}

$oldModId      = $props['mod_id']
$oldPackage    = $props['mod_package']
$oldMainClass  = $props['mod_main_class']
$mavenGroup    = $props['maven_group']

if ([string]::IsNullOrWhiteSpace($oldModId)) { throw "Could not read mod_id from $propsPath." }
if ($oldModId -ceq $NewModId) { throw "mod_id is already '$NewModId'. Nothing to do." }

if (-not $NewModName)   { $NewModName   = (Get-Culture).TextInfo.ToTitleCase($NewModId -replace '[_-]', ' ') }
if (-not $NewPackage)   { $NewPackage   = "$mavenGroup.$NewModId" }
if (-not $NewMainClass) { $NewMainClass = ($NewModName -replace '[^A-Za-z0-9]', '') }

$oldPkgPath = 'src/main/java/' + ($oldPackage -replace '\.', '/')
$newPkgPath = 'src/main/java/' + ($NewPackage -replace '\.', '/')

Write-Host ""
Write-Host "  mod_id          $oldModId  ->  $NewModId"
Write-Host "  mod_name        $($props['mod_name'])  ->  $NewModName"
Write-Host "  mod_package     $oldPackage  ->  $NewPackage"
Write-Host "  mod_main_class  $oldMainClass  ->  $NewMainClass"
Write-Host ""
if ($DryRun) { Write-Host "-DryRun: nothing was changed."; return }

# git mv when the file is tracked so history follows; plain move otherwise.
function Move-Path([string]$from, [string]$to) {
    if (-not (Test-Path $from)) { Write-Host "  skip (absent): $from"; return }
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $to) | Out-Null
    $tracked = $false
    try { git ls-files --error-unmatch $from *> $null; $tracked = $? } catch { $tracked = $false }
    if ($tracked) { git mv $from $to } else { Move-Item $from $to }
    Write-Host "  moved: $from -> $to"
}

# ---- 1. Resource trees (their directory names ARE the namespace) --------
Move-Path "src/main/resources/assets/$oldModId" "src/main/resources/assets/$NewModId"
Move-Path "src/main/resources/data/$oldModId"   "src/main/resources/data/$NewModId"

# ---- 2. Java package directory -----------------------------------------
Move-Path $oldPkgPath $newPkgPath

# ---- 3. Entrypoint class file ------------------------------------------
if ($oldMainClass -cne $NewMainClass) {
    Move-Path "$newPkgPath/$oldMainClass.java" "$newPkgPath/$NewMainClass.java"
}

# ---- 4. Rewrite the Java sources ---------------------------------------
# Only three things change in Java: the package/import lines, the class
# name, and the MOD_ID constant. Everything else already goes through
# id(String) and needs no edit.
Get-ChildItem -Recurse "src/main/java" -Filter *.java -ErrorAction SilentlyContinue | ForEach-Object {
    $text = Get-Content $_.FullName -Raw
    $orig = $text
    $text = $text -creplace [regex]::Escape($oldPackage), $NewPackage
    $text = $text -creplace "\b$([regex]::Escape($oldMainClass))\b", $NewMainClass
    $text = $text -creplace "(MOD_ID\s*=\s*"")$([regex]::Escape($oldModId))("")", "`${1}$NewModId`${2}"
    if ($text -cne $orig) {
        Set-Content -Path $_.FullName -Value $text -Encoding utf8 -NoNewline
        Write-Host "  rewrote: $($_.FullName.Substring($repo.Length + 1))"
    }
}

# ---- 5. gradle.properties ----------------------------------------------
$gp = Get-Content $propsPath -Raw
$gp = $gp -creplace '(?m)^mod_id=.*$',         "mod_id=$NewModId"
$gp = $gp -creplace '(?m)^mod_name=.*$',       "mod_name=$NewModName"
$gp = $gp -creplace '(?m)^mod_package=.*$',    "mod_package=$NewPackage"
$gp = $gp -creplace '(?m)^mod_main_class=.*$', "mod_main_class=$NewMainClass"
Set-Content -Path $propsPath -Value $gp -Encoding utf8 -NoNewline
Write-Host "  rewrote: gradle.properties"

# ---- 6. settings.gradle.kts --------------------------------------------
$settingsPath = Join-Path $repo 'settings.gradle.kts'
$sg = Get-Content $settingsPath -Raw
$sg = $sg -creplace '(?m)^rootProject\.name\s*=.*$', "rootProject.name = `"$NewModId`""
Set-Content -Path $settingsPath -Value $sg -Encoding utf8 -NoNewline
Write-Host "  rewrote: settings.gradle.kts"

# ---- 7. Report survivors ------------------------------------------------
# A rename cannot find a hardcoded "oldid:something" literal. Surface them
# rather than letting a mod ship that builds but resolves nothing.
Write-Host ""
$survivors = Get-ChildItem -Recurse -File -Path 'src','docs','tools','README.md' -ErrorAction SilentlyContinue |
    Select-String -Pattern $oldModId -SimpleMatch -CaseSensitive -ErrorAction SilentlyContinue

if ($survivors) {
    Write-Warning "'$oldModId' still appears in $($survivors.Count) place(s). Review each:"
    $survivors | ForEach-Object { Write-Host ("    {0}:{1}: {2}" -f $_.Path.Substring($repo.Length + 1), $_.LineNumber, $_.Line.Trim()) }
    Write-Host ""
    Write-Host "  Registry paths should be built with $NewMainClass.id(String), never a literal namespace."
} else {
    Write-Host "  No stray references to '$oldModId' remain."
}

Write-Host ""
Write-Host "  Next: .\gradlew build    (the .gradle cache keys off rootProject.name; a first"
Write-Host "        build after a rename re-resolves and is slower than usual.)"
Write-Host ""
