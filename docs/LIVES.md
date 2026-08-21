# LIVES — death, memory, and who is living in the ruins

**Set down 2026-08-17.** The first piece is built and verified; the rest is a brief,
not a promise. Written now because the idea arrived whole and the reasoning is the
part that gets lost.

---

## I. What is built

**Keep-inventory is held on for every Octia save.** `life/KeepInventory.java`, gated
on the same create-screen switch as the beacon, re-asserted every tick so
`/gamerule keepInventory false` reverts inside 50ms.

It is retroactive with no migration step: the rule is a property of the running
server, not of the save, so a world made in July gets it the next time it opens
through the same path a new save takes. Nothing is written into `level.dat`, so
opening that save without the mod leaves it as vanilla found it.

No mixin, for the reason `OctiaClient` gives. `GameRules` has been stable since
1.13; the only loader-specific surface is three event registrations, each mapped
to its NeoForge counterpart in the class javadoc. That matters because
`D:\Serenity\OctiaModpack` is NeoForge while this repo is Fabric.

Verified by `tools/verify.ps1`: 57 in-world tests, 0 failed.

---

## II. Why keep-inventory comes first

Because it decides what death is *for*.

With inventories dropping, death costs a walk. The walk is not interesting - it is
twenty minutes to a pile that may have despawned, and it teaches nothing. Removing
that cost is not generosity; it clears the slot so something else can occupy it.

**What death costs in Octia is memory.**

---

## III. More hull finished, better life memory

The rule, stated by the gardener 2026-08-17: *more ship hull finished, better life
memory.*

A life is the span between spawning and dying. It accumulates a record - where it
went, what it found, what it built, what it met. On death that record is not simply
kept or lost. It is **compressed**, and how much survives compression is a function
of how many hulls that life completed.

- A life that moored nothing dies vague. A few place names, no detail.
- A life that finished hulls dies **legible** - the record comes through with its
  particulars intact.

This is the reason to build ships that is not "ships are the content." Mooring a
hull is how a life buys the right to be remembered accurately. It also means the
existing loop already carries the new mechanic: `ShipCoreBlock.hullIntact` and
`ShipMoorings` are the measurement, and they are built.

**Not lives-as-currency.** An earlier framing had hulls granting lives to spend at
death, hardcore-style. That was rejected. Nothing here is a countdown and there is
no fail state to design around; hulls buy *fidelity*, not *attempts*.

---

## IV. The record is a book

Also from the gardener, and it is a design constraint rather than a flourish:

> players love reading books in game. signs are just a patch for the real features

A sign is four lines and a wall. A written book is an object with an author, a
title, pages, and a life of its own - it can be carried, shelved, copied, left in a
chest for somebody else, and found two hundred blocks from where it was written.
That is exactly the shape a dead life's record wants.

So a life ends and leaves **a written book**, and its fidelity is what section III
decided. Nothing about this needs new UI, new packets, or a new screen. Minecraft
already shipped the reader.

*(Attribution note: the signs-are-a-patch line was recalled as possibly Notch to
Jeb in a player interview. That has not been sourced and should not be quoted as
one. It stands on its own as a design instinct.)*

---

## V. The ruins should be lived in

The largest part, and the part that is only a brief. In the gardener's words:

> make the ruins feel alive around the player. lived. in. someone went and explored
> that cave and they are now a tree. someone is that village's master now. someone
> built another kickass hill fort. loot. adventure. time.

The claim is that a world should carry evidence of lives other than the one you are
currently living - and that the evidence should be *specific*, not ambient. Not
"ruins exist"; **that** ruin, and the person who is still there in some form.

### The law this must not break

`Habitation` states, and `HabitationGameTest.nobodyIsEverHome` enforces:

> **No entities, ever.** These are things left behind. Beds and hearths imply a
> person; the person is gone, and the emptiness is the point.

That law is correct and stays. It is also **narrower than it looks**, and the whole
design fits through the gap:

`Habitation.dress` runs at **generation time**, from a `WorldGenLevel`, on terrain
no one has ever stood in. It cannot know anything about anybody, so what it dresses
must be empty - a generator that invents inhabitants is inventing history that never
happened.

Inhabitation from a past life is the opposite case. It is **runtime**, it refers to
an event that genuinely occurred in this save, and it has a name attached. The
person is there because somebody was there.

**So: worldgen never spawns anybody, and past lives do.** `nobodyIsEverHome` keeps
passing, unmodified, and it should - it pins the generator, and the generator is not
what changed. Any implementation that has to weaken that test has taken a wrong
turn.

### Shape

A life ends. Its record - already compressed by section III - is written back into
the world as marks at the places that life actually touched:

| the life did | the world gets |
|---|---|
| died in a cave it had explored | something rooted at the spot. a tree that was a person |
| spent a long time at a village | that village has a master now, and they have a name |
| built and moored a hull | the hull stays, and it is *theirs*, attributed |
| carried things it never spent | they are somewhere findable, not deleted |

The through-line is that a later life should be able to *read* an earlier one off
the landscape - and if it wants the details, the book is out there too.

---

## VI. What is open

1. **`hull` now means two things.** In code it is a structure: eight frame panels
   ringing a core, `ADRIFT` / `MOORED` / `CALLED`. In the new idea it is also a
   player's vessel-identity, their "server". These are close enough to collide and
   different enough to mislead. One of them should get a different word before
   either is written into a class name. Not decided.
2. **There is no per-player state in this mod at all.** `ShipMoorings` is keyed by
   position and nothing else - the line in `OctiaDebug`'s javadoc about "every hull
   a player has completed" is loose language, not a store that exists. Every part of
   this brief needs a per-player record that has to be built from scratch. That is
   the first slice, and it is a prerequisite for all of section III, IV and V.
3. **What "a long time at a village" means numerically** - and every other threshold
   in the table above. `docs/NUMERIC_MODEL.md` is where those belong once they are
   real, not here.
4. **Multiplayer.** Everything above says "a life" as though there were one. Two
   players leaving marks in the same save is the interesting case and is not
   thought through.

`[2026-08-17]`
