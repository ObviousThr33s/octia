```
ACT ONE
MILESTONE 0
OCTIOID_[0.1.0.A.C.T.1]_codex
SEEK KEG |ALL|
```

# Agent codex

Rules for anyone working this repo — human or agent. Short on purpose. Each
rule exists because breaking it already cost something.

---

## I. Notation

Use the KEG notation, all three forms as one system. Do not deviate.

Head walkthroughs, milestones, and status reports with the four-line block.
Name artifacts with the bracket-flag form. Tag audience with pipe-scopes.

The rules are codified in [docs/NOTATION.md](docs/NOTATION.md) and transposed
to types in `com.serenity.octioid.codex`. Prefer the types over re-parsing
strings — that is what they are for.

`SEEK` is the call verb. Not "find", not "get", not "resolve".

**The world is the source of truth, not the docs.** `NotationTest` asserts the
sign at `(-112, 67, -149)` round-trips through the parser unchanged. If the
sign and the code disagree, the test fails and the code is wrong.

---

## II. The name will change

`OCTIOID` is not permanent. It changes as goals, milestones, and metrics
change, and the scaffold is built for that.

The name lives in **exactly four places**: `gradle.properties`,
`rootProject.name`, `Octioid.MOD_ID`, and the directory names. Everything else
derives. `fabric.mod.json` is generated — never hand-edit it.

> **Never write a namespaced string literal.** Build every `ResourceLocation`
> with `Octioid.id("path")`.

A rename can move directories and rewrite a constant. It cannot find
`"octioid:andesite_frame_panel"` buried mid-string, and a mod with one stale
literal builds clean and fails at runtime. Rename with
`tools/rename-mod.ps1`; it greps for survivors afterwards for exactly this
reason.

---

## III. Verify by running the game

`gradlew build` proves it compiles. It does not prove it works.

```powershell
.\tools\verify.ps1
```

Boots a real headless server, runs every `@GameTest`, exits non-zero on
failure. See [docs/DEVOPS.md](docs/DEVOPS.md).

**This is not theoretical.** The entrypoint once had a private constructor and
a comment asserting Fabric builds it reflectively — which is precisely why
private was wrong. It compiled, packaged, produced a valid jar, and died at
load with `IllegalAccessException`. No build and no unit test would have caught
it. The first gametest run caught it immediately.

New in-world behaviour gets a `@GameTest`. Pure logic gets a JUnit test. Both
run under `verify.ps1`.

---

## IV. Back up before you touch a world

```powershell
.\tools\backup-world.ps1 -World "<name>" -Label before-something
```

Backups go to `D:\Serenity\world-backups`, outside the repo, where neither
gradle nor git can reach them.

`session.lock` is excluded deliberately — Minecraft holds it open, and a stale
lock restored into a world makes Minecraft refuse to open it. Quit to the title
screen before backing up: chunks sit in memory until an autosave, and a
mid-session snapshot can silently miss the last few minutes of building.

---

## V. Verify against the jar, not against memory

Two facts here contradict what is widely recalled. Both were checked by opening
the 1.21.1 jar, and both would have been silent bugs:

- Datapack directories are **singular** in 1.21.1: `loot_table`, `recipe`,
  `tags/block`. Not the plurals.
- Fabric instantiates the entrypoint reflectively, so its constructor must be
  **public and no-arg**.

When a version-dependent detail matters, open the artifact and look.

---

## VI. Windows specifics that have already bitten

**PowerShell scripts must be ASCII.** Windows PowerShell 5.1 decodes UTF-8
without a BOM as CP1252, where an em dash ends in `0x94` — a curly quote, which
PowerShell accepts as a **string delimiter**. One em dash inside a quoted string
silently terminates it and the parser reports a missing brace fifty lines away.

**Bash is broken on this machine** (Malwarebytes blocks the MSYS2 fork). Use
PowerShell. Heredocs are not PowerShell — write the file, then `-F` it.

**Loom is pinned at 1.11.x.** Loom 1.17+ assumes the new official-namespace
runtime and cannot process 1.21.1-era mods. Sources are mojmap, not yarn.

---

## VII. Still open

Answer these before building on them; do not decide them silently.

1. **`|SCOPE|` — selector or cardinality?** Written as an alternation it reads
   as "pick one"; divided by, it means "how many". Different aberration curves.
   `Scope` exposes both and commits to neither.
2. **Is hull stress genuinely second order**, or does the mechanic not need it?
3. **Does the Mobius flip apply to the era index only**, or also to the
   position-keyed store? A flip that changes addressing is a real exception to
   the spine.

See [docs/NUMERIC_MODEL.md](docs/NUMERIC_MODEL.md).
