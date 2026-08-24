```
ACT TWO
MILESTONE 3
OCTIA_[0.2.0.M.Y.S.T]_spec
SEEK KEG |ALL|
```

# MYSTERIES â€” what the world knows and will not say

**Set down 2026-08-23, on branch `sky-islands`.** Fifteen proposals from five designers, three
independent judges, and one thing none of them knew. A recommendation, not an implementation.
Nothing in section III is built.

The flags spell `MYST` on purpose, in the same spirit as `S.A.F.E`. If that reads as cute, it is
also the shortest available statement of what this milestone is for.

---

## 0. The tree was asked first, and it answered

Before writing a word of this I did what `ISLANDS.md` Â§VIII records not being done and paid for:
I looked at the working tree instead of at the summary of it.

```
?? src/main/java/com/serenity/octia/world/Mystery.java
?? src/test/java/com/serenity/octia/world/MysteryTest.java
```

Untracked at the moment this was read, and committed as `137ad78` before this file landed - the
two-question-mark status above is preserved because it is the evidence, not the state. Read by
nothing in `src/main` either way, which is the part that still holds. `Mystery.toward(seed, x, z)` is a pure
function of seed and position that returns one of eight ring offsets pointing at the cell's node,
or **null** when the position is within `ARRIVED = 24` blocks of the node itself. Its javadoc
already carries the whole design argument â€” why eight and not degrees, why the cell boundary is
structure rather than a bug, why the first marked thing must teach nothing.

Two of the fifteen proposals ([1] Keel and [13] The Bearing) each proposed writing that class from
scratch, under different names, with the same convention and the same intent, and neither knew it
existed. One judge marked Keel down for a degenerate-bearing gap â€” `atan2(0, -0)` silently
answering north â€” that `Mystery` had already closed with `null`, and closed better than either
proposal did, because null-at-the-node is a *discovery* rather than a special case.

That is the finding this document opens with, because it changes the ordering below and because
the failure mode is instructive twice over: the docs did not know, and neither did five designers
reading the same repo.

---

## I. What a mystery is in Octia

`ISLANDS.md` Â§I states the Skyblock bar and then holds the island work to it. This is the same move
for this milestone.

**A mystery in Octia is a true fact about the world that the world states without saying it, that a
player can induce by walking and test with their own hands, and that gets larger rather than
smaller when they solve it.**

Five tests. All five, or it is not one.

1. **The first instance teaches nothing.** One lit panel is decoration. Two is a coincidence. The
   rule arrives somewhere around the fifth, unaided. `Mystery`'s javadoc already says this and it
   is the bar, not a nicety â€” a thing that reads as a rule on first contact is a tutorial.
2. **It is inducible from the world alone.** No wiki, no legend block, no menu, no lang key. The
   ring of eight panels teaches itself because you can see all eight and see the light change; a
   mystery has to teach itself the same way. `ISLANDS.md` Â§VI: *a mechanic that needs a wiki has
   failed.*
3. **The player can test it, cheaply and reversibly, with their own hands.** Break a panel and put
   it back. Put blocks under and pull them out. The test must cost seconds, not an afternoon, and
   the result must be unambiguous.
4. **Solving it opens rather than closes.** The answer a player can reach is a *shape*; the
   question the object poses is untouched by it. `EMPERORS.traj`: *`?` is not a gap, it is a
   finding.*
5. **It is deterministic and reproducible.** Same seed, same answer, on any machine, forever. A
   mystery that is random is a bug report waiting to be filed.

### What is merely scenery, so nobody builds it by accident

- **A variant nobody can resolve.** One obelisk in six with a different mark, where the rule is
  unlearnable, does not add mystery â€” it degrades the signal the player *can* read. `SIGHTLINES.md`
  Â§IV measured that channel's lift already down near the noise floor. An inconsistency you cannot
  resolve is litter, and `ROADMAP.md` Â§V paid the litter bill once already.
- **A fact you can only confirm by surveying.** `TRAJECTORY.traj` Â§XIV limit 2 is in writing:
  *"You cannot see a ring. Two kilometres around is not a thing a person standing in a field
  perceives, ever... any design that needs the player to feel the ring from the ground is a design
  that will not work."* Three of the fifteen proposals were exactly that design. See Â§IV.
- **A missing effect with no second beat.** Absence is this mod's native voice, but an absence has
  to be *counted against something the player has been shown three times*, and it needs a second,
  positive signal or it is indistinguishable from a bug. Silence where a chime should be, with
  nothing else changed, is a support ticket.
- **A number in a chat line.** The readout is the mod's only prose and it should stay three lines.
  Arithmetic on a `DARK_GRAY` string is not a mystery; it is an audit, and the player cannot audit
  it because right-clicking any wild wreck moves the number.
- **An alphabet the mod does not own.** Vanilla's pottery sherds already mean something to the
  player. Borrowing four of them and assigning private meanings makes a cipher, and a cipher needs
  a key, and a key is a wiki.

---

## II. What this milestone answers, stated loudly

Three open questions in this repo get answered below. Naming them here rather than burying them in
the recommendations, because this is the highest-value paragraph in the document.

> **`ISLANDS.md` Â§VI â€” "what do i do when I get to the hull"**
>
> **Answered by recommendation 1.** Today arrival is a status change. The berth makes it an event
> the player *causes*, on a hull they did not build, using `FirstLight` which already ships. No new
> systems. It is a subtraction: one block not written.

> **`ISLANDS.md` Â§VII.2 â€” "does `hullIntact` mean anything in a void?"**
>
> **Answered by recommendation 2**, and answered the way this repo answers things: with the
> gametest that file says is one line, plus a measurement. The answer is *yes, and that is the
> problem* â€” `ShipCoreBlock.hullIntact` reads eight horizontal neighbours and nothing else, so a
> hull moors over open void exactly as the rule says it should.

> **`ISLANDS.md` Â§VII.3 / Â§IX â€” "what is scarce?"**
>
> **Half-answered by recommendation 2, and deliberately not fully.** Anchorage is ground under a
> hull, said by dropping light through the hole where the ground should be. That is perception, not
> physics. The full answer â€” rationing berths â€” is [14] and it is **deferred with a named
> prerequisite**, not rejected. See Â§IV.

Recommendation 3 additionally gives `Mystery.java` its first caller, which is the same debt
`Ring.java` is still carrying: *"Nothing reads this yet. It is measurement, not mechanism."*

---

## III. Build these three, in this order

### 1. THE BERTH â€” a wreck with everything except the ship

From [4], with the ending of [10] Foundered grafted in and the good half of [5] The Third Mooring
folded into an open question rather than into the code.

**What the player sees.** By the fourth wreck they know the shape absolutely: a sunken cube of
andesite frame panel, its top course weathered open in patches, a block glowing in the middle with
motes falling inward. This one is the same cube, on the same ground, with the same suspicious
gravel ringed round it and the same cold campfire beside it. From forty blocks off at night it is a
dark cube among lit ones. Break in: the middle is air. Eight panels stand around the hole,
unbroken. Drop into it and the block under your boots is glowing â€” dimmer than a core, the light
that is supposed to be buried â€” lighting the socket from the floor instead of from the middle.

**The discovery chain.**

1. The guaranteed near-spawn wreck. Right-click the core, get three lines, learn what a hull is.
   That tutorial already exists and costs nothing.
2. A wild wreck. Same cube, same light in the middle. Second data point, unremarked.
3. An `ANCIENT` wreck: lid gone entirely, sunk a course deeper, ring and floor intact. The world has
   now shown them, without a word, that **the ring is the part that survives**.
4. The berth. The tell is at range and at night: every other wreck glows, this one does not.
5. They break in. No core, and eight whole ring panels in the one course they have just learned
   never weathers. So it did not fall out and it did not erode.
6. They stand in the socket and the floor is lit. They have seen that light once before, by
   accident, where a slope had fallen away from a hull.
7. Nobody instructs them. `ship_core` is `PPP / PEP / PBP` â€” eight panels around an ender pearl over
   a brush â€” and the panels and the brush-fodder came out of the digs this berth put in the ground
   around itself. The ender pearl is the good part: a berth found in hour two is somewhere you come
   *back* to. The world hands them a debt, not a chore.
8. They seat a core. `onPlace` runs, `survey` finds eight panels and a dig within six,
   `ShipMoorings.moor` returns true because the position is new, and **`FirstLight` fires** â€”
   `BEACON_ACTIVATE` and twenty-four `END_ROD` motes over a hull nobody had moored. First arrival
   event in this mod that a player caused.

**The mechanism.** `DerelictFeature.build` writes twenty-six panels, then debris, then digs, then
one core last. A berth is that pipeline with the core write skipped and the buried middle panel at
`(0, -1, 0)` overwritten to `PanelLight.GENERIC` afterwards. `cube()` is not touched, so its
`RandomSource` consumption is unchanged and the `dy == 0` erosion exemption holds by the same code
that makes every other ring complete. It reports to `RuinRegistry` under a new kind, not under
`DERELICT`, so nothing counts it as a wreck.

**Correction to the proposal, and it matters.** [4] claims that taking the new draw immediately
before the core write leaves every existing seed untouched. It does not. `Habitation.dress` runs
*after* the core write at `DerelictFeature.java:294` and consumes the same `RandomSource`, so every
wreck's dressing shifts in every existing save. That is cosmetic and acceptable at alpha, but the
proposal asserts otherwise and a reviewer will believe it. Write the truth in the comment.

**Existing machinery it rides on.** `RuinGround.scatter` rejects any offset with
`abs(dx) < 2 && abs(dz) < 2` (`Habitation.SPREAD_MIN = 2`), so `(0,0)` and all eight ring slots are
already unreachable by `Habitation.spot` â€” the coreless socket needs no new safety. I checked this
rather than assumed it. `RuinRegistry.report` only appends to a `ConcurrentLinkedQueue` drained on
`END_SERVER_TICK`, so it is legal from a generation worker. `FirstLight` already guards itself out
of worldgen by requiring a `ServerLevel`.

**Files.** `DerelictFeature.java`: one constant, one boolean parameter, a branch around the single
core write, one extra `RuinGround.put`. `OctiaWorldgen.java`: one public constant `BERTH = "berth"`
beside `DERELICT` and `OBELISK` (public, because unlike `ARCH` this kind is genuinely reported and
genuinely queried). `seat()` must pass the flag **false** so the guaranteed spawn wreck is never a
berth â€” a player whose first wreck is a berth has met the anomaly before the rule.

**The @GameTest.** `DerelictGameTest` is already on the `fabric-gametest` entrypoint list, so this
needs no `fabric.mod.json` edit. Two methods: `aBerthHasNoCoreAndALitFloor` (ring is eight panels,
middle is air, `(0,-1,0)` is `GENERIC`) and `aBerthMoorsWhenYouSeatACore` (place `SHIP_CORE`, assert
`survey` answers `MOORED` or `CALLED` and `ShipMoorings.isMoored` is true). Assert the decision, not
the particles â€” the gates are headless, per the `EraEcho`/`FirstLight` precedent. `seat()`'s
exclusion also wants its own assertion, which drags in the gametest `ROADMAP.md` Â§VII records the
near-spawn derelict as never having had.

**Why it earns first place.** It is the only proposal that answers `ISLANDS.md` Â§VI. It is a
subtraction, so it adds no visual noise, no store, no tick, no block, no sound. It is tellable in
one sentence â€” *some wrecks are missing their core and you can finish them* â€” and the mechanical
read works even for a player who thinks it is an unfinished wreck rather than a removal, which is
what a self-teaching object looks like.

**What [10] Foundered contributes, and what it does not.** Foundered is structurally the same verb â€”
find an absence, ring a core, the world goes quiet â€” with the object removed and an invisible hum
in its place. It is superseded. But its *ending* is worth taking: a berth position should stay in
`RuinRegistry` after it is moored, because it is still a place where a ship failed to arrive, and
the ratio of berths-found to berths-finished is the shape of a save. Keep the record; prune
nothing.

---

### 2. PLUMBLINE â€” a hull moored to nothing, saying so

From [8], unchanged in substance. Top of the scoreboard on all three judges.

**What the player sees.** On a sky world you run out of island. You bridge to a spur, or lay a
platform into the dark, and build a hull on it, because a hull is eight panels and a core and the
ground is not part of the rule. It moors. It chimes. Then motes start coming off the *underside* â€”
falling, out of the bottom of the block you are standing on, past the edge of your platform, into
the part of the world where there is nothing. And a tone from below.

**The discovery chain.**

1. Every hull on ordinary ground behaves the same. Nothing suggests ground is part of anything.
2. The sky preset makes you build off an edge within an hour. Everyone does this.
3. The hull chimes normally. Nothing is refused and nothing warns you. Then the motes fall â€” and
   *falling is the tell*, because every mote this mod has ever drawn goes up (`FirstLight` rises off
   a core, `EraEcho` stands in a column) or inward (`Luminaries` fall onto a lit block). A direction
   the player has never seen the mod use registers before it is understood.
4. The comparison is free: they own an older hull on solid ground. It does not leak.
5. The test is theirs, instant and reversible. Blocks under: it stops. Blocks out: it starts.
6. The cruel, excellent step, which they will take because players dig: mine a shaft under a hull
   that has stood for a week. It starts leaking. *They* did that.

**The mechanism.** Nothing is vetoed, nothing refused, no status added. A fourth `ShipStatus` is off
the table and the reason is written in the enum itself: `isMoored()` is defined by exclusion, and
*"it is also the line a fourth status inherits silently."* Instead, a sibling of `EraEcho` on the
same 40-tick interval asks one question per nearby core: how many air-or-fluid blocks hang under
the hull floor, capped. On ordinary terrain that is one block read and an exit; over void it is the
cap. Where it is the cap, motes are drawn downward and the tone comes from below.

**Files.** `ship/Anchorage.java` â€” `hollowBelow(BlockGetter, BlockPos core, int max)`, the decision
and only the decision, with `REACH` carrying its reason: it is a perception threshold, not a physics
one. Deliberately does **not** reuse `RuinGround.hasFooting`, which is package-private, asks about a
footprint plane, and is a worldgen siting question. `world/Plumbline.java` â€” the effect, copying
`EraEcho`'s shape exactly: `END_SERVER_TICK`, interval guard, online-player loop, squared flat
distance against `ShipMoorings.positions()`, `hasChunk` before touching anything, build-height
guard. Borrowed vocabulary only: `END_ROD` and `BEACON_AMBIENT`. One `Plumbline.bootstrap()` line in
`Octia.java`.

**The @GameTest.** New `gametest/AnchorageGameTest.java`, **added to the `fabric-gametest` list in
`fabric.mod.json`** â€” a class not on that list is not tested, and I checked the current eighteen
entries. Methods: `aHullMoorsOverNothing` (this *is* the one-line gametest `ISLANDS.md` Â§VII.2 asks
for, and it pins the behaviour rather than changing it), `hollowBelowIsZeroOverGround`,
`hollowBelowSaturatesOverVoid`, and the causable one â€” ground removed under a standing hull flips
the answer. Plot floors cap at five or six blocks of reach; `helper.setBlock` does not bounds-check
and a generous floor writes into the next test.

**Then edit `docs/ISLANDS.md`** â€” a new dated section, appended not edited, closing Â§VII.2 with the
measurement and stating plainly that Â§VII.3 is *started* and not answered.

**Why it earns second place.** It is the tightest cause-and-effect loop in the set and uses no text
at all. It closes a logged open item on its own. And it refuses to resolve its own contradiction:
the store still says MOORED, the readout still says MOORED, the count still counts it. The mod
disagrees with itself in front of the player and declines to pick a side. The residue is a habit â€”
every hull they build afterwards gets a glance downward â€” and a habit is what a mystery leaves
behind when it has actually taught something.

**The live risk, named.** The moment it reads as a penalty, the mod has grown a warning system.
Keep it quiet, keep it rare, never refuse the mooring, never add a status, never put text on it. If
a playtest reports it as *the game telling me off*, the volume is wrong, not the idea.

---

### 3. THE MARK â€” one lit panel of eight, on a wreck's floor and on your own hull

**[1] Keel and [13] The Bearing are the same mystery.** Both quantise a bearing to the node into one
of eight ring slots and light that slot. Keel writes it into a derelict's buried course at
generation; Bearing writes it into the player's own ring at first light. They were scored
separately and should be built together, because **the pair is the fix for the objection both of
them attract on their own.**

Both judges who marked them down said the same thing: a 45-degree octant aimed at an invisible node
up to ~360 blocks away is a wedge hundreds of blocks wide at the far end. That is true of one mark.
It is not true of two. `Mystery.java`'s javadoc already states the answer:

> Two wrecks in one cell point at the same place, so their marks *cross*, and a player who has
> noticed that much can walk to the intersection without ever being told there is anything there.

**What the player sees.** A wreck's floor comes up while they are stripping it for panels â€” the
recipe wants eight and the dig table is the heaviest source â€” and most of the buried course is dark,
one or two glow dimly (which has been true for months and always read as weathering), and exactly
one is bright. Later, they place the eighth panel of their own hull, the core lights, the chime
goes, and when they step back **one of their eight panels is glowing**, wearing the `Luminaries`
halo, and they placed every one of them dark themselves.

**The discovery chain.** The hull half is the hook, because the change happens to *their* build:
misclick â†’ cycle it back to dark â†’ it stays dark â†’ weeks later a panel replaced in the same ring
lights again â†’ build a second hull six hundred blocks out and a *different* slot lights. That last
move teaches *this is a fact about the ground, not about your ship* in one step with no words. The
wreck half is the confirmation: two marked objects in one cell agreeing, and the walk to where their
lines cross. And the exception is already built in â€” `Mystery.toward` returns null within
`ARRIVED = 24` of the node, so **a wreck seated on a waypoint has no mark at all**, and the
exception discovers the node rather than reading as a bug.

**The mechanism, most of which is already written.** `Mystery.toward(seed, x, z)` exists, is pure,
has no Minecraft imports, and is covered by `MysteryTest`. Nobody writes `Keel.java` and nobody
writes `Bearing.java`. Two call sites:

- `DerelictFeature.cube()`: after the existing loop, not inside it, write `PanelLight.STYLED` at
  `core.offset(mark.dx(), -1, mark.dz())`. After, so the 1-in-8 buried draw cannot overwrite it,
  and derived from `Mystery` rather than from the `RandomSource` so no existing seed's wreck moves.
  The existing 1-in-8 `GENERIC` panels become the padding, and the padding is authored rather than
  random, which is what `TRAJECTORY.traj` Â§X is emphatic about.
- `FirstLight.moored()`: it already holds the `ServerLevel`, and `getSeed()` is on it. Write
  `GENERIC` into the ring slot `Mystery` names, **only if that slot is currently `NONE`**, and never
  again. The mark is a mark, not an assertion; a player can cycle it off and the ship will not fight
  them.

**The ordering that makes it safe, and it must be commented where it happens.** Writing a panel runs
`LevelChunk.setBlockState`, which fires `AndesiteFramePanelBlock.onPlace` server-side regardless of
flag, which calls `reconcileAdjacentCores` â†’ `reconcile` â†’ `moor()`. That terminates *only* because
`reconcile` calls `moor()` at line 203 before `FirstLight.moored` at line 204, so the position is
already in the set when the panel lands. Move the `FirstLight` call above the `moor` call and this
recurses. [13] found this independently and stated it correctly; I confirmed it in the file.

**Two gaps neither proposal named, both decisions rather than bugs.** `cube()` is also called from
`stamp()`, so `TemplateRuinFeature`'s waystation hulls would carry a mark too â€” decide that out
loud. And a wild derelict woken by right-click takes a hull mark inside a buried ring where nobody
will ever see it, which is harmless and is exactly what the existing 1-in-8 buried light already
does.

**The @GameTest.** `DerelictGameTest` (already listed) gains one method asserting exactly one
`STYLED` panel in the `dy == -1` ring, at the slot `Mystery` computes from the leg read **out of the
world seed at runtime** â€” never a hard-coded heading, because the gametest seed changes every run,
which is the discipline every method in `ObeliskGameTest` already follows. A new
`gametest/MarkGameTest.java`, **added to `fabric.mod.json`**, builds a ring at a known position and
asserts exactly one `GENERIC` ring slot after first light. `MysteryTest` needs one addition: a
measured sweep of how far a mark's 45-degree wedge actually is from the node it points at, across N
seeds, because that number is the honest strength of the promise and nobody has it.

**Why it earns third place and not first.** It is the fourth instrument saying a sentence the
obelisk slot, the stripe and the arch already say, and `SIGHTLINES.md` Â§IV measured that channel's
lift as weak. It also walks a player toward a node the arch may have refused â€” and that refusal rate
is unmeasured. **Both of those are gated on the same sweep** (Â§VI.1). If the refusal rate is high,
build the reporting instrument from [6] first, then this.

---

## IV. Rejected, and why â€” kept visible

This repo keeps its mistakes. It should keep its rejections too, or the next hand proposes them
again.

**[2] Keystone, [7] Spoken For, [12] The hand on the near face â€” dropped, all three for the same
reason.** Every one requires the player to perceive a closed circuit from the ground. `TRAJECTORY`
Â§XIV limit 2 rules that out in writing. [2] quotes limit 1 from the same numbered list and does not
mention limit 2. Additional independent kills: [2] lights the voussoirs to `STYLED`, producing
exactly the smeared all-round glow `SIGHTLINES.md` Â§VI says stops the arch reading at range. [7]'s
headline scene â€” the readout saying 41 when you built 12 â€” does not follow from its own mechanism,
because claimed berths are arithmetic and never enter `ShipMoorings`, so `count()` never moves; it
also silences `FirstLight` on the exact flat ground beside an arch where players build. [12]'s mark
sits at `c = 0`, `y` 2â€“3 on the arriving end face, which is precisely where `ObeliskFeature.slot`
bores the sighting line â€” written after the slot as instructed, it plugs the thing the entire
lattice exists to provide.

**[3] Sherds â€” dropped.** Its jar homework was right and its catch about `digSiteInRange` counting
`Blocks.DECORATED_POT` is real and worth keeping. But `Sightlines.CORRIDOR` is **128**, not the 32
it was handed, and `SIGHTLINES.md` Â§IV measures 50.47% of ground inside it â€” so `HOWL` would be on
roughly half of all pots and `HEARTBREAK`, the mark meaning *nothing points at this place*, would be
rare. Its own stated fear was already answered in the file it cited. Beneath that: the marks are
vanilla's, the mod cannot redefine them, and a cipher needs a key.

**[5] The Third Mooring â€” held, not dropped.** The idea it is protecting is excellent and belongs
somewhere: *an anchorage you can occupy but never gain*. The delivery is not â€” the hook is
arithmetic on the mod's only chat line, it fires once per save at one position so it can teach no
rule, and it has a hole nobody named: `ShipCoreBlock.onRemove` unmoors on core removal, so ringing a
core at the seeded node and then breaking it **permanently deletes the seeded fact from that save**.
The mystery is destructible by an ordinary action. Its good half goes into Â§V.4 as an open question
against the berth.

**[6] The Unmarked Node â€” drop the mystery, build the instrument.** Reporting arch refusals to
`RuinRegistry` closes the refusal rate that `SIGHTLINES.md` Â§VII.1 and `ROADMAP.md` Â§XII both log as
unmeasured, and it is the gate on recommendation 3. Build it this week as instrumentation. The
mystery half is not observable: one wayfarer look in forty, at night, against a prior that needs
several 512-block-spaced nodes walked to form. Two corrections for whoever builds the instrument:
`ArchGameTest` is **not** on the `fabric-gametest` entrypoint list (verified against all eighteen
entries), and `RuinRegistry.count()` is total across kinds â€” `of(kind).size()` is the per-kind
method the measurement plan actually needs.

**[9] The Answering and [15] The Mast Counts â€” deferred, and they are siblings.** Both make the same
underlying number perceptible; they are two presentations of one fact, not two discoveries. Do not
ship both. [9] additionally has a constant problem a judge settled by reading the jar:
`SoundEvent.getRange` returns a flat `16.0f` for volume â‰¤ 1, so a quiet tone carries sixteen blocks
â€” inside one base â€” and reaching the guaranteed spawn wreck at 48â€“112 blocks needs volume 3 to 7,
which is not "a faint tone at the edge of hearing". The design must choose between *quiet* and
*carries*. [15] has a coupling it missed: `MastLight.set` would fire from `FirstLight.moored`, which
fires in a good half of the gametest suite, making the mast's state a function of test execution
order. `ROADMAP.md` Â§XIII already holds two more answers to this same question; minute whichever
survives as superseding the rest.

**[11] Worked over â€” reject the package, build one tenth of it.** The single best beat in all
fifteen is in here: five dig pits in a straight line at even spacing, already emptied, with one left
unbrushed at the far end where somebody stopped. Nothing in Minecraft is in a line unless a person
put it there, no text is needed, and the reward goes to reading the pattern rather than to walking
further. That is a small, pure, testable mechanic â€” `RuinGround.line()` beside `dig()` â€” worth
building alone. Everything hung off it (the mod's first `DataComponentType`, an `inventoryTick` in a
mod whose pillar is that nothing of Octia's ticks, a shared name `Cast`, a UUID-keyed store that
exists nowhere, and a payoff only a local model can deliver and no gate can assert) is four features
in a trench coat. And its own objection is correct: a seeded name on a bag is a generator inventing
an inhabitant, which `LIVES.md` Â§V legislates against. **That is a KEG ruling, not an implementer's
call.**

**[14] The Berth (rationing) â€” deferred with a prerequisite, and it should be first in the queue
afterwards.** It is the only real answer anyone gave to `ISLANDS.md` Â§VII.3, and its teaching moment
is the best in the set: break one panel out of hull A and hull B lights instantly, back and forth as
fast as you can click, no text anywhere. It is not built now for three reasons, all of them costs
rather than taste. The gametest suite shares one world and one save-wide moorings store with plots
thirteen blocks apart, so any `BERTH` over about twelve makes five existing classes interfere
order-dependently â€” green today, red on a reorder, with nothing pointing at the cause. That is
silent gate rot, the worst failure mode this repo has. A wild derelict claims a berth permanently
the moment somebody right-clicks it, at a position they never chose, releasable only by vandalising
a stranger's hull â€” and the clean fix needs the per-player store `LIVES.md` Â§VI.2 records as absent
from the entire tree. And it makes the blockstate lie: `ADRIFT` while `hullIntact` reads true.
**Build the UUID-keyed slice first, then this.** Note also that it shares a name with [4]; two
proposals called The Berth is itself a problem, and [4] holds the name here.

---

## V. Open, and refused rather than decided silently

1. **Does a berth carry a `Habitation` dressing?** [4] says yes â€” hearth laid, barrel shut, nobody
   home â€” which is what makes the removal read as deliberate rather than as an unfinished wreck. It
   also means the berth is a `RuinAge` roll away from an `ANCIENT` berth whose lid is gone entirely
   and which sits a course deeper, at which point the socket may read as a hole in the ground with a
   lamp in it. `ROADMAP.md` Â§IV's standard applies: decide with eyes in a world.
2. **Does a moored berth stay in `RuinRegistry`?** I have argued yes above, taking [10]'s ending.
   But `RuinRegistry.nearest` with a null kind now returns berths to any future caller asking for
   "any landmark", including `Wayfarer` if its query is ever widened. Decide it now, in the kind's
   javadoc, rather than discovering it.
3. **Does `Plumbline` fire over caves and overhangs on ordinary terrain?** [8] claims it "almost
   never fires" on a non-sky world. A 16-block downward scan fires over any cave roof, which is
   common. Either that is fine â€” hollow is hollow, and the sentence is the same â€” or `REACH` wants
   to be much larger so only true void saturates it. These are different designs and the difference
   is one constant. Do not pick it by editing the constant.
4. **Where does "an anchorage you can occupy but never gain" live?** [5]'s good half. The berth is
   the obvious home: a berth whose position is *already* in `ShipMoorings` would moor without ever
   firing `FirstLight`. That is a true and permanent fact about arrival, and it is also a second
   meaning written into the spine with no way to tell the two apart â€” which `AGENTS.md` Â§VII.3 says
   is a declared exception, not a slip-in. Not decided.
5. **Does `Mystery` mark a waystation hull?** `DerelictFeature.cube()` is called from `stamp()` as
   well as from `build()`. A `TemplateRuinFeature` waystation would carry a keel mark by default.
   Silence here is a decision by accident.
6. **`|SCOPE|` is still open** (`AGENTS.md` Â§VII.1), and nothing in this document depends on it.
   Recorded so the next hand knows it was checked rather than forgotten.

---

## VI. Not measured â€” say it out loud

`docs/SIGHTLINES.md` ran a 200-seed sweep over 2,040,200 cells and corrected two of its own claims.
That is the standard. None of the numbers below meets it.

1. **The arch refusal rate.** How often a node's ground refuses an arch. Logged unmeasured in
   `SIGHTLINES.md` Â§VII.1 and `ROADMAP.md` Â§XII. **This gates recommendation 3**, because a mark
   that points at a node with nothing on it teaches the player that the mark is decoration. The
   sweep: build [6]'s `RuinRegistry` refusal reporting as pure instrumentation, then `HeadlessRun` at
   a stated radius, `of(kind).size()` against the node count in the same window. Same apparatus that
   produced `ROADMAP.md` Â§V's 5,041-chunk table.
2. **The berth rate.** [4] proposes 1-in-4 or 1-in-8 of wrecks. At roughly one wreck per 840
   chunks that is one berth per 3,360 or 6,720 chunks. The target to measure against is stated:
   *a player who has found five wrecks has found one berth*. `HeadlessRun` with
   `-Doctia.worldgen.radius`, plus `tools/new-world.ps1` and `world-report.py`. **Do not pick this
   by editing a constant.**
3. **The mark's wedge.** An octant is 45 degrees and a node sits up to ~360 blocks away
   (`SPACING = 512`, `JITTER = 96`). The error cone at the far end is plausibly Â±140 blocks, and the
   distance at which two marks in one cell actually cross usefully is unknown. `MysteryTest` is pure
   JUnit and this sweep costs milliseconds. There is no excuse for not having it.
4. **How often `Plumbline` saturates on ordinary terrain** (see Â§V.3), and **on `octia:sky`** where
   it may be near-universal near island edges.
5. **`Ring`'s numbers are recorded but not pinned.** `TRAJECTORY.traj` Â§XIV carries 15.17% starting
   on a ring, 6.03% two-leg, 66.42% four-leg, ~248 rings per 41 km window. `RingTest` measures
   closure, parity, stability and circumference â€” not these. Nothing in this document depends on
   them, and that is partly why the ring proposals were rejected, but the gap should be closed
   before anything ever does.
6. **Island spacing on a `floating_islands` seed**, still unmeasured since `ISLANDS.md` Â§IX asked
   for it: for N seeds, how far from spawn is the nearest column with ground in it. It gates [14]
   and it is the honest floor under "what is scarce".

---

## VII. The shape of the milestone

Three things, in order, none of which needs a mixin, a block entity, an access widener, a packet, a
new `SavedData`, a new block, a new texture, or a new line of prose:

| # | what | answers | cost | gate |
|---|---|---|---|---|
| 1 | **The Berth** â€” a wreck with a hole where the light should be | `ISLANDS.md` Â§VI, *what do i do when I get to the hull* | small | the berth rate sweep |
| 2 | **Plumbline** â€” light falling out of a hull with nothing under it | `ISLANDS.md` Â§VII.2, and half of Â§VII.3 | small | eyes in a world, for volume |
| 3 | **The Mark** â€” one lit panel of eight, on a wreck's floor and on your own ring | gives `Mystery.java` its first caller | small | the arch refusal rate |

Then, and only then, the per-player UUID-keyed slice `LIVES.md` Â§VI.2 calls the prerequisite for
everything â€” and after that, [14], which is the real answer to what is scarce and cannot be built
before it.

The world is the source of truth, not this document. `Mystery.java` was sitting untracked in the
tree while five designers proposed writing it. Ask the tree.

`[2026-08-23]`