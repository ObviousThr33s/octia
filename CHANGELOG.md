```
ACT ONE
MILESTONE 0
OCTIA_[0.2.0.A.L.P.H.A]_build
SEEK KEG |ALL|
```

# Changelog

What changed, in the order it will be read: newest release first.

Written by hand, on purpose. `release.yml` already appends the commit list to
every GitHub release, and a commit list is a record of typing rather than a
record of the game. This file is the other half - what a person who downloaded
the jar would want to know before they load a world.

**One `##` heading per released version, and the heading is the exact
`mod_version` string.** `tools/release-card.ps1` looks its own version up in
here by that heading and refuses to build a card when it finds nothing, on the
same argument the script already makes about a missing version number: a card
with a hole in it reads as authoritative and is not. So a version bump and an
entry here are one move, never two.

`## Unreleased` collects what has landed since the last tag. It is not a
version heading and the card never reads it - rename it when the version bumps.

---

## Unreleased

- CI runs the actions on Node 24.
- **The build survives one Fabric Maven being down.** Plugin resolution probes
  `maven.fabricmc.net`, then `maven2`, then `maven3`, with a two-second HEAD
  each, and takes the first that answers; dependency resolution simply names all
  three and lets Gradle walk them. `docs/TRAJECTORY.traj` XV records the outage
  that made this necessary - work went unverified because there was no second
  address to try. Borrowed from `Turnip-Labs/bta-example-mod`.
- **This file**, and the release card reads it.
- **A release says which act it belongs to.** `mod_act`, `mod_milestone` and
  `mod_flags` join `gradle.properties`, so the card and the GitHub release
  title carry the KEG block. The jar's version is still SemVer and still what
  the tag is checked against.
- `docs/PRIOR-ART.md`: what neighbouring projects do, and what Octia took.

## 0.2.0-alpha.1

The first build worth handing to somebody else. The toolchain, the mappings and
the loader handshake were proven before any content leaned on them; this is what
landed on top of that, and it is an alpha because the questions it asks are still
open - not because it is unfinished in some way that a bug report would fix.

### The world you wake up in

- **Octia Sky.** A world type that draws the Overworld from
  `minecraft:floating_islands` with ordinary Overworld biomes on top - plains and
  savanna and snowy taiga with open air underneath. It stands on the world-type
  button and anyone can pick it.
- **Landfall.** A sky world's spawn column is usually nothing at all, so the
  first load hunts for real ground, moves spawn to it, and prints one island if
  nothing answers within 96 blocks. You wake up standing on something.
- **A switch on the create-world screen**, decided once and kept by the save for
  life. On: the sky world type, a lit mast at spawn, one derelict within a short
  walk, then derelicts and obelisks out through the world.

### Things to walk to

- **Sightlines.** A seeded lattice of waypoints and a leg between each pair.
  Every obelisk is a large andesite prism laid along its leg with a slot bored
  down the length that you can sight through, and an arch - keystone plus four -
  stands on every node, squared across the leg so you walk through it facing the
  way the thread runs. Nothing in a vanilla world points at anything.
- **Derelicts, ruins and habitations.** Ruins that refuse a bad site rather than
  levelling it, that read their own age, and that carry the signs somebody lived
  there and left. They are never inhabited; strangers are met on the road.
- **Watersheds, stair towers and switchback stairways**, massed as prisms rather
  than placed block by block.
- **Obelisks and waystations on the F6 map.**

### Things to carry

- **Bindles.** A cloth tied to a stick: four stacks, no screen, worked like a
  bundle. Crafted, found in a ruin's store, or left on the road by a wayfarer who
  put theirs down where they stood.
- **Cubes**, whose contents live in the save rather than on the stack.
- **A sail rig** - descent only, a glide and not a flight.
- **The HEV suit.** Worn *over* whatever skin you have rather than instead of it:
  the player model inflated a quarter block and drawn as a second pass,
  transparent at the head so your own face is still in there. Cosmetic, and it
  does not come off.

### Who else is out there

- **Crew.** Server-side fake players seated by `/octia crew muster`, spoken for
  by a local model when one answers and by an offline tender when none does. LAN
  guests see them without installing anything.
- **Wayfarers**, met on the road, and a ledger of who has been met.
- **Herobrine**, as a director of haunt moments and an announcer.
- **The void squid.**

### The ship

- **Two blocks.** `andesite_frame_panel`, whose panel cycles dark, generic and
  styled and lights with it, and `ship_core`, which surveys the panels around it
  and reads ADRIFT, MOORED or CALLED.
- **Moorings**, written to `SavedData` keyed by `BlockPos` and nothing else. The
  same position in another dimension is the same mooring - deliberate, and a
  gametest pins it.

### Living in a world

- **Keep-inventory is held on** for the life of an Octia save and re-asserted
  within 50ms, retroactively on old saves. Death costs a life, not a backpack.
  It is not a switch in this mod.
- **A debug map** on F6, with F7 for its range.

### Getting a build

- **`SEEK.cmd` at the root opens the front door**, and the door's jar and icon
  are committed, so a fresh clone opens it without building anything first.
  **SEEK PLAY |DEV|** runs both gates in one press.
- **One gate, run twice.** `tools/verify.ps1` runs what `.github/workflows/verify.yml`
  runs: build, then every in-world test on a headless dedicated server, with no
  display, no Mojang account and no secrets.
- **A tag publishes the jar**, and refuses to when the tag and `mod_version`
  disagree. A release card ships beside it carrying the four versions that must
  agree, a seed that was actually walked, and how to send a verdict back.
