<#
.SYNOPSIS
    Write the card that ships beside the jar.

.DESCRIPTION
    release.yml publishes a jar and generated commit notes. A jar is not a
    playtest. Somebody who downloads one still has to work out four versions
    that must agree, and then they spawn somewhere ordinary and may never meet
    a watershed, a derelict or the squid at all - which is the difference
    between shipping a mod and shipping something anyone can report on.

    This writes that missing half: what you need, how to start, where to stand,
    and how to send a verdict back.

    EVERY VERSION IS READ, NEVER TYPED. mod_version, minecraft_version,
    fabric_loader_version, fabric_api_version and java_version all come out of
    gradle.properties at the moment the card is written. A card is published
    once per tag and then outlives the memory of whoever cut it, so a hand-typed
    Fabric API version is a support burden with a delay fuse on it. This is the
    same argument release.yml already makes when it refuses a tag that disagrees
    with mod_version: the file names the numbers, nothing else gets to.

    It writes and prints. It does not tag, publish, upload or touch the network.

.PARAMETER Out
    Where to write the card. Defaults to build/RELEASE-CARD.md, beside the jar
    it describes. Use - to write to stdout instead.

.EXAMPLE
    .\tools\release-card.ps1

.EXAMPLE
    .\tools\release-card.ps1 -Out -
#>
[CmdletBinding()]
param(
    [string]$Out
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot

# ---- the numbers, read ----------------------------------------------------

$gp = Join-Path $repo 'gradle.properties'
if (-not (Test-Path -LiteralPath $gp)) { throw "no gradle.properties at $gp" }

$props = @{}
foreach ($line in (Get-Content -LiteralPath $gp)) {
    if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.+?)\s*$') {
        $props[$Matches[1]] = $Matches[2]
    }
}

$need = @('mod_version', 'minecraft_version', 'fabric_loader_version', 'fabric_api_version', 'java_version')
$missing = @($need | Where-Object { -not $props.ContainsKey($_) })
if ($missing.Count) {
    # Refused rather than papered over. A card with a blank where a version
    # belongs is worse than no card: it reads as authoritative and is not.
    throw "gradle.properties is missing: $($missing -join ', ')"
}

$modVersion = $props['mod_version']
$mc         = $props['minecraft_version']
$loader     = $props['fabric_loader_version']
$api        = $props['fabric_api_version']
$java       = $props['java_version']
$jar        = "octia-$modVersion.jar"

# ---- the card -------------------------------------------------------------

$card = @"
## What you need

Four things, and they must all agree. These numbers are read out of
``gradle.properties`` when this card is built, so they are the versions this jar
was actually compiled and tested against - not a copy of them.

| | Version |
|---|---|
| Minecraft | **$mc** |
| Fabric Loader | **$loader** or newer |
| Fabric API | **$api** |
| Java | **$java** or newer |

## Installing

1. Install Fabric Loader $loader for Minecraft $mc.
2. Drop **``$jar``** into ``mods/``.
3. Drop **Fabric API $api** into ``mods/`` beside it. Octia will not load without it.

No installer, no launcher plugin, no account linkage. Your existing saves are
not touched.

## Making a world worth testing

Octia's features are opt-in per save. **On the world-creation screen, turn Octia
on** - the same switch raises the beacon and holds keep-inventory. A world made
without it is ordinary Minecraft.

Death costs a life, not your inventory. That is deliberate: keep-inventory is
held on and re-asserted, so ``/gamerule keepInventory false`` reverts within
50ms. It is not a switch in this mod.

## Where to stand

If you want to meet the features rather than hunt for them, this seed is a real
world that was walked and measured, not a guess:

``````
seed        9107396776871887392
world type  octia:sky
``````

Explored to 11,436 chunks it held **11 derelicts, 11 obelisks, 9 waystations**,
a beacon at spawn and 3 moorings. Spawn is high - around y 188 - because the
Overworld is drawn from floating islands.

A seed reproduces a world only for the same Minecraft version and the same mod
version. On $mc with octia $modVersion it is the world described above. On
anything else it is just a number.

## Sending a verdict back

The useful report is not a bug list. It is what you said while you were looking
at it.

- **Type it in chat, in the moment.** "not very bindle like", "cool but too
  dark!" - those are direction, and they are worth more than a considered
  write-up afterwards. Everything typed in chat is captured with its timestamp.
- **Press F2 when something is wrong or right.** Screenshots are matched back to
  your chat lines automatically by clock time.
- **Open F3 when the note is about a place**, so the coordinates are in the shot.
- **Press F5 once** if the note is about the player model.
- **One verdict line before you quit**, however blunt.

If you have the repo, ``tools/playtest.ps1`` turns a session into a single
reviewable bundle - your notes as a checklist, the screenshots interleaved with
them in the order they happened, the world's register entry, and a probe of what
you built. Otherwise just send the chat log from ``logs/`` and your screenshots.

## What this build is

**$modVersion** - an alpha, and it says so in the version string so Fabric's
loader orders it correctly. Things are provisional on purpose: the sail rig's
top speed, the watershed's density, and whether the whole thing holds its
austere andesite register are all still open questions, and a playtester's
reaction is how they get settled.
"@

if ($Out -eq '-') {
    Write-Output $card
    exit 0
}

if (-not $Out) { $Out = Join-Path $repo 'build\RELEASE-CARD.md' }
$dir = Split-Path -Parent $Out
if ($dir -and -not (Test-Path -LiteralPath $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
[System.IO.File]::WriteAllText($Out, $card, (New-Object System.Text.UTF8Encoding $false))

Write-Output ''
Write-Output "  card     $Out"
Write-Output "  jar      $jar"
Write-Output "  against  Minecraft $mc, Fabric $loader, API $api, Java $java"
Write-Output ''
exit 0
