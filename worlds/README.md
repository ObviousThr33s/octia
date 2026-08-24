# worlds/

Minecraft save snapshots that belong to this mod, kept in the repo so a world and the
code that shapes it move together. Written by [`tools/backup-world.ps1`](../tools/backup-world.ps1),
which snapshots a dev world before anything touches it.

Naming is `<world>__<yyyy-MM-dd>_<HHmm>__<what-just-happened>`, so the folder name says
why the snapshot exists without opening it.

| snapshot | taken | what it holds |
|---|---|---|
| `SERENITY_[0_0_0_S_A_F_E]_project__2026-07-30_1646__baseline` | 2026-07-30 16:46 | the world as found, before the build session |
| `SERENITY_[0_0_0_S_A_F_E]_project__2026-07-30_1719__after-signs-quit-to-title` | 2026-07-30 17:19 | after the spawn hub and its signage, quit to title |

## What SERENITY is

Minecraft Java 1.21.1 on Fabric, seed `95512464`, overworld preset `amplified`, spawn
pinned to `0/112/0`, cheats on, `keepInventory` true. The bracketed folder name is
literal: `[0.0.0.S.A.F.E]` is the 0/0/0 spawn plus SAFE, and `level.dat` backs that up.

`level.dat` enables a datapack named `octioid` — this mod, under its old name. Anything
thawing these saves against a current build should expect that id to have become `octia`
(renamed 2026-07-30); see [docs/NAMING.md](../docs/NAMING.md).

The 16:46→17:19 pair covers a single ~68-minute creative session: roughly 1,300 blocks
placed, weighted to decoration and light, and 25 hand-written signs staging an entrance.
Two lecterns were placed and both are still empty.

## Why these are tracked, and the cost

These are binary region files. Git stores each snapshot whole and cannot delta them, so
every future snapshot adds its full size to history permanently — 38 MB for these two,
against 0.2 MB for all the source in the repo combined. That is the deliberate trade:
the worlds are evidence the GameTests cannot replace, and a save that drifts away from
the code that made it is worth less than the disk it costs.

If this grows past a few hundred MB, the answer is Git LFS or an external store — not
quietly deleting history.

---

## `[2026-08-23]` The threshold above has been crossed, and the table above is stale

Both stated flatly rather than fixed by editing, per the house rule on corrections.

**The table names two snapshots. Eighteen are tracked** — 616 files under `worlds/`.
The 38 MB figure is the two 2026-07-30 snapshots only.

Measured this date:

| | |
|---|---|
| `worlds/` on disk | **548.1 MB** in 825 files |
| of which saves | 467.7 MB |
| of which PNG | 80.3 MB in 109 files |
| **`.git`** | **302.6 MB** |

**"A few hundred MB" has arrived.** The paragraph above names Git LFS or an external
store as the answer at this point, and neither has been set up. Nothing here proposes
which; it is recorded so the next snapshot is a decision rather than a reflex. The rule
that it is *not* solved by deleting history stands.

### Screenshots, and which sets are tracked

The 2026-08-10 (2) and 2026-08-17 (32) sets were already tracked. Added this date and
**untracked as of writing**:

| set | files | size | what it holds |
|---|---|---|---|
| `2026-08-22_14.*` | 15 | 2.8 MB | shape A's first light — the sky world, the day it was chosen |
| `2026-08-23_22.*` | 41 | 14.0 MB | the 22:22–22:27 playtest of the shipped terrain |

The 08-23 set is the evidence behind `docs/ISLANDS.md` §X. Four of its frames carry the
F6 debug readout, and all four say `obelisks: 1 (within 1024b of you)` — which is what
put the lattice's survival on `octia:sky` onto that file's not-measured list.
`2026-08-23_22.27.52.png` puts the player at `130 -11 341`, below the noise band's
floor, in the dark: the void under the world, photographed.

### `slices/`

[`slices/`](slices/) holds vertical cross-sections read straight out of region files by
`tools/chunk-probe.py`, with no game running. Seven of them, one per rung of that
evening's terrain search, all on seed 1 at the same framing.

**81 KB for the set.** The seven saves they were read from are ~90 MB, and given the
paragraph above they were deliberately *not* snapshotted. A slice is not a substitute
for a save — you cannot walk it — but for the one question those rungs were asked, it is
the whole answer, and it is three orders of magnitude cheaper.

They are also the **only surviving record of rungs 1 to 5**: each regenerated
`sky.json` and only the last version was ever committed.
