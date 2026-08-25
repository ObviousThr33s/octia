# Verifying a Minecraft mod in 2026

The brief: keep the game **free, open, installable, and playable**, and prove
the mod works without a human clicking through it. Here is the reasoning, not
just the commands.

## Two scripts, two jobs

| | Command | What it does |
|---|---|---|
| **Play** | `.\tools\play.ps1` | Builds, then launches the real game with the mod loaded, for you to drive |
| **Verify** | `.\tools\verify.ps1` | Builds, then runs every in-world test headless and exits non-zero on failure |
| **Verify, with a bench** | `.\tools\verify.ps1 -Bench` | The same, but declares that a local model endpoint is running and holds the crew to it |

A bare verify declares `octia.crew.network=absent`, which is an assertion and not
a skip: it holds the crew to the offline tender. `-Bench` declares `required`
instead and needs something answering on 1234 / 11434 / 8080.
[docs/BENCH.md](BENCH.md) is where that decision and its alternatives are
recorded; `build.gradle.kts` is where the property is passed to the gametest run.

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
- **Not playable by the user — a correction.** This list used to claim anyone
  who installs the mod can run `/test runall` on their own copy. They cannot,
  and the reason is worth keeping so nobody re-derives it wrong.
  `fabric-gametest-api-v1` sits in Fabric API's `devOnlyModules`, so it is not
  in the published fat jar; `FabricGameTestModInitializer` is the only thing
  that reads the `fabric-gametest` entrypoint, and it never loads. Nothing
  crashes — the test classes are just inert in a release jar, about two
  kilobytes of it. In a **dev** client (`runClient`) the module is present and
  `/test runall` works normally.

  Making it true for end users means `include()`-ing
  `net.fabricmc.fabric-api:fabric-gametest-api-v1` at the version fabric-api's
  own POM pins for this line — `2.0.5+6fc22b9919`, confirmed in the `runGametest`
  log — not the newest version that module's maven-metadata lists, which targets
  a later Minecraft and would break the build. A dedicated server would also
  need `-Dfabric-api.gametest.command=true`, since the command defaults off
  there and on only for clients.

## Two things about the harness, settled by experiment `[2026-08-24]`

Both of these were raised as concerns during the cube push. One was a real defect
with a one-line fix; the other was a correct reading of vanilla's bytecode that is
nevertheless wrong about this repo. They are recorded together because the second
one, if believed, would have condemned the entire suite.

### A class not on the entrypoint list does not run, and one never had

`ArchGameTest` carried nine `@GameTest` methods and had **never been on the
`fabric-gametest` list in `fabric.mod.json`** — so from the day it was written to
`[2026-08-24]`, not one of its nine assertions had ever executed. It was added to
the list and every one of the nine passes; the suite went from 130 to 139.

Nothing warns about this. The class compiles, the methods look like tests, the
gate goes green, and the count is the only thing that would have told you. **When
adding a gametest class, the list entry is the test — the file is just where it
lives.** A count that does not grow after adding a class is the symptom.

### `throw new AssertionError` *does* fail its test here, despite the bytecode

The concern, and it is bytecode-accurate: `GameTestInfo` has exactly two exception
tables — in `tickInternal()` and `startTest()` — and **both catch
`java.lang.Exception` and nothing wider**. `GameTestAssertException` extends
`RuntimeException` and is caught; `java.lang.AssertionError` extends `Error` and is
not. Since 22 of this repo's 23 gametest classes are built on `throw new
AssertionError(...)` rather than `helper.fail(...)`, the conclusion drawn was that
roughly 292 assertion sites could not fail a test, and the green gate meant
nothing.

**It was tested rather than argued, and it is false.** A throwaway class with two
deliberately-failing methods — one per form — was put on the entrypoint list and
run:

```
2 required tests failed :(
   - assertprobegametest.probehelperfail
   - assertprobegametest.probethrownassertionerror
```

Both forms fail, cleanly, per test. The reason the bytecode reading does not
decide it is that vanilla's exception tables are not on the path a Fabric gametest
takes. `FabricGameTestHelper` invokes the test method reflectively and handles
what comes back — the log line is `(FabricGameTestHelper) Exception occurred when
invoking test method` — and reflection wraps **any** `Throwable`, `Error`
included, in an `InvocationTargetException`. The adapter sees it before vanilla's
narrower catch ever applies.

The general lesson is the one worth keeping: reading the bytecode of the framework
told us what vanilla catches, and told us nothing about what this stack does,
because a thin adapter sat in front of it. **The experiment cost three minutes and
the argument would have cost the suite.**

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

Two things had to be true before that sentence was:

- **`gradlew` is committed executable (mode `100755`).** It was `100644`, which
  is what git records for a file created on Windows, and `./gradlew` on
  `ubuntu-latest` answers that with `Permission denied` before Gradle is ever
  reached. The fix is in the index, not in the workflow: a `chmod +x` step in CI
  would have hidden the fact that a fresh clone on any Linux machine had the
  same problem.
- **The report is read, not assumed.** `runGametest` returning 0 is not proof
  that anything ran. `tools/verify.ps1` has refused a missing report and a
  report with zero `<testcase>` nodes since it learned the difference; CI now
  refuses both too, for the same reason and in the same words.

Two more workflows sit beside it, and neither is a second gate.

**`release.yml`** fires on a `v*` tag. It runs *both* gates before it publishes
anything — build, then the in-world tests — because standing order 1 in
[../OCTIA.md](../OCTIA.md) is that the local gate and the CI gate are one gate,
and a release path that only compiled would be a third and weaker one attached
to the jar people actually download. It also refuses a tag that disagrees with
`mod_version` in `gradle.properties`: the tag names the release and
`gradle.properties` names the jar inside it, so `v0.2.0` cut against
`mod_version=0.1.0` would otherwise publish a release whose contents quietly
carry the wrong number. A prerelease tag therefore needs `mod_version` to say
so too — `v0.1.0-rc1` wants `mod_version=0.1.0-rc1`.

**`milestones.yml`** syncs `.github/milestones.json` onto the repo's milestones,
and is the one place here where the interesting decision is not about Gradle.

## Why milestones sync by id and not by title

A milestone is a bag of issues wearing a name. Deleting one detaches every
issue on it, with no undo and no record of what was attached — so a sync that
reconciles by title cannot survive a rename: it sees a title it does not
recognise, creates a second milestone, and leaves the issues on the first.

So the title is display text and the **id** is the identity. Each entry in
`milestones.json` carries an id, and the workflow writes it into the milestone
description as a trailer line:

```
Hull built. Ten doors placed. Skipped eras sealed.

octia-id: m3-doors
```

That trailer — not the title — is what the next run matches on. Rename
`Ten Doors and Hull` to `The Hull` and the run issues a title `PATCH` against
the same milestone number. Nothing is created and nothing is detached.

`octia-id`, not `octioid-id`: `ObviousThr33s/Octioid` is a different project
sharing no history with this one, and the two were nearly confused once already.

Three consequences worth knowing before editing the file:

- **Deletion is deliberately absent.** Dropping an entry leaves the milestone
  alone; close it by hand. Automating that trade is trading an undoable loss
  for a tidier list.
- **A duplicate id is rejected before anything is written.** It is the one
  input that would break the design silently — both entries match the same
  milestone and the second overwrites the first.
- **The trailer is read back off the server after every write.** The whole
  scheme rests on the id surviving the round trip, so the run fails loudly if
  it ever does not, rather than leaving the next rename to orphan the issues.

A milestone made by hand in the web UI is adopted rather than duplicated, as
long as its title matches and it carries no trailer of its own — creating a
second one with that title would fail the uniqueness constraint anyway.

### The sync fires when the file *lands*, not when you are ready

Paid for on 2026-08-18, within four minutes of the workflow first existing.

The trigger is a push to `main` touching `.github/milestones.json`, and adding
the file is a push touching it. So the pull request that introduced the sync
also ran it — against the placeholder list it happened to carry, which named a
different project's milestones. Six of them were created before anyone had
decided they were the right six. The follow-up that replaced the list then
created six more beside them, correctly: new ids, no trailer to match, so the
only honest answer was to create.

Nothing malfunctioned. The workflow did what it says, twice, and both runs are
green. What was wrong was the order, and the belief — written down in the pull
request, which made it worse — that there was a safe window in which the file
existed but had not been synced. **There is no such window.**

So: land `.github/milestones.json` with the content you actually want, in the
same change that adds it. If you need the workflow in place before the list is
settled, land the workflow first and the file second.

The wreckage is cheap only because the repo had no issues yet, so the six
strays had nothing attached to detach. That will not be true next time, and it
is the reason the sync refuses to delete anything on its own.

## What is deliberately not here

- **No datagen yet.** With two blocks, hand-written JSON is shorter than the
  generator and easier to read. That flips around ten blocks; revisit then.
- **No screenshot or pixel tests.** They are slow, flaky, and would need a
  display, which forfeits the headless property that makes the rest work.
- **No auto-accepted EULA.** `play.ps1 -Server` detects an unaccepted EULA and
  tells you where to accept it. Agreeing to Mojang's terms is yours to do, and
  a script that clicks through a licence on your behalf is not a convenience.
- **No separate `build.yml`.** A compile-only workflow would be a second gate
  that goes green while the in-world tests are red, which is the thing standing
  order 1 exists to prevent. `verify.yml` already builds, and then does more.
  The one thing such a workflow would add is coverage of a push to a branch
  with no pull request open — `verify.yml` triggers on `push` to `main` and on
  `pull_request`, so that case runs nothing today. Close it by widening the
  existing trigger to `branches: ['**']` rather than by adding a second
  workflow, and note that doing so doubles CI on any branch that also has a PR
  open, since `push` and `pull_request` both fire.
- **No `chmod +x gradlew` step, in any workflow.** The bit is in the index
  already. See the note in [ROADMAP.md](ROADMAP.md) for why putting one back is
  worse than it looks.
- **No `docs.yml` yet.** A workflow enforcing a per-feature README shape wants
  a `features/` directory to enforce it against, and there is not one — a path
  filter that can never match is config that reads as coverage and is not. Two
  things to get right when it does arrive: `for dir in features/*/` reports a
  nonexistent `features/*//README.md` when the glob fails to expand, and
  `ls .../*.png | wc -l` under `set -o pipefail` fails the whole script rather
  than counting zero.
