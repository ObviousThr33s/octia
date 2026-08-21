# Octia

A Serenity-class ship, called to the dig.

A Fabric mod for Minecraft 1.21.1. The toolchain, the mappings, and the loader
handshake were proven first, before any content leaned on them. That part is
done; the content sits on top of it.

## Status

Not a scaffold any more, and not a finished content mod. What is registered:

- **Two blocks.** `andesite_frame_panel`, whose panel cycles dark, generic,
  styled and lights with it, and `ship_core`, which surveys the panels around
  it and reads ADRIFT, MOORED, or CALLED.
- **Moorings.** A moored core writes its position to `SavedData` keyed by
  `BlockPos` and nothing else. The same position in another dimension is the
  same mooring — deliberate, and a gametest pins it.
- **Worldgen, behind a per-save switch.** A button on the create-world screen
  decides it once and the save keeps that answer for life. On: a lit mast at
  spawn, one derelict within a short walk of it, then derelicts and obelisks
  out through the world.
- **Sightlines.** A seeded lattice of waypoints, and a leg from each one to the
  next. Every obelisk is a large andesite prism laid along the leg under it,
  with a slot bored down its length that you can sight through, and an arch
  stands on every node — keystone plus four — squared across the leg so you walk
  through it facing the way the thread runs. Nothing in a vanilla world points
  at anything; the survey of the dev saves that establishes that is in
  [docs/SIGHTLINES.md](docs/SIGHTLINES.md).
- **Crew.** Server-side fake players seated by `/octia crew muster`, spoken for
  by a local model when one answers and by an offline tender when none does.
  LAN guests see them without installing anything.
- **Debug map.** F6 draws the moorings around you; F7 changes its range.

`onInitialize` opens the registries and installs those hooks. Each one carries
the reason it is registered there and not somewhere earlier.

## Two scripts

**Play it yourself** — builds, then launches the real game with the mod loaded:

```powershell
.\tools\play.ps1
```

**Verify it** — builds, then runs every in-world test on a headless server and
exits non-zero on failure. No display, no account, no window:

```powershell
.\tools\verify.ps1
```

They are separate on purpose. Verification that needs a person watching a
window is a demo, not verification. See [docs/DEVOPS.md](docs/DEVOPS.md) for
why the tests are GameTests rather than unit tests.

The dev client writes to `run/` inside this repo, so your real `.minecraft`
saves are never touched by development.

## Build directly

```powershell
.\gradlew build
```

The jar lands in `build/libs/octia-<version>.jar`.

This machine has JDK 24 and no 21, so `settings.gradle.kts` applies the foojay
toolchain resolver and Gradle provisions the pinned JDK 21 itself. The first
build therefore downloads a JDK and is slow; later builds are not.

## The version pins are deliberate

`gradle.properties` is the single control panel. Two pins are load-bearing and
should not be bumped casually:

- **Loom is held at 1.11.x by the Gradle wrapper**, not by Minecraft. Loom
  declares a minimum Gradle in its module metadata — 1.13.4 asks for 8.14,
  1.14.10 for 9.2.0, 1.17.17 for 9.5.0 — and Gradle refuses a plugin variant
  above itself. The wrapper here is 8.14.2, so the 1.13 line is the ceiling.
  Newer Loom still builds 1.21.1 perfectly well: what changed at Loom 1.14 was
  the plugin *id* (`net.fabricmc.fabric-loom-remap` for obfuscated Minecraft,
  1.21.11 and older), not the support. Reaching 1.17.x means bumping the
  wrapper first; `1.11.8 -> 1.12.7` needs no wrapper change at all.
- **Sources are written against Mojang official mappings (mojmap), not yarn.**
  Mixing the two produces compile errors that read like missing methods.

What breaks if you move the Minecraft pin — a startup crash at 1.21.2, both
saved-data classes at 1.21.5, and one silent data-loss hazard to check before
opening an existing save — is written down in
[docs/UPGRADING.md](docs/UPGRADING.md) with file and line.

## Naming

The name is expected to change as goals, milestones, and metrics change, so
nothing duplicates it by hand: `fabric.mod.json` is generated from
`gradle.properties`, and Java keeps exactly one constant.

Renaming is a scripted operation, not a find-and-replace:

```powershell
.\tools\rename-mod.ps1 -NewModId <new_id> -NewModName "<New Name>" -DryRun
```

See [docs/NAMING.md](docs/NAMING.md) for the conventions and the one rule that
makes the rename safe — never write a namespaced string literal; build every
`ResourceLocation` with `Octia.id(...)`.

## Layout

```
octia/
├─ gradle.properties                    identity + version control panel
├─ settings.gradle.kts                  rootProject.name, toolchain resolver
├─ build.gradle.kts                     loom, deps, fabric.mod.json templating
├─ AGENTS.md                            the rules, each one already paid for
├─ OCTIA.md                             what this repo is, and what it is not
├─ .github/
│  ├─ milestones.json                   the milestone list, synced by stable id
│  └─ workflows/
│     ├─ verify.yml                     CI: the same two gates on every push
│     ├─ release.yml                    a v* tag: both gates, then publish the jar
│     └─ milestones.yml                 milestones.json -> the repo's milestones
├─ docs/
│  ├─ NAMING.md                         conventions + rename procedure
│  ├─ DEVOPS.md                         why GameTest, and how it stays open
│  ├─ UPGRADING.md                      what breaks above 1.21.1, with lines
│  ├─ NOTATION.md                       the KEG notation, codified
│  ├─ WORLDS.md                         the dev saves, and what generates now
│  ├─ SIGHTLINES.md                     the survey, the lattice, the arch
│  ├─ ROADMAP.md                        what is wrong, missing, wanted next
│  ├─ MINUTES.md                        the sittings, and what each resolved
│  ├─ FRONT_DOOR.md                     the desktop window onto the repo
│  ├─ LSP.md                            shared Java language server setup
│  └─ NUMERIC_MODEL.md                  which design quantities are measurable
├─ tools/
│  ├─ play.ps1                          build + launch the game
│  ├─ verify.ps1                        build + headless in-world tests
│  ├─ new-world.ps1                     generate a world headlessly, then log it
│  ├─ backup-world.ps1                  snapshot a dev save outside the repo
│  ├─ world-report.py                   read a save without launching the game
│  ├─ sightline-map.py                  draw the lattice over a save, as HTML
│  ├─ sightline-map/                    that page's parts; no network, no CDN
│  ├─ frontdoor.ps1                     build + open the desktop window
│  ├─ frontdoor/                        its sources; no Fabric, no Loom
│  └─ rename-mod.ps1                    the rename
├─ src/main/
│  ├─ java/com/serenity/octia/
│  │  ├─ Octia.java                     entrypoint; MOD_ID and id() live here
│  │  ├─ OctiaBlocks.java               registration, one place
│  │  ├─ block/                         the andesite frame panel
│  │  ├─ ship/                          the core, its status, the moorings
│  │  ├─ world/                         beacon, derelict, obelisk, arch, lattice
│  │  ├─ crew/                          seats, orders, the gangway
│  │  ├─ life/                          keep-inventory, held on: death costs a life
│  │  ├─ client/                        client entrypoint and the F6 map
│  │  ├─ debug/                         the payload that map rides on
│  │  ├─ codex/                         the KEG notation, as types
│  │  └─ gametest/                      in-world tests (ship in the jar)
│  └─ resources/
│     ├─ fabric.mod.json                TEMPLATE — generated, do not hand-edit
│     ├─ assets/octia/                  textures, models, blockstates, lang
│     ├─ data/octia/                    recipes, loot tables, worldgen
│     └─ data/minecraft/tags/block/     the vanilla tags these blocks join
└─ src/test/java/com/serenity/octia/    JUnit: codex, crew, sightlines
```

## License

Code LGPL-3.0-only. Following the AE2 layered model recorded in the design
brief: code LGPL, API MIT, assets CC BY-NC-SA. Only the code layer exists yet.
