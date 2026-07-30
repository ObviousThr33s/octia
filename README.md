# Octioid

A Serenity-class ship, called to the dig.

A Fabric mod for Minecraft 1.21.1. This is the bare install: the toolchain,
the mappings, and the loader handshake, proven before any content leans on
them. Nothing is registered yet.

## Status

Scaffold only. `onInitialize` logs a line and returns.

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

```bash
./gradlew build
```

The jar lands in `build/libs/octioid-<version>.jar`.

This machine has JDK 24 and no 21, so `settings.gradle.kts` applies the foojay
toolchain resolver and Gradle provisions the pinned JDK 21 itself. The first
build therefore downloads a JDK and is slow; later builds are not.

## The version pins are deliberate

`gradle.properties` is the single control panel. Two pins are load-bearing and
should not be bumped casually:

- **Loom is held at 1.11.x.** Loom 1.17+ assumes the new official-namespace
  runtime and cannot process 1.21.1-era mods, whose access wideners are written
  in intermediary names.
- **Sources are written against Mojang official mappings (mojmap), not yarn.**
  Mixing the two produces compile errors that read like missing methods.

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
`ResourceLocation` with `Octioid.id(...)`.

## Layout

```
octioid/
├─ gradle.properties                    identity + version control panel
├─ settings.gradle.kts                  rootProject.name, toolchain resolver
├─ build.gradle.kts                     loom, deps, fabric.mod.json templating
├─ .github/workflows/verify.yml         CI: the same two gates on every push
├─ docs/
│  ├─ NAMING.md                         conventions + rename procedure
│  ├─ DEVOPS.md                         why GameTest, and how it stays open
│  ├─ LSP.md                            shared Java language server setup
│  └─ NUMERIC_MODEL.md                  which design quantities are measurable
├─ tools/
│  ├─ play.ps1                          build + launch the game
│  ├─ verify.ps1                        build + headless in-world tests
│  └─ rename-mod.ps1                    the rename
└─ src/main/
   ├─ java/com/serenity/octioid/
   │  ├─ Octioid.java                   entrypoint; MOD_ID and id() live here
   │  ├─ OctioidBlocks.java             registration, one place
   │  ├─ block/                          the andesite frame panel
   │  └─ gametest/                       in-world tests (ship in the jar)
   └─ resources/
      ├─ fabric.mod.json                TEMPLATE — generated, do not hand-edit
      ├─ assets/octioid/                textures, models, blockstates, lang
      └─ data/octioid/                  recipes, loot tables, worldgen
```

## License

Code LGPL-3.0-only. Following the AE2 layered model recorded in the design
brief: code LGPL, API MIT, assets CC BY-NC-SA. Only the code layer exists yet.
