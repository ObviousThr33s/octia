# Octioid

A Serenity-class ship, called to the dig.

A Fabric mod for Minecraft 1.21.1. This is the bare install: the toolchain,
the mappings, and the loader handshake, proven before any content leans on
them. Nothing is registered yet.

## Status

Scaffold only. `onInitialize` logs a line and returns.

## Build

```bash
./gradlew build
```

The jar lands in `build/libs/octioid-<version>.jar`. To run a dev client:

```bash
./gradlew runClient
```

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
├─ docs/
│  ├─ NAMING.md                         conventions + rename procedure
│  ├─ LSP.md                            shared Java language server setup
│  └─ NUMERIC_MODEL.md                  which design quantities are measurable
├─ tools/rename-mod.ps1                 the rename
└─ src/main/
   ├─ java/com/serenity/octioid/
   │  └─ Octioid.java                   entrypoint; MOD_ID and id() live here
   └─ resources/
      ├─ fabric.mod.json                TEMPLATE — generated, do not hand-edit
      ├─ assets/octioid/                textures, models, blockstates, lang
      └─ data/octioid/                  recipes, loot tables, worldgen
```

## License

Code LGPL-3.0-only. Following the AE2 layered model recorded in the design
brief: code LGPL, API MIT, assets CC BY-NC-SA. Only the code layer exists yet.
