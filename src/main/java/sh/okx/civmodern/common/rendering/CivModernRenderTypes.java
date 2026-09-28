package sh.okx.civmodern.common.rendering;

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

    /** Node territory drawn inside the circular minimap's picture, so the mask clips it too. */
    public static final RenderType MINIMAP_NODES = RenderType.create(
        "civmodern_minimap_nodes",
        RenderSetup.builder(CivModernPipelines.GUI_QUADS).createRenderSetup()
    );
}
