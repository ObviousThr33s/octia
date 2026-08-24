package com.serenity.octia.client;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

/**
 * The suit itself, as a second pass over the player.
 *
 * <p>One method, and almost all of it is vanilla's. {@code
 * coloredCutoutModelCopyLayerRender} is the helper the game's own copy layers
 * use - a sheep's wool, a llama's rug - and it does the two things that make a
 * worn layer work:
 *
 * <ul>
 *   <li><b>It copies the parent's pose onto ours.</b> Every part of the shell
 *       takes its angle from the same part of the body underneath, so the suit
 *       walks, swims, sneaks and swings an arm with the player inside it rather
 *       than standing rigid around them. Nothing here animates anything; the
 *       animation is borrowed, which is why there is no way for the two to fall
 *       out of step.</li>
 *   <li><b>It draws through a cutout render type.</b> A fully transparent texel
 *       is discarded rather than blended, so the transparent head of the suit
 *       texture is not a faint pane over the player's face - it is nothing at
 *       all, and what shows is their own skin. Cutout rather than translucent
 *       on purpose: translucent would sort by depth every frame and put a
 *       shimmer on a thing that should simply be solid where it is solid.</li>
 * </ul>
 *
 * <p><b>The white is not a colour choice.</b> The last argument is a packed
 * ARGB tint multiplied into the texture, and white at full alpha is the
 * identity - the suit renders exactly as it is drawn. It is the same value
 * vanilla passes for a layer it does not want to tint, and it is written as a
 * constant here so nobody reads {@code -1} and wonders.
 */
final class HevSuitLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    /** Opaque white: multiply by one, tint nothing. */
    private static final int NO_TINT = 0xFFFFFFFF;

    private final PlayerModel<AbstractClientPlayer> suit;

    HevSuitLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent,
                 EntityModelSet models) {
        super(parent);
        // Baked once, here, rather than per frame. Wide-armed regardless of the
        // player it is going over - see the note on HevSuit.MODEL.
        this.suit = new PlayerModel<>(models.bakeLayer(HevSuit.MODEL), false);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int packedLight,
                       AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        // Invisible means invisible. A player under a potion, or a spectator,
        // is not wearing a visible suit either - vanilla stops rendering the
        // body and a layer that kept going would draw an empty orange shell
        // walking around on its own.
        if (player.isInvisible()) {
            return;
        }

        coloredCutoutModelCopyLayerRender(getParentModel(), suit, HevSuit.TEXTURE,
                pose, buffers, packedLight, player,
                limbSwing, limbSwingAmount, partialTick, ageInTicks, netHeadYaw, headPitch,
                NO_TINT);
    }
}
