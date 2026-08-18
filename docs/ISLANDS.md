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
  island is the best plinth one could have, and it answers the sightline complaint
  in the same stroke: an island reads at distance in a way a half-buried cube never
  will.

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

`[2026-08-17]`
