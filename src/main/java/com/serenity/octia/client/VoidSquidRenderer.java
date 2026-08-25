package com.serenity.octia.client;

import com.serenity.octia.Octia;
import com.serenity.octia.entity.OctiaEntities;
import com.serenity.octia.entity.VoidSquid;

import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * What a void squid looks like from a ledge.
 *
 * <p>The whole of it: one texture, one model, and no layers. There is no glow,
 * no held item, no name plate worth drawing and no state to show - see
 * {@link VoidSquidModel#setupAnim} on why an animation that asked the squid a
 * question would be asking about nothing.
 *
 * <p><b>No shadow, and it is not an omission.</b> {@code MobRenderer} takes a
 * shadow radius and vanilla's flyers pass a real one because they fly over
 * ground. This animal lives under the continent in open void, where the shadow
 * would be cast onto nothing at all, and on the rare tick it drifted over
 * something the round blot under it would be the only thing in the world saying
 * it was solid. Zero skips the shadow entirely.
 *
 * <p><b>Client only, and reached from {@code OctiaClient}.</b> Both halves are
 * one-shot registrations - a model layer that must exist before any model is
 * baked, and a renderer that must exist before any entity is drawn - so this
 * hangs off the client entrypoint the way {@link HevSuit} does rather than being
 * done lazily on a first render, which would have missed the only moment it
 * wanted.
 */
public class VoidSquidRenderer extends MobRenderer<VoidSquid, VoidSquidModel> {

    /**
     * The texture, in this mod's namespace, built through {@link Octia#id} like
     * everything else - a namespaced literal is the one thing a rename cannot
     * find. Compiled from {@code art/entity/void_squid.txt} by
     * {@code tools/pixel.py}; the grid is the source of record and the PNG is
     * derived.
     */
    private static final ResourceLocation TEXTURE = Octia.id("textures/entity/void_squid.png");

    /** No shadow. See the class note. */
    private static final float SHADOW = 0.0F;

    public VoidSquidRenderer(EntityRendererProvider.Context context) {
        super(context, new VoidSquidModel(context.bakeLayer(VoidSquidModel.LAYER)), SHADOW);
    }

    @Override
    public ResourceLocation getTextureLocation(VoidSquid squid) {
        return TEXTURE;
    }

    /**
     * Registers the model layer and the renderer.
     *
     * <p>The layer first: {@code bakeLayer} in this class's own constructor
     * resolves against the layer registry, and a renderer built before its layer
     * is filed is a crash at the first squid rather than at load. Reading
     * {@link OctiaEntities#VOID_SQUID} here is what ties the two sides together,
     * and it is safe only because Fabric runs the main entrypoint before the
     * client one - see the invariant note on {@code OctiaEntities}.
     */
    public static void bootstrap() {
        EntityModelLayerRegistry.registerModelLayer(VoidSquidModel.LAYER,
                VoidSquidModel::createBodyLayer);
        EntityRendererRegistry.register(OctiaEntities.VOID_SQUID, VoidSquidRenderer::new);
    }
}
