```
ACT TWO
MILESTONE 7
OCTIA_[0.1.0.B.E.D]_project
SEEK KEG |ALL|
```

# THE FLOOR — the bottom of Neptune's Ocean, and what is holding it up

**Set down 2026-09-03**, on six words: *look at the ocean floor and begin
the project space.* This is the project space. Nothing in it is built.
It says what the floor is as of today, measured where it could be and
read where it could not, and lists what the project has to decide before
anything stands on it.

---

## I. What was looked at, and how

**Read from the files that make the world.**

- `dimension_type/sky.json`: `min_y: 0`, `height: 256`. The world ends
  at y=0. There is no y=-1.
- `noise_settings/sky.json`: `sea_level: 96`, aquifers on. Derived from
  vanilla `floating_islands` by `make-sky-noise.py`; never hand-edited.
- its surface rules: one `vertical_gradient`, deepslate, true at and
  below 0, false at and above 8. It rewrites **stone** into deepslate.
  There is no bedrock rule anywhere in the file. Vanilla's overworld has
  a `bedrock_floor` gradient; `floating_islands` never did, and this
  file inherits that absence.

**Not measured today.** The only save in this checkout, `[0_8_0] OCTIA`,
is a shell: `level.dat`, no region directory, zero chunks, seed unread.
`chunk-probe.py` cannot open it and `world-report.py` reads *nothing
generated*. The 1.21.1 jar is not present either, so `AGENTS.md` V could
not be honoured here; two claims below are marked as read from memory
for that reason.

**The last measurement of the floor** is `ISLANDS.md` §XI's, 2026-08-28,
seed 1, 289 chunks, on `FLOORCHECK_0_7_0`:

| | |
|---|---|
| fluid below y=0 | none |
| rock below y=0 | 0 of 289 chunks |
| the spawn column | water 0..95, air 96..141, stone 142..169, mast above |

That column is the floor, read from the bottom up: water at y=0, and
under the water, the end of the world.

---

## II. What the floor is

**Neptune's Ocean has no floor. It has a bottom.**

Under open water there is no bedrock, no deepslate, no gravel, no sand.
The deepslate gradient only rewrites stone, and under open water there
is no stone to rewrite: rock below y=0 measured zero, and the swell field
between 0 and 95 is water wherever it is not island. The column goes
water, water, water, y=0, nothing.

The only seabed the world has is **the underside of the islands.** Where
an island's root reaches down into the water, the gradient turns its
bottom eight rows to deepslate, and that is a floor a diver can touch. It
is a ceiling from below, not a bed. Between islands there is none.

**What that does to the code that thinks there is one.** `ROADMAP.md` VI
lets a derelict drop through air and fluid "to whatever is holding the
world up," by way of `RuinGround.descend`. Read today: `descend` answers
null when the next step down would be outside build height, and null
again when its drop budget runs out on water. A derelict over open water
in the sky world is therefore **refused**, not seated on nothing. That is
the safe answer, and it means the seabed dressing §VI left undone has no
seabed to be left on here. Vanilla's ocean ruins, cited in `ROADMAP.md`
as one find per 700 chunks, bury their sand in a floor this world does
not have; whether they generate over the bottom at all is unmeasured.

`WORLDS.md` and `ROADMAP.md` VI describe a beacon seated "on the gravel of
the seafloor." That was a save on ordinary terrain. It is not this sea.

---

## III. What it does to a swimmer

*Read from memory, not from the jar. Verify before building on it.*

Below `min_y` there are no chunk sections; the game reads the space as
air. Void damage does not begin at the boundary but sixty-four blocks
under it. So a diver who swims down through y=0 leaves the water, falls
sixty-four blocks through the dark, and then takes void damage until
dead. Keep-inventory is held on for every Octia save (`LIVES.md` I), so
what that death costs is what every death here costs: memory, not gear.

§XI said that under an ocean, falling off an island "is a swim and a
climb." That is true from above. From below, **the ocean has a trapdoor,**
and nothing in the world marks where it is. A diver has no way to tell
the last row of water from the second-to-last.

---

## IV. What the project has to decide

Nothing here is decided. Each is a door, and the owner opens them.

1. **Does the ocean get a floor, and of what?** Three shapes:
   - **A. Bedrock.** A `bedrock_floor` gradient at 0..5, the vanilla
     shape. Closes the trapdoor. Gives the sea a bottom that is not a
     place: nothing grows on bedrock and nothing buries in it.
   - **B. A bed.** Gravel or sand over bedrock, or over nothing, so ocean
     ruins have somewhere to bury and a derelict has somewhere to seat.
     Turns the bottom into terrain, which §I of `ISLANDS.md` has to be
     re-asked about: a seabed you can walk is *normal terrain* with water
     on it, and the scarcity contract was met by there being none.
   - **C. No floor, and say so.** Keep the trapdoor as the ocean's own
     danger, the way the void was the sky's. Then the tooltip and the
     world must *mark* it: a diver deserves to know the bottom is not a
     bottom before the last row.
2. **Where the void squid goes.** `ISLANDS.md` §XII: its band, -54 to
   -10, no longer exists. Under the sea there is nothing to drift in.
   Inside the sea, a void squid is a squid. Either it is re-cut into the
   water, or into the sixty-four rows of dark below the bottom, which is
   the only open void this world has left, and where no player survives
   to see it.
3. **The derelict at the bottom.** `descend` refuses open water today.
   If A or B is chosen, it will stop refusing, and the seabed dressing
   `ROADMAP.md` VI left undone becomes owed. Whichever way, a gametest
   should hold what `descend` does at `min_y`, because today that is
   read from the source and not proven.
4. **The pattern worlds.** `make-sky-noise.py` gives every pattern world
   `sea_level: -64` and no aquifers, with its reason written in: *until
   that has a floor, a pattern world does not get water.* If this project
   lands a floor, that constraint lifts, and the tool should be the one to
   say so.
5. **Measure first.** Before any of the above is chosen, generate a
   floored world here, probe the column under open water, and record what
   y=0 is and what `OCEAN_FLOOR` answers over it. §X and §XI were both
   settled by a probe verdict and not by an argument, and this one
   should be too.

---

## V. The law of this project space

- **Measure before deciding.** A rule for the floor is chosen against a
  probe verdict, in this file, dated.
- **Regenerate, never hand-edit.** A floor lands as a flag on
  `make-sky-noise.py` and a regenerated `sky.json`, the way every other
  change to that file has.
- **The trapdoor is stated until it is closed.** However long §IV.1
  stays open, §III stays true, and no document downstream may describe
  the sea as safe from below.
- **Corrections are new entries.** What is wrong here is struck and
  dated, never deleted.

`[2026-09-03]`
