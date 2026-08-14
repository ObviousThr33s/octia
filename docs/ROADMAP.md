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

**Where it sits now.** Deliberately dense while the shapes are being judged:
`derelict` at 260, `obelisk` at 180. You cannot tune a silhouette you never
meet. This is the wrong number for shipping and the right one for looking, and
the two should not be confused - pull both up once the shapes are settled, and
judge the result from a walked world rather than a flown one.

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

**Closing it.** Give the feature the beacon's walk-down: drop through fluid to
the floor, then decide. A wreck on the seabed is arguably the *most* in-fiction
place for one - a ship that was called and never arrived.

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

## XII. The threads point at nothing

**What landed.** `Sightlines` lays a seeded lattice over the world — a node
every 512 blocks, each with a next node one cardinal step away — and
`ObeliskFeature` stands its prism along the leg between them, with a sighting
slot bored down it. Distance to the leg decides how likely an obelisk is to have
stayed standing, so the lit ones trace the route. The survey of the saves that
motivated it, and the reason none of this could be read out of vanilla terrain,
is in [SIGHTLINES.md](SIGHTLINES.md).

**What is wrong with it.** A leg points at a waypoint with nothing on it.
Placement is still a per-chunk rarity roll that knows nothing about the lattice,
so following a thread converges on an empty field. That is a promise the world
does not keep, and it is the kind of thing a player only has to hit once.

**Closing it, two ways, and they are different designs.** Either the first-load
path seats an obelisk at the nearest node the way `placeNearSpawn` seats the
guaranteed derelict — the node becomes a place — or a node is declared to be
only a direction and never a destination, and the fiction has to carry that. The
second is cheaper and the first is what a player will expect. Decide it out
loud; do not let the current behaviour become the answer by default.

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

**Commit titles must be exactly 29 characters.** A `commit-msg` hook enforces it
and rejects anything else. It lives in `.git/hooks`, which is not tracked, so it
does not survive a clone and is not discoverable from the repo - you find out by
being rejected. The recent history is all 29 on purpose.

**No world has ever rendered the gold beacon mark.** `recordBeaconAt` arrived in
`644b83e` and every existing save raised its beacon before that;
`claimBeacon()` fires once per save, so none of them can ever record it. The
next world created will be the first.
