/*
 * Vendored from owo-lib 0.13.0+1.21.11 (https://github.com/wisp-forest/owo-lib).
 * Licensed under the MIT License; see NOTICE.md at the repository root for the
 * upstream copyright notice and full licence text.
 *
 * Remapped intermediary -> Mojang and relocated by tools/vendor-owo.js.
 * Keep edits minimal so future owo-lib releases stay diffable.
 */
package sh.okx.civmodern.common.ui.core;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.resources.Identifier;
import sh.okx.civmodern.common.ui.CivModernUI;
import org.jetbrains.annotations.ApiStatus;

public final class OwoUIPipelines {

    public static final RenderPipeline GUI_TRIANGLE_FAN = RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
        .withLocation(Identifier.fromNamespaceAndPath(CivModernUI.NAMESPACE, "pipeline/gui_triangle_fan"))
        .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLE_FAN)
        .build();

    public static final RenderPipeline GUI_TRIANGLE_STRIP = RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
        .withLocation(Identifier.fromNamespaceAndPath(CivModernUI.NAMESPACE, "pipeline/gui_triangle_strip"))
        .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLE_STRIP)
        .build();

    public static final RenderPipeline GUI_TEXTURED_NO_BLEND = RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
        .withLocation(Identifier.fromNamespaceAndPath(CivModernUI.NAMESPACE, "pipeline/gui_textured"))
        .withoutBlend()
        .build();

    @ApiStatus.Internal
    public static void register() {
        RenderPipelines.register(GUI_TRIANGLE_FAN);
        RenderPipelines.register(GUI_TRIANGLE_STRIP);
        RenderPipelines.register(GUI_TEXTURED_NO_BLEND);
    }
}
