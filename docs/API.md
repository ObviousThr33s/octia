# API — the seams, planned before anyone needs them

**Set down 2026-08-17, on branch `lives-and-islands`.** Documentation only. **No
API exists today**, and this file does not create one. It records where the seams
should go, so that the first time somebody wants a hook there is a plan rather than
a hurried public field.

---

## I. The rule this file exists to state

**Nothing is API until it is named API.**

Right now every public member in `com.serenity.octia.*` is public because Java made
it so, not because anyone decided it was a promise. That is fine while the only
caller is this repo. It stops being fine the moment a second thing calls in, because
the difference between "public" and "supported" will never again be recoverable.

So: a real API lives in **`com.serenity.octia.api`**, that package is the only thing
promised, and everything outside it may be moved or deleted without notice. An empty
`api` package with one honest line in its `package-info.java` is worth more than a
dozen accidentally-public methods.

---

## II. What is already load-bearing (and therefore at risk)

These are called from outside their own package today and would be the first things
an outside caller reached for. None is promised yet, and each needs a decision:

| what | today | should it be API? |
|---|---|---|
| `Octia.id(String)` | the only sanctioned `ResourceLocation` builder | yes - trivial, stable, and already the documented rule |
| `ShipCoreBlock.hullIntact(...)` | the definition of a hull | yes, read-only. it is the mod's central predicate |
| `ShipMoorings` | save-wide store, position-keyed | read yes, write no. an outside writer could invent moorings |
| `RuinRegistry` | landmarks worldgen reported | read yes. it is already a query surface in all but name |
| `OctiaWorldOption.enabled()` | the per-save switch | yes. anything integrating must be able to ask "is Octia on here" |
| `CrewConfig` | JSON: endpoints, pollSeconds, maxCrew | already a config file, which is already a public contract |
| `OctiaBlocks` | the block instances | yes by necessity - recipes and tags need them |

---

## III. Hooks worth planning for

Stated as events, because that is the shape that survives a port. Each is a thing
that already happens internally and currently tells nobody.

1. **A hull moored / unmoored.** The single most useful hook in the mod, and the one
   `ISLANDS.md` §VI shows is missing internally too - "what do I do when I get to the
   hull" is the same gap seen from inside. Fire on the transition, not per tick.
2. **A ruin registered.** `RuinRegistry` already drains reports from generation
   workers onto the server thread; that drain is the natural emission point.
3. **A life ended, and a life record written.** From `LIVES.md`. This is the hook an
   integration would want most after mooring, because it is the one carrying a story.
4. **Inhabitation placed.** When a past life writes itself back into the world
   (`LIVES.md` §V), something outside should be able to see it happen - and to veto
   it, since a claims or protection mod has a legitimate reason to say no.
5. **An island placed.** Only once `ISLANDS.md` picks a shape.

**Cancellable vs. notify-only:** default to notify-only. A cancellable event is a
promise that the mod can cope with being told no at that point, which is a much
larger claim and should be made one hook at a time. Hook 4 is the only one with an
obvious case for it today.

---

## IV. Portability, which is the actual constraint

The same rule the mod already follows for behaviour applies to its API: **no mixin,
and nothing that assumes Fabric.**

- Events should be declared as **plain interfaces** (`@FunctionalInterface`), with
  the loader's event system used only to *carry* them. Fabric's `Event<T>` wraps a
  plain interface happily; NeoForge's bus wants a class. If the interface is ours,
  both ports keep the same callback signature and only the registration line differs
  - which is exactly the property `KeepInventory`'s javadoc is careful about, and
  the reason `OctiaModpack` being NeoForge is survivable.
- **No reflection-based registration.** It cannot be checked at build time and fails
  at load, which is the failure class `Octia`'s constructor note already warns about.
- **No API that only exists because of a mixin.** There are no mixins here; an API
  that requires one would import the breakage the mod has so far refused.

---

## V. The API most people will actually use

Not Java. **Datapacks.** `data/octia/` already carries recipes, loot tables and
worldgen, and `data/minecraft/tags/block/` already joins vanilla tags. That surface
is public the moment the jar ships, whether or not it is documented - which means
resource-location names, tag names and loot-table paths are *already* promises being
made silently.

Documenting those names is cheaper and higher-value than any Java hook above, and
should come first.

---

## VI. Versioning

- `com.serenity.octia.api` is additive-only within a major version.
- A hook may gain a *new* interface; an existing method signature does not change.
- The mod version in `gradle.properties` stays the identity of the whole artifact -
  per `OCTIA.md` standing order 3, Gradle is the parts list and there is not a second
  one. If the API ever needs its own number, it goes in `gradle.properties` too.

---

## VII. Open

1. Does an `api` package get created empty now, to hold the line, or only when the
   first hook lands? Creating it early is cheap and states the rule where a future
   hand will actually read it.
2. Hook 1 needs the answer to `ISLANDS.md` §VI first. What arrival *means* has to be
   decided internally before it can be published.
3. Nothing here is worth building until something outside wants to call it. This
   file is a plan so that the answer to "can I hook this" is a design, not a
   scramble.

`[2026-08-17]`
