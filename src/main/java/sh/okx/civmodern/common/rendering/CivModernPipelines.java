package sh.okx.civmodern.common.rendering;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public class CivModernPipelines {
    public static final RenderPipeline GUI_TRIANGLE_STRIP_BLEND = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
        .withLocation(Identifier.fromNamespaceAndPath("owo", "pipeline/gui_triangle_strip2"))
        .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLE_STRIP)
        .build());
    public static final RenderPipeline GUI_QUADS = RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
        .withLocation(Identifier.fromNamespaceAndPath("civmodern", "pipeline/gui_quads2"))
        .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
        .build();

    public static final RenderPipeline TEXT2 = RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
        .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
        .withBlend(new BlendFunction(SourceFactor.SRC_ALPHA, DestFactor.ONE_MINUS_SRC_ALPHA, SourceFactor.ONE, DestFactor.ONE_MINUS_SRC_ALPHA))
        .withDepthWrite(true)
        .withLocation(Identifier.fromNamespaceAndPath("civmodern", "pipeline/text"))
        .build();
    public static final RenderPipeline TEXT = RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
        .withBlend(new BlendFunction(SourceFactor.SRC_ALPHA, DestFactor.ONE_MINUS_SRC_ALPHA, SourceFactor.ONE, DestFactor.ONE_MINUS_SRC_ALPHA))
        .withDepthWrite(true)
        .withLocation(Identifier.fromNamespaceAndPath("civmodern", "pipeline/text"))
        .build();

    // Untextured translucent quads for the waypoint column: depth-tested against terrain (so it
    // is normally occluded like real world geometry, unlike the always-visible waypoint icon)
    // but writes no depth, so the three nested translucent shells do not occlude each other or
    // fight for pixels. Culling is off so the inside faces stay visible when the camera is inside
    // a shell (it commonly is, given how close to the waypoint the shells are).
    public static final RenderPipeline COLUMN = RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
        .withLocation(Identifier.fromNamespaceAndPath("civmodern", "pipeline/column"))
        .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
        .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
        .withBlend(new BlendFunction(SourceFactor.SRC_ALPHA, DestFactor.ONE_MINUS_SRC_ALPHA, SourceFactor.ONE, DestFactor.ONE_MINUS_SRC_ALPHA))
        .withDepthWrite(false)
        .withCull(false)
        .build();

    // Punches the corners out of the circular minimap's offscreen picture. The blend function
    // zeroes both source and destination, so whatever tiles were drawn there become transparent
    // black and the world shows through once the picture is composited. The quads themselves are
    // drawn opaque: the GUI fragment shader discards alpha-zero fragments, so a transparent quad
    // would never reach the texture at all.
    public static final RenderPipeline MINIMAP_MASK = RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
        .withLocation(Identifier.fromNamespaceAndPath("civmodern", "pipeline/minimap_mask"))
        .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
        .withBlend(new BlendFunction(SourceFactor.ZERO, DestFactor.ZERO))
        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
        .withDepthWrite(false)
        .withCull(false)
        .build();

    // Fades a finished offscreen picture in one stroke, for a layer whose pieces overlap. Drawn
    // over the picture as a white quad of alpha a, the blend multiplies every channel already
    // there by a - colour and alpha alike, so the picture stays premultiplied, which is how the
    // GUI composites pictures. Fading each piece as it was drawn would stack wherever pieces
    // overlap, and a dense cluster would end up as opaque as ever.
    public static final RenderPipeline LAYER_FADE = RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
        .withLocation(Identifier.fromNamespaceAndPath("civmodern", "pipeline/layer_fade"))
        .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
        .withBlend(new BlendFunction(SourceFactor.ZERO, DestFactor.SRC_ALPHA))
        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
        .withDepthWrite(false)
        .withCull(false)
        .build();

    public static final RenderPipeline.Snippet MATRICES_PROJECTION_SNIPPET = RenderPipeline.builder().withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER).withUniform("Projection", UniformType.UNIFORM_BUFFER).buildSnippet();
    public static final RenderPipeline.Snippet COLOR_WRITE = RenderPipeline.builder().withColorWrite(true).withDepthWrite(false).buildSnippet();
    public static final RenderPipeline.Snippet POSITION_TEX_COLOR_SHADER = RenderPipeline.builder(MATRICES_PROJECTION_SNIPPET).withLocation("cm/pipeline/position_tex_color").withVertexShader("core/position_tex_color").withFragmentShader("core/position_tex_color").withSampler("Sampler0").buildSnippet();
    public static final RenderPipeline REGION_DEFAULT_RENDER_PIPELINE = RenderPipeline.builder(POSITION_TEX_COLOR_SHADER, COLOR_WRITE).withLocation("cm/pipeline/pos_tex_color").withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS).withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST).withBlend(BlendFunction.TRANSLUCENT).withCull(false).build();

    public static void register() {
        RenderPipelines.register(GUI_QUADS);
        RenderPipelines.register(TEXT);
        RenderPipelines.register(TEXT2);
        RenderPipelines.register(COLUMN);
        RenderPipelines.register(MINIMAP_MASK);
        RenderPipelines.register(LAYER_FADE);
        RenderPipelines.register(REGION_DEFAULT_RENDER_PIPELINE);
    }
}
