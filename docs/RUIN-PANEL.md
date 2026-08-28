# RUIN PANEL - the generative pass

**Run 2026-08-27.** Six designers on six lenses, each critiqued by a hostile
reader as it landed, then synthesised. 52 designs, 53 verdicts, 13 agents,
zero errors. The panel was re-run because the first pass was not generative
enough; this one was told to prefer bold over safe and concrete over abstract.

Everything below section 0 is the panel's own report, unedited.

---

## 0. Read this before you act on section I

**One load-bearing claim in the panel's top build is wrong, and it is wrong in
exactly the way this repo warns about.**

Build 1 justifies itself partly on a gravity defect: that `Habitation.looseSpot`
lets `wear()` place `MOSS_CARPET` on nothing. Checked against the tree at
`Habitation.java:565-574`, that is not what the code does, and the author had
already considered it:

> *"{@link #spot}'s old body, kept for {@link #wear} alone. A cobweb hangs in
> vanilla mineshafts, and vanilla carpet itself asks only for non-air below, so
> the wear strands keep the looser question - a decision, not an omission."*

The justification holds up: vanilla `CarpetBlock.canSurvive` asks only that the
block below is not air, and moss carpet inherits it. So the moss does not hang
on nothing and will not pop. The panel read a documented decision as a bug.

This matters beyond one bullet, because the panel's own learning 8 says
*"a reviewer will believe a comment"* - and then the panel wrote a comment a
reviewer would have believed. `reclaimInEight()` is still a good idea on its own
merits. It just does not arrive carrying a bug fix.

**Status of the other two gravity claims:**

| Claim | Status |
|---|---|
| `Habitation.looseSpot` places `MOSS_CARPET` on nothing | **False.** Documented decision, vanilla-legal, verified above. |
| `RuinGround.dig` seats a `Fallable` brushable via `surfaceNear` | **Unverified.** Plausible - `dig()` does write `SUSPICIOUS_GRAVEL` at a `surfaceNear` spot (`RuinGround.java:390-404`) - but whether `surfaceNear`'s support test admits a replaceable plant was not confirmed. Check before building on it. |
| `DerelictFeature.debris` scatters panels with no support check | **Unverified.** |

The playtest's actual gravity verdict - the floating pot - is already scoped in
`GREENFIELD.md` F4, and it is against `Habitation.dress`, which is the `spot()` /
`settled()` path, not `wear()`.

The panel closed by saying *"the world is the source of truth, not this
document."* Section 0 exists because that sentence was right.

---

# THE RUIN PANEL — what to build, what to queue, what to kill

52 designs, seven lenses, one owner's ruling to come. Sorted against four playtest verdicts (too dark, the bindle did not read as a bindle, gravity broke the fiction, the ground ruins were not interesting enough) and against what the code actually does, which was read rather than assumed.

---

## I. THE THREE TO BUILD FIRST

### 1. THE INVERSE BUDGET — `reclaimInEight()`, and the growing things get the other half

*(with the vine-and-lichen half of **What the Dark Grew** folded in; the amethyst is killed in §IV)*

One method beside `presenceInEight()`:

```java
/** Chance in eight that any one growing thing has taken hold. */
public int reclaimInEight() { return 8 - presenceInEight(); }
```

Human props keep 6/4/2 and thin out. A second vocabulary gets 2/4/6 and thickens, rolled per prop like everything else: **VINE** on the hull's outer vertical faces; **MOSS_BLOCK** replacing the grass block at the wreck's foot with **MOSS_CARPET** spilling round it, a patch rather than a strand; **HANGING_ROOTS** under an overhanging top-course panel with **ROOTED_DIRT** above it; **BROWN_MUSHROOM** on the shaded side; **AZALEA** on the spoil heap's cap — `Working.heapCap` is handed back by `excavate()` and nothing has ever read it; **CAVE_VINES_PLANT** two or three long ending in **CAVE_VINES[berries=true]**, light 14, hanging under a surviving lid exactly where a lantern would have; **GLOW_LICHEN** continuing to do what `light()` already does at ANCIENT.

**Why it goes first.** ANCIENT is 2-in-8 on *everything* today, so the age that should read as most transformed reads as most empty. "The ground ruins were not interesting enough" is that sentence said in arithmetic. And the gravity verdict has its address in the same method: `Habitation.looseSpot` (Habitation.java:571) is kept loose "for `wear` alone", and `wear()` places **MOSS_CARPET** through it — so moss currently hangs on nothing. The same change routes carpet, moss, mushroom and azalea through `settled()` and confines the hanging licence to **COBWEB**, **VINE**, **HANGING_ROOTS** and **CAVE_VINES**, the four that hang in vanilla. An accepted looseness becomes a named list, which is the difference between a decision and a bug.

**Cost: dressing, medium.** No fourth enum value, no new taste constant — a reviewer checks it by subtraction. Four bills, all real:

- **Route the vine writes through `free()` as well as `settled()`.** `free()` carries the `nearCore()` guard, and a face two out from a core *is* one of the eight slots `hullIntact` counts. The design said "the hull's outer vertical faces" and never said `nearCore`.
- **It moves every existing seed's wear.** Tightening `looseSpot` changes which positions take a block, and `dress()` consumes the same `RandomSource`. Write that in the javadoc. `MYSTERIES.md` §III.1 caught [4] asserting the opposite, and a reviewer will believe a comment.
- **Cave vines random-tick, grow downward, and the berries come back.** Every ancient ruin becomes a small renewable food source. Decide that; do not discover it.
- **Check the lid panel's DOWN face is sturdy before hanging from it.** It is a full cube, so it is — but a vine whose ceiling fails is the floating-block complaint refiled.
- `HabitationGameTest`: an ANCIENT site writes more growth than a RECENT one, and no **MOSS_CARPET** stands on a non-sturdy face.

**What changes.** ANCIENT stops meaning empty and starts meaning taken back: vine down two faces, a moss patch at the foot, roots off the open top course, mushrooms in the shade, and exactly one human object left — now the rarest thing on the site, and therefore the thing you walk over to look at. WEATHERED becomes genuinely in-between at 4/4 rather than "recent with less stuff", which is what it reads as today.

---

### 2. THE LIVED SIDE — every ruin gets a front and a back

`Habitation.spot` gains a preferred arc: the 180 degrees facing the cell's node, tried first, falling back to the existing scatter when the arc refuses. That is the shape `RuinGround.dig`'s preferred-positions list already proved (RuinGround.java:348-375). Everything routing through `spot()` — hearth, light, rest, store, work — lands on the front. Everything routing through `looseSpot()` — the wear — lands behind.

Walk up from the wrong side and it is a dead cube with weeds on it: **MOSS_CARPET**, **COBWEB**, **GLOW_LICHEN**. Round it and there is a lit **CAMPFIRE**, a **BARREL**, a **CRAFTING_TABLE** and a worn line of **DIRT_PATH** leaving. Two wrecks in one cell present the same face. Four in a valley face one way, and the way they face is the way to walk.

**Why second.** It is almost entirely subtraction — no new block, no new siting question, no new refusal to get wrong, and no code that could terraform. It is the cheapest thing on the panel and it changes every ruin in the world. It is also the only instrument here legible *without stopping, from range*, and the only one that outlives what it agrees with: `path()` returns early for ANCIENT (Habitation.java:442), so an ancient ruin genuinely has no path today, and the litter really would be all that points. ANCIENT's front-marker is **SOUL_SOIL** where the hearth was — a block `hearth()` already writes, so nothing new is invented.

**Cost: dressing, small.** One bill, and it is the same one as build 1: **a preferred arc is never empty, so every existing save's dressing shifts.** That is why these two ship in one push — one shift, one note, paid once. `HabitationGameTest`: the arc is preferred, and a blocked arc still dresses. Standing objection it survives: it is the fifth instrument saying *the node is that way*, a channel `SIGHTLINES.md` §IV measured near the noise floor. It survives on being readable at range and on outliving the path it agrees with.

---

### 3. THE LINE — `RuinGround.line()` beside `dig()`

**Four designers reached this independently** (The Line, The Line That Stops, The Digline, The Prospect Line) and `MYSTERIES.md` §IV blessed it by name: *"the single best beat in all fifteen... a small, pure, testable mechanic — `RuinGround.line()` beside `dig()` — worth building alone."* Build the version that costs nothing.

Five stations at a fixed stride of three along one bearing — the working's own rolled `face` where there is a working, `Mystery.toward` otherwise. Four are plain **GRAVEL** at grade with a collar of bare **DIRT**: that is literally what a brushed suspicious gravel leaves behind in vanilla, so a worked-out pit needs no hole, no spoil, no strata and no cut. The fifth, at the far end, is **SUSPICIOUS_GRAVEL** on `OctiaLoot.RUIN_DIG`, unbrushed at every age, because nobody came back.

Refusal is the mechanism: a station whose ground will not take it is skipped and the gap stays. A line across a slope steps with the slope; a line across a pond has a hole in it. A line with a hole in it is more convincing than a perfect one.

RECENT — five stations, two still loaded: they stopped mid-job and you can see where. WEATHERED — four, collars gone to **COARSE_DIRT**. ANCIENT — three, no collars, the loaded one still at the far end, so the survivors read as an arrow pointing away from the ruin at the only untouched dig on the site.

**Cost: java, small.** One method in `RuinGround`, one call site. Four things it must carry:

- **Seat each station through the promoted `settled()`, not `surfaceNear` alone.** `BrushableBlock` is `Fallable` and `surfaceNear` only promises "below is not air". A station on a grass tuft is the playtest's complaint refiled, and `RuinGround.dig` has this defect today.
- **Start the line past `DIG_MAX`** (4 at the derelict, 5 at the template) so it never interleaves with the scatter `dig()` already puts around every ruin. Otherwise it has to beat noise the mod generates in the identical block.
- **Name the null bearing.** `Mystery.toward` returns null within `ARRIVED = 24` of a node. `path()` answers that with `crossroads()`; this needs its own answer, and *no line at a node* is a good one, said out loud rather than by accident.
- **Do not take the trench coat.** No pits, no spoil, no `atGrade` extraction, no `DataComponentType`, no `inventoryTick`, no UUID store, no seeded name on a bag. §IV rejected all of those in the same paragraph that blessed this. Gametest asserts the spacing, not the block count.

**What changes.** Nothing in Minecraft is in a straight line unless a person put it there. The reward goes to reading the pattern rather than to walking further, with no text anywhere. It is the one thing on this panel a player walks into and understands unaided.

---

## II. THE REST — grouped by lens, nothing lost

Each design appears exactly once. Rejections and deferrals are in §IV, not here.

### Dressing

- **Too Heavy, or Not Worth It** — the law ("everything left is too heavy to carry or too worthless to bother") is worth more than its props; write it on `Habitation`'s class javadoc beside *no entities, ever*, and take **ANVIL / CHIPPED_ANVIL / DAMAGED_ANVIL** as the free three-state age dial. Small.
- **The Trade** — SMOKE / SPOIL / PASSING vocabularies; keep every prop on its own `presenceInEight` roll or it builds the shop `Habitation`'s javadoc forbids, and cite `ruin_store.json`'s existing `octia:bindle` + `set_contents` as the bindle mechanism rather than a new component. Large.
- **Inside and Out** — `floor`/`door` DATA markers so props can be placed inside an authored room for the first time, corner computed after `BlockRotProcessor` ran; order it before any new template. Medium.
- **What Is in the Ash** — an **ANDESITE** ring round the hearth with one to three stones missing, and the ANCIENT dig seated inside the scorch via `dig(preferred)`; the campfire contents must be written without `markUpdated()`, which is the one thing `sherds()` and `dig()` both refuse to do. Small.
- **The Post and What Hangs on It** — **LANTERN** hanging, then **CHAIN** empty with the lantern lit on the ground a block away, then chain and lichen; the middle age is the best two-block sentence on the panel. Small.
- **The Pit Is Dressed** — a **LADDER** on the cut's far rock wall, a **WALL_TORCH** in that same rock, a **STONECUTTER** on the lip and one panel on the bench; this is the caller `Working`'s javadoc was written for, and it lifts one dry site in three. Small.
- **The Kiln** — a brick beehive with the unfired clay batch outside its mouth, which is the only thing that would explain `brick` and `clay` at weight 4 in `archaeology/ruin.json`; queue it behind Inside and Out, and drop its §V.5 claim. Template, small.

### Absence

- **The Line That Stops** — the cut version of build 3; if the pits are ever wanted, `atGrade` moves out of `DerelictFeature` (line 851) rather than being copied, per that file's own rule. Medium.
- **The Standing Shadow** — a 5x3 **COARSE_DIRT** rectangle with four **ANDESITE_FRAME_PANEL** pads flush at its corners; correct its claim that `isGround` refuses gravel bars — it does not. Small.
- **The Notch** — an obelisk cut at `SLOT_HIGH + 1` so the sighting slot survives as an open trough with no rubble at its feet; relabel `buildAs` as java, it is a third branch in `ObeliskFeature`. Small.
- **The Piers** — capped columns in a river at one deck grade with no deck; the only design that opens a siting question the mod cannot ask (`hasFooting` refuses fluid, `descend` drops through it), gated on a write envelope. Large.
- **The Cradle** — a 3x3 chocked pit the exact print of a hull, lit from one wall, with a drag scar leading away; share the `atGrade`/`cut`/`heap` extraction with The Line That Stops, and differentiate its ANCIENT from The Standing Shadow's. Medium.
- **The Threshold** — a doorframe on a cliff rim with the path arriving at its sill and sky through the doorway; rewrite WEATHERED so no lintel cantilevers over the drop. Medium.
- **The Empty Cellar** — a room structurally unreachable by `Habitation.scatter` because `SURFACE_DOWN` is 3; needs one optional `sink` field on `TemplateRuinFeature.Config`, defaulted to today's 1, or its own argument evaporates. Small.

### Terrain

- **The Fall Line** — where `relief()` reports 2 blocks of fall across 5, wear and debris stop scattering in a ring and run downhill in a tail, digs loaded into `dig()`'s preferred list; drop stairway from the claim, it calls neither. Small.
- **Under Cover** — a canopy overhead means no moss, mushrooms on the floor, and the light budget spent on the top course so it pools against the leaves; keep gravel, do not write podzol into an oak forest, and seat the mushrooms out of the lit panels' reach or they pop. Small.
- **The Cant** — a derelict that keels with the hill instead of refusing it, carrying `relief()`, which five other designs consume; hold the `dy == 0` course at core Y always and let only the floor travel, or `hullIntact` silently loses a ring member. Large.
- **The Tide Mark** — a slipway authored to be cut by the waterline and refused anywhere the water misses its middle third; `dig()`'s preferred path uses `surfaceNear`, which drops every submerged tread, and off an andesite tread you get gravel not sand. Template, medium.
- **The Undercut** — a rock shelter with a **BLACKSTONE** soot stain and a **COARSE_DIRT** drip line whose shape the cliff dictates; match the stain to the wall the way `ore()` matches the rock, and argue the new-type cost in its own javadoc. Medium.

### Adjacency

- **The Road Kept** — a chain of lit cairns at a fixed stride down a leg, placed by a pure `Waymarks.inChunk` in `Beamline.inChunk`'s shape; add `clearOfStructures` before anything else and sweep the 96-block stride rather than picking it. Medium.
- **The Backsight** — a second stone eight blocks behind an obelisk on its slot axis, whose ANCIENT survivor gives a broken stump its bearing back; make the notch an actual gap at slot height or it instructs you to stand where you cannot see. Small.
- **Squatters' Floor** — a broken 7x7 old floor with a new camp misaligned on it; author the floor only, at integrity 0.95, and let `Habitation.dress` supply the camp — `Rotation.getRandom` is four 90s and an `.nbt` cannot carry three ages. Template, small.
- **The Ford** — three natural bank steps either side of shallow water with slab treads and one lit panel; sweep the acceptance rate with `HeadlessRun` before dressing it, and relax the tread count before ever relaxing the never-cut-a-bank rule. Medium.
- **The Drop Stone** — a flush **POLISHED_ANDESITE** apron with a sunk barrel at every arch, unmaintained beside a monument the house rule keeps maintained; decide whether it is furniture (identical, no roll) or a ruin (rolled) — the pitch claims both. Small.
- **The Approach Furrow** — a scar off one hull face on `Sightlines.legAt().heading()`; give it a medium the path can never wear (gravel-flecked, three wide throughout) or it is a second dirt line on a different bearing in the same palette, and drop the two-wrecks-in-a-cell claim. Small.

### Smallest

- **The Wearline** — a worn dirt line with nothing at either end, bearing `Mystery.toward`; the pitch is wrong and the body is right — wearlines in a cell **converge** on the node, they do not run parallel, and the reach must come back inside the eleven-block envelope. Small.
- **The Postline** — **OAK_FENCE** posts at exactly four-block spacing perpendicular to the bearing, with `presenceInEight` applied to the elements of a series rather than to one prop; floor ANCIENT at one surviving post, and state the envelope, because 6-10 stations is 24-40 blocks. Small.
- **The Roadside Lamp** — a **COBBLESTONE** footing written flush, an **OAK_FENCE** post, a standing **LANTERN**, and at ANCIENT the lantern alone on one cobble in the grass still lit; drop the `RuinRegistry` proximity query, which is order-dependent and reads a server-thread store from a worker. Small.
- **The Felling** — a stump, chips and the trunk, in whatever wood a single 8-block scan finds, and no felling on bare plains ever; make the **STRIPPED_OAK_LOG** cut face and one chip mandatory at RECENT or it is a natural deadfall. Medium.
- **The Windbreak** — a knee-high arc of **COBBLESTONE_WALL** open on the side you walk in from, a cold fire, a log to sit on; keep the bearing-checked opening whatever else changes, it is two dressers agreeing through the lattice with zero coupling. Small.
- **The Crossing** — stepping stones where a wearline meets shallow water, with the WEATHERED gap that makes you jump; it cannot be its own placed feature, it has to be the wearline extending itself. Medium.
- **The Hearthstone** — nine **COBBLESTONE** flush with the grass and a fire in the middle, the cheapest way to hand a player a room size; build it as a trace kind, not a template — `TemplateRuinFeature` cannot deliver its three ages and reports every placement to `RuinRegistry`. Small.
- **The Rutway** — two parallel worn lines with a **BARREL** lying on its face at the end; widen the gauge so the pair resolves at eye height, and fold it into the wearline rather than shipping a name. Small.

### Light

- **The Grate Floor** — a 2x2 of **COPPER_GRATE** flush at grade over a sealed pocket holding a lit bulb: a drain at noon, a lattice of light lying flat in a dark field at night; the pocket needs its own footing-and-dry read on `floor.below(1..2)` and must refuse the site rather than backfill — `hasFooting` reads one plane and says nothing about what is under it. Small.
- **The Burn** — a tapering **SOUL_SOIL** stripe with a **MAGMA_BLOCK** at its wide end and `wear()` refused everywhere the fire went; add a slope refusal, state the stripe length inside `walk()`'s envelope, and filter the suppression after the draw. Medium.
- **The Cold Lamp** — chain-and-lantern, then chain alone, then a **SOUL_LANTERN** on the ground; put the doorway in a template and have the dressing write only into a lintel it *finds*. Medium.
- **The Vigil** — four candles on an **ANDESITE_SLAB**, lit count falling 4/2/0 while the count present holds; ANCIENT's soul soil merges with the hearth's and destroys the count, which is the design. Small.
- **The Lamp Line** — posts along the aimed path where the decay increases with distance, the far one down; argue the change of register first — `path()`'s own javadoc says a path is a direction and never a road. Medium.

---

## III. WHAT THE PANEL LEARNED

**1. Four designers wrote the same five-pit line, and the repo had already blessed it.** The Line, The Line That Stops, The Digline and The Prospect Line are one design arrived at independently, and `MYSTERIES.md` §IV had already ruled it buildable by name while rejecting everything hung off it. Convergence plus a standing ruling is as strong as this panel's evidence gets. That is why it is build 3 and why it ships stripped.

**2. The panel's real deliverable is three primitives, not thirty props.** `Habitation.settled` is private and its own javadoc pre-authorises the move — *"the day a second dresser wants it, it moves there"*. Nine designs want it. `DerelictFeature.atGrade` (line 851) carries the identical note and three designs want it. `relief()` does not exist and five terrain designs consume it. Extract `settled` in build 1, `atGrade` when the first cut design lands, and make `relief()` the deliverable of whichever terrain design goes first rather than a side effect of it.

**3. The gravity verdict is one bug with eight witnesses, and it has three addresses.** `Habitation.looseSpot` places **MOSS_CARPET** on a licence kept "for wear alone"; `RuinGround.dig` seats a `Fallable` **SUSPICIOUS_GRAVEL** on `surfaceNear`, which only asks whether the block below is air; `DerelictFeature.debris` writes **ANDESITE_FRAME_PANEL** through a raw scatter with no check at all. Eight designs re-diagnosed those three sites. Fix all three once, in build 1, and every later design inherits the fix instead of restating it. This is `GREENFIELD.md` F4 discharged properly rather than for the pot alone.

**4. Every good answer to "too dark" refused to raise a light level.** The light moves down the post (The Post). The light is under the floor (The Grate Floor). The light is the thing that grew rather than the thing that was lit (What the Dark Grew, folded into build 1). The light is *absent* along a line where fire went (The Burn). The one design that answered by doubling a budget — Under Cover — is the one whose own lit panels delete the mushrooms it just placed. The mod's answer to darkness is placement and contrast, not level.

**5. The write envelope is the collective blind spot.** `Habitation.walk` reaches eleven blocks at the far end *specifically* so a site stays inside its own chunk, and says so. The Wearline reaches ~21, the Postline 24-40, the Piers ~30, the Road Kept strides 96. Two designs out of fifty-two mentioned it. Any vehicle or new feature needs a stated envelope standing beside `hasFooting`, `isDry` and `clearOfStructures`.

**6. `clearOfStructures` is about to acquire its second logged gap.** `RuinGround.java:200-208` records `TemplateRuinFeature` never being offered that question as *"a gap rather than a decision"*. Six proposals would have added the second instance — including a 7x7 flush platform and a 512-block chain of cairns, both of which land in village squares. Standing rule from this panel: nothing new ships without it, and closing it for template ruins is its own change with a walked world behind it, not a side effect of adding a hearthstone.

**7. Nine designs picked a number and none measured one.** `SIGHTLINES.md` ran 200 seeds over 2,040,200 cells and corrected itself twice; that is the bar. Refusal rates for the Ford, the Backsight, the Piers, the Digline and the Span are all unknown, and two of those could silently never generate — the failure mode `TemplateRuinFeature`'s own logger comment names.

**8. Three of the strongest designs each shift every existing seed's dressing.** Any new draw or changed acceptance test in `dress()` moves every site in every save, because `Habitation.dress` runs after the feature body on the same `RandomSource`. It is cosmetic and acceptable at alpha. It is not acceptable to claim otherwise, which `MYSTERIES.md` §III.1 already caught [4] doing. Pay it once, in one push, and write the truth at the call site.

---

## IV. REJECTED, AND WHY — kept visible

**The Drift and The Pinhole — dropped, both, for the same reason, and it is a reason already in writing.** Both plug `ObeliskFeature.slot`. §IV of `MYSTERIES.md` kills [12] in these words: its mark *"is precisely where `ObeliskFeature.slot` bores the sighting line — written after the slot as instructed, it plugs the thing the entire lattice exists to provide."* The Drift silts the bore with **ICE** and offers "one hit clears it", which is no defence — the slot has to read at range, and at range a plugged slot is a slot that is not there. The Pinhole argues itself clear of [2] Keystone correctly and then walks into [12] without naming it, and `ObeliskFeature`'s own `MIN_BROKEN_HEIGHT` states the principle: *a stump whose sighting line has gone is a rock*. Two things survive and should be taken by whoever comes next. The Drift's **scoured windward face** is a real idea — a pile has no bare side, and that asymmetry is a shape nobody lays by hand. And the slot is two courses, `SLOT_LOW` and `SLOT_HIGH`, so a bulb at the far mouth of `SLOT_HIGH` **only**, with `SLOT_LOW` left clear, is a different proposal that does not attract this ruling. It has to be written as one. Separately, The Drift puts a second, different bearing on an object whose long axis already points along `Sightlines.legAt().heading()`; §III.3 worries about a fourth instrument saying one sentence, and two instruments on one object saying different sentences is worse.

**What the Dark Grew — reject the package, build the tenth.** The inversion is the best single sentence anyone wrote for this panel and it is now inside build 1: *light that needs a person dies, and light that does not, spreads.* The amethyst is what kills the package, and the design's own justification is the murder weapon: buds either side are supposed to make the cluster read as spreading, but amethyst does not spread — in vanilla it grows on **BUDDING_AMETHYST** and nowhere else, so a cluster in surface masonry with no budding block behind it is exactly a thing a generator put there. Supplying the budding block is worse: an unobtainable renewable amethyst source in every ancient ruin is a resource ruling no dressing pass gets to make on its own. Vines and lichen carry the whole inversion without it.

**The Bell Nobody Answers — dropped as a second post; one sentence kept.** It builds the same object as The Post — three fence up, one fence arm, something hanging under it, at the edge of the site — and two designs cannot both build that. It also invokes the §I bar and fails two of the five tests it names: at one ruin in eight there is no fifth instance for a rule to arrive at, and there is nothing to solve. It is dressing with a verb, which is fine and needs no mystery framing. What survives is the ANCIENT bell lying on its stand in the moss, still ringing — *the only thing left working at an ancient ruin is the thing whose whole purpose is calling people, and nobody comes.* That is one face the arm can be carrying, not a second structure.

**The Oxide Ladder — dropped, on a mechanism its own headline contradicts.** The ladder is bulbs at light 15 / 8 / 0 with the oxidation colour agreeing in daylight, and the bottom rung is authored as **OXIDIZED_COPPER_BULB[lit=false]**. But `WeatheringCopperBulbBlock` random-ticks and `WeatheringCopper.getNext` carries properties across, so the bare WEATHERED bulb the design deliberately leaves unwaxed creeps to an oxidized **lit** bulb at light 4. The rule a player induces by watching a bulb in their own base and the rule the world states three thousand blocks away disagree, and the design's proudest feature is the creep. It also has nowhere to live: "set INTO the masonry, never standing beside it" is unbuildable in `Habitation`, which places every prop through `spot()` -> `RuinGround.scatter` onto open ground and owns no wall, lintel or recess anywhere. Author it as a template that carries its own recess, or say which existing Java geometry gets the fitting, and fix the two rungs first.

**The Cut Flight — dropped until a siting test exists.** The image is good and the mechanism does not produce it. `StairwayFeature` carves the switchback *into* the face — `build()` cuts headroom of air per walking cell — and `underpin()` only fills where the survey found air below. Delete the lowest treads and what remains is the carved corridor with the bank still under it, one block down: the player walks up. "The first tread you can reach is five blocks over your head" needs the ground beneath the missing flight to be gone too, and nothing in the design tests for that. The `BASE_STONE_OVERWORLD` gate it borrows from `seam()` also no-ops wherever the riser is the feature's own andesite underpin, which is often. Re-propose it with the drop as a *requirement* (the `descend()`-returns-null shape The Threshold uses), and it becomes buildable.

**The Light Well — dropped as a derelict change, held against the berth.** Its silhouette argument is the best reasoning in the set: wide-and-low, narrow-and-tall, then a slot — three night reads out of one cube with no new geometry. It is aimed at the wrong object. `(0,0,0)` is the ship core; `cube()` skips that offset precisely because the core lives there and carries its own light. Look down through a missing lid-centre panel on a derelict and you are looking at the core, not down a shaft. Where it works exactly is a **berth** — `MYSTERIES.md` recommendation 1 — whose middle is air and whose `(0,-1,0)` is already written **GENERIC**. It is then a subtraction on an object that is already a subtraction. Two conditions when it comes back: settle GENERIC-versus-STYLED at `(0,-1,0)` in one place (§V.4 calls that a declared exception, not a slip-in), and consume the forced erosion draw, because `litPanels(WEATHERED)` is 2 and forcing a lid slot re-routes the budget onto ring slots in every existing save.

**The First Pair — deferred with a named prerequisite, and its arithmetic corrected.** `OctiaWorldgen.placeNearSpawn` does return the core position and does run on the server thread at world load, and that is genuinely the one place in this codebase where knowing about another landmark is legal. But it is a rider on The Road Kept and cannot exist without it. Its stride is also wrong: `SPAWN_RADII` is `{48, 64, 80, 96, 112}`, so thirds of the spawn-to-wreck line put the specimen waymarks 16 to 37 blocks apart while the wild chain is 96 — it would teach the object correctly and mis-teach the cadence, and the cadence is the entire claim the wild chain rests on. Put **one** waymark on the wild stride if the radius allows and none if it does not. And the design's stated purpose — teach a rule at hour zero with no text — runs at §I.1, *a thing that reads as a rule on first contact is a tutorial*. `placeNearSpawn`'s javadoc already calls the near wreck "the signpost", so a precedent for exactly one taught object exists. Exactly one. **That is a KEG ruling, not an implementer's call.**

**The Trace Table — deferred with a named prerequisite, and it is the largest ruling on this panel.** The vehicle is right and law-6-clean by construction: one `Feature<Trace.Config>` with the kind in the config, one array of names, no Java per trace, which is `TEMPLATE_RUINS` verbatim. Declining `RuinRegistry.report` is correct and precedented — `STAIRWAY` and `WATERSHED` already never report, and `OctiaWorldgen` keeps their kind strings private for exactly that reason. Its diagnosis of the two live gravity defects is accurate and matches the playtest. And it names a real finding nothing else did: §I.1 requires about five instances before a rule arrives unaided, and at roughly one wreck per 840 chunks nobody ever reaches five, so the lattice is currently an **uninducible rule** — the mod's central mystery is unlearnable in dirt. That is worth acting on. What it may not do is ship the number. `rarity_filter 6` plus `count 2` is one trace every three chunks against the derelict's one per 840, and §VI.2 says in writing *do not pick this by editing a constant*. A world at that density reads populated rather than abandoned, which brushes the spirit of the emptiness law even with nobody home. Before it is built: a `HeadlessRun` sweep at a stated radius, the same apparatus that produced `ROADMAP.md` §V's 5,041-chunk table; a **write envelope added to the vehicle's refusals** beside `hasFooting`, `isDry`, `clearOfStructures` and the promoted `settled`; and a check on `clearOfStructures`' live-level branch, which passes `require = true` and therefore *generates* missing chunks — fine for `/place` with a player standing in loaded terrain, not fine in a loop at this call frequency. Build the sweep this week. It gates nine designs, and it is the honest floor under every density argument in this document.

The world is the source of truth, not this document. Three of the panel's fifty-two designs were describing defects that are in the tree today, and one of them has been placing moss on nothing since the class was written.