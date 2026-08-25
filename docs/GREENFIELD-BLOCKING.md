# GREENFIELD-BLOCKING.md - F6 and F7, blocked but not built

Set down 2026-08-24 by the greenfield push (charter: GREENFIELD.md). These two are
client-side and verified by eye, so this push delivers their blocking only. The
specs below were grounded against the repo, the mapped 1.21.1 bytecode, the
suit texture's pixel data, and the [0_6_7] save.

---

Both docs are grounded. Everything below was verified against the repo, the mapped 1.21.1 bytecode (javap on the Loom jar), the texture's pixel data, the Fabric API jars on this project's classpath, and the `[0_6_7]` save itself.

---

# F6 BLOCKING - HEV suit render fixes

Repo: `D:\Serenity\octia`. Files owned by this feature:
`src\main\java\com\serenity\octia\client\HevSuit.java`,
`src\main\java\com\serenity\octia\client\HevSuitLayer.java`,
`src\main\resources\assets\octia\textures\entity\hev_suit.png`.
Shared-file touches (integration pass): `fabric.mod.json` only if defect C's mixin is ruled in.

Owner's report, verbatim: "shows the player face too much, z-fighting, and doesnt show on arms."

## What is actually on disk, measured

The texture (64x64), scanned per skin region for alpha:

| region | opaque texels |
| --- | --- |
| head base (0,0-32,16) | 0 of 512 |
| head hat (32,0-64,16) | 0 of 512 |
| body base | 352 of 384 - every used texel |
| each limb base | 224 of 256 - every used texel |
| every overlay region (jacket, sleeves, pants) | 0 |

So: the limbs and body are fully painted, the head is fully blank on both layers, and the suit's own second-layer regions are all blank.

The model: `HevSuit.bootstrap()` bakes `PlayerModel.createMesh(new CubeDeformation(STANDOFF), false)` with `STANDOFF = 0.25F` (`HevSuit.java:67,87-89`). Verified in the mapped jar: `PlayerModel.createMesh` builds the skin's own sleeve/jacket/pants overlay at `deformation.extend(0.25F)`, and `HumanoidModel.createMesh` builds the hat at `deformation.extend(0.5F)`. A player's own model is built from `CubeDeformation.NONE`, so the player's overlay shells sit at +0.25 and +0.5.

The layer: `HevSuitLayer.render` (lines 56-72) delegates to vanilla's `RenderLayer.coloredCutoutModelCopyLayerRender`.

## Defect A - "shows the player face too much"

**Root cause: texture, not code.** The suit's head is 100 percent transparent on both layers, so the entire head - all six faces - is bare skin. The javadoc's design ("their own face is still inside it") intended a framed face; what shipped is no helmet at all.

**Fix - paint a helmet with a visor aperture, and stand the head off past the skin's hat layer.**

1. `hev_suit.png`: paint the head base region (0,0)-(31,15) - all six faces - in the suit's orange register, leaving a transparent aperture in the front face. Front face is texels (8,8)-(15,15); aperture provisionally x 9-14, y 10-13 (6x4, framing eyes and nose). Aperture size and placement are provisional - owner tunes by looking. Fallback the owner can choose by eye: leave the whole front face open and paint only sides, back, top, bottom.
2. Head geometry: the skin's own hat layer (hair on most skins) sits at +0.5. A helmet at the current +0.25 would render UNDER it and z-fight or clip hair through the shell. The head cube must be inflated past 0.5 - see defect B's mesh change, which gives the head its own deformation.
3. Order gate: do NOT paint the helmet before defect C's call-order fix lands. Today the layer feeds `ageInTicks` into the helper's `netHeadYaw` slot (see defect C), so the suit head's yaw grows without bound - invisible only because the head is untextured. Painted first, the helmet visibly spins on the player's neck.

**Verified by eye:** F5 front view - face visible only through the aperture; side and back - zero skin. With a skin that has hat-layer hair - hair inside the helmet, no poke-through, no flicker. Turn the head fully left and right - the helmet turns with it and does not spin.

## Defect B - z-fighting

**Root cause: `STANDOFF = 0.25` is exactly the inflation of the player's own overlay layer.** The suit's base cubes at +0.25 are geometrically coplanar with the skin's jacket/sleeve/pants shells at +0.25 - identical box dimensions, identical transforms, identical depth. Which surface shows is a tie decided by buffer flush order and depth rounding, per pixel, per frame. That is the flicker. The `STANDOFF` javadoc (HevSuit.java:62-66) says the quarter was chosen so seams "do not z-fight with the body underneath" - it clears the body at +0.0 and lands exactly on the overlay at +0.25. The javadoc gets rewritten with the corrected reason when the constant moves.

**Fix - per-part deformation, quarter steps, in `HevSuit.bootstrap()`:**

```java
private static final float BODY_STANDOFF = 0.5F;  // provisional - owner tunes by walking the world
private static final float HEAD_STANDOFF = 0.75F; // provisional - owner tunes by walking the world

MeshDefinition mesh = PlayerModel.createMesh(new CubeDeformation(BODY_STANDOFF), false);
mesh.getRoot().addOrReplaceChild("head",
        CubeListBuilder.create().texOffs(0, 0)
                .addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(HEAD_STANDOFF)),
        PartPose.ZERO);
EntityModelLayerRegistry.registerModelLayer(MODEL, () -> LayerDefinition.create(mesh, 64, 64));
```

- Body/limbs at +0.5 clear the skin overlay's +0.25 by a quarter; head at +0.75 clears the hat layer's +0.5 by the same quarter. Quarter steps because they are vanilla's own shell-separation unit - they cannot land on another vanilla shell. Any value strictly between the shells also works (0.3 body / 0.55 head is the thin-bulk alternative) - the trade is bulk against margin, and it is an art call made by looking.
- `addOrReplaceChild("head", ...)` is safe: in the 1.21.1 player mesh, `hat` and `ear` are root children, not head children - nothing is lost.
- Hide the suit model's own overlay parts in the `HevSuitLayer` constructor - their texture regions are all-transparent (measured above), so today they buffer ~1,500 discarded-texel quads per player per frame, and any texel ever painted there would appear at an unplanned offset:

```java
suit.hat.visible = false;
suit.jacket.visible = false;
suit.leftSleeve.visible = false;
suit.rightSleeve.visible = false;
suit.leftPants.visible = false;
suit.rightPants.visible = false;
```

**Verified by eye:** third person, a skin with a full second layer (any hoodie-style skin), orbit at 5 and at 30 blocks, standing still - no flicker on chest, arms, or legs. Sneak and walk - still none. A caped account is worth one look at the back seam.

## Defect C - "doesnt show on arms"

Three verified causes, one per render path.

**C1 - first person: feature renderers never run.** Verified in the mapped bytecode: `PlayerRenderer.renderRightHand`/`renderLeftHand` call private `renderHand(PoseStack, MultiBufferSource, int, AbstractClientPlayer, ModelPart arm, ModelPart sleeve)`, which renders exactly two `ModelPart`s of the renderer's own model with `player.getSkin().texture()` - arm through `entitySolid`, sleeve through `entityTranslucent` - and returns. No `RenderLayer` runs on this path, so the suit does not exist in first person, which is where the owner lives. Fabric API 5.1.0 `fabric-rendering-v1` (the version on this classpath - class listing checked) has no first-person hand hook.

The only complete fix is a client mixin into `PlayerRenderer.renderHand`, and that collides with the mod's written law - `HevSuit`'s javadoc: "The mod still patches nothing," same stance in `OctiaClient`. The collision is the owner's to rule on, not this doc's. Both branches, specified:

- **Ruled in:** new `client\HevSuitHandMixin.java` - `@Mixin(PlayerRenderer.class)`, `@Inject(method = "renderHand", at = @At("TAIL"))`. Body: return if `player.isInvisible()`; pick the suit arm by identity (`arm == ((PlayerRenderer)(Object)this).getModel().rightArm` picks right, else left) from a client-static suit model baked once via `Minecraft.getInstance().getEntityModels().bakeLayer(HevSuit.MODEL)`; `suitArm.copyFrom(arm)` (vanilla has already posed the arm and zeroed its xRot by TAIL); render with `buffers.getBuffer(RenderType.entityCutoutNoCull(HevSuit.TEXTURE))`, `packedLight`, `OverlayTexture.NO_OVERLAY`. Plumbing: a new `octia.mixins.json` (client list) and a `"mixins"` entry in `fabric.mod.json` - which currently has none - via the integration pass.
- **Ruled out:** accept vanilla-armor behavior - vanilla never shows armor on first-person arms either. Zero code; the report stays half-answered and the HevSuit javadoc records that as a decision.

**C2 - third person, coplanar sleeves.** Most skins paint the sleeve overlay, and at +0.25 it ties with the suit arm on every texel (defect B). Where the tie breaks toward the skin, the arm reads as unsuited. Fixed by defect B's standoff change alone.

**C3 - third person, dead arm poses.** Vanilla's helper calls `EntityModel.copyPropertiesTo` (the `EntityModel`-typed overload - verified in bytecode), which copies only `attackTime`/`riding`/`young`. `HumanoidModel`'s `leftArmPose`/`rightArmPose`/`crouching` are never copied, so the suit's arms hold `EMPTY` pose while the real arms take item, bow, crossbow, spyglass, eating, and sneak poses - the skin arm rotates out of the static orange sleeve whenever anything is held, which is always. Fix is defect D's rewrite.

**Verified by eye:** (C1, if ruled in) first person, bare hand, then holding a torch, then eating - orange sleeve on the visible arm in all three. (C2/C3) third person holding a sword, drawing a bow, sneaking - the suit arm tracks the skin arm exactly, no skin emerging at the wrist or shoulder.

## Defect D - found while diagnosing, gates A and C3: the layer call is wrong twice

`HevSuitLayer.render` lines 68-71 pass `(limbSwing, limbSwingAmount, partialTick, ageInTicks, netHeadYaw, headPitch)` straight through. Verified against the mapped bytecode of `coloredCutoutModelCopyLayerRender`: its float order is `(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, partialTick)` - it calls `setupAnim(entity, f1, f2, f3, f4, f5)` and `prepareMobModel(entity, f1, f2, f6)`. Vanilla's own `SheepFurLayer` reorders at the call site; this layer does not. Net effect today: suit head yaw = ageInTicks (unbounded spin, masked by the blank head), head pitch = yaw, idle arm bob frozen, swim interpolation fed a pitch in degrees.

**Fix - drop the helper, use the armor-layer idiom.** Replace the body of `HevSuitLayer.render` after the `isInvisible` guard with:

```java
getParentModel().copyPropertiesTo(suit);
renderColoredCutoutModel(suit, HevSuit.TEXTURE, pose, buffers, packedLight, player, NO_TINT);
```

- `getParentModel()` and `suit` are both statically `PlayerModel`, so overload resolution picks `HumanoidModel.copyPropertiesTo(HumanoidModel)` - verified in bytecode to copy `leftArmPose`, `rightArmPose`, `crouching`, and the final animated transforms of head, hat, body, both arms, both legs via `ModelPart.copyFrom`. The parent was fully posed by `LivingEntityRenderer` before layers run, so the suit inherits the exact pose - item poses, sneak, swim, and all - with nothing recomputed and nothing that can drift. This closes C3 and unmasks nothing: the head stops spinning before defect A paints it.
- `renderColoredCutoutModel` is `protected static` on `RenderLayer` - directly reachable. Keep the `isInvisible()` guard; the helper carried its own and the low-level call does not.
- The parts that overload does not copy (sleeves, jacket, pants, hat) are exactly the ones defect B hides.
- Keep the six float parameters in `render`'s own signature untouched - it is the override contract.

## Order of work

D (call rewrite) and B (standoff + hidden overlays) first, in either order - both are small and independent. Then A (texture), which D unmasks. C1 last, after the owner rules on the mixin. No gametests: client render work is verified by eye, per the F6 charter line, and nothing here touches the `fabric-gametest` list.

## Defects E and F - added [2026-08-24] from the [0_6_8] playtest

The owner played the greenfield build the afternoon of 8/24 and ruled in chat:
"skin is still broken" (15:46:42, expected - no fix above is applied yet), then
the clarifying direction after review: "also note the broken textures... not
just broken model (item view instead of new model)." That splits into two
entries this doc did not carry:

**E - the suit texture is broken across the whole sheet, not just the face.**
Field evidence (screenshots `15.46.43` third person, `15.40.21` first person):
white vest, purple shoulder patches, bare orange forearms; in first person the
arm reads as an oversized purple tube with an orange fist filling the corner of
the frame. Defect A above scoped the texture work to the face rows; E widens
it - the repaint pass covers the full 64x64 sheet, and the purple/orange
patchwork is UV territory to re-verify against `HevSuit`'s part boxes, not just
paint.

**F - new items render as the flat item sprite in hand, not a model.** The
Bindle on screen is the GUI sprite extruded ("item view instead of new model" -
owner's words). Direction: every new octia item wants either a dedicated
in-hand model with display transforms, or sprite art drawn knowing it will be
extruded - crude sprite art fails twice, once in the GUI and once in the hand.
Applies to the Bindle now and to the sail-rig before its first field test (the
rig went unheld for the whole [0_6_8] session - it exists only as a recipe and
nothing in the game surfaces it).

Both are verified by eye, same as A-D; neither touches the gametest list.

---

# F7 BLOCKING - portal indicator, and the missing DIM generation

Repo: `D:\Serenity\octia`. New file owned by this feature:
`src\main\java\com\serenity\octia\client\Transit.java` (+ `src\test\java\com\serenity\octia\client\TransitTest.java`).
Shared-file touches (integration pass, from this manifest): one `Transit.bootstrap();` line in `OctiaClient.onInitializeClient` after `HevSuit.bootstrap()`; two lang keys in `en_us.json`.

Owner's report: "i dont feel any different after portaling... needs indicator... fresh cool indicator."

## The recorded finding, re-verified in the save

`D:\Serenity\octia\run\saves\[0_6_7]` (the 8/23 playtest world):

- `DIM-1\` contains exactly one file: `data\raids.dat`, 90 bytes. `DIM1\` contains exactly one file: `data\raids_end.dat`, 90 bytes. No `region\`, no `entities\`, no `poi\` in either.
- The overworld has 23 region files.
- `level.dat`: overworld generator is `octia:sky`; the nether and end are the standard stacks (`minecraft:nether`, `minecraft:the_end` noise settings). Nothing in the preset removes or stubs them.
- The 8/23 session log (`run\logs\2026-08-23-2.log.gz`) shows the server saving "chunks" for all three levels repeatedly - the levels exist and tick - and a 90-byte raids file is what an empty level writes on every save regardless of visits.

Read together: the dimensions are real and would generate normally, and **not one nether or end chunk was ever generated in that world**. Whatever the 8/23 "portal trip" was, the player never arrived in another dimension - which is the literal content of "i dont feel any different after portaling." Vanilla logs no player dimension changes, so the log cannot say what the trip did; the region absence can, and does. Candidate explanations, in likelihood order: the portal was entered in survival without standing the full warp delay; the portal was never actually lit or entered; something in the preset or a mod swallowed the teleport. Not decidable from disk. The indicator below is the instrument that decides it: once the cue exists, a reproduced portal trip that produces no cue and no `DIM-1\region\` is a broken trip, not a missing indicator - one walk in a test world separates the two bugs. That diagnostic walk is part of this feature's verification.

## The cue

Arrival grade, in the hull register: one sound and a brief overlay, fired when the client's dimension changes. Client-side and cosmetic only, the suit's stance: nothing sent, nothing saved, no server involvement, no toggle.

### Where it hooks

**Picked: the client PLAY events.**

- `ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE` (`fabric-lifecycle-events-v1` 2.6.0, on this classpath - verified; callback `(Minecraft client, ClientLevel world)`). Vanilla creates a fresh `ClientLevel` for every respawn and dimension change, so this fires for every candidate moment. Track the last seen `ResourceKey<Level>`; cue when the previous key exists and differs from `world.dimension()`.
- `ClientPlayConnectionEvents.DISCONNECT` (`fabric-networking-api-v1`, on this classpath - verified): null the tracker. Without this the first join of the next server reads as a "change" if the dimensions differ - the exact cross-save leak `OctiaDebugOverlay`'s snapshot note documents as the mod's one unclosed boundary. Do not add a second.
- Caution for the implementer: the event runs during `Minecraft.setLevel`, when `client.player` may be null. Read nothing but the passed `ClientLevel`.

The decision is a pure function, and the test asserts the decision, not the particles:

```java
/** Whether moving from one dimension id to another deserves the cue. Null previous is a join, not a trip. */
static boolean cue(String previous, String next) { ... }
```

`TransitTest` (JUnit, beside `SightlinesTest`'s kind, no world): join is silent (`cue(null, x)` false), same-dimension respawn is silent (`cue(x, x)` false), a crossing cues (`cue(x, y)` true). A cross-dimension respawn after death therefore cues - recorded as a decision, not a surprise: arriving is arriving. Nothing goes on the `fabric-gametest` list - stated so the integration pass does not wonder.

**Not picked, and why - the server events.** `ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD` plus `ServerPlayerEvents.AFTER_RESPAWN` (`fabric-entity-events-v1` 1.8.0, on this classpath - verified) can tell a portal trip from a respawn, and would then need either an S2C payload (the `OctiaDebug` idiom: `CustomPacketPayload.Type`, `PayloadTypeRegistry.playS2C()` registered common-side because both ends must know the type - `Octia.onInitialize` step 6's law) or a vanilla title/actionbar instead of a real overlay. Everything that buys - respawn discrimination - the client buys with one string compare, and the register precedent for cosmetic work (the suit, the debug overlay's draw side) is client. If the cue ever needs to differ for portal against respawn, the server route is the upgrade path, and the payload idiom to copy is `OctiaDebug.Snapshot`.

### The sound

`Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(...))` - UI-scoped, not positional, because at the moment of the swap the player has no position that means anything. The hull register already speaks beacon and amethyst (`ShipCoreBlock.useWithoutItem`: `BEACON_ACTIVATE` for CALLED, `AMETHYST_BLOCK_RESONATE` for the survey). `BEACON_ACTIVATE` is taken - reusing it would alias two meanings in one register - so:

```java
// provisional - owner tunes by walking the world
private static final SoundEvent ARRIVAL = SoundEvents.BEACON_POWER_SELECT;
private static final float ARRIVAL_VOLUME = 0.7F;
private static final float ARRIVAL_PITCH = 0.9F;
```

Played at the event, so it sounds during the swap itself, under the loading screen - transit felt now, arrival seen next.

### The overlay

Drawn through `HudRenderCallback` and timed through `ClientTickEvents.END_CLIENT_TICK` - both idioms already in `OctiaDebugOverlay.bootstrap`. The event ARMS the overlay; the countdown starts on the first HUD frame actually rendered after it, so the cue is not spent behind the "Loading terrain" screen. Hidden when `client.options.hideGui`, like the debug box.

- Two lines, top-center, provisional y = one quarter of the screen height:
  line 1 `OCTIA TRANSIT` - "OCTIA" in the register's gold, "TRANSIT" aqua, the `ShipCoreBlock` readout pattern;
  line 2 lowercase gray detail in the core's own voice: `arrived: the_nether` - the dimension printed as `level.dimension().location().getPath()`, the way `Situation.java:133` already prints it. Both lines through lang keys (`octia.transit.title`, `octia.transit.arrived`) for the integration pass.
- Duration `OVERLAY_TICKS = 50`, fading over the last 20 - both `provisional - owner tunes by walking the world`. Fade by scaling the ARGB alpha; clamp the floor at `0x10` and skip the draw below it - vanilla font rendering treats near-zero alpha as opaque, and a fade that snaps back to solid on its last frame is the bug the clamp prevents.
- Colors: the debug overlay's `0xFFD8D3C8` text and `0xFF8A8A8A` dim as the base palette, gold `0xFFFFAA00` for the label. Constants in `Transit`, not shared - the debug overlay owns its own.

### Verified by eye, and the walk that answers 8/23

1. New default world: build, light, and enter a nether portal. Cue on arrival in the nether; cue again on return. Then confirm `DIM-1\region\` now holds files.
2. New `octia:sky` world: repeat. If the cue fires and region files appear, 8/23 was a player-side miss (unlit portal or an uncompleted survival warp) and the question closes. If the cue never fires and `DIM-1` stays a raids stub, the teleport itself is broken in this preset - a new, separate defect, recorded with the world name and seed.
3. The negatives: initial world join silent; `/tp` inside the overworld silent; die and respawn in the same dimension silent; die in the nether, respawn in the overworld - cues, by the recorded decision above.
4. Quit to title mid-overlay, join a different world - no stale overlay, no cue on join (the DISCONNECT reset).
