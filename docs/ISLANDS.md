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
