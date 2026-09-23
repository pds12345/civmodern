package sh.okx.civmodern.common.rendering;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.util.Util;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

import java.util.function.Function;

public class CivModernRenderTypes {
    public static final Function<Identifier, RenderType> TEXT = Util.memoize(
        resourceLocation -> RenderType.create(
            "text",
            RenderSetup.builder(CivModernPipelines.TEXT)
                .withTexture("Sampler0", resourceLocation)
                .createRenderSetup()
        )
    );

    public static final Function<Identifier, RenderType> TEXT2 = Util.memoize(
        resourceLocation -> RenderType.create(
            "text2",
            RenderSetup.builder(CivModernPipelines.TEXT2)
                .withTexture("Sampler0", resourceLocation)
                .createRenderSetup()
        )
    );

    // Untextured, so unlike TEXT/TEXT2 this needs no per-resource memoization - one RenderType
    // covers every waypoint column.
    public static final RenderType COLUMN = RenderType.create(
        "column",
        RenderSetup.builder(CivModernPipelines.COLUMN).createRenderSetup()
    );

    /** The circular minimap's corner mask, drawn inside its offscreen picture. */
    public static final RenderType MINIMAP_MASK = RenderType.create(
        "civmodern_minimap_mask",
        RenderSetup.builder(CivModernPipelines.MINIMAP_MASK).createRenderSetup()
    );

    /**
     * Untextured GUI quads drawn inside an offscreen picture: node territory inside the circular
     * minimap's picture (so the mask clips it too), and the outlines in the snitch layer.
     */
    public static final RenderType PICTURE_QUADS = RenderType.create(
        "civmodern_picture_quads",
        RenderSetup.builder(CivModernPipelines.GUI_QUADS).createRenderSetup()
    );

    /** A snitch's block face, drawn inside the snitch layer's picture. One per block texture. */
    public static final Function<Identifier, RenderType> SNITCH_ICON = Util.memoize(
        texture -> RenderType.create(
            "civmodern_snitch_icon",
            RenderSetup.builder(RenderPipelines.GUI_TEXTURED)
                .withTexture("Sampler0", texture)
                .createRenderSetup()
        )
    );

    /** The whole-picture fade, see {@link CivModernPipelines#LAYER_FADE}. */
    public static final RenderType LAYER_FADE = RenderType.create(
        "civmodern_layer_fade",
        RenderSetup.builder(CivModernPipelines.LAYER_FADE).createRenderSetup()
    );
}
