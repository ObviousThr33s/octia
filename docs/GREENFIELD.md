# GREENFIELD.md — the traversal loop, the water, and the small dues

Set down 2026-08-24, from the 8/23 playtest of `[0_6_7]` and a nine-agent design
panel run the same night. The owner named it: *"this is THE greenfield project."*
Nothing below replaces an Octia system, because none of it exists yet — the elytra
was vanilla creative, the waterfalls are vanilla `spring_feature`, and there is no
traversal code in the mod at all. Every feature here is a first answer.

The panel's full deliberation (four traversal advocates, two watershed designs,
one weighing) is preserved in the session record. The verdicts are binding for
this push; the dials marked **owner** are not.

---

## I. Why the traversal shape is a save-safety decision

The 8/23 session elytra'd origin → X=2224 at ~33 m/s. Loaded server chunks climbed
monotonically to 2,690 and never fell — fast horizontal flight outruns the chunk
unloader — and server MSPT hit 70 ms mid-flight against a 50 ms budget. That
backlog is what stalled the autosave ("world is having trouble saving"). No data
was lost, but the lesson is structural:

**No mechanic in this mod may enable sustained fast horizontal travel.** Descent
is free; crossing 2 km in a minute is forbidden. This is a hard constraint on
every feature below, not a tuning preference.

---

## II. The features, with deliverables

### F1 — The sail-rig (descent-only glide) · the panel's traversal pick

A hand-made andesite-ribbed sail-frame. Deploy while airborne: glide forward
along the look vector, always sinking, never gaining net altitude, horizontal
speed hard-capped near boat pace. The void becomes a medium you commit to —
one-way, memory-not-gear (keep-inventory already prices a mis-glide).

Deliverables:
- `com.serenity.octia.traverse.SailRigItem` — item + the two per-tick clamps
  (server-applied): `HARD_CAP` horizontal (provisional **9.0 blocks/s**, marked
  owner-tunable), and a no-net-climb clamp (vertical velocity never nets
  positive while deployed). Charges/flap-hover do not exist; height is regained
  by stairs (F2), never by the rig.
- Registration in `OctiaItems` (the `BINDLE` template), recipe JSON (andesite
  frame panels + string/leather register), item model + lang entries.
- `SailRigGameTest` — asserts the two invariants headlessly: dive-chaining never
  exceeds `HARD_CAP` sustained; net altitude never increases over N ticks of any
  input pattern. On the `fabric-gametest` list in `fabric.mod.json` (a test not
  on the list does not run — standing rule).

### F2 — Switchback stairs (the ascent half) · grafted from the runner-up

Worn andesite-clad switchbacks cut into terrace risers and void-edge cliffs —
"history in the ground" that is also the way back up after a glide. Local
switchbacks only (per-chunk, terrain-blind worldgen cannot promise a continent
road — that scope is an owner call, deferred). Biased toward Sightlines legs as
a corridor *weight*, never a gate, so stairs and glides follow the same threads.

Deliverables:
- `com.serenity.octia.world.StairwayFeature` — one-site-one-chunk (the Beamline
  rule), footing-verified via `RuinGround`, steps rest on solid, never float,
  never write outside the safe window.
- `configured_feature/stairway.json` + `placed_feature/stairway.json`
  (rarity in the placed JSON), registered in the `OctiaWorldgen.bootstrap()`
  schedule loop.
- `StairwayGameTest` — steps have footing; a placed stairway climbs monotonically;
  a void-edge landing is solid. On the gametest list.

### F3 — The watershed (hybrid) · seeded soul, painted-static body

Water as a second reading of the same lattice the obelisks sight down. Springs
are born ON Sightlines nodes (same seed, same splitmix64 mixer, new
`SPRING_SALT`), but only where the node is the uphill end of its own leg. The
runnel traces strictly downhill along the leg's `heading()`, built entirely from
the single owning chunk. Every pool is a sealed, rimmed bowl of flag-2 still
source water — costs nothing to tick, cannot spread, cannot spill. At a void
edge the water terminates in a lip-basin: held at the edge of nothing, a still
mirror. The terminal basin is `Mystery.ARRIVED` made of water.

Deliverables:
- `com.serenity.octia.world.Watershed` — pure function: `springAt(seed, cell)`,
  uphill gate, bounded trace (`MAX_FALL_LEGS` provisional 4). Deterministic,
  testable without a world, like `Sightlines`/`Mystery`.
- `com.serenity.octia.world.WatershedFeature` — the carve: per-save gate first
  line (the `ObeliskFeature:154` idiom), `RuinGround` helpers throughout
  (`surfaceNear`, `descend`, `hasFooting`, `isDry`, `clearOfStructures`,
  `put` flag 2), sealed rims (andesite register), never a source adjacent to an
  open island edge.
- `configured_feature/watershed.json` + `placed_feature/watershed.json`
  (rarity provisional ~800 — rarer than the obelisk's 520; one spring is a
  whole watershed), registered in the schedule loop.
- `WatershedGameTest` — pure determinism (same seed, same answer); in-world:
  every pool sealed (no fluid updates escape), rim solid, no source at an edge.
  On the gametest list.
- Known coexistence: vanilla pool-less trickles remain until the owner decides
  on suppressing `spring_feature` (needs a noise-settings override — **owner**,
  out of scope here).

### F4 — The floating pot (bug, from the playtest)

`23.43.26`, `264 193 22`: a decorated pot on a grass tuft. `Habitation.dress`
places on replaceable support.

Deliverables: a sturdy-face support check in `Habitation.dress` before any pot
(and any other gravity-implausible dressing); one new `HabitationGameTest`
method: *a pot never floats*.

### F5 — The CALLED readout (the "what is this pointing too" due)

The core's right-click status says a dig is calling but not from where — the
player asked for *"orientation and direction and magnitude???"* and clicked 26
times in 14 seconds getting the same three lines and a beacon chime each time.

Deliverables: in `ShipCoreBlock` — a bearing + distance line for the CALLED
state ("calling from NE, 41 paces" register, derived from the dig the radius
found); a per-player cooldown (provisional 60 ticks) on re-printing and on the
sound. `ShipGameTest` addition: repeated use inside the cooldown emits once;
the bearing names the dig's actual direction.

### F6 — HEV suit render (blocking only, this push)

Owner's report, verbatim: *"shows the player face too much, z-fighting, and
doesnt show on arms."* Client render work is verified by eye, not headlessly —
this push delivers the blocking doc (root-cause per defect: model inflation
offsets, layer geometry, arm feature-renderer coverage) and no code.

### F7 — Portal indicator (blocking only, this push)

*"i dont feel any different after portaling... needs indicator... fresh cool
indicator."* Also recorded: the 8/23 portal trip generated no DIM-1/DIM1 region
data at all — dimensions exist as `raids.dat` stubs. Blocking doc covers both
the cue (arrival grade: sound + brief overlay in the hull register) and the
missing-generation question.

### Queued, explicitly not this push

- **Plumbline** — plan Phase 4; wants the terrain decision's dust settled.
- **Civilization-evidence generator** — ruled (anonymous → partial → named
  attribution decay); large surface, own push.
- **Per-seed ocean↔sky spectrum** — plan Phase 2.2, wants the slice strip first.

---

## III. The dials that stay the owner's

1. The `HARD_CAP` number (9.0 provisional; the band is sprint-to-boat, walked
   and felt, not computed).
2. Stair network scope: local switchbacks shipped; a continent road is a
   different risk appetite.
3. Void lethality for the glide: soft-landing kept or not.
4. Vanilla `spring_feature` suppression.
5. Watershed density and the uphill-gate thresholds.
6. Whether the sail-rig, stairs, and basins hold the austere-andesite register —
   an art call made by looking.

---

## IV. Push mechanics (for the agents laying this down)

- Read `ObeliskFeature`, `BeamlineDerelictFeature`, `RuinGround`, `Sightlines`,
  `OctiaItems` before writing a line. Match the idiom and the comment voice —
  reasons-first javadoc, plain sentences, ASCII throughout.
- New files are owned by their feature. Shared files (`OctiaItems`,
  `OctiaWorldgen`, `Octia`, `fabric.mod.json`, `en_us.json`) are touched only
  by the integration pass, from manifests.
- Provisional constants carry the comment: `provisional - owner tunes by
  walking the world`.
- Gametests: assert the decision, not the particles; never hard-code a heading;
  plot floors cap reach (a generous floor writes into the next test).
- The gate: `tools\verify.ps1` green — build + full suite, 73 gametests + unit
  tests at last count, plus every new class on the `fabric-gametest` list.
