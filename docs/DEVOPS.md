# Verifying a Minecraft mod in 2026

The brief: keep the game **free, open, installable, and playable**, and prove
the mod works without a human clicking through it. Here is the reasoning, not
just the commands.

## Two scripts, two jobs

| | Command | What it does |
|---|---|---|
| **Play** | `.\tools\play.ps1` | Builds, then launches the real game with the mod loaded, for you to drive |
| **Verify** | `.\tools\verify.ps1` | Builds, then runs every in-world test headless and exits non-zero on failure |

They are separate on purpose. Verification that needs a person watching a
window is not verification — it is a demo. And a demo that a robot drives is
not play.

## Why GameTest, and not unit tests

A unit test can prove `PanelLight.next()` cycles correctly. That was never the
risky part. What actually breaks in a mod is the seam with the game:

- the blockstate change not surviving the client/server split
- `useWithoutItem` returning the wrong `InteractionResult` and eating the click
- a `lightLevel` lambda missing from the Properties builder, so the block
  cycles perfectly and stays dark
- a registry name that compiles fine and resolves to nothing at runtime

None of those are reachable from a mocked `Level`. Minecraft's **GameTest**
framework runs the real dedicated server, places the real block, and clicks it.
`AndesiteFramePanelGameTest` asserts the blockstate after each click *and*
reads the light back out of the world, because the light wiring lives at
registration rather than in the block class and is otherwise invisible.

## Why this is better than the Forge-era equivalent

Forge deserves the credit for making automated mod testing normal. What changed
is that the harness is now **upstream**: GameTest ships in vanilla Minecraft,
so tests are not written against a loader's private test API. Fabric's
`fabric-gametest-api-v1` is a thin adapter over the vanilla framework — a few
mixins and one interface. Consequences worth having:

- **No display, no account.** `runGametest` boots a headless dedicated server.
  It runs on a CI box with no GPU and nobody logged in.
- **Standard output format.** It writes JUnit XML, so any CI reads it with no
  Minecraft-specific plugin.
- **Portable tests.** They target vanilla's `GameTestHelper`, so the same test
  bodies survive a loader change — which matters given the design brief's plan
  to ship NeoForge alongside Fabric via Architectury.
- **Playable by the user, too.** The tests are in the shipped jar, so anyone
  who installs the mod can run `/test runall` on their own copy. That is a
  deliberate cost of about two kilobytes. A mod meant to stay open should be
  verifiable by the person who downloaded it, not only by its author.

## Free, open, installable, playable

| Principle | How it is kept |
|---|---|
| **Free** | Nothing in the toolchain is paid or gated. Gradle, Loom, Fabric API, and the JDK are all open source and fetched by the wrapper. |
| **Open** | Code LGPL-3.0. The test suite ships with the mod. Every version is pinned in one file with the reason written next to it. |
| **Installable** | Output is a single self-contained jar. Drop it in `mods/` next to Fabric API. No installer, no launcher plugin, no account linkage. |
| **Playable** | `runClient` writes to `run/` inside the repo. Your real `.minecraft` saves are never touched by development. |

## CI

`.github/workflows/verify.yml` runs the same two gates on every push. It uses
the checked-in Gradle wrapper rather than a preinstalled Gradle, so the CI box
and your machine resolve identical versions — the thing that makes "works on my
machine" stop being a sentence anyone says.

## What is deliberately not here

- **No datagen yet.** With one block, hand-written JSON is shorter than the
  generator and easier to read. That flips around ten blocks; revisit then.
- **No screenshot or pixel tests.** They are slow, flaky, and would need a
  display, which forfeits the headless property that makes the rest work.
- **No auto-accepted EULA.** `play.ps1 -Server` detects an unaccepted EULA and
  tells you where to accept it. Agreeing to Mojang's terms is yours to do, and
  a script that clicks through a licence on your behalf is not a convenience.
