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
in the second half is `Sightlines`, `ObeliskFeature` and `ArchFeature`.

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

Or look at it. This draws the lattice over the save's own structure starts — one
self-contained HTML file, no network, pan and zoom, and hovering anywhere
reports which cell you are in, which way its leg runs, how far off the line you
are, and therefore whether the feature would stand an obelisk there or break
one:

```bash
python tools/sightline-map.py worlds -o sightlines.html
```

> **The map's arithmetic is a copy, and copies drift.** `Sightlines.java` is the
> authority and it runs in the JVM, so the Python cannot share it — only match
> it. `--probe` prints the same handful of nodes the Java prints, for diffing:
>
> ```bash
> javac -d /tmp/sl src/main/java/com/serenity/octia/world/Sightlines.java \
>                  tools/sightline-map/Probe.java
> diff <(python tools/sightline-map.py --probe) <(java -cp /tmp/sl Probe)
> ```
>
> No Gradle and no Minecraft in that: `Sightlines` has neither on its imports,
> which is the practical dividend of keeping it pure.
>
> This is not hypothetical. The generator carried `JITTER = 144` after the Java
> moved to 96, and drew every node in the wrong place while looking entirely
> plausible. The probe is what caught it.

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
`atan(2J / (SPACING - 2J))` — 30.96 degrees at 96 against 512, and the sweep in
section IV measured 30.007 over two million cells, so the bound is nearly tight
rather than generous. Under 45, so the cardinal a leg lies nearest is never in
doubt. A diagonal step would sit *at* 45 degrees, exactly between two cardinals,
and the prism's long axis would be a coin toss. **`JITTER` must stay under a
quarter of `SPACING`** or that guarantee goes, and `SightlinesTest` measures the
worst bend across sixty seeds rather than trusting the arithmetic.

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

## IV. The lattice, measured

Everything above about the lattice was arithmetic and spot checks. `Sweep` runs
the same questions over 200 seeds — 2,040,200 cells, 16,321,600 sampled points:

```bash
javac -d /tmp/sl src/main/java/com/serenity/octia/world/Sightlines.java \
                 tools/sightline-map/Sweep.java
java -cp /tmp/sl Sweep 200 50 8
```

| | measured |
|---|---|
| nodes outside their own cell | **0** — the containment invariant holds |
| worst bend off the cardinal | **30.007°** (bound is 30.96°, one leg past 30 in two million) |
| heading balance | 24.93% – 25.03%, all four |
| leg length | min 320, mean 518, max 725 |
| two-cycles (A→B→A) | 25.0% → **0.392%** |
| ground inside the corridor | 12.66% → **50.47%** |

**Two of these corrected the design, both had been asserted rather than checked,
and both have since been acted on.** The right-hand column is where they stand
now; the readings below are what they were when first measured, and why each was
changed.

**The corridor was a thin ribbon. It is half the world now.** A leg is a line
through a cell 512 blocks across, so 32 blocks either side of it was about an
eighth of the ground: roughly **45% of all obelisks broken**, and a standing
obelisk only about **20% likely to be on a thread** against a 12.66% base rate.
A real signal and a weak one.

On **2026-08-21** KEG widened `CORRIDOR` to **128** — the first of the three
doors [ROADMAP](ROADMAP.md) XII offered. Re-measured at the same constants:

| | before | after |
|---|---|---|
| ground inside the corridor | 12.66% | **50.47%** |
| obelisks broken | 45.25% | **31.07%** |
| a standing obelisk is on a thread | 20.24% | **64.07%** |
| lift over chance | 1.60× | **1.27×** |

**Both halves of that are true at once.** A player meets the route now — two
thirds of standing obelisks are genuinely on a thread rather than one fifth, and
many more survive to be seen at all. But each one *says* less: a corridor
covering half the world is a weaker claim about any block inside it. ROADMAP XII
predicted this as "stops the corridor being a corridor"; 1.60× falling to 1.27×
is that sentence with a number attached. The plural still holds and always did —
several standing obelisks along one bearing means more than any single one.

**A quarter of all legs used to double back.** Each node picked its step
independently from four cardinals, so the chance the neighbour stepped straight
back was exactly 1 in 4 — and it was, to three decimal places. A thread ran about
four legs, two kilometres, before returning on itself. "A chain across the world"
was the wrong picture; it was closer to a loose weave of short routes and facing
pairs. That was never a decision, it was a consequence nobody had looked at.

Closed in `76a967f` by a parity rule: a cardinal step always flips the parity of
`cellX + cellZ`, so the lattice is a checkerboard, black cells keep their draw
and white cells adjust against neighbours that are already final. One pass, no
recursion. Two-cycles fell to **0.392%**, and that residue is exactly the 0.3866%
of cells whose four neighbours all answer back and where no legal step exists.
Threads now run **11.15 legs, 5,709 blocks** — 2.4× further.

---

## V. How the thread becomes visible

Distance to the leg decides **how likely an obelisk is to have stayed up**, and
nothing else: 1 in 8 broken within **128** blocks of the line, 4 in 8 beyond it.
The lit spires trace the route and the stumps lie off it, so the route is
something you notice rather than something you are told.

**It is deliberately not a filter on placement.** An obelisk that could only
generate inside a corridor would refuse about half of all sites at the current
width — and refused seven in eight at the old one — so `/place feature
octia:obelisk` would fail often, and that command is how both ruins are judged,
per [WORLDS.md](WORLDS.md). It would also silently re-tune the rarity in
`placed_feature/obelisk.json` by a factor nobody wrote down. The corridor
changes the odds of a break. It does not change what exists.

That the refusal rate would have moved from seven-in-eight to one-in-two when
`CORRIDOR` widened is the argument for never having built it as a filter: a
placement rule would have quietly re-tuned obelisk density estate-wide the
moment the constant moved. The break-odds design absorbs the same change without
touching how many obelisks the world contains.

---

## VI. The arch on the node

The threads shipped pointing at waypoints with nothing on them. That is closed:
an arch now stands on every node, squared across the leg leaving it, so you walk
**through** it and the way you face on the way through is the way the thread
runs.

**Keystone plus four.** The ring is five stones, stepping up from the springers
to the middle:

```
            [K]          keystone, STYLED, light 15
        [v]     [v]      voussoirs, GENERIC, light 7
    [s]             [s]  springers, dark, on top of the piers
    [p]             [p]
    [p]     ^       [p]  the opening: three wide, facing down the leg
    [p]             [p]
```

Five is the smallest span that reads as an arch rather than a doorway, and it is
odd — which is what lets a single keystone sit on the centre line. An even span
could not carry one at all: there would be a joint where the middle stone
belongs. The opening left under it is three wide and clear to the ring, and the
whole thing is one block thick, so walking the leg means passing one plane of
stone.

The light does the work at range. A bright point with two dimmer ones under it
is a *shape* across a valley; the springers stay dark on purpose, because a ring
lit all the way round smears into one glow and the step stops reading.

**Nothing here ever breaks.** Obelisks erode and snap so that what is still
upright carries information. A node is the thing that is maintained. That
asymmetry is the design: the road between nodes is not kept, the places are.
`ArchGameTest.anArchIsNeverBroken` raises twenty with twenty different randoms
and is the test that should stop erosion ever being added here.

**How it finds its site.** No placement vocabulary can say "the node of this
cell" — modifiers see a chunk and a random, never a function of the world seed.
So the placed feature has *no rarity filter at all*: it is offered once per
chunk, and `ArchFeature.place` declines every chunk whose node is elsewhere. One
arithmetic test per chunk, one acceptance per 1,024 of them. A rarity filter in
front of that would be strictly wrong — it would throw away the one chunk that
matters and leave nodes bare at random.

To find one without wandering:

```bash
python tools/sightline-map.py --node 0 0 --seed 95512464
```

which prints the node, the leg leaving it, and the `/tp` for it.

---

## VII. Open

1. **A node's ground may refuse it.** The arch closed the empty-waypoint
   problem, but not unconditionally: it rejects rather than terraforms, like
   every ruin here, so a node whose column is a cliff face, a lake, or a cave
   roof gets nothing. The thread still points there and there is still nothing
   to find. Unknown how often — it wants the same walked-world measurement the
   density question does, and it is the one case where levelling a five-block
   strip might be worth breaking the no-terraforming rule for.
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
