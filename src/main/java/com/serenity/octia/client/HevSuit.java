package com.serenity.octia.client;

import com.serenity.octia.Octia;

import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * What everybody is wearing, and cannot take off.
 *
 * <p>A hazardous environment suit, worn <em>over</em> whatever skin a player
 * actually has rather than in place of it. The fiction is one line long and the
 * rest of the mod already implies it: somebody went to work in a facility,
 * something went wrong with the geometry of the place, and they came out
 * somewhere with grass on it. The ship, the dig, the derelicts and the obelisks
 * are all what that person found after they arrived. The suit is what they
 * arrived in, and their own face is still inside it.
 *
 * <p><b>A shell, not a repaint.</b> The suit is the player model inflated by a
 * quarter of a block and drawn as a second pass over the first, so it stands
 * off the body the way a suit does. Where its texture is transparent - the
 * whole head - nothing is drawn at all and the player's own skin is what you
 * see. That is the entire mechanism, and it is why this could not be done by
 * swapping the skin texture: a swap replaces a person, a layer dresses one.
 *
 * <p><b>No mixin.</b> An earlier cut of this patched
 * {@code AbstractClientPlayer.getSkin()}, which worked and was the wrong shape.
 * Fabric offers {@link LivingEntityFeatureRendererRegistrationCallback}
 * precisely so a mod can add a layer to a vanilla renderer without patching
 * bytecode, and {@code OctiaClient}'s own note already states the rule this
 * mod holds to: a mixin into vanilla is a class of breakage that arrives
 * silently on the next Minecraft release. The mod still patches nothing.
 *
 * <p><b>Everyone, not only you.</b> The callback fires for every player
 * renderer the game builds - wide-armed and slim - so LAN guests and the
 * mustered crew are suited too. That is the point rather than an oversight: a
 * bench of eight identical orange suits reads as a crew, and one suit among
 * seven Steves reads as a bug.
 *
 * <p><b>Client-side and cosmetic only.</b> Nothing here is sent anywhere,
 * nothing is saved, and a server neither knows nor cares. There is no toggle
 * and no config, by request - the suit does not come off.
 */
public final class HevSuit {

    /**
     * The texture, in this mod's namespace. Built through {@link Octia#id} like
     * everything else - see that method's note on why a namespaced literal is
     * the one thing a rename cannot find.
     */
    static final ResourceLocation TEXTURE = Octia.id("textures/entity/hev_suit.png");

    /**
     * How far the shell stands off the body, in blocks.
     *
     * <p>A quarter. Enough that the suit is visibly outside the skin at every
     * angle and the seams do not z-fight with the body underneath; small enough
     * that a player still fits through their own doorway, which is a rendering
     * question and not a collision one - the hitbox is untouched.
     */
    private static final float STANDOFF = 0.25F;

    /**
     * The baked model, keyed in this mod's namespace.
     *
     * <p>The second component is the {@code layer} name inside the location, and
     * "main" is what vanilla calls the one every ordinary entity model uses.
     */
    static final ModelLayerLocation MODEL = new ModelLayerLocation(Octia.id("hev_suit"), "main");

    private HevSuit() {
    }

    /** Registers the model and hangs the layer on every player renderer. */
    public static void bootstrap() {
        // Wide, not slim, and the choice is load-bearing. An oversuit is bulky,
        // and a four-pixel shell sits correctly over a three-pixel arm while a
        // three-pixel shell would pass through a four-pixel one and show the
        // body coming out through the sleeve. One mesh covers both kinds of
        // player because the shell is bigger than either.
        EntityModelLayerRegistry.registerModelLayer(MODEL,
                () -> LayerDefinition.create(
                        PlayerModel.createMesh(new CubeDeformation(STANDOFF), false), 64, 64));

        LivingEntityFeatureRendererRegistrationCallback.EVENT.register(
                (entityType, renderer, helper, context) -> {
                    // Asked of the renderer rather than the entity type. There
                    // is one player EntityType and several player renderers -
                    // one per skin model - and it is the renderer that has to
                    // carry the layer, so the renderer is the thing to test.
                    if (renderer instanceof PlayerRenderer player) {
                        helper.register(new HevSuitLayer(player, context.getModelSet()));
                    }
                });
    }
}
