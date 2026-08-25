package com.serenity.octia.client;

import com.serenity.octia.Octia;
import com.serenity.octia.entity.VoidSquid;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * A mantle and eight strands, cut to fit a sixteen-by-sixteen sheet.
 *
 * <p><b>The sheet size is the whole shape of this model, and it was chosen
 * before a single box was.</b> {@code docs/PALETTE.md} makes an ASCII grid the
 * source of record for every texture in the mod and {@code tools/pixel.py} the
 * one thing that compiles one, and pixel.py writes 16x16 and nothing else. An
 * entity sheet does not have to be 16x16 - it could have been hand-authored at
 * 32x32 as a PNG - but a hand-authored PNG is a texture with no readable source,
 * which is the exact failure PALETTE.md exists to prevent: <i>a PNG edited
 * directly is a correction the next regeneration silently throws away.</i> So
 * the model was built to the tool instead of the tool being set aside for the
 * model.
 *
 * <p><b>What 16x16 buys and what it costs.</b> A box of {@code w x h x d} needs
 * {@code 2(w + d)} by {@code d + h} texels, so the whole budget is
 * {@code w + d <= 8}. The mantle is 4x5x4, which is exactly 16 by 9 and fills
 * the top of the sheet; the strand is 1x5x1, four by six, and sits under it. All
 * eight strands share that one region, which is what vanilla's own squid does
 * and is the only reason eight of them fit. Row 15 and the right of the strand
 * band are unused and painted in the outline key rather than left transparent,
 * because pixel.py enforces a closed outline and a fully opaque sheet with a
 * dark border ring satisfies it without costing the picture anything - the
 * animal is drawn in ink and void black, so its border being dark is what it
 * would have been anyway.
 *
 * <p><b>Grown, not scaled.</b> A 4x5x4 mantle is a third of a block and far too
 * small beside a 0.7-block hitbox, and the obvious fix - scaling in the
 * renderer - moves the pose stack's origin as well as the model, so the creature
 * ends up floating away from its own shadow and its own hitbox. {@link
 * CubeDeformation} instead: it inflates the rendered cube while leaving the
 * texture mapping alone, which is the same trick {@link HevSuit} uses for the
 * suit standoff. The mantle renders at 8x9x8 out of a 4x5x4 UV footprint, the
 * renderer scales nothing, and the texel density stays deliberately coarse -
 * which is the register the owner asked for: <i>crappy opensource pixel art</i>.
 */
public class VoidSquidModel extends HierarchicalModel<VoidSquid> {

    /**
     * The baked model, keyed in this mod's namespace. "main" is what vanilla
     * calls the layer every ordinary entity model uses - the {@link HevSuit}
     * precedent.
     */
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(Octia.id("void_squid"), "main");

    /** Both edges of the sheet. See the class note on why it is not larger. */
    private static final int SHEET = 16;

    private static final String MANTLE = "mantle";

    /** Eight, because a squid has eight and because they share one UV island. */
    private static final int STRANDS = 8;

    /** How far the rendered mantle stands off its 4x5x4 texture footprint. */
    private static final float MANTLE_GROWTH = 2.0F;

    /** The same for a strand, which only needs to stop being one texel wide. */
    private static final float STRAND_GROWTH = 0.25F;

    /**
     * Where the mantle's middle sits, in model units down from the top of the
     * standard 24-unit frame. 18 puts an 8x9x8 body across the middle of a
     * 0.7-block hitbox instead of hanging out of the top of it.
     */
    private static final float MANTLE_Y = 18.0F;

    /** Where the strands are hung from, and how far out from the middle. */
    private static final float STRAND_Y = 21.0F;
    private static final float RING = 2.5F;

    /** The sway: how fast, and how far. Slow and small - it is drifting. */
    private static final float SWAY_RATE = 0.06F;   // provisional - owner tunes by walking the world
    private static final float SWAY_REACH = 0.22F;  // provisional - owner tunes by walking the world

    /** How much the mantle itself leans over the same cycle. */
    private static final float LEAN = 0.06F;        // provisional - owner tunes by walking the world

    private final ModelPart root;
    private final ModelPart mantle;
    private final ModelPart[] strands = new ModelPart[STRANDS];

    public VoidSquidModel(ModelPart root) {
        this.root = root;
        this.mantle = root.getChild(MANTLE);
        for (int i = 0; i < STRANDS; i++) {
            this.strands[i] = root.getChild(strand(i));
        }
    }

    /** The strands are numbered rather than named, since they are identical. */
    private static String strand(int index) {
        return "strand" + index;
    }

    /**
     * The mesh. One mantle, then eight strands off one {@link CubeListBuilder} -
     * reusing the builder is what makes them share the sheet's second island,
     * and it is what vanilla's squid does.
     */
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        root.addOrReplaceChild(MANTLE,
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-2.0F, -2.5F, -2.0F, 4.0F, 5.0F, 4.0F,
                                new CubeDeformation(MANTLE_GROWTH)),
                PartPose.offset(0.0F, MANTLE_Y, 0.0F));

        CubeListBuilder island = CubeListBuilder.create().texOffs(0, 9)
                .addBox(-0.5F, 0.0F, -0.5F, 1.0F, 5.0F, 1.0F,
                        new CubeDeformation(STRAND_GROWTH));

        for (int i = 0; i < STRANDS; i++) {
            double around = i * 2 * Math.PI / STRANDS;
            root.addOrReplaceChild(strand(i), island,
                    PartPose.offset((float) (Math.cos(around) * RING), STRAND_Y,
                            (float) (Math.sin(around) * RING)));
        }

        return LayerDefinition.create(mesh, SHEET, SHEET);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    /**
     * One slow cycle, and nothing that reads the world.
     *
     * <p>The strands lean out and back on a single sine, each rotated by where
     * it sits on the ring so the set opens and closes rather than all swinging
     * the same way. Nothing here consults the squid at all, which is correct: a
     * void squid has no state a viewer can see - no target, no anger, no age -
     * so an animation that asked it something would be asking about nothing.
     *
     * <p>{@code Mth.sin} rather than {@code Math.sin} because the argument is
     * {@code ageInTicks}, which grows without bound: Mth's table lookup wraps,
     * and the double version would slowly lose its shape on a long-lived world.
     */
    @Override
    public void setupAnim(VoidSquid squid, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        float sway = Mth.sin(ageInTicks * SWAY_RATE) * SWAY_REACH;

        for (int i = 0; i < STRANDS; i++) {
            double around = i * 2 * Math.PI / STRANDS;
            strands[i].xRot = (float) (sway * Math.cos(around));
            strands[i].zRot = (float) (-sway * Math.sin(around));
        }

        mantle.xRot = Mth.cos(ageInTicks * SWAY_RATE) * LEAN;
        mantle.zRot = sway * LEAN;
    }
}
