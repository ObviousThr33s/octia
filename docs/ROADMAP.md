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

## XII. Smaller things, all cheap

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

**`verify.ps1` sometimes hangs after the tests pass, and it is not our code.**
The symptom is a run that prints "All N required tests passed" and then never
returns. A thread dump of the stuck JVM puts the server thread here:

```
MinecraftServer.stopServer -> ServerChunkCache.tick
  -> ChunkMap.tick -> ChunkMap.processUnloads
```

That is vanilla's shutdown drain, which ticks the chunk cache until every chunk
has unloaded, spinning at full CPU while it does. It is intermittent - the same
suite hung once and completed the next run - and it got easier to hit as the
suite grew past thirty tests, because gametests are laid out across enormous
coordinates and each one leaves chunks behind.

The report is written *before* shutdown, so a hung run's results are complete and
trustworthy. If it bites often enough to matter, the fix is a watchdog in
`verify.ps1`: wait for the report, allow a grace period, then kill. That has not
been done, because a watchdog would also hide a hang in our own code, and that
trade wants deciding on purpose.


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
