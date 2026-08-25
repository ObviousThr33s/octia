```
ACT TWO
MILESTONE 1
OCTIA_[0.1.0.A.C.T.2]_spec
SEEK KEG |ALL|
```

# Upgrading

What breaks, and exactly where. Established 2026-07-31 against live Fabric
sources and the pinned `minecraft-merged-1.21.1` jar, not from memory.

The mod's name is **OCTIA**, and the code agrees: `mod_id=octia`, the package is
`com.serenity.octia`, and `Octia.MOD_ID` is the one constant. This paragraph used
to announce a rename to "OCITIA"; no such rename was made, and `OCTIA.md` is the
record of which spellings are this repo and which belong to another project. Read
`gradle.properties` before writing any identifier — see `NAMING.md`.

---

## What holds the pins

| Pin | Held by | Free move |
|---|---|---|
| `loom_version=1.11.8` | **The Gradle wrapper**, 8.14.2. Loom declares `org.gradle.plugin.api-version` in its module metadata: 1.13.4 wants 8.14, 1.14.10 wants 9.2.0, 1.17.17 wants 9.5.0. Gradle refuses a plugin variant above itself. | `1.11.8 -> 1.12.7`. Both 1.12 and 1.13 still declare 8.14. |
| `minecraft_version=1.21.1` | Deliberate. Everything below is the cost of leaving it. | none |
| `fabric_loader_version` / `fabric_api_version` | Nothing. Bump freely within the `+1.21.1` line. | current as of 2026-07-31 |

Loom ≥ 1.14 still builds 1.21.1. What changed at 1.14 was the plugin **id**, not
the support: `net.fabricmc.fabric-loom-remap` for obfuscated Minecraft (1.21.11
and older), with plain `fabric-loom` kept for compatibility and slated for
removal in Loom 2.0. Fabric's own `fabric-example-mod` pins `1.17-SNAPSHOT` on
its 1.21.1 branch. The unobfuscated runtime starts at **Minecraft 26.1**, not at
any Loom version. This project has no access widener at all — `:validateAccessWidener`
reports `NO-SOURCE` — so no access-widener argument applies to the pin either.

---

## Minecraft 1.21.2 — a startup crash

**`OctiaBlocks.java:37, 53, 66`.** From 1.21.2, both `BlockBehaviour.Properties`
and `Item.Properties` must have `setId(ResourceKey)` called before being handed
to a constructor. All three sites pass un-keyed properties, and both fields are
`static final` forced by `bootstrap()` — so this is a crash at class-init, not a
warning. The symptom upstream reports is `NullPointerException: Item id not set`.

Fix: thread a `ResourceKey` into the private `register()` helper at `:64-68` and
call `.setId(...)` on the block properties **and** the `Item.Properties`. Add
`.useBlockDescriptionPrefix()` to the item properties too, or the block item's
translation key resolves under `item.` instead of `block.`.

**`ShipCoreBlock.java:189`.** `neighborChanged` gains an `Orientation` parameter
in 1.21.2. The override stops overriding — silently, unless `@Override` is present.

---

## Minecraft 1.21.5 — both saved-data classes

Verified at every release in between: 1.21.2, 1.21.3 and 1.21.4 all still have
`SavedData.Factory` and the abstract `save`. 1.21.5 replaces the whole surface.

Affected: `ship/ShipMoorings.java:30, 46, 59, 68` and
`world/OctiaWorldOption.java:29, 55, 82, 93`.

`SavedDataType<T>` is a record `(String id, Function<Context,T> constructor,
Function<Context,Codec<T>> codec, DataFixTypes dataFixType)`. The migration:

- the **filename moves into the type**, so `computeIfAbsent` takes one argument;
  fold `FILE` (`"octia_moorings"`, `"octia_world"`) into the type's `id` and drop
  it from the call sites at `ShipMoorings.java:56` and `OctiaWorldOption.java:79`;
- delete both `save(CompoundTag, HolderLookup.Provider)` overrides and write a
  `Codec<T>` for each store;
- the private no-arg constructors must take a `SavedData.Context`;
- 1.21.5 also reworked `CompoundTag` getters to return `Optional`, so
  `tag.getLongArray(...)` (`ShipMoorings.java:61`) and `tag.getBoolean(...)`
  (`OctiaWorldOption.java:84-85`) break too. Moving to a `Codec` dissolves most
  of that.

Note the pre-1.21.5 argument order is **factory first** — `computeIfAbsent(FACTORY, FILE)`,
as the code has it today. Do not "correct" it while still on 1.21.x < 5.

---

## The borrowed `DataFixTypes` constant — check this before opening a save

Both stores pass `DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES`
(`ShipMoorings.java:46-47`, `OctiaWorldOption.java:55-56`). That is the correct
choice on 1.21.1 — see the comment at `ShipMoorings.java:37-45`, which is
accurate — but it is not free.

`SavedData.save` stamps the file via `NbtUtils.addCurrentDataVersion`, so both
`.dat` files carry **DataVersion 3955**. On read, `DataFixTypes.update` runs, and
the fixer chain early-returns only while the stored version is at or ahead of the
target. So vanilla's fixers for that type **will** run over this mod's NBT on any
future Minecraft upgrade.

It is inert today, but not because Mojang wrote no fixer.
`RandomSequenceSettingsFix` ships in 1.21.1, registered at schema 3565 — already
in the past of the 3955 stamp. It shows the failure shape exactly: it rewrites
`data` into `data.sequences`, and `V99` registers this type as `DSL.remainder()`,
so the mod's own keys sit entirely inside the rewritten remainder. A future fix of
that shape moves `data.moorings` to `data.sequences.moorings`; `ShipMoorings.load`
then reads an absent long array and returns an empty set, and `OctiaWorldOption`
falls back to its `pending` default. **No crash and no log line** — every moored
ship simply forgotten.

**Checkpoint, on every Minecraft bump, before opening any existing save:** diff
the fixers registered against `References.SAVED_DATA_RANDOM_SEQUENCES` between
DataVersion 3955 and the target. Cheaper permanent fix: stop borrowing a vanilla
type — nest the payload under one mod-owned root key, or move the state to a
Fabric attachment, so no vanilla remainder rewrite can reach it.

#### `[2026-08-24]` A third store now borrows it, and it is the one that hurts

`item/CubePockets.java` (`octia_pockets.dat`) joined the two above in the cube
push, on the same borrowed `DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES`. **The
checkpoint above covers it too** — this entry exists because the checkpoint named
only `octia_moorings` and `octia_world`, and a reader diffing fixers would have
had no reason to think a third file was at risk.

The stakes are higher here than for the other two, and that is worth saying
plainly. A forgotten mooring is a ship that stops being remembered; the world
still holds every block the player laid. `octia_pockets` holds **the actual
contents of every gold and purple cube in the save** — items the player put
there and can no longer see anywhere else. A remainder rewrite of the shape
described above does not degrade that; it deletes it, with no crash and no log
line, exactly as described for the other two.

That asymmetry is the argument for doing the "cheaper permanent fix" rather than
carrying the checkpoint forever. Whoever bumps Minecraft next should fix all
three at once, and start with this one.

### Do not "just pass null"

A future reader will find `@Nullable DataFixTypes` in NeoForge javadocs and
`null // Supposed to be an 'DataFixTypes' enum, but we can just pass null` on the
Fabric wiki. Neither applies here.

- The `@Nullable` is a **NeoForge patch of vanilla** — NeoForge adds both the
  annotation and an `if (type != null)` guard to `DimensionDataStorage`, because
  it does not support data fixers at all. Fabric ships no equivalent.
- On vanilla 1.21.1, `readTagFromDisk` has zero null checks and calls
  `DataFixTypes.update` unconditionally. `readSavedData` catches the resulting
  exception, logs `Error loading saved data: {}`, and returns null — so
  `computeIfAbsent` hands back a blank store. A null here does not crash. It
  discards every moored ship, quietly.
- This does **not** become safe at 1.21.5. Null is only documented as fine on the
  modern signature, in docs targeting **Minecraft 26.2**. Verify against the
  target version's actual bytecode before ever writing `null`.

Fabric never documented SavedData for 1.21.1 in either direction, so there is no
official recommendation to cite. That is why this file exists.

---

## Things checked and found already correct

Recorded so nobody spends the research twice.

- **`repositories { mavenCentral() }` alone is correct.** Loom's
  `LoomRepositoryPlugin` injects `https://maven.fabricmc.net/` itself; the
  literal is in `Constants.class` inside the published `fabric-loom-1.11.8.jar`,
  and Gradle repository lists are a union. Fabric's own template ships an empty
  `repositories {}` and still resolves. Adding it explicitly is a harmless
  duplicate, not a fix.
- **`build.gradle.kts:68-72`, "exits non-zero on failure", is accurate.**
  `GameTestServer.onServerExit` is `System.exit(failedRequiredCount)`, and Loom's
  run task never sets `ignoreExitValue`. Precisely: the count is of failed
  **required** tests, so `@GameTest(required = false)` will not fail the run.
- **The gametest system properties are exact** for this API line, and the
  report-file wiring really does write `build/test-results/gametest/report.xml`.
- **`ScreenEvents.AFTER_INIT`'s four-argument listener matches 1.21.1.**
- **The singular data-pack layout is right.** `data/<ns>/recipe/`,
  `loot_table/`, `tags/block/` — the plural-to-singular rename landed in 24w19a
  (tags) and 24w21a (registry content), i.e. before release 1.21.
- **`loom.officialMojangMappings()` matches Fabric's own 1.21.1 template**, and
  is orthogonal to the Loom plugin-id split.
