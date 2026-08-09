```
ACT TWO
MILESTONE 1
OCTIA_[0.1.0.W.R.L.D]_register
SEEK KEG |ALL|
```

# The worlds

Three dev saves, and they are not three copies of the same experiment. Each one
runs a different Overworld noise preset, which is the fact that governs
everything else in this file: what the terrain does, where caves open, and how
far apart structures land.

Everything under **Read from the save** is machine-extracted and can be
regenerated at any time. It is never edited by hand.

```bash
python tools/world-report.py
```

The tool reads `level.dat`, both Octia `SavedData` files, and every region file,
all read-only, without launching the game. `--json` for the raw form.

Everything under **Seen in world** is the opposite: a judgement, made by whoever
was standing there. The scanner can find a mineshaft because the save records
one; it cannot tell you a cave mouth is worth walking into. Those lines get
added by hand, and an empty section means nobody has looked yet, not that there
is nothing there.

---

## One caveat that applies to all three

Every world reads **`beacon: not recorded`**.

`OctiaBeacon.recordBeaconAt` arrived in `644b83e`, and all three of these saves
raised their beacon before that. `claimBeacon()` returns true exactly once per
save, so none of them will ever record it now — the position is simply lost, and
the debug map will keep drawing their beacon column as an ordinary teal mooring
rather than a gold beacon for the life of the save.

This is not a bug and needs no fix. It does mean **the gold beacon mark has
never actually been rendered**, in any world. The first world created from here
on will be the first to exercise that path.

---

## SERENITY_[0.0.0.S.A.F.E].project — the amplified one

**Read from the save**

| | |
|---|---|
| seed | `95512464` |
| terrain | `minecraft:amplified` |
| spawn | `0 112 0` |
| age | day 4 |
| explored | 4865 chunks — x `-848..415`, z `-624..415` |
| moorings | 2 — `-49 68 -66` and `0 124 0` |

The oldest and by far the most explored, and the only **amplified** save. That
is the one to mine for cave entrances: amplified pushes terrain to extreme
heights, so caves open in cliff faces at eye level instead of hiding under
grass. Its spawn beacon sits at y=124 rather than the y=42-54 of the others,
which is the same fact from the other end.

`-49 68 -66` is a **second, hand-built ship** — the only player-moored hull in
any of the three. About 82 blocks northwest of spawn.

**Structures**

| structure | at |
|---|---|
| trial_chambers | (24,56) ← **61 blocks from spawn**, (-280,24), (-248,-488), (216,-328) |
| mineshaft | 24 of them, thickest around (-664,-424) → (-264,408) |
| ocean_ruin_cold | (-792,-600), (-792,24), (-632,120), (-520,-456), (-456,-152) |
| ruined_portal | (-312,-424), (-280,136), (40,-440), (376,120) |
| village_plains | (-264,392), (136,-280) |
| village_taiga | (-312,-392) |
| monument | (-776,-232) |
| shipwreck | (-744,-200), beached at (-568,264) |
| trail_ruins | (-280,-408) |

**Seen in world**

- 16 KEG signs, a walkthrough beginning at **ACT ONE (-112, 67, -149)**. Carried
  from earlier notes and not re-verified against the save — `NotationTest` pins
  the text of the first one, not its position.
- _cave entrances: unrecorded_
- _player edits beyond the second ship: unrecorded_

---

## SERENITY_[0.0.1.S.A.F.E].project — the large-biomes one

**Read from the save**

| | |
|---|---|
| seed | `6940978335206254584` |
| terrain | `minecraft:large_biomes` |
| spawn | `0 97 0` |
| age | day 0 |
| explored | 2905 chunks — x `-432..431`, z `-496..367` |
| moorings | 1 — `0 96 0` (the spawn beacon) |

Day 0: generated, walked through, never lived in. **Large biomes** stretches
every biome roughly 4x, so this is the save where a structure has room to sit in
the middle of one biome instead of on a seam — the useful control case when
testing placement rules.

**Structures**

| structure | at |
|---|---|
| mineshaft | 9, nearest (-136,-56) ← **147 blocks from spawn** |
| trial_chambers | (-392,152), (-280,-376), (88,280), (200,-360) |
| village_savanna | (-376,8), (-328,-360) |
| village_plains | (232,72) |
| ruined_portal | (312,360) |

**Seen in world**

- _nothing recorded — this world has never been properly walked_

---

## SERENITY_[0.2.1.S.A.F.E].project — the default one, seed 1

**Read from the save**

| | |
|---|---|
| seed | `1` |
| terrain | `minecraft:overworld` (default) |
| spawn | `112 67 176` |
| age | day 1 |
| explored | 2778 chunks — x `-272..607`, z `-256..559` |
| moorings | 1 — `0 54 0` (the spawn beacon) |

Today's world, and the one the debug map was first read in. **Seed 1** and stock
terrain make it the reproducible one: anybody with this build can generate the
identical world and stand in the same place, which is what you want underneath a
bug report.

Note the spawn is offset from the beacon — you appear at `112 67 176`, roughly
**204 blocks NW** of the mast at `0 54 0`. That gap is why the map needs the
range control at all, and it is the case the bearing readout was added for.

**Structures**

| structure | at |
|---|---|
| ocean_ruin_cold | (56,360) ← **~192 blocks from spawn**, (-200,24), (-184,360), (152,-248) |
| shipwreck | (136,392), beached at (456,168) |
| buried_treasure | (584,184), (600,168), (600,216) — three, clustered |
| mineshaft | 15 |
| trial_chambers | (296,232), (600,104) |
| ruined_portal | (312,296) |

**Seen in world**

- Fully submerged around the explored edge — nothing of interest found yet.
- _cave entrances: unrecorded_

---

## What Octia now generates

**The derelict** — a Serenity-class hull that was called and never arrived,
half-buried, with the dig that called it still in the ground beside it. A real
hull by `ShipCoreBlock`'s own rule, with brushable ground inside the call
radius, so it surveys as `CALLED`.

**One is guaranteed near spawn.** On a save's first load, `placeNearSpawn` seats
a derelict 48–112 blocks out, ringing outward until ground takes it, seeded off
the world seed so the same seed always answers the same. A rarity roll cannot
promise a player will ever meet one; the first is placed rather than rolled, the
way a ruined portal is reachable from where you wake up. It prints where it went:

```
Octia: derelict seated at BlockPos{...} (48 blocks from spawn).
```

Because that one is placed into a live level it moors on placement, so it is on
the F6 map from the moment the world opens. The wild ones are not — see below.

Rarity for the rest is `900` in
`data/octia/worldgen/placed_feature/derelict.json` — roughly one per 900 chunks,
between the density of ocean ruins and trial chambers in these three saves. Far
too rare to go looking for on foot while testing. Place one instead:

```bash
/place feature octia:derelict
```

Two things behave differently between a placed derelict and a generated one, and
neither is a defect:

- **`/place` moors it immediately.** A live `ServerLevel` runs `onPlace`, so the
  core surveys itself as it lands. Natural generation runs on a `WorldGenRegion`,
  which does not — so a wreck nobody has touched is absent from the moorings
  store, and therefore absent from the F6 map. Right-click the core and it
  appears. Discovery is registration.
- **Existing chunks will never contain one.** Terrain already generated is
  finished. Walk past the explored edge in the table above, or make a new world.

A new world is the better test anyway: it is the only way to see the **gold
beacon mark**, which no save has ever rendered.

---

## Reading this before generating a structure

Four things in here bear directly on placement work.

**The presets are the test matrix.** Amplified, large biomes, and default is a
genuine spread, and a placement rule that behaves in all three is a placement
rule. Test in one and you have tested in one.

**`[0.2.1]` is the one to iterate in.** Seed 1, stock terrain, one day old,
nothing built. Reproducible for anyone, and nothing of yours to lose.

**`[0.0.0]` is the one not to experiment in.** It holds the sign walkthrough and
the only hand-built ship. Back it up before anything writes to it:

```bash
.\tools\backup-world.ps1 -World "SERENITY_[0_0_0_S_A_F_E]_project" -Label before-structure-gen
```

**Vanilla density is the baseline to match.** Across these three, structures land
somewhere between 60 and 400 blocks apart. Trial chambers 61 blocks from spawn in
`[0.0.0]` is the tight end; a lone ruined portal in `[0.0.1]` is the loose end.
Anything Octia generates should sit inside that band or it will read as either
litter or absence.
