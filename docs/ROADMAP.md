```
ACT TWO
MILESTONE 2
OCTIA_[0.1.0.R.O.A.D]_roadmap
SEEK KEG |ALL|
```

# Roadmap

Standing notes on what is wrong, what is missing, and what is wanted next.
Written down because a note kept in the head is a note that gets re-derived.

Each entry says what was observed, what it actually is where that is known, and
what would close it. Entries move out of here when they land, not when they are
started.

---

## I. The map reads as one repeated dot

**Observed.** The player icon is the teal dot, and it is the same size as the
centre point of the obelisk reticle. Everything on the box looks like everything
else.

**What it actually is.** Confirmed in the code, and worse than it looks.
`OctiaDebugOverlay.plot` computes `half = Math.max(1, size / 2)` and fills
`x-half` to `x+half+1`. For the mooring's `size = 2` that is 3px across; for the
beacon's `size = 3` it is *also* 3px across, because integer division of 3 by 2
is 1. The player marker is a third hand-written 3x3 fill at the centre. So all
three marks are the same 3x3 square, the `size` argument has never had any
effect, and the player sits exactly on the crosshair intersection where it is
least distinguishable.

**Closing it.** Distinct silhouettes, not distinct sizes - colour and scale both
fail at 3px against a dark panel. A filled square for a mooring, a hollow ring
for the beacon, an open cross for the player so the reticle reads through it.
*(First pass landed; see the entry below for what it does not solve.)*

---

## II. The map needs a vibe, and an image cache

**Wanted.** A visual language, and a sprite cache behind it that makes sense,
rather than marks composed out of `graphics.fill` calls.

**Why it is a real item and not polish.** Every mark on the box today is
rectangles drawn one at a time in immediate mode. That caps the vocabulary at
what can be spelled in axis-aligned boxes - no diagonals, no outlines thinner
than a pixel, no rotation, no anti-aliasing - which is why distinguishing three
marks is already hard at the sizes involved. It also means every new marker type
is new drawing code rather than a new entry in an atlas.

**Closing it.** One texture atlas under `assets/octia/textures/gui/`, blitted
through `GuiGraphics.blit`, with the marker set defined as data. The cache is the
point: the icons stop being code.

---

## III. Starfield launch-day map

**Wanted.** The map rendered the way Starfield drew planets at launch: an array
of height-mapped points describing topology, rather than a flat panel with dots
on it.

**What it would take.** A grid of samples over the visible range, each carrying
a height, drawn as points whose brightness or offset encodes elevation. The
client cannot read terrain outside its own render distance, so the samples have
to come from the server in the same snapshot the moorings already ride in - and
that changes `OctiaDebug.Snapshot` from a handful of longs into something with a
real payload size. Worth measuring before designing: a 64x64 sample grid is 4096
values per refresh, at two refreshes a second, and the whole point of the pull
model was that a debug overlay nobody has open costs nothing.

**Open question.** Is this the debug map growing up, or a second, separate
instrument? They want different things. The debug box wants to be legible and
free; a topology map wants to be beautiful and can afford to cost something.

---

## IV. The derelict does not read as a wreck

**Observed.** The generated structure makes no sense to look at.

**What it actually is.** Partly identified. The mast used
`if (random.nextInt(height + 1) < y) continue;` per course, which skips
individual blocks anywhere up the column - so a "snapped mast" could generate
with holes in the middle and panels floating above the gap. Debris panels were
scattered onto any free surface with no relation to the wreck. Neither reads as
a structure; both read as litter.

**Closed, by throwing the shape away.** The mast was the wrong object. The ship
is a **cube** - the Hexahedron brief, and the shape `hullIntact` was always
describing, since the core's eight horizontal neighbours *are* the middle slice
of a 3x3x3. A derelict is now twenty-six panels around one core, sunk to two
thirds. Erosion eats the top course only; the core's slice is exempt, because
taking one panel out of it stops the ruin being a ship at all.

**Still open.** Whether a 3x3x3 is the right *size*. It is legible and it is
correct, but it is small - a wreck the player can step over. A larger hull would
mean a hollow shell with a solid slice at the core's level, which is buildable
but is a different object. Decide with eyes in the world.

---

## V. Density

**Observed.** The amount per chunk seems high.

**What it is.** One number: `chance` in
`data/octia/worldgen/placed_feature/derelict.json`. It is a rarity filter, so
`chance: N` means a one-in-N roll per chunk. Started at 400.

**Note the sampling problem.** Density judged while flying through fresh chunks
is not density as experienced on foot, and the explored areas in
[WORLDS.md](WORLDS.md) give a real yardstick: vanilla trial chambers land about
one per 1400 chunks in these saves, ocean ruins about one per 700. A landmark
should sit nearer those than to mineshafts at one per 185.

**Closed, with numbers.** Measured rather than guessed, over 5041 generated
chunks of seed 4242 via `new-world.ps1 -Chunks 24` and `world-report.py --ruins`:

| | before | after |
|---|---|---|
| `derelict` / `obelisk` / `waystation` | 260 / 180 / 320 | **800 / 520 / 900** |
| hull-bearing ruins | 1 per 240 chunks | 1 per 840 |
| nearest-neighbour spacing | min 36b, median **97b** | min 58b, median **122b**, max 272b |

One per 240 chunks is mineshaft density - vanilla runs about one per 185 in
these saves - and a median of 97 blocks between wrecks means one is nearly
always in view, which is the definition of litter. One per 840 sits just past
vanilla's ocean ruins at one per 700, which is a find.

The counts include the two guaranteed at spawn, so the wild density is slightly
lower than the figures above. Obelisks carry no core and are not counted at all;
their number was moved by the same factor on the same reasoning.

**What would change it again.** These are numbers from a *generated* area, not a
walked one. Density as experienced on foot depends on sightlines, and an obelisk
is meant to be seen from much further than a half-buried cube. If obelisks start
feeling sparse before wrecks do, move obelisks alone.

**The obelisk's 180 has stopped being a rate.** Its footprint grew from a 3x3
plinth to a 7x5 one and its footing check now asks for headroom over the whole
prism rather than a flat eight, and the check refuses rather than levels. So
strictly fewer candidates survive it than did, by a factor nobody has measured.
Read 180 as an upper bound on the density until somebody walks a world and says
what it actually is.

---

## VI. Derelicts refuse water

**Observed, from the other direction.** The beacon seats its column properly on
the gravel of the seafloor, which is `OctiaBeacon.raise` doing exactly what its
note promises - `MOTION_BLOCKING_NO_LEAVES` plus a walk-down through fluid.

**The wild ones do not do this.** `RuinGround.hasFooting` rejects any position
whose fluid state is not empty, and the placed feature drops it at
`WORLD_SURFACE_WG`, which in an ocean is the top of the water. So every marine
candidate from natural generation is refused and no wild derelict has ever
generated underwater. That is a safe default rather than a considered one - it
was written to stop wrecks bobbing in open water, and it overshot into stopping
them entirely.

**The near-spawn one already does.** `OctiaWorldgen.tryAt` reads
`Heightmap.Types.OCEAN_FLOOR`, the seabed rather than the water surface, so the
footing check lands on solid ground two below it and passes. A save that spawns
over water gets its guaranteed derelict on the floor of it - bare, because
`RuinGround.surfaceNear` needs an air block and neither digs nor debris can find
one. The walk-down this entry asks for is owed to the placed-feature path only.

**One half of this was a bug, and is fixed.** `placeNearSpawn` asked
`OCEAN_FLOOR`, which answers with the seabed, so the guaranteed derelict was
handed solid rock beneath it and accepted - it generated underwater in seagrass,
thirteen blocks below the spawn it is meant to be a short walk from. The wild
ones refused water the whole time. Two paths through one feature, disagreeing
about what counts as ground. It now asks `MOTION_BLOCKING_NO_LEAVES`, and
`RuinGround.isDry` checks the volume a ruin will occupy rather than only the
plane it stands on.

**And now they do.** Taken on purpose: a ship that was called and never arrived
is most at home under water. `RuinGround.descend` is the beacon's walk-down,
written against block reads so a feature can use it during generation, and a
derelict now drops through air and fluid to whatever is holding the world up.

What it still refuses is the **waterline**. A hull with its floor in the sea and
its lid in the air is neither a shipwreck nor a ruin, so the water has to still
be water two courses above the cube. Digs go in the seabed the way vanilla's
ocean ruins bury suspicious sand.

**Left undone deliberately.** A submerged wreck is not dressed at all. Every prop
in `Habitation` is a thing somebody left in a room - a lit campfire, a made bed,
a path worn into dirt - and none of them mean anything on a seabed. World 0's
palette has the marine half already (sea lantern, sea pickle, prismarine,
seagrass); a submerged dressing built from those would be better than nothing.
Nothing is better than a bed underwater.

---

## VII. The near-spawn derelict has no gametest

**What landed.** `OctiaWorldgen.placeNearSpawn` puts one derelict within 48-112
blocks of spawn on a save's first load, rings outward until something takes it,
and is seeded off the world seed so a given seed always answers the same.

**Why there is no `@GameTest` for it**, against rule III. What it does is search
real terrain out to a hundred blocks from a real spawn. The gametest world is a
flat void and the plots sit thirteen blocks apart, so there is nothing out there
to find and nowhere to put it without writing into the neighbouring test. The
feature it calls is covered by six tests; the search around it is not.

**Closing it.** Either parameterise the radii so a test can drive it at six
blocks on a laid floor, or accept that this one is verified by opening a new
world and reading the log line it prints. Do not leave it looking tested.

---

## VIII. Travelers

**Observed.** There are a lot of travellers in this game mod.

**Unresolved, deliberately.** This note has two readings and they lead opposite
directions: either wandering traders are appearing too often and something in
the pack or the spawn rules wants looking at, or travellers are a thing the mod
should lean into - a Serenity-class ship called to a dig is a story about people
who arrive from somewhere. Not acting on it until which one is meant is settled.

---

## IX. Strangers

**Wanted.** Mysterious but friendly and reserved strangers.

**The hard part is already built.** `com.serenity.octia.crew` is a complete
LLM-backed fake-player framework: `CrewPlayer` is a real server-side
`ServerPlayer`, so a stranger appears in the tab list and is visible to LAN
guests who have never installed Octia. `Cleric` runs the model asynchronously
and never blocks a tick. `Tender` supplies orders with the bench powered down,
which matters because most players will not be running Ollama. `Situation`
already assembles a briefing. A stranger is not new machinery; it is a crew
member nobody mustered.

**What makes it a stranger rather than a crew member.**

| | crew | stranger |
|---|---|---|
| arrives | `/octia crew muster` | unbidden, near an obelisk, at dusk |
| distance | fans out around you at 1.6 blocks | holds a band, will not close past ~6 |
| speech | answers freely | short, oblique, deflects direct questions |
| leaving | dismissed | logs off once nobody has had eyes on them |

**Reserved is a prompt, not a behaviour tree.** The briefing is the whole
mechanism - short answers, no exposition, never volunteer where you are going.
That also degrades correctly: with the bench down, `Tender` gives a stranger who
says almost nothing, which is *in character* rather than broken. Very few
designs get a graceful offline mode for free.

**The detail worth building it for.** A stranger you meet twice remembers. Keep
a small per-save record of which names have been met and where, and have the
second meeting open by naming the *place* rather than you. That is the entire
feature; everything else is staging.

**Landed.** `Wayfarer` drives one at a time: arrives at night under open sky,
more than 64 blocks from spawn, on a cooldown; holds a 6-block band; asks its
cleric every ten seconds; leaves once nobody has been within 30 blocks for
twenty. `WayfarerLedger` is the memory, and `Situation.wayfarerBriefing` is the
reserve — a paragraph, not a state machine, which is why it still works with the
bench switched off.

**They are chaos, and the design is built for that rather than against it.**
Three things make a strange answer harmless, and only the third is about the
model at all:

- **No hands.** A `CrewPlayer` has no client sending block-break or item-use
  packets, so a wayfarer physically cannot mine, build, steal or attack.
- **A narrower vocabulary.** `Wayfarer.permit` drops FOLLOW and JUMP even when a
  model produces them. A stranger that trails you home is not reserved.
- **The band is not the model's to decide.** `Wayfarer.band` overrides whatever
  the cleric said. Reserve that depends on a language model remembering to be
  reserved is not reserve.

**They leave something now.** One departure in three puts a bindle down where
they stood - two or three stacks of road things, on an unlimited lifetime
because they leave once nobody is watching and a five-minute despawn would rot
it on an empty road every time. It is the only thing a wayfarer ever gives you,
and it is given by being put down rather than handed over, which is the whole
character: one that traded would be a wandering trader with a better prompt.
See [BINDLES.md](BINDLES.md).

**Still open.** They arrive anywhere outdoors at night rather than near an
obelisk, because obelisk positions are not recorded anywhere — a ruin registry
would fix that and would also serve the minimap integration. And nothing yet
puts a wayfarer at a ruin *they* are also visiting, which is the encounter the
fiction most wants.

**Note against entry IV and VIII.** Ruins stay empty - always. Strangers are met
on the road, never found living in a wreck. The emptiness is what makes an
encounter land.

---

## X. The echo across eras

**The best-value idea available, because it is already paid for.**
`ShipMoorings` is keyed by `BlockPos` with no dimension, deliberately - held
there by the signature of `get`, which takes a server rather than a level.
(`ShipGameTest.mooringsAreDimensionAgnostic` is named for the property but does
not pin it: every `ServerLevel` hands back the same `MinecraftServer`, so both
of its lookups pass the identical argument. See the note on the test.) That
property is the spine of the whole design and **nothing currently uses it.**

Stand in the Nether at the same X/Z as a ship moored in the Overworld and there
should be something: a low hum, a faint particle column, the core's light
bleeding through from a world away. No new state, no new store, no new packet -
the answer is already in the save. It is the cheapest wonderment in the codebase
and the closest thing the mod has to a thesis statement.

**Landed.** `EraEcho` wakes every forty ticks, checks each player against the
moorings store, and draws a shaft of end-rod motes with an occasional low tone
where the store says a ship is and this level has no core. Standing beside your
own ship is a ship, not an echo of one.

**The thing it taught.** The era stack does not share a vertical range - the
Overworld begins at -64, the Nether at 0 - so a ship moored at y=-58 has a Y
that does not exist one layer over. The first version gated on whether the
mooring's exact position was loaded, which switched the whole feature off across
the Overworld-to-Nether boundary: the one crossing it was written for, failing
silently. What travels between eras is the **column**, never the point.

**Still open.** Nothing carries the echo the other way - a player in the
Overworld gets no hint that something is moored beneath them in an era below,
because there is only one era stack layer implemented. When there are more, the
question of whether an echo should be directional is a real one.

---

## XI. Mod integrations

**First, a problem worth raising.** `D:\Serenity\OctiaModpack\mods-manifest.tsv`
lists 124 mods: **116 NeoForge**, one NeoForge decoy, one unknown, and six that
ship for both loaders. Octia is Fabric. As things stand it cannot go in its own
modpack, and neither can the 116.

That is a fork to take deliberately rather than discover late - port Octia to
NeoForge, build multi-loader, or accept that the pack and the mod are separate
projects that will never share a launcher. Worth settling before the integration
list below is written against the wrong loader, since Jade, JourneyMap and EMI
all have distinct APIs per side.

Integrations should be **soft** in every case: `FabricLoader.isModLoaded` plus a
separate entrypoint class, so the base mod never hard-depends and never
reflects.

- **Jade / WTHIT** — put `ADRIFT / MOORED / CALLED` and the mooring count in the
  look-at tooltip. The survey already exists and returns exactly this; the
  integration is a display. Highest value for the least code.
- **JourneyMap / Xaero** — moorings and obelisks as real waypoints. This is the
  honest answer to roadmap entries II and III: rather than growing the F6 box
  into a cartography mod, hand the data to the cartography mods people already
  run, and let the debug box stay a debug box.
- **EMI / REI** — recipe display. Expected rather than interesting.
- **Mod Menu + Cloth Config** — a real screen for the crew endpoints, which are
  currently a JSON file nobody will find.

---

## XII. The threads, and what they point at

**What landed.** `Sightlines` lays a seeded lattice over the world — a node
every 512 blocks, each with a next node one cardinal step away — and
`ObeliskFeature` stands its prism along the leg between them, with a sighting
slot bored down it. Distance to the leg decides how likely an obelisk is to have
stayed standing, so the lit ones trace the route. The survey of the saves that
motivated it, and the reason none of this could be read out of vanilla terrain,
is in [SIGHTLINES.md](SIGHTLINES.md).

**Closed: the node is a place.** A leg used to point at a waypoint with nothing
on it, because placement was a per-chunk rarity roll that knew nothing about the
lattice - a promise the world does not keep, and the kind a player only has to
hit once. `ArchFeature` now stands an arch on every node, squared across the leg
leaving it, so you walk through it facing the way the thread runs. Keystone plus
four: five stones in the ring, a three-wide opening, one block thick. Nothing
about it erodes, which is the counterpart to the obelisks between nodes that do -
the road is not maintained, the places are.

It carries no rarity filter and must not grow one. The placed feature is offered
once per chunk and declines every chunk whose cell's node is elsewhere: one
arithmetic test per chunk, one acceptance per 1,024. A rarity filter in front of
that would throw away the one chunk that matters and leave nodes bare at random.

**Still open about it.** The arch rejects rather than terraforms, like every ruin
here, so a node on a cliff face or under water gets nothing and the thread points
at an empty place after all. How often is unmeasured. It is the one case where
levelling a five-block strip might be worth breaking the no-terraforming rule
for.

**It now also declines a site somebody already built on.** The lattice is a
function of the seed and has never heard of a structure, so a node lands inside a
village exactly as often as chance says - and an arch is the one thing here that
never erodes, so it would stand in the wheat for the life of the save.
`RuinGround.clearOfStructures` asks the structure *starts* rather than the
blocks, because starts are settled two chunk statuses before features run and are
therefore immune to what order things get placed in within
`SURFACE_STRUCTURES`. The obelisk and the derelict ask the same question; the
obelisk needs it most, being rolled per chunk across the whole world rather than
once per cell.

**The radius checked is the radius written to, and those are not the ruin's own
dimensions.** The obelisk's prism is three from the middle but its digs and its
fallen panels reach six; the derelict's cube is one but its digs reach four. A
site cleared on the footing's footprint alone passes, builds clear of the
village, and then puts suspicious gravel in somebody's wheat. Both features
therefore check `DIG_MAX` rather than their own radii. Anyone adding a scatter
that reaches further has to move that number with it.

**The guaranteed spawn derelict is exempt, deliberately.** `placeNearSpawn` goes
straight to `DerelictFeature.seat` and never asks. Two reasons, and the first
outranks the second: a check in front of forty candidate columns can refuse all
forty and hand back a world with no starter wreck, which breaks the one promise
that a player meets a derelict at all - and a rarity roll cannot make that
promise, which is why the first one is placed rather than rolled. Second, the
query's inner read passes `require=true`, so on a live level it *generates*
missing chunks; forty of those during `SERVER_STARTED` is a world-load stall.

**What that check is not proven to do.** A gametest plot cannot contain a
village - there is no way to ask the framework for a vanilla structure start - so
`bareGroundIsAClearSite` proves only that the question can be asked without
throwing and that empty ground answers yes. That is the failure that would be
silent (a blanket no takes every arch in the world out and looks exactly like the
1,023-in-1,024 decline it does anyway). **The rejection itself has been verified
by reading, not by running.** Walk a seed with a village on a node before
believing it.

**CLOSED 2026-08-21: the corridor is half the world, because KEG widened it.**
The entry below is kept as written, because the measuring is what made the
choice possible and the old numbers are the only way to read what the new ones
cost.

`CORRIDOR` moved from 32 to **128** - the first of the three doors this entry
offered. Re-measured by the same tool at the same constants:

| | `CORRIDOR` 32 | `CORRIDOR` 128 |
|---|---|---|
| ground inside the corridor | 12.66% | **50.47%** |
| obelisks broken | 45.25% | **31.07%** |
| a standing obelisk is on a thread | 20.24% | **64.07%** |
| lift over chance | 1.60x | **1.27x** |

**Read the last two rows together or not at all.** A player now meets the route:
two thirds of standing obelisks are genuinely on a thread instead of one fifth,
and far more of them survive to be seen. But each one says less - a corridor
covering half the world is a weaker claim about any given block inside it. This
entry warned that widening "stops the corridor being a corridor", and 1.60x
falling to 1.27x is exactly that sentence with a number on it. It was paid
deliberately.

**There is no next knee to aim for.** The sweep's histogram is flat at about
3.16% of the world per 8-block bucket all the way out to 136, so width buys
share linearly at roughly 0.395% per block of half-width. Any further move is
taste, not discovery.

**Also closed, in `76a967f`: the legs no longer double back.** The parity rule
took two-cycles from **24.998% to 0.392%**, and the residue is not slack - it is
the 0.3866% of cells whose four neighbours all answer back, where no legal step
exists. Threads run 11.15 legs and 5,709 blocks, up from 4.65 and 2,380. This
entry said the doubling-back was worth doing "only once the corridor question is
settled"; both are settled now, and in that order.

---

*What follows is the entry as it stood before either closed, kept because it is
the record of how the numbers were arrived at.*

**The corridor is an eighth of the world, and the prose said half.** Measured,
not guessed: `tools/sightline-map/Sweep.java` over 200 seeds and sixteen million
sampled points puts the ground within 32 blocks of its own cell's leg at
**12.66%**. Push the break odds through that and about **45% of every obelisk in
the world is broken**, and a standing obelisk is only **20% likely to be on a
thread** against a 12.66% base rate. The signal is real and weak - a run of
standing obelisks along one bearing means something, a single one barely does.

Three ways out, and they are different games. Widen `CORRIDOR` toward 128, which
buys about half the world and stops the corridor being a corridor. Soften the
far odds from 4-in-8, which keeps the ribbon thin and makes the contrast quieter.
Or leave it, on the grounds that a route you have to read from several markers is
a better puzzle than one a single lit block hands you. **Do not pick by editing
the constant** - whichever it is, the sweep number moves with it and the prose
has to move too.

**A quarter of all legs double back.** Each node picks from four cardinals
independently, so the neighbour steps straight back one time in four - measured
at 24.998%. A thread runs about four legs, two kilometres, before it returns on
itself. That was never decided; it fell out of the step being uniform over four
options. Excluding the reverse step costs four extra hashes and turns the weave
into longer routes. Worth doing only once the corridor question above is settled,
since the two together decide whether a thread is something a player can follow
at all.

**Also open, and cheap: the map cannot draw a thread.** The legs are a pure
function of the world seed, so the F6 overlay could draw them with no new
packet — except that a vanilla client is never told the seed. That is the whole
obstacle, and it is one long in `OctiaDebug.Snapshot` away from not being one.

---

## XIII. Smaller things, all cheap

- **First light.** The first time a player surveys a wild derelict in a save, the
  spawn beacon flares once, visible from wherever they are. Two points in the
  world acknowledging each other.
- **The hum rises.** An ambient tone whose density scales with the moorings
  count. A progress bar you hear instead of read.
- **Obelisk chain.** Two or more obelisks in view pulse in sequence, so you
  learn to navigate by them.
- **Stratigraphy.** Deeper digs draw from older loot tables. Archaeology that
  means something vertically instead of being flat everywhere.
- **The ledger.** A lectern at spawn that gains a line every time you brush a
  dig. The world keeping your record, in KEG notation.

---

## Kept for whoever hits it next

**`gradlew` was never executable, and CI had never once run.** Every workflow
run in this repo's history - all eight, from 2026-08-07 to the sightlines branch
- died in about eighteen seconds on `./gradlew: Permission denied`, exit 126,
before Gradle started. The file was committed mode `100644`. So the verify
workflow has never built the mod, never run a GameTest, and never uploaded a
jar; a red badge that had always been red read as normal.

That contradicts standing order 1 in [../OCTIA.md](../OCTIA.md) - *the local gate
and the CI gate are one gate, and CI is the one that cannot be skipped* - which
was true as written and false in practice for the life of the repo. Fixed with
`git update-index --chmod=+x gradlew`, which is a change to the index, not the
working tree: a plain `chmod` on Windows does nothing git will record.

**Do not "fix" this with a `chmod +x` step in the workflow.** That hides the
missing bit rather than restoring it, and every fresh clone stays broken for
anyone on a filesystem that honours the mode.

**The gate hangs after it passes, and the tests are not the reason.** The first
run that ever got past `gradlew` - 31861107125 - ran all thirty GameTests in
5.122 seconds, printed `All 30 required tests passed :)`, wrote the JUnit report,
and then logged `Stopping server` / `Saving players` / `Saving worlds` and said
nothing for twenty-seven minutes, until the job timeout cancelled it. Runner
cleanup then terminated three orphan `java` processes.

The suspicion that cost the first round trip - that a gametest with twenty
iterations in it was too slow - is disproved by the 5.122: whatever this is, it
happens *after* every test has passed. `MinecraftServer.stopServer` in 1.21.1
spins `while (chunkMap.hasWork())` immediately after logging `Saving worlds`,
and that loop logs nothing at all, which fits the shape of the silence.

**[2026-08-21] It is a finding now, and it was not this branch that found it.**
The sentence above used to end "it is a fit, not a finding, and it is not written
down here as one." The `lives-and-islands` branch had been chasing the same hang
from the other side and had the thing this side lacked - a thread dump of a stuck
JVM, which puts the server thread exactly where the guess said it would be:

```
MinecraftServer.stopServer -> ServerChunkCache.tick
  -> ChunkMap.tick -> ChunkMap.processUnloads
```

That is vanilla's shutdown drain, ticking the chunk cache until every chunk has
unloaded and spinning at full CPU while it does. Two things the dump adds that
the guess could not. It is **intermittent** - the same suite hung once and
completed the next run - and it **got easier to hit as the suite grew past thirty
tests**, because gametests are laid out across enormous coordinates and each one
leaves chunks behind. So it worsens as the mod grows, which a one-off timeout
would hide rather than fix.

Also from that side, and load-bearing for anyone reading a hung run's output: the
report is written *before* shutdown, so **a hung run's results are complete and
trustworthy**. The tests really did pass.

Two independent investigations, a hypothesis on one branch and its confirmation
on the other, and neither knew about the other until the merge. Written down at
length because the next hand should not have to do either half again.

What is written down as the response: `tools/gametest-ci.sh` bounds the run and
`jstack`s every JVM on the box before killing it. **Do not replace it with
`timeout(1)`** - that kills the process and takes the stacks with it, which is
the only thing worth having.

The islands branch proposed a watchdog in `verify.ps1` and declined to build one,
on the grounds that a watchdog would also hide a hang in our own code and that
trade wanted deciding on purpose. That was the right worry and it is already
answered: `gametest-ci.sh` takes the stacks *first* and kills *after*, so a hang
in our own code arrives with its evidence attached rather than being swallowed.
The local `verify.ps1` still bounds nothing, which is the remaining half of the
job and is listed under the gate work.

**A structure query may only ever ask about its own chunk.** `startsForStructure`
reads twice - the references held by the chunk you name, and then the chunk that
*owns* each start those references point at. A chunk's references are filled from
every start within **eight** chunks, and a `WorldGenRegion` at `FEATURES`
declares dependencies for distances zero through eight only. So naming a chunk
one over lets the second read reach nine, and past the end `WorldGenRegion` does
not return null or block - it throws *"Requested chunk unavailable during world
generation"* and takes the server down mid-generation.

`RuinGround.clearOfStructures` therefore asks about `new ChunkPos(floor)` and
nothing else, which is what vanilla's own `applyBiomeDecoration` does. **Do not
"improve" it by sampling the footprint's corner chunks** to catch a ruin
overhanging a border. The bug it would buy is seed-dependent, needs a mineshaft
or a stronghold in range, and no gametest can reach it - a test plot holds no
structure references at all, so the whole path is dead code under `verify.ps1`.
**Worlds made before 2026-08-10 have their beacon in the wrong place, forever.**
The beacon and the spawn derelict were placed on `ServerWorldEvents.LOAD`, which
fires before Minecraft chooses the world spawn, so `getSharedSpawnPos()` answered
`(0, y, 0)`. On seeds that spawn near the origin - which is most of them, and all
three of the early dev saves - it made no difference. On seed 1, spawn is
`(112, 67, 176)` and the beacon went up 209 blocks away. Fixed by moving
placement to `SERVER_STARTED`, but `claimBeacon()` fires once per save, so every
world made before the fix keeps the beacon it got. `[0.2.1]` and `[0.2.2]` are
both wrong; `[0.2.3]` is the first correct one.

**Commit titles must be exactly 29 characters.** A `commit-msg` hook enforces it
and rejects anything else. It lives in `.git/hooks`, which is not tracked, so it
does not survive a clone and is not discoverable from the repo - you find out by
being rejected. The recent history is all 29 on purpose.

**No world has ever rendered the gold beacon mark.** `recordBeaconAt` arrived in
`644b83e` and every existing save raised its beacon before that;
`claimBeacon()` fires once per save, so none of them can ever record it. The
next world created will be the first.
