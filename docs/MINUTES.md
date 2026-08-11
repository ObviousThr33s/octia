```
ACT TWO
MILESTONE 2
OCTIA_[0.1.0.M.I.N.S]_minutes
SEEK KEG |ALL|
```

# Minutes

A running record of sittings. One entry per working day, written at the close of
it, in the order things actually happened.

Not a changelog — `git log` is the changelog and it is better at it. What goes
here is the part the diff cannot hold: what was decided, what turned out to be
false, and what was agreed as a standing rule. A commit says the code changed.
These say why anyone would have changed it that way.

Entries are closed when written. Nothing in a past sitting is edited afterwards;
if a resolution is later overturned, the overturning is minuted in its own
sitting and the old one left standing, wrong, with a pointer.

---

## Sitting of 2026-08-10

**Present.** Kaegan (chair), Claude Opus 5.
**Carried in** from the evening of 2026-08-09: the ship cube and the obelisk, the
era echo and the strangers sketched but not built, the headless loop named.
**Landed.** Thirteen commits, `48c6577` through `86b8b1b`. Tree clean at close.
**Standing at close.** 56 gametests, 8 JUnit tests, all passing.

### 1. The F6 map was not broken

Opened as a bug: *"f6 shows nothing in the new world."* It was not a bug. The
client had booted at 07:15 and the overlay was compiled at 07:27, so the running
game had never contained the keybind. The receipt was `run/options.txt`, which
had no `key_key.octia.debug` line at all — a client writes every registered
binding to that file on exit, so its absence is proof of absence, not of
misconfiguration.

**Resolved.** A running `runClient` never sees code compiled after it booted.
Before investigating any missing client feature, compare the client's start time
against the class file's. This is now ROADMAP-adjacent standing practice and is
recorded in memory as `octia-dev-client-stale-code`.

### 2. The headless loop, and the first EULA

`tools/new-world.ps1` generates a world with no human at a keyboard, moves it
into `run/saves`, and catalogues it on the way out.

The chair accepted Minecraft's EULA once, by hand. The script writes
`eula=false` and stops; it will not set that field on anyone's behalf, and asks
exactly once.

**Resolved.** The generator is shut to the outside world by several independent
means — loopback bind, no player slots, no status ping, no query, no rcon,
authentication left on, an enforced empty whitelist. It draws terrain for a
minute with nobody connected and has no business being reachable. Raised by the
chair; `cc5f777`.

**Resolved.** The server halts itself (`HeadlessRun`) rather than being killed.
Killing a Minecraft server races its final chunk save, which is the one thing a
world generator must never do. Two earlier attempts to send `stop` over stdin
both put a byte-order mark in front of the word and left a finished world locked
behind a server that had ignored it; the stdin path was abandoned entirely and a
JVM property used instead, because a property crosses PowerShell, Gradle and the
forked JVM with nothing in the middle to encode it.

### 3. Density, settled from evidence

5041 chunks were generated and measured. The tuning then in the tree produced a
ruin every 240 chunks with a 97-block median separation — mineshaft density,
which is to say litter. Retuned to 800/520/900 and re-measured: one per 840
chunks, 122-block median.

**Resolved.** This is the first number in the mod chosen from a walked world
rather than from taste. Any future change to it re-runs the measurement.
`42e80b8`.

### 4. Ruins became places

In order: authored `.nbt` templates with DATA markers (`0e0f545`); the lived-in
dressing pass, `Habitation` (`9a3fd5c`); Octia's own loot tables, mundane on
purpose — a person's belongings, not treasure (`1c8ba93`).

**Resolved, on the chair's instruction and not negotiable.** Ruins are **never
inhabited**. No occupants, no entities. Strangers are met on the road.

**Resolved.** The ship core is stamped in code after template placement, never
carried by the template. Vanilla's integrity erosion may chew the architecture
freely; it gets no vote on the hull ring. `BlockRotProcessor(float)` is the 1.21.1
spelling — `setIntegrity` does not exist and was verified absent in the jar
before the call was written.

**Resolved.** The core is placed **last**. On a live `ServerLevel`, `onPlace`
fires and surveys immediately, so the dig evidence must already be in the ground
or `/place` and natural generation disagree about whether the ship was called.

### 5. Strangers

Wayfarers walk the road and remember where they met you (`WayfarerLedger`).

The chair's note: *"but know they are chaos."* Taken as a design constraint
rather than a warning. Containment is a whitelist at the order boundary —
`SAY`, `GO`, `LOOK`, `HOLD` pass; `FOLLOW` and `JUMP` degrade to `HOLD`. The
chaos is in what they say and where they are, never in what they can be made to
do. `c72cdc8`.

### 6. The era echo, and the lesson in it

The echo shows the same mooring across dimensions. It was written gating on exact
position and was therefore silently disabled across the one boundary it existed
for: the Overworld starts at y −64 and the Nether at y 0, so a mooring at y −58
has no Y in the Nether at all. Nothing errored. The feature simply never fired.

**Resolved.** What crosses eras is **the column, never the point**. `b61cd0f`.

### 7. The ruin registry, and obelisks on the map

`RuinRegistry` is a `SavedData` landmark store. Features report from worldgen
threads into a `ConcurrentLinkedQueue`; the queue drains on `END_SERVER_TICK`,
which is the only place writing `SavedData` is legal. `734d555`, `7097a20`.

A correction minuted because it was stated wrongly to the chair more than once
during the sitting: a **derelict draws teal**. **Violet is the obelisk.**

### 8. Creative mode, and a mistake worth keeping

All nine existing saves had `allowCommands=0`. This means `/place feature
octia:derelict` — the only way to see a ruin without walking to one, and the
command this repo's own docs had been recommending for days — was being refused
by every world in the folder. Nobody had noticed because the refusal is quiet.

`tools/world-gamemode.py` sets three fields in place. The second is the one that
is easy to miss: `Data.GameType` is what a new player arrives in;
`Data.Player.playerGameType` is the host, because a singleplayer save carries
the host's own player data inside `level.dat`. Six of nine saves had an embedded
player. Setting only the first changes what somebody who never arrives would
get, and leaves you exactly as you were.

**Fault, recorded.** The first version of the open-world guard called
`open(lock, "r+b")`, reported every world free — including one that was open at
that moment — and **wrote a `level.dat` out from under a running game**.
Minecraft's `DirectoryLock` uses `FileChannel.tryLock`, a byte-range lock, so
`session.lock` stays perfectly openable and only a competing *lock* attempt
fails. Opening it proves nothing. The guard now takes the same region lock
through `msvcrt` / `fcntl` and correctly refuses.

**Resolved.** The log is not a second opinion on whether a world is open. It was
consulted first and said no world was open while one was: an integrated server
does not announce loading a world the way a dedicated one does. The lock file is
the only thing that knows.

**Resolved.** A writing NBT codec is a different program from a reading one, and
the two are deliberately not shared. `world-report.py` answers in plain Python —
an int for a byte, a list for three different array tags — which is right for
reading and makes writing impossible, because there is no way back to the tag
that was on the wire. `world-gamemode.py` keeps every tag. Proved lossless on
all nine `level.dat` files, on copies, before a single real save was touched.

### 9. Not carried

Two items from the chair were flagged rather than guessed at, and remain
uninterpreted: **"40k will have a heart"** and **"how many streaming bytes for
the tensor net?"** — the only occurrence of anything adjacent in the tree is
`safetensors`. Minuted so they are not lost, not so they are answered.

### Carried forward

- **Visual confirmation of the violet obelisk marks.** The chair saw them and
  reported them good; no screenshot exists. Outstanding as a palette reference,
  not as verification.
- **The loader fork.** 116 of the 124 mods in OctiaModpack are NeoForge. Octia
  cannot currently go in its own modpack.
- **The Starfield launch-day topology map** — ROADMAP III.
- **`placeNearSpawn` has no gametest**, the gametest world being a flat void.
- **Submerged wreck dressing**, marine palette.
- **A vanilla shutdown hang**, documented and not fixed:
  `MinecraftServer.stopServer → ChunkMap.processUnloads` spins at full CPU
  intermittently after tests pass, holding `build/gametest/session.lock` and
  poisoning the next run. Worked around with a five-minute age cutoff in the
  verify sweep.

**Closed** at the chair's word: *"i have achieved inner peace with this."*
