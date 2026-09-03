# ISLANDS — floating land, and the debt it owes Skyblock

**Set down 2026-08-17, on branch `lives-and-islands`.** A plan, not an
implementation. Nothing in here is built.

---

## I. The bar, named by the gardener

> let me meet the creators of ex nihilo and skyblock then we can talk

Taken as the standard to clear, not as dismissal. So state plainly what those two
did, because it is the thing that makes floating islands worth building and the
thing most floating-island content skips.

**Skyblock (2011)** was not a map with islands on it. It was a map that removed
everything else. The island is small, the void is total, and the design content is
entirely in what you *cannot* reach. Take the same island and put it 200 blocks
above ordinary terrain and the map is worth nothing - not because the island got
worse, but because the void stopped being a constraint.

**Ex Nihilo** answered the question Skyblock created: if you have nothing, how do
you bootstrap? Hammer cobble down through gravel and sand, sieve it for seeds and
ore, melt leaves for water. It made *nothing* into a resource tier. It is
remembered because it is a progression system, not a decoration set.

**The lesson, in one line:** floating islands are a **scarcity contract**. If a
player can walk off the island and find normal terrain, there is no contract, and
what has been built is scenery.

That is the bar. Octia should either take the contract seriously or place islands
for a different stated reason - but not place them for the Skyblock reason and then
quietly leave the ground in.

> **Read §XI with this.** On 2026-08-28 the sky world was given a floor and the sea
> was kept rather than drained, so what is under the islands today is an ocean and
> not a void. The contract above still holds — an ocean is not normal terrain and
> there is no ground under it — but the bar is now met a different way than this
> section imagines, and §XI names what that costs.

---

## II. What Octia already brings to it

Octia is unusually well-placed here, because the fiction is already about scarcity
and arrival rather than about terrain:

- **You are a ship.** `ShipCoreBlock` plus a ring of eight frame panels is already a
  bootstrap object - a thing you assemble in order to become moored somewhere. That
  is a far more diegetic Ex Nihilo than a sieve would be. Octia's answer to "how do
  you start from nothing" should be *the hull*, not a hammer.
- **Derelicts are already the reward for reaching somewhere.** A wreck on an island
  is a stronger find than a wreck in a field, because getting to it cost something.
- **Obelisks are already meant to be seen from far off** (`ROADMAP.md` §V). An
  island is the best plinth one could have: it reads at distance in a way a
  half-buried cube never will.

**Correction, same day.** The bullet above originally said an island "answers the
sightline complaint." It does not, because **sightlines were answered while this file
was being written** — see §VIII. An island would give an obelisk a better plinth; it
would not give the world its bearings, because the world now has them.

---

## III. Three shapes, and what each costs

| shape | the contract | honest verdict |
|---|---|---|
| **A. Void world** - a dimension or world type with no ground at all | full Skyblock. nothing exists but what you are given and what you build | clears the bar. largest build by far: new worldgen, a bootstrap loop, and every existing feature must be re-asked "does this work with no ground" |
| **B. Islands above normal terrain** | none. the ground is still there | scenery. clears no bar. cheap, and worth doing only if the stated goal is landmarks rather than scarcity |
| **C. Islands as destinations** - rare, high, ruin-bearing, hard to reach | partial. scarcity of *access*, not of matter | the honest middle. fits the current loop, and pairs with `LIVES.md` |

**C is the recommendation for tomorrow**, with A named as the real ambition. C can
be built against the existing worldgen and immediately gives `LIVES.md` its best
stage: an island is bounded, visible and memorable, which is exactly what "someone
built another kickass hill fort" needs in order to land. A hill fort in a field is
a build. A hill fort on an island you had to work to reach is a *story about
somebody*.

A is not ruled out. It is a second project that shares this one's vocabulary.

---

## IV. The technical thing that will bite first

`ROADMAP.md` §VI records that derelicts already refuse water: `RuinGround.hasFooting`
rejects positions it does not consider seated, which is why wrecks sit on a seabed
and not at the waterline. **Air is the same class of problem and has not been
asked.** Every placement path assumes ground beneath the anchor:

- `RuinGround.hasFooting` - the gate. What does it answer above a void?
- `OctiaBeacon.raise` - walks *down* through fluid to seat its column. On an island
  there may be nothing to walk down to.
- `DerelictFeature` - sinks the hull relative to the floor it found.
- `Habitation.dress` - scatters props 2-5 blocks from an anchor, and would happily
  scatter them off an edge into the void.

None of these is hard, but all four are load-bearing and all four have tests. The
first real task is not generating an island - it is answering *what footing means
when the thing below is nothing*, once, in `RuinGround`, so the other three inherit
the answer. Doing it per-feature is how the four drift apart.

---

## V. Where lives meet islands

From `LIVES.md` §V: past lives write themselves back into the world at runtime, and
worldgen still spawns nobody. An island is the ideal carrier for that:

- it is **bounded** - "this island is theirs" is a claim whose edges a player can
  see, unlike "this region is theirs"
- it is **visible from off it** - so the mark is found by being noticed rather than
  by being walked into
- it is **rare** - so attribution stays meaningful. Every island having a former
  owner is the same litter problem `ROADMAP.md` §V already solved once for wrecks

---

## VI. Logged 2026-08-17, unanswered

Recorded in the gardener's words. These are the design questions, and they outrank
the island work - all three are about the hull, which is the object everything else
hangs from.

> **what do i do when I get to the hull**

The sharpest question in the file. Today the answer is *nothing*: a hull validates,
the core lights, `ShipMoorings` records a position, and the loop stops there. Arrival
is currently a status change, not an event. Until this has an answer, more places to
arrive at is a bigger empty room, and that is a real argument for doing this before
islands.

> **the hull a multiblock structure, how should this be executed?**

It already is one - eight horizontal neighbours around a core, validated by
`ShipCoreBlock.hullIntact`, with no block entity and no controller. The open part is
whether that stays a *rule checked on demand* or becomes a *structure that owns
state*. Rule-checked is what makes the current design cheap and unbreakable; owning
state is what would let a hull do something on arrival, i.e. answer question one.
The two questions are one question.

> **what is the best way to keep this simple and self explanatory in terms of
> gameplay loops and visible gameplay mechanics. a do as game does kind of thing.**

The standard to hold everything above to. "Do as game does" - the ring of panels
teaches itself because you can see all eight of them and see the light change. Any
mechanic here that needs a wiki has failed. Note that `LIVES.md` §IV already chose
the book for this reason, and that keep-inventory was chosen partly because "your
things are safe" needs no explanation at all.

---

## VII. Open

1. **Dimension or overworld?** Shape A implies a dimension; C does not. Deciding
   this decides whether `fabric-dimensions-v1`, already on the classpath, gets used.
2. **Does `hullIntact` mean anything in a void?** Eight horizontal neighbours is a
   rule about a plane and should still hold - but a hull floating with nothing under
   it has never been tested, and finding out is a one-line gametest.
3. **What is scarce?** Unanswered, and it is the whole of shape A. Ex Nihilo's
   answer was matter. Octia's should probably be *anchorage*.

---

## VIII. What already exists — read this before building any of the above

**Added 2026-08-17, ~22:15, after finally fetching `origin/main`.** Everything above was
written against a local `main` that was eleven commits stale, so parts of it argue for
things that are already built. Recorded here rather than silently edited, because the
mistake is instructive: *the tree was never asked.*

**Sightlines are built.** `world/Sightlines.java` — a seeded lattice, one node per
512-block cell jittered up to 96, each node joined to a neighbour one cardinal step away,
with a 32-block corridor either side of the leg. Pure Java, no Minecraft imports, because
features run on chunk-generation workers and must consult a *function*, not a lookup. It is
covered by `SightlinesTest` (11 cases) and documented at length in `docs/SIGHTLINES.md`.

Its thesis is the answer to the density complaint this file leans on:

> nothing in a vanilla world points at anything else.

**Obelisks are no longer columns.** They are solid 3×5 prisms, 9–13 tall, laid along the
leg beneath them, with a slot bored the full length at eye height — the sightline you
actually look through.

**The arch is built.** `world/ArchFeature.java`: keystone plus four, squared across the leg
so you walk through it facing the way the thread runs, built entirely from existing frame
panels — no new blocks. It has **no rarity filter on purpose**; every chunk is offered and
`place()` rejects the ones whose cell node is elsewhere, because a rarity filter "would
throw away the one chunk that matters."

**And a real measurement worth respecting:** a 200-seed sweep over 2,040,200 cells corrected
two of that design's own claims — a standing obelisk is only ~20% likely to be on a thread
against a 12.66% base rate ("a real signal and a weak one"), and a quarter of all legs
double back. That is the standard of evidence this file has not met and should.

### Still unclaimed as of this writing

Per-player records, inhabitation, and floating islands appear nowhere on `origin/main`, and
there is still no UUID-keyed storage anywhere in the mod. `docs/LIVES.md` and the shapes
above remain open ground.

### The merge is not done, and here is exactly why

`main` and `origin/main` have genuinely diverged — 13 local commits against 11 remote off
`cc5f777`. A merge was attempted and **deliberately aborted**; safety refs
`backup/main-pre-rebase-20260817` and `backup/lai-pre-rebase-20260817` hold the pre-merge
state. Four of five conflicts resolved cleanly and are reproducible: `RuinGround` keeps both
new methods (`isDry` and `clearOfStructures`), `OctiaWorldgen` keeps both features with
`ARCH` scheduled through the existing `scheduleInOverworld` helper.

**One block needs a decision, not a resolution.** In `OctiaWorldgen.placeNearSpawn`:

- *ours* calls `derelict.place(...)` and then **searches downward** for the core it placed.
- *theirs* calls `DerelictFeature.seat(...)` directly, so the guaranteed wreck is exempt
  from the new structure check — deliberate, and argued in `seat`'s javadoc: between "the
  first wreck is guaranteed" and "no wreck shares ground with a village", the guarantee wins.

They cannot simply be combined. Theirs calls `DerelictFeature.sink()` with no arguments; the
merged file has only `private static int sink(RuinAge age)`, and the age is rolled *inside*
`place()`. Meanwhile `searchDepth()`'s javadoc states the core position **"cannot be
calculated any more"** — the wreck walks down through air and water, then sinks by its age.

The likely answer is a small `DerelictFeature` method that mirrors `place()` minus the
structure check and returns the core it seated. It was not written tonight because
`ROADMAP.md` VII records that **the near-spawn derelict has no gametest**, so a wrong guess
here breaks "the promise that a player meets a derelict at all" silently, and `verify.ps1`
would still report green.

`[2026-08-17]`

---

## IX. Built 2026-08-22 — shape A, and what it cost

**The gardener chose A.** *"make all terrain like this."* So the middle option this file
recommended was not taken, and the ambition was.

### What terrain is now

`data/octia/worldgen/world_preset/sky.json` — the Overworld generated from
**`minecraft:floating_islands`** with the **ordinary Overworld biome source** over it.
Islands of plains, savanna, snowy taiga; sea level at -64; the noise band y 0 to 256, and
open air under all of it. The Nether and the End are left exactly as vanilla wrote them.

It is tagged into `minecraft:normal`, so it stands on the world-type button as **Octia
Sky** and can be chosen by anyone, mod switch or no. `client/SkyChoice.java` is what makes
the mod's own switch select it: **Octia: On** moves the type from `minecraft:normal` to
`octia:sky`, **Off** moves it back, and it will not touch any other choice — pick
Amplified and the switch leaves it alone, because at that point the player has said
something more specific than the switch has.

**This answers §VII.1.** Dimension or overworld: **overworld**, through a world preset.
`fabric-dimensions-v1` stays on the classpath and unused. A preset is data, and the moment
terrain becomes Java it stops being something a pack author can borrow, override or turn
off.

### §IV was half wrong, and the correction matters

That section named four load-bearing paths that "assume ground beneath the anchor" and
called the footing question the first real task. **It was already answered.**
`RuinGround.hasFooting` rejects any position in the floor plane whose block `isAir()` —
so a derelict, an obelisk, an arch and a habitation all decline an island edge today,
without being asked to and without a line being written. Air was never the untested case;
it was the case the existing gate happened to already cover.

What was genuinely missing was not footing but **arrival**. Nothing in the mod could say
*there is no ground here.* `OctiaBeacon.groundAt` walked down and returned wherever it
stopped, which over a void is the bottom of the world — a mast at y=-63 under an empty
sky, and a player spawning into open air and falling out of it before the title fades.
That method is gone; `world/Landfall.java` replaced it with the one thing it could not do,
which is answer **null**.

`Landfall.secure` looks in the spawn column, then rings outward 16 to 96 blocks, and moves
the world spawn to the first real ground it finds. It asks no question about which
generator made the level, deliberately: *is there ground under the spawn* is a fact about
the world, not about how it was made, and on ordinary terrain the first read answers yes
and nothing below it ever runs.

### The gift, stated rather than hidden

When nothing answers within 96 blocks, `Landfall` **builds an island** — fifteen across,
grass on two of soil on stone, at y=96. `world/Isle.java` is its shape as arithmetic, with
no Minecraft imports, so `IsleTest` can assert the profile without a world and
`LandfallGameTest` can assert that the world got the shape `Isle` described.

This is in tension with §I and the tension should stay visible. Skyblock's island is
given too — the map *is* the gift, and the contract is what is missing around it. But an
island this mod prints is not terrain, and calling it terrain would be the quiet kind of
lie this file exists to prevent. It is the last resort, taken because the alternative is
handing a player a fall as their first experience of the mod.

### Still open, and now sharper

**§VII.3 — what is scarce — is still unanswered, and it is now the whole game.** Islands
over void make *anchorage* scarce by accident: there is less ground, so there are fewer
places a hull can be moored. Nothing yet makes it scarce **on purpose**. Ex Nihilo's
answer was matter and it was a progression system; Octia's should be anchorage and it is
currently a side effect of the terrain. Until somebody designs that, this is a beautiful
world with the same loop in it.

**§VII.2 is now cheap to answer and has not been.** A hull floating with nothing under it
is one gametest, and `hasFooting` is not consulted by `ShipCoreBlock.hullIntact` at all —
the eight neighbours are a rule about a plane. It should hold. Nobody has looked.

**Unmeasured:** how far apart islands actually are on a `floating_islands` seed, and
therefore how often `Landfall` rings out to 96 and how often it gives up and builds. §VIII
set the standard — a 200-seed sweep over two million cells — and this section has not met
it. The sweep to run is: for N seeds, how far from spawn is the nearest column with ground
in it.

`[2026-08-22]`

---

## X. Measured 2026-08-23 — the islands are gone, and one of them is a bathtub

**The standard §VIII set has finally been met for this file.** Seven saves, one seed,
read block by block out of the region files. The strip and every number below is in
[`worlds/slices/README.md`](../worlds/slices/README.md); this section states what it
means for the design.

### §IX's claim "sea level at -64" is no longer true

Two keys in `octia:sky` diverge from vanilla `floating_islands` beyond the density
term, and both were changed on 2026-08-23 without a line anywhere:

| key | vanilla | `octia:sky` |
|---|---|---|
| `sea_level` | −64 | **96** |
| `aquifers_enabled` | false | **true** |

§IX is left as written, per the house rule. Read that sentence as history.

### The shipped world has no void column and no dry chunk

`chunk-probe.py --profile` over every full chunk, same seed:

| | vanilla `floating_islands` | shipped `octia:sky` |
|---|---|---|
| median rock per chunk | 13.21% | **29.20%** |
| median fluid per chunk | 0 | **18,059 blocks** |
| completely empty chunks | 3.4% | **0.0%** |
| chunks with no fluid at all | 76.4% | **0.0%** |

Not one chunk in 1,681 is empty. Not one chunk is dry. **§I's bar is not cleared and
is not close** — *"if a player can walk off the island and find normal terrain, there
is no contract, and what has been built is scenery."* Shape A was chosen; what
generates today is nearer shape B with a water table.

### The last step in the search tuned nothing — it flooded the world

The `tune` rung against the `sea` rung, full chunks only: **18.30% of cells differ,
99.5% of them below y=96, and 89.5% of the substitutions are `air → water`.** Above
y=96, 0.5%. Median rock moved from 28.74% to 29.20% — half a percent.

So the terrain shape currently shipping is the `tune` rung's, and everything the last
change did was fill its caverns.

### `isle` is where the archipelago actually was

The `isle` rung sits at **13.16% rock with 2.4% empty chunks and 77.9% of chunks dry**
— statistically indistinguishable from vanilla on density, while running octia's own
swell field. It is the only rung that both keeps the void and composes it. The search
then went to 41.09%, back to 28.74%, and shipped 29.20% with water in everything.

**If shape A is still the goal, `isle` is the rung to return to**, and the numbers to
beat are its three. That is a decision for the chair, not for this file — §VII.3 is
what it turns on.

### §VII.3 — "what is scarce" — is now answerable, and the answer is nothing

§IX said anchorage was scarce "by accident: there is less ground". There is no longer
less ground. There is more ground than vanilla and water in every chunk of it. The
accident has been undone, and nothing replaced it on purpose.

### What the terrain being data means, stated because it was asked

Terrain is `data/octia/worldgen/noise_settings/sky.json`, referenced by
`world_preset/sky.json`, shipped in the jar. **A new world generates exactly this**: a
fresh seed-1 save, generated headlessly on 2026-08-23 after the provenance comment
landed, matches the played save to **99.68%** over the compared range, and effectively
all of the 0.32% is fluid still settling — `bubble_column → water`, lava that had
chilled to obsidian. About 0.06% is rock.

No Java shapes it. `SkyChoice` moves the world-type button and `Landfall` guarantees
ground under spawn afterwards; neither decides a block of terrain. That is §IX's
argument holding up.

**But an existing save keeps its chunks.** Changing `sky.json` only reaches newly
generated ones, so a world made before a change shows a seam rather than re-shaping,
and every dev save in `run/saves` is a record of the settings of the hour it was made.

### The inputs to the shipped terrain are now in the file

`sky.json` carries a `_comment` naming the command that produced it —

```
python tools/make-sky-noise.py --gain 1 --bias 0.25 --swell 0.3 \
    --swell-scale 0.08 --sea-level 96 --aquifers
```

— and `make-sky-noise.py` writes that line on every run, so it cannot go missing
again. The six values were **proven rather than inferred**: regenerating with them
produces a file identical to the shipped one apart from the comment. Verified as
harmless by generating a world with the key present.

For a day they were recoverable only by reading the expression tree by hand, and the
usage example at the top of `make-sky-noise.py` still names `--bias 0.6 --swell 0.9`,
which builds a *different world* than the one that shipped.

### Still not measured

1. **Island spacing**, asked for in §IX and still unanswered — for N seeds, how far
   from spawn is the nearest column with ground. It is now a stranger question than it
   was, because on the shipped settings the answer is almost always zero.
2. **What the lattice does on this terrain.** `RuinGround.hasFooting` rejects an air
   floor plane, `ObeliskFeature` wants a 7×5 footprint with headroom over the whole
   prism and refuses rather than levels, and `ArchFeature` carries no rarity filter on
   purpose. All four debug readouts from the 22:22–22:27 playtest say
   `obelisks: 1 (within 1024b of you)`. Nobody has measured the real rate on
   `octia:sky` against `ROADMAP.md` §V's one-per-840-chunks on ordinary terrain.
3. **Rungs 1 to 5 are unrecoverable.** Each regenerated `sky.json` and only the last
   was committed. The slices are the only surviving record of what they made.

### The sea is draining out of the bottom of the world

**This is a live bug, not a taste question, and it is the sharpest thing on this
page.**

The freshly generated seed-1 save has **3,756 blocks of water below y=0** — below the
noise band's own floor, where nothing generates at all. They sit in exactly **25 of
169** chunks, and those 25 are the ones around spawn.

That distribution is the proof. Generation touched all 169 chunks equally; only the
ones the server actually **ticked** during its five-second run have water down there.
So this is not generation. It is fluid flow: the sea is pouring off the underside of
the world, and it will start wherever a player loads chunks.

The spawn column, read with `--column 0 0`:

```
 -64..-2    - air -
  -1..38    minecraft:water      <- one block below the band floor already
  39..40    minecraft:stone
  41..58    minecraft:water
  59..63    minecraft:stone
  64..64    minecraft:gravel
  65..95    minecraft:water
  96..141   - air -              <- sea_level 96, and nothing above it
```

**The cause is the two changed keys meeting a floorless band.** `octia:sky` sets
`sea_level: 96` and `aquifers_enabled: true` over a band whose `min_y` is **0**, and
`floating_islands` has no bedrock and no floor. Water placed at the bottom of that
band has nothing to sit on. Vanilla sets `sea_level` to −64 for precisely this reason:
so no water is ever placed at all. Its own note in `make-sky-noise.py` said as much
before either key was moved — *"whether they CAN move without the islands drowning is
a question for a generated world rather than an argument."* It was a good question and
the generated world has answered it.

Three consequences, in the order a player meets them:

1. Every water edge is an endless waterfall into the void.
2. Those are fluid ticks, forever, in every loaded chunk that has one.
3. It compounds: the played save carries more water than the fresh one at the same
   seed, and the difference is fluid that had time to move.

Not fixed here. The fix is a decision — drop `sea_level` back toward the band floor,
or give the band a floor — and §VII.3 is what it turns on.

### And the instrument was wrong three times, which is why none of this was known

All fixed 2026-08-23, all silent until now, all in `chunk-probe.py`:

- **`"air"` was a substring rule and `"air"` is inside `"stairs"`.** Every
  `stone_brick_stairs` and every trial chamber's `waxed_*_cut_copper_stairs` rendered
  as **void**, in the one tool built to answer *is there void here*.
- **`--profile` counted water as solid, and `cave_air` as solid.** The test was
  `!= "minecraft:air"`, which `cave_air` and `void_air` both pass. So caves inflated
  every density figure, and on a world with aquifers on a flooded cavern scored
  exactly like one packed with stone — the difference between an archipelago and a
  bathtub.
- **The headline VERDICT was wrong on both kinds of world it was built to tell
  apart.** Its rule was *"nothing below y=0, or it is not a sky world"*, and it failed
  twice in opposite directions:
  - it called the genuine `octia:sky` save **not a sky world**, because of the leaked
    water above;
  - and it called **vanilla `floating_islands` not a sky world too** — because a
    **trial chamber** generates at y −16 to −47, tuff bricks and waxed copper hanging
    in the void. Structures place in dimension space, −64..320, not in the noise band,
    so one anchored near the floor goes straight through it. Nothing to do with this
    mod.

  Presence below the band therefore proves nothing. The tell is a **ratio**: an
  ordinary overworld has rock under essentially every chunk (measured: 451/451,
  100%), a floating one under the few that caught a structure (10/1089, 0.9%). And the
  drain check is now only asked of a floorless band, because on an ordinary world
  water under y=0 is an ocean doing its job.

Also 57 block kinds had no palette entry and drew magenta, including the whole
amethyst geode and every ruined-portal block. A name that falls through now prints to
stderr with a count.

`[2026-08-23]`

---

## XI. Fixed and decided 2026-08-28 — the band has a floor, and the ocean stays

Two things settled on the same day. The first was a bug and had one right answer.
The second was a taste call and KEG made it: **keep the ocean.**

### The floor

§X called the draining sea "the sharpest thing on this page" and left the fix as a
choice between dropping `sea_level` and giving the band a floor. It was neither,
quite. **Two files disagreed about how tall the world was.**

| file | says |
|---|---|
| `data/octia/worldgen/noise_settings/sky.json` | `min_y: 0`, `height: 256` |
| `data/octia/worldgen/world_preset/sky.json` → `minecraft:overworld` | `min_y: -64`, `height: 384` |

So 64 rows of world existed that the generator never wrote to — open air, no
bedrock, no floor — and the sea at `sea_level: 96` poured into them. Water was not
falling out of the bottom of the world. It was falling out of the part of the world
that nothing had built.

The fix is `data/octia/dimension_type/sky.json`: vanilla `minecraft:overworld`
extracted byte for byte from the 1.21.1 jar per AGENTS.md V, with three keys changed
— `min_y` to 0, `height` and `logical_height` to 256 — and `world_preset/sky.json`
typing its overworld `octia:sky` instead of `minecraft:overworld`. The world now
ends where the band ends and there is nowhere left to drain to.

**Measured the same way §X was, seed 1, 289 chunks, `tools/chunk-probe.py`:**

| | shipped `[0_5_6_S_E_A]` | floored `FLOORCHECK_0_7_0` |
|---|---|---|
| fluid below y=0 | **5,152 sections** | **none** |
| rock below y=0 | 32/1681 | **0/289 (0.0%)** |
| probe verdict | `THE SEA IS DRAINING.` | `floating islands over void, as asked for.` |

### The ocean

The floor did not empty the world. It **contained** the sea — 1,558 water sections
remain, and now they behave. The spawn column reads:

```
   0.. 95   water          the sea, at sea_level 96
  96..141   - air -        46 blocks of open air
 142..169   stone          an island
 173..198   the mast, and octia:ship_core lit at the top
```

**§I's contract is not broken by this, and that is why the ocean can stay.** The bar
was never "void" for its own sake — it was *"if a player can walk off the island and
find normal terrain, there is no contract."* An ocean is not normal terrain. You
cannot walk off onto it, farm it as it stands, or find the ground under it: rock
below y=0 measures 0.0%. The scarcity contract survives.

**What it does cost, stated plainly so nobody rediscovers it in a playtest.** A fall
into water is survivable. Under a void, falling off an island was the sky world's
principal danger and it was absolute. Under an ocean it is a swim and a climb. That
is a real change to how the world plays, it was accepted knowingly, and anything
that depends on falling being fatal — the sail rig's stakes above all — must be
re-asked against it.

### Still not cleared

The density miss from §X is untouched. Median rock is still 29.20% against vanilla
`floating_islands`' 13.21%, and the `isle` rung (13.16% rock, 77.9% dry) is still the
rung to return to if the archipelago is wanted. **That is a separate decision and it
is still open.** Fixing the leak did not make the islands the right shape; it made
them the right shape's problem again.

`[2026-08-28]`

---

## XII. Named 2026-09-03 — the ocean is Neptune's Ocean

**Corrected the same day `[2026-09-03]`.** This section was first written
naming the sea *Ken's Ocean*, derived by an assistant from ARCADIA's
prefix registry. The owner named it within the hour: **the water under the
islands is Neptune's Ocean.** The wrong text is kept below, struck, because
corrections are new entries. The session that told it is titled
`NEPTUNES_OCEAN`, and it is the first of the **Neptune's Ocean series** —
the sessions that carry this sea forward take that name, in order, the
way ACT and MILESTONE carry the codex forward.

§XI kept the sea and called it "the ocean" eleven times without a name. It
has one now.

~~It was already on the estate before the sea was: the water under the
islands is **Ken's Ocean**. The name is not new and was not chosen here.
It is the oldest ocean the crew has:~~

| where | what it says |
|---|---|
| ARCADIA `bin/logs/logKEN0001.txt` | the prefix registry, first entries: *KEN -- for the one who fishes the ocean.* |
| ARCADIA `MANUAL.md` | registered prefixes: **KEN** (the ocean), **KEG** (commissary) |
| ARCADIA `boot.go` | the title selector draws `KEN'S OCEAN v<settings>, SIMULATED` as one of the device's six true names |

~~KEG made the call to keep the sea (§XI). KEN is the one who fishes it.
The same crew that seats the bench in `crew/` is the crew the registry
names, so the ocean under Octia's islands is the ocean Ken fishes, and it
carries his name.~~ The table stands as a record of the derivation, and
of what stays true in it: KEG made the call to keep the sea, KEN is the
one who fishes it, and `KEN'S OCEAN, SIMULATED` remains one of ARCADIA's
device titles. None of that names *this* sea. Neptune does. The
`SIMULATED` on the device title is doing honest work here too: this one
is a `sea_level: 96` in a noise file.

**Where the name lives.** Only in prose, on purpose. It is not a registry
path, a biome, a dimension, or a translation key, and nothing in the mod
resolves it — so it cannot go stale the way `octia:` literals do
(`NAMING.md`). It is written down in exactly two places:

1. this section;
2. the world-type tooltip, `octia.create_world.toggle.tooltip` in
   `en_us.json`, which until today still said *floating islands over open
   void*. That was true on 2026-08-17 and false since 2026-08-28. It now
   says *floating islands over Neptune's Ocean* (it said *Ken's Ocean*
   for one commit, `7c9ca46`).

**A defect found on the way, stated and not fixed.** The void squid's band
is absolute world Y: `VoidSquidDrift` states `WORLD_FLOOR = -64`, and puts
the band at `BAND_FLOOR = -54` to `BAND_CEILING = -10`, in "the gap between
-64 and 0 where nothing generates." §XI's fix — `dimension_type/sky.json`
at `min_y: 0` — removed that gap. On the sky world today there is no y
below 0, and the rows the squid was cut out of are not open void with the
continent's underside over them; the rows *above* them, 0..95, are
Neptune's Ocean. `VoidSquidDriftTest`'s `theBandSitsInsideTheVoid` still passes,
because it checks the band against the constants and not against the
dimension type. Both numbers are marked *provisional - owner tunes by
walking the world*, so where the squid goes now is the owner's to say.

`[2026-09-03]`
