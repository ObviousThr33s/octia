# Naming — conventions, and how to rename

The mod's name is expected to change as goals, milestones, and metrics change.
This document is the contract that keeps that cheap.

## The conventions this project follows

These are Minecraft/Fabric conventions, not house style. Breaking them produces
a mod that builds and then silently fails to resolve anything.

| Thing | Convention | Here |
|---|---|---|
| **mod id** | `^[a-z][a-z0-9_-]{1,63}$` — lowercase, starts with a letter, no spaces. It is the `ResourceLocation` namespace. | `octia` |
| **display name** | Human-readable, Title Case. Free-form. | `Octia` |
| **Java package** | Reverse domain, all lowercase, no `_` or `-`. Never under `net.minecraft` or `net.fabricmc`. | `com.serenity.octia` |
| **maven group** | The publishing coordinate. Kept separate from the package on purpose. | `com.serenity` |
| **jar name** (`archivesName`) | The mod id. | `octia` |
| **registry paths** | `snake_case`. Never CamelCase, never a hyphen. | `andesite_frame_panel` |
| **resource dirs** | `assets/<mod_id>/`, `data/<mod_id>/`. The directory name *is* the namespace — a mismatch here is the single most common "my textures are missing" bug. | `assets/octia/` |
| **translation keys** | `<registry>.<mod_id>.<path>` | `block.octia.andesite_frame_panel` |
| **repo directory** | No spaces, no parentheses. Gradle, Loom, and the JVM all handle those unevenly. | `D:\Serenity\octia` |

## Where the name is allowed to live

Exactly four places. Everything else derives from these.

1. **`gradle.properties`** — `mod_id`, `mod_name`, `mod_package`, `mod_main_class`.
   `fabric.mod.json` is templated from these at build time by `processResources`,
   so it is generated output and must never be hand-edited.
2. **`settings.gradle.kts`** — `rootProject.name`. Gradle resolves this before
   properties are applied on some IDE import paths, so it cannot read from
   `gradle.properties`.
3. **`Octia.MOD_ID`** — the one string constant in Java.
4. **Directory names** — `assets/<id>/`, `data/<id>/`, and the Java package path.

## The rule that makes this work

> Never write a namespaced string literal. Build every `ResourceLocation`
> with `Octia.id("some_path")`.

A rename can move directories and rewrite a constant. It cannot find
`"octia:andesite_frame_panel"` buried in the middle of a string, and a mod
with one stale literal builds clean and then fails at runtime with a missing
resource. `tools/rename-mod.ps1` greps for survivors afterwards precisely
because this is the failure mode it cannot prevent on its own.

## Renaming

```powershell
.\tools\rename-mod.ps1 -NewModId serenity_class -NewModName "Serenity Class" -DryRun
```

Drop `-DryRun` to apply. The script moves the resource trees and the Java
package (with `git mv` when tracked, so history follows), rewrites the four
locations above, then reports any surviving reference to the old id.

Afterwards run `.\gradlew build`. The first build after a rename is slower than
usual: the Gradle cache keys off `rootProject.name`, so it re-resolves.

## The one cost a rename always carries

Changing `mod_id` changes the namespace, which orphans every block and item
already placed in existing saves — they resolve to nothing and are stripped on
load. That is unavoidable and is the reason the id is worth deciding
deliberately rather than drifting into.

While the project is pre-release and worlds are disposable, renaming is free in
practice. Once a world matters, a rename needs a datapack-driven or
`DataFixer`-style migration instead.
