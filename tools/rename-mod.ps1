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

# Read and write UTF-8 explicitly, both directions. Two separate bugs lived here.
#
# WRITING: Set-Content -Encoding utf8 on Windows PowerShell 5.1 writes a BOM, so every
# .java file, gradle.properties and settings.gradle.kts this script touched came back
# with EF BB BF on the front. A BOM on a .properties file can swallow the first key, on
# JSON it breaks strict parsers, and it is invisible in every editor that would show it.
#
# READING: worse. Get-Content -Raw on PS 5.1 decodes a UTF-8 file that has no BOM using
# the ANSI codepage, so an em-dash (E2 80 94) came back as three CP1252 characters and
# was then re-encoded as UTF-8 on write. Every non-ASCII character in the file was
# silently corrupted — "Octioid — a Serenity-class ship" became "Octia â€”
# a Serenity-class ship". The build still passes, which is what makes it dangerous.
#
# Both caught on the octioid -> octia rename, 2026-07-30. Read with
# [System.IO.File]::ReadAllText (UTF-8 with BOM detection) and write with Write-Text.
# Never Get-Content -Raw, never Set-Content, on any file that may hold a character
# above U+007F.
$Utf8NoBom = New-Object System.Text.UTF8Encoding($false)
function Write-Text([string]$path, [string]$text) {
    if ($text.Length -gt 0 -and $text[0] -eq [char]0xFEFF) { $text = $text.Substring(1) }
    [System.IO.File]::WriteAllText($path, $text, $Utf8NoBom)
}

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
# src/test/java is walked too. It was missed once: the test package kept the old name,
# its directory was never moved, and the build broke on a package/path mismatch that the
# survivor report showed but nothing acted on.
$oldTestPkgPath = 'src/test/java/' + ($oldPackage -replace '\.', '/')
$newTestPkgPath = 'src/test/java/' + ($NewPackage -replace '\.', '/')
Move-Path $oldTestPkgPath $newTestPkgPath

foreach ($tree in @('src/main/java', 'src/test/java')) {
    Get-ChildItem -Recurse $tree -Filter *.java -ErrorAction SilentlyContinue | ForEach-Object {
        $text = [System.IO.File]::ReadAllText($_.FullName)
        $orig = $text
        $text = $text -creplace [regex]::Escape($oldPackage), $NewPackage
        $text = $text -creplace "\b$([regex]::Escape($oldMainClass))\b", $NewMainClass
        $text = $text -creplace "(MOD_ID\s*=\s*"")$([regex]::Escape($oldModId))("")", "`${1}$NewModId`${2}"
        if ($text -cne $orig) {
            Write-Text $_.FullName $text
            Write-Host "  rewrote: $($_.FullName.Substring($repo.Length + 1))"
        }
    }
}

# A class whose name changed must have its FILE renamed to match, or javac refuses it.
# Only the entrypoint is handled above; any other type carrying the old name (OctioidBlocks
# -> OctiaBlocks) has to move as well, and it will not be found by looking at ids alone.
Get-ChildItem -Recurse 'src' -Filter *.java -ErrorAction SilentlyContinue | ForEach-Object {
    $decl = Select-String -Path $_.FullName -Pattern '^\s*public\s+(?:final\s+)?(?:class|record|enum|interface)\s+(\w+)' |
            Select-Object -First 1
    if ($decl) {
        $cls = [regex]::Match($decl.Line, '(?:class|record|enum|interface)\s+(\w+)').Groups[1].Value
        $stem = [System.IO.Path]::GetFileNameWithoutExtension($_.Name)
        if ($cls -cne $stem) {
            $rel = $_.FullName.Substring($repo.Length + 1) -replace '\\', '/'
            Move-Path $rel (($rel -replace [regex]::Escape($stem + '.java'), ($cls + '.java')))
        }
    }
}

# ---- 4b. Hardcoded namespace literals ----------------------------------
# The header says a rename cannot find these. It can — they are exactly `oldid:` in a
# resource JSON and `<registry>.oldid.` in a lang file. Fixing them beats reporting them:
# the octioid -> octia rename surfaced 30 of these across blockstates, models, loot
# tables, recipes, the vanilla block tags, and en_us.json, every one of which would have
# produced a mod that builds and resolves nothing.
Get-ChildItem -Recurse 'src/main/resources' -Filter *.json -ErrorAction SilentlyContinue | ForEach-Object {
    $text = [System.IO.File]::ReadAllText($_.FullName)
    $orig = $text
    $text = $text -creplace "$([regex]::Escape($oldModId)):", "${NewModId}:"
    $text = $text -creplace "\.$([regex]::Escape($oldModId))\.", ".$NewModId."
    if ($text -cne $orig) {
        Write-Text $_.FullName $text
        Write-Host "  rewrote: $($_.FullName.Substring($repo.Length + 1))"
    }
}

# ---- 5. gradle.properties ----------------------------------------------
$gp = [System.IO.File]::ReadAllText($propsPath)
$gp = $gp -creplace '(?m)^mod_id=.*$',         "mod_id=$NewModId"
$gp = $gp -creplace '(?m)^mod_name=.*$',       "mod_name=$NewModName"
$gp = $gp -creplace '(?m)^mod_package=.*$',    "mod_package=$NewPackage"
$gp = $gp -creplace '(?m)^mod_main_class=.*$', "mod_main_class=$NewMainClass"
# The description is prose and carries the display name; mod_sources is a URL and is
# deliberately NOT touched — renaming a repository on a forge is the owner's act, not a
# side effect of renaming a mod locally.
$oldNameRe = [regex]::Escape($props['mod_name'])
$gp = $gp -creplace "(?m)^(mod_description=.*)$oldNameRe(.*)$", "`${1}$NewModName`${2}"
Write-Text $propsPath $gp
Write-Host "  rewrote: gradle.properties"
if ($gp -cmatch '(?m)^mod_sources=.*' + [regex]::Escape($oldModId)) {
    Write-Warning "mod_sources still points at '$oldModId'. If the remote is renamed, update it by hand."
}

# ---- 6. settings.gradle.kts --------------------------------------------
$settingsPath = Join-Path $repo 'settings.gradle.kts'
$sg = [System.IO.File]::ReadAllText($settingsPath)
$sg = $sg -creplace '(?m)^rootProject\.name\s*=.*$', "rootProject.name = `"$NewModId`""
Write-Text $settingsPath $sg
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
