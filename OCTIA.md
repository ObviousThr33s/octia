# OCTIA — the law of the ship, and what it will not register

Set down 2026-08-06, on the day the mod first reached a remote of its own. The README
opens *"A Serenity-class ship, called to the dig"* — this file is what that costs in
practice, stated once so a later hand does not have to guess.

**Written 2026-08-06. Updated 2026-08-10.**

> **STATUS: CLOSED 2026-08-10.** The README went on saying *"Scaffold only. `onInitialize`
> logs a line and returns."* long after the front door and the whole `crew/` package
> landed — 5,765 lines. It has been rewritten: its status section and its layout tree
> now say what is actually registered. Kept here rather than deleted, because the
> failure mode is the point — a status line written once outranks nothing.

## The rooms

| what | where |
|---|---|
| the mod | `src/main/java/com/serenity/octia/` — Fabric, Minecraft 1.21.1 |
| the crew | `src/main/java/com/serenity/octia/crew/` — seats, orders, the gangway |
| the front door | `tools/frontdoor/` and `tools/frontdoor.ps1` — a desktop window onto the repo |
| the gates | `tools/verify.ps1` locally, `.github/workflows/verify.yml` in CI — the same two gates |
| the passdown | `AGENTS.md` |

## Standing orders

1. **The local gate and the CI gate are one gate.** `tools/verify.ps1` runs what
   `verify.yml` runs: build, then in-world tests on a headless dedicated server. If they
   ever diverge, the local one is wrong, because CI is the one that cannot be skipped.
2. **No display, no Mojang account, no secrets.** The gates run without any of the three
   and must keep doing so. A test that needs a human logged in is not a test.
3. **Gradle is the parts list.** There is no MANIFEST here and there should not be — a
   second parts list is a second source of truth, and the build already owns that job.
4. **The package is `com.serenity.octia`.** Not `octioid`. The folder on disk and the
   remote have both caught up since — `D:\Serenity\octia`, `ObviousThr33s/octia`. Where
   the older spelling still appears it is sample text or history — the codex's worked
   examples, the line `ShipCoreBlock` prints on survey, the rename script's own account
   of the rename, one dev save's datapack name. Grep before assuming one is a live name.
5. **Sidecars are the safety net beside a file, never the record.** `*.backup-*` and
   `*.bak` are ignored. Two of them were sitting untracked here on 2026-08-06 and were
   deliberately left out of history.

## The name

This repo is **`octia`**, not `Octioid`. `ObviousThr33s/Octioid` is a *different
project* — Hexehedron, public, with its own branches and an outside watcher — and the
two share no history whatsoever. They were nearly confused on 2026-08-06, and a
force-push over the wrong one would have destroyed five branches of somebody else's
work. The names are close; the repos are not related. Do not merge them.

## What this is not

Not a content mod yet — the toolchain, the mappings, and the loader handshake are proven,
and the crew has landed, but the world is still mostly unregistered. Not Hexehedron, as
above. And not the modpack: `D:\Serenity\OctiaModpack` is a third thing wearing the
fourth spelling of this name.

`[2026-08-06]`
