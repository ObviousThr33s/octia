```
ACT TWO
MILESTONE 6
OCTIA_[0.1.0.S.E.A]_series
SEEK KEG |ALL|
```

# NEPTUNE'S OCEAN — the series

**Set down 2026-09-03.** The sea under the islands has a name (`ISLANDS.md`
§XII), and the sessions that carry it forward have one too. This file is
where they tell it.

The rule is the one the estate already keeps. **A session in the series is
a storyteller.** It is titled `NEPTUNES_OCEAN`, it is tagged
`neptunes-ocean-series` and `neptunes-ocean-series:<n>`, and before it
ends it appends one chapter here: numbered, dated, headed with the session
that told it. A chapter is a story and it is also a record, so every
number in it is one the repo can show — a probe verdict, a commit, a
section of a doc. Nothing decorative. A chapter that cannot be checked
against the world is a rumour, and the world is the source of truth
(`AGENTS.md` I). Chapters are appended and never rewritten; a chapter that
turns out wrong gets a dated correction under it, the way `ISLANDS.md`
§XII carries its own.

The series is open at the top. The first chapter is below.

---

## Chapter 1 — how the sea came to be under the islands

*Told by the session `NEPTUNES_OCEAN`, 2026-09-03, the first of the series.*

There was a world that was supposed to be sky.

It said so in the file that makes it: floating islands, a noise band from
zero to two hundred and fifty-six, nothing beneath. The tooltip on the
create screen said so too, to every player who hovered over the switch —
*floating islands over open void*. The bar it was built to clear was
Skyblock's, set down in `ISLANDS.md` §I: if a player can walk off the
island and find normal terrain, there is no contract, and what has been
built is scenery.

But the world had a sea in it that nobody had asked for, and the sea was
draining.

The probe found it first, not a player. `tools/chunk-probe.py`, seed 1,
two hundred and eighty-nine chunks, on the shipped build: five thousand
one hundred and fifty-two sections of fluid below y=0, where the noise
band said there was nothing at all. The verdict it printed was one line
and it was not a metaphor. `THE SEA IS DRAINING.`

The reason took a day to see because it was not in one file. It was in
two files that disagreed about how tall the world was. The noise settings
said the world began at zero. The dimension type said it began at minus
sixty-four. So there were sixty-four rows of world that existed and that
the generator had never written to — no bedrock, no floor, open air with
a sea sitting on top of it at level ninety-six. Water was not falling out
of the bottom of the world. It was falling out of the part of the world
that nothing had built.

The fix was a floor. `dimension_type/sky.json`, vanilla's overworld with
three numbers changed, so the world ends where the band ends and there is
nowhere left to drain to. Probed again, same seed, same chunks: fluid
below zero, none. Rock below zero, none. The verdict this time was the
one the world was built for. *Floating islands over void, as asked for.*

And that was the moment the sea could have gone. Drop the sea level, let
the band be air to the floor, and the tooltip would have been true again.

KEG kept it.

That was a taste call and §XI says so, and it says what it costs: under a
void, falling off an island was the sky world's one absolute danger.
Under a sea it is a swim and a climb. The sail-rig's stakes, above all,
have to be re-asked against that. But §I's contract holds, and it holds
for a reason worth writing down. The bar was never *void*. The bar was
*no normal terrain*. You cannot walk off an island onto this sea. You
cannot farm it as it stands. You cannot find the ground under it, because
there is none; rock below zero measures zero point zero percent. A sea
that goes all the way down is as much a scarcity contract as a void, and
it has waves.

The spawn column reads, from the bottom:

```
   0.. 95   water          the sea, at sea_level 96
  96..141   - air -        46 blocks of open air
 142..169   stone          an island
 173..198   the mast, and octia:ship_core lit at the top
```

So it was kept. It was kept for six days without a name, and §XI called
it "the ocean" eleven times.

The first name it was given was the wrong one, and the wrongness is
instructive enough to keep. An assistant, asked only *OCEAN NAME*, went
looking for an ocean the estate already had and found one: ARCADIA's
prefix registry, first entries, *KEN — for the one who fishes the ocean*;
the device's boot title, `KEN'S OCEAN, SIMULATED`, one of its six true
names. The derivation was clean. It was also a different sea. Ken fishes
ARCADIA's ocean, and this one is a `sea_level: 96` in a noise file under
a Serenity-class ship. Within the hour the owner named it, and the name
was not derived from anything. **Neptune's Ocean.** The wrong name lives
in `ISLANDS.md` §XII, struck through, because corrections are new
entries, and for one commit, `7c9ca46`, the tooltip said it too.

There is one creature that the story leaves in the wrong place, and the
chapter would be a rumour if it left this out. The void squid lives in a
band cut out of the sixty-four rows under the continent — minus fifty-four
to minus ten, with clearance to spare — because when it was written those
rows were open void with the underside over them. The floor removed those
rows. There is no y below zero on the sky world now, and the rows above
where the band was are Neptune's Ocean. The test that holds the band still
passes, because it holds the band against its own constants and not
against the dimension type. Both constants are marked *provisional —
owner tunes by walking the world*, and the world has changed under them.
Where the squid goes now is not this chapter's to say. It is the owner's,
and it is open.

That is the whole of the first chapter: a sea that was a bug, a floor that
made it a sea, a keep that made it a place, and a name that made it
Neptune's. The next teller starts from here.

`[2026-09-03]`
