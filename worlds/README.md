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
