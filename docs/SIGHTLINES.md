```
ACT TWO
MILESTONE 3
OCTIA_[0.1.0.S.I.G.H.T]_survey
SEEK KEG |ALL|
```

# Sightlines

Two questions, asked in that order: **do the landmarks in a Minecraft world
point from one location to another?** and, since the answer turned out to be no,
**what does it take to make Octia's own point?**

Everything in the first half is read out of the saves in `worlds/`. Everything
in the second half is `Sightlines` and `ObeliskFeature`.

---

## I. What the saves actually say

Both saves on disk are snapshots of `SERENITY_[0.0.0.S.A.F.E].project`, seed
`95512464`, amplified — the baseline and the after-signs one. The register in
[WORLDS.md](WORLDS.md) describes three worlds; only this one is committed, so
the survey below is one seed and one terrain preset. Read it as strong evidence
about vanilla's *mechanism*, not as a sample.

Every structure start in the richer of the two, by type, with the chunk it began
in:

| type | starts | chunks |
|---|---|---|
| mineshaft | 21 | dense, no grid recoverable at this count |
| trial_chambers | 4 | (-18,1) (-16,-31) (1,3) (13,-21) |
| ruined_portal | 4 | (-20,-27) (-18,8) (2,-28) (23,7) |
| ocean_ruin_cold | 2 | (-33,-29) (-29,-10) |
| village_plains | 1 | (8,-18) |
| village_taiga | 1 | (-20,-25) |
| trail_ruins | 1 | (-18,-26) |

**Every one of them falls on a jittered grid, one start per cell.** Take trial
chambers at a cell of 34 chunks: the four land in four *different* cells —
(-1,0), (-1,-1), (0,0), (0,-1) — at offsets 16, 18, 1 and 13 inside them, all
inside the window a 34/12 spacing-and-separation rule allows. Ruined portals at
a cell of 40: four different cells, offsets 20, 22, 2 and 23, all inside the
window a 40/15 rule allows. Ocean ruins at 20: two cells, offsets 7 and 11.

That is vanilla's `RandomSpreadStructurePlacement`, and it is doing exactly what
it says on the tin. The grid is real and it is measurable from the save.

> **One thing here was not verified against the jar**, against rule V. The cell
> sizes and separations quoted — 34/12, 40/15, 20/8 — are recalled, not read out
> of the 1.21.1 artifact. What *is* verified is that the save's starts fit them
> exactly: distinct cells, every offset inside the window. Treat the numbers as a
> hypothesis the data has not falsified, and open the jar before anything depends
> on them. Nothing in `Sightlines` does — it lays its own grid.

**And it is a spacing rule, not a route.** This is the finding that mattered:

- No two structures of the same type are aligned with each other beyond what
  the grid forces.
- No structure of one type has any relationship to any structure of another —
  the grids are independently salted and do not share cells.
- Nothing anywhere records a bearing, an order, or a next.

So a landmark in a vanilla world knows how far it must be from its own kind and
nothing else at all. **Threads between locations are not a property of the
terrain waiting to be read out. They have to be laid down.**

*(One oddity, recorded and not explained: 16 of the 21 mineshaft chunks in this
save pair up as exact negations — (-21,7) with (21,-7), (-15,15) with (15,-15),
and so on through eight pairs. The explored region is not symmetric, so the
exploration bounds are not causing it. Left here as an observation. Do not build
on it.)*

Reproduce the read with:

```bash
python tools/world-report.py worlds
```

---

## II. The lattice

`com.serenity.octia.world.Sightlines`. Pure Java, no Minecraft on its imports,
tested by JUnit rather than in-world — features run on several chunk-generation
worker threads at once, so anything they consult has to be a function of (seed,
position) rather than a lookup.

It borrows vanilla's shape and adds the one thing vanilla never does.

| | |
|---|---|
| **cell** | 512 blocks square |
| **node** | one per cell, wandering up to 96 blocks from the middle |
| **step** | one of the four cardinals, chosen by hash — *this is the new part* |
| **leg** | the segment from a node to the node its step reaches |

A node with a next node is a chain rather than a scattering, and the leg between
two of them is a line across the world **with a direction along it**. That is
the whole invention.

**Why the steps are cardinal and never diagonal.** Two nodes a step apart wander
independently, so a leg's cross-axis can reach `2 x JITTER` while its along-axis
shrinks to `SPACING - 2 x JITTER`. The worst bend is therefore
`atan(2J / (SPACING - 2J))` — 31 degrees at 96 against 512. Under 45, so the
cardinal a leg lies nearest is never in doubt. A diagonal step would sit *at* 45
degrees, exactly between two cardinals, and the prism's long axis would be a
coin toss. **`JITTER` must stay under a quarter of `SPACING`** or that guarantee
goes, and `SightlinesTest` measures the worst bend over four thousand cells
rather than trusting the arithmetic.

---

## III. The prism

`ObeliskFeature` was a 1x1 column with a lit block on top. It is now a solid
3x5 prism, 9 to 13 blocks tall, of andesite frame panels on a plinth one block
proud of it, laid **long axis along the leg under its feet**.

The column had to go for a reason that has nothing to do with taste: an obelisk
is meant to be the thing you see across a valley and decide to walk toward, and
one block square is one pixel wide at that distance. It read as a marker only
because nothing else in the world was that shape.

Three marks, each doing a different job:

- **The crown.** Top course, all of it, `STYLED` at light 15 — the brightest
  thing the mod owns. Says *something is standing here*, to anyone who can see
  the top of it.
- **The slot.** A one-wide, two-tall gap bored the whole length of the prism at
  eye level. Stand at one end, look through, and you are looking down the leg.
  This is the sightline, and it is the only mark that survives a break.
- **The stripe.** A band of `GENERIC` up the end face the leg leaves by, framing
  the slot's mouth. Says *and it runs that way*, to somebody who has already
  walked up to it. Standing obelisks only: `GENERIC` is light 7, and a lit mark
  on a ruin that is supposed to have lost its light would be a lie.

**A broken one loses the crown and the stripe, keeps the slot.** It still says
which way the thread ran; it just no longer says it in light. That is why
erosion is confined to the top course and a break can never cut below
`SLOT_HIGH + 2`.

---

## IV. How the thread becomes visible

Distance to the leg decides **how likely an obelisk is to have stayed up**, and
nothing else: 1 in 8 broken within 32 blocks of the line, 4 in 8 beyond it. The
lit spires trace the route and the stumps lie off it, so the route is something
you notice rather than something you are told.

**It is deliberately not a filter on placement.** An obelisk that could only
generate inside a corridor would make `/place feature octia:obelisk` fail nine
times in ten — the command is how both ruins are judged, per
[WORLDS.md](WORLDS.md) — and it would silently re-tune the rarity in
`placed_feature/obelisk.json` by a factor nobody wrote down. The corridor
changes the odds of a break. It does not change what exists.

---

## V. Open

1. **A node carries nothing.** The leg points at a waypoint that is guaranteed
   to have no obelisk on it, because placement is still a per-chunk rarity roll
   that knows nothing about the lattice. Following a thread converges on a place
   where nothing is. Either the near-spawn path should seat one at the first
   node the way it seats the guaranteed derelict, or a node should stop being a
   place and be only a direction — and the second answer needs saying out loud
   rather than being what happens by default.
2. **Density moved and was not re-tuned.** The footing check now demands a
   flat 7x5 with headroom for the whole prism, where it used to want a 3x3 with
   eight above. Strictly fewer sites pass, so obelisks are rarer than
   `chance: 180` implies and the number is now a bound rather than a rate.
   Judge it from a walked world before touching it — see ROADMAP V on why
   density read while flying is not density.
3. **The threads are invisible on the F6 map.** The overlay draws moorings and
   the beacon. Legs are a pure function of the seed, so the client could draw
   them with no new packet at all — it already knows the seed is the one thing
   it does *not* know. Which is the actual obstacle, and worth writing down
   before somebody assumes it is easy.
