/*
 * Vendored from owo-lib 0.13.0+1.21.11 (https://github.com/wisp-forest/owo-lib).
 * Licensed under the MIT License; see NOTICE.md at the repository root for the
 * upstream copyright notice and full licence text.
 *
 * Remapped intermediary -> Mojang and relocated by tools/vendor-owo.js.
 * Keep edits minimal so future owo-lib releases stay diffable.
 */
package sh.okx.civmodern.common.ui.util;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import sh.okx.civmodern.common.ui.CivModernUI;
import sh.okx.civmodern.common.ui.core.Color;
import sh.okx.civmodern.common.ui.core.OwoUIGraphics;
import sh.okx.civmodern.common.ui.core.PositionedRectangle;
import sh.okx.civmodern.common.ui.core.Size;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.resources.FileToIdConverter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

public class NinePatchTexture {

    private final Identifier texture;
    private final int u, v;
    private final PatchSizing patchSizing;
    private final Size textureSize;
    private final boolean repeat;

    public NinePatchTexture(Identifier texture, int u, int v, PatchSizing patchSizing, Size textureSize, boolean repeat) {
        this.texture = texture;
        this.u = u;
        this.v = v;
        this.textureSize = textureSize;
        this.patchSizing = patchSizing;
        this.repeat = repeat;
    }

    public NinePatchTexture(Identifier texture, int u, int v, Size cornerPatchSize, Size centerPatchSize, Size textureSize, boolean repeat) {
        this(texture, u, v, new PatchSizing(null, cornerPatchSize, centerPatchSize), textureSize, repeat);
    }

    public NinePatchTexture(Identifier texture, int u, int v, Size patchSize, Size textureSize, boolean repeat) {
        this(texture, u, v, new PatchSizing(patchSize, null, null), textureSize, repeat);
    }

    private Size cornerPatchSize() {
        return this.patchSizing.cornerPatchSize();
    }

    private Size centerPatchSize() {
        return this.patchSizing.centerPatchSize();
    }

    public void draw(OwoUIGraphics context, PositionedRectangle rectangle) {
        this.draw(context, rectangle, Color.WHITE);
    }

    public void draw(OwoUIGraphics context, PositionedRectangle rectangle, Color color) {
        this.draw(context, rectangle.x(), rectangle.y(), rectangle.width(), rectangle.height(), color);
    }

    public void draw(OwoUIGraphics context, int x, int y, int width, int height) {
        this.draw(context, x, y, width, height, Color.WHITE);
    }

    public void draw(OwoUIGraphics context, int x, int y, int width, int height, Color color) {
        this.draw(context, RenderPipelines.GUI_TEXTURED, x, y, width, height, color);
    }

    public void draw(OwoUIGraphics context, RenderPipeline pipeline, int x, int y, int width, int height) {
        this.draw(context, pipeline, x, y, width, height, Color.WHITE);
    }

    public void draw(OwoUIGraphics context, RenderPipeline pipeline, int x, int y, int width, int height, Color color) {
        int rightEdge = this.cornerPatchSize().width() + this.centerPatchSize().width();
        int bottomEdge = this.cornerPatchSize().height() + this.centerPatchSize().height();

        context.blit(pipeline, this.texture, x, y, this.u, this.v, this.cornerPatchSize().width(), this.cornerPatchSize().height(), this.textureSize.width(), this.textureSize.height(), color.argb());
        context.blit(pipeline, this.texture, x + width - this.cornerPatchSize().width(), y, this.u + rightEdge, this.v, this.cornerPatchSize().width(), this.cornerPatchSize().height(), this.textureSize.width(), this.textureSize.height(), color.argb());
        context.blit(pipeline, this.texture, x, y + height - this.cornerPatchSize().height(), this.u, this.v + bottomEdge, this.cornerPatchSize().width(), this.cornerPatchSize().height(), this.textureSize.width(), this.textureSize.height(), color.argb());
        context.blit(pipeline, this.texture, x + width - this.cornerPatchSize().width(), y + height - this.cornerPatchSize().height(), this.u + rightEdge, this.v + bottomEdge, this.cornerPatchSize().width(), this.cornerPatchSize().height(), this.textureSize.width(), this.textureSize.height(), color.argb());

        if (this.repeat) {
            this.drawRepeated(context, pipeline, x, y, width, height, color);
        } else {
            this.drawStretched(context, pipeline, x, y, width, height, color);
        }
    }

    protected void drawStretched(OwoUIGraphics context, RenderPipeline pipeline, int x, int y, int width, int height, Color color) {
        int doubleCornerHeight = this.cornerPatchSize().height() * 2;
        int doubleCornerWidth = this.cornerPatchSize().width() * 2;

        int rightEdge = this.cornerPatchSize().width() + this.centerPatchSize().width();
        int bottomEdge = this.cornerPatchSize().height() + this.centerPatchSize().height();

        if (width > doubleCornerWidth && height > doubleCornerHeight) {
            context.blit(pipeline, this.texture, x + this.cornerPatchSize().width(), y + this.cornerPatchSize().height(),
                this.u + this.cornerPatchSize().width(), this.v + this.cornerPatchSize().height(),
                width - doubleCornerWidth, height - doubleCornerHeight,
                this.centerPatchSize().width(), this.centerPatchSize().height(),
                this.textureSize.width(), this.textureSize.height(), color.argb());
        }

        if (width > doubleCornerWidth) {
            context.blit(pipeline, this.texture, x + this.cornerPatchSize().width(), y,
                this.u + this.cornerPatchSize().width(), this.v,
                width - doubleCornerWidth, this.cornerPatchSize().height(),
                this.centerPatchSize().width(), this.cornerPatchSize().height(),
                this.textureSize.width(), this.textureSize.height(), color.argb());
            context.blit(pipeline, this.texture, x + this.cornerPatchSize().width(), y + height - this.cornerPatchSize().height(),
                this.u + this.cornerPatchSize().width(), this.v + bottomEdge,
                width - doubleCornerWidth, this.cornerPatchSize().height(),
                this.centerPatchSize().width(), this.cornerPatchSize().height(),
                this.textureSize.width(), this.textureSize.height(), color.argb());
        }

        if (height > doubleCornerHeight) {
            context.blit(pipeline, this.texture, x, y + this.cornerPatchSize().height(),
                this.u, this.v + this.cornerPatchSize().height(),
                this.cornerPatchSize().width(), height - doubleCornerHeight,
                this.cornerPatchSize().width(), this.centerPatchSize().height(),
                this.textureSize.width(), this.textureSize.height(), color.argb());
            context.blit(pipeline, this.texture, x + width - this.cornerPatchSize().width(), y + this.cornerPatchSize().height(),
                this.u + rightEdge, this.v + this.cornerPatchSize().height(),
                this.cornerPatchSize().width(), height - doubleCornerHeight,
                this.cornerPatchSize().width(), this.centerPatchSize().height(),
                this.textureSize.width(), this.textureSize.height(), color.argb());
        }
    }

    protected void drawRepeated(OwoUIGraphics context, RenderPipeline pipeline, int x, int y, int width, int height, Color color) {
        int doubleCornerHeight = this.cornerPatchSize().height() * 2;
        int doubleCornerWidth = this.cornerPatchSize().width() * 2;

        int rightEdge = this.cornerPatchSize().width() + this.centerPatchSize().width();
        int bottomEdge = this.cornerPatchSize().height() + this.centerPatchSize().height();

        if (width > doubleCornerWidth && height > doubleCornerHeight) {
            int leftoverHeight = height - doubleCornerHeight;
            while (leftoverHeight > 0) {
                int drawHeight = Math.min(this.centerPatchSize().height(), leftoverHeight);

                int leftoverWidth = width - doubleCornerWidth;
                while (leftoverWidth > 0) {
                    int drawWidth = Math.min(this.centerPatchSize().width(), leftoverWidth);
                    context.blit(pipeline, this.texture,
                        x + this.cornerPatchSize().width() + leftoverWidth - drawWidth, y + this.cornerPatchSize().height() + leftoverHeight - drawHeight,
                        this.u + this.cornerPatchSize().width() + this.centerPatchSize().width() - drawWidth, this.v + this.cornerPatchSize().height() + this.centerPatchSize().height() - drawHeight,
                        drawWidth, drawHeight,
                        drawWidth, drawHeight,
                        this.textureSize.width(), this.textureSize.height(), color.argb());

                    leftoverWidth -= this.centerPatchSize().width();
                }
                leftoverHeight -= this.centerPatchSize().height();
            }
        }

        if (width > doubleCornerWidth) {
            int leftoverWidth = width - doubleCornerWidth;
            while (leftoverWidth > 0) {
                int drawWidth = Math.min(this.centerPatchSize().width(), leftoverWidth);

                context.blit(pipeline, this.texture, x + this.cornerPatchSize().width() + leftoverWidth - drawWidth, y,
                    this.u + this.cornerPatchSize().width() + this.centerPatchSize().width() - drawWidth, this.v,
                    drawWidth, this.cornerPatchSize().height(),
                    drawWidth, this.cornerPatchSize().height(),
                    this.textureSize.width(), this.textureSize.height(), color.argb());
                context.blit(pipeline, this.texture, x + this.cornerPatchSize().width() + leftoverWidth - drawWidth, y + height - this.cornerPatchSize().height(),
                    this.u + this.cornerPatchSize().width() + this.centerPatchSize().width() - drawWidth, this.v + bottomEdge,
                    drawWidth, this.cornerPatchSize().height(),
                    drawWidth, this.cornerPatchSize().height(),
                    this.textureSize.width(), this.textureSize.height(), color.argb());

                leftoverWidth -= this.centerPatchSize().width();
            }
        }

        if (height > doubleCornerHeight) {
            int leftoverHeight = height - doubleCornerHeight;
            while (leftoverHeight > 0) {
                int drawHeight = Math.min(this.centerPatchSize().height(), leftoverHeight);
                context.blit(pipeline, this.texture, x, y + this.cornerPatchSize().height() + leftoverHeight - drawHeight,
                    this.u, this.v + this.cornerPatchSize().height() + this.centerPatchSize().height() - drawHeight,
                    this.cornerPatchSize().width(), drawHeight,
                    this.cornerPatchSize().width(), drawHeight,
                    this.textureSize.width(), this.textureSize.height(), color.argb());
                context.blit(pipeline, this.texture, x + width - this.cornerPatchSize().width(), y + this.cornerPatchSize().height() + leftoverHeight - drawHeight,
                    this.u + rightEdge, this.v + this.cornerPatchSize().height() + this.centerPatchSize().height() - drawHeight,
                    this.cornerPatchSize().width(), drawHeight,
                    this.cornerPatchSize().width(), drawHeight,
                    this.textureSize.width(), this.textureSize.height(), color.argb());

                leftoverHeight -= this.centerPatchSize().height();
            }
        }
    }

    public static void draw(Identifier texture, OwoUIGraphics context, int x, int y, int width, int height) {
        draw(texture, context, RenderPipelines.GUI_TEXTURED, x, y, width, height);
    }

    public static void draw(Identifier texture, OwoUIGraphics context, int x, int y, int width, int height, Color color) {
        draw(texture, context, RenderPipelines.GUI_TEXTURED, x, y, width, height, color);
    }

    public static void draw(Identifier texture, OwoUIGraphics context, RenderPipeline pipeline, int x, int y, int width, int height) {
        ifPresent(texture, ninePatchTexture -> ninePatchTexture.draw(context, pipeline, x, y, width, height));
    }

    public static void draw(Identifier texture, OwoUIGraphics context, RenderPipeline pipeline, int x, int y, int width, int height, Color color) {
        ifPresent(texture, ninePatchTexture -> ninePatchTexture.draw(context, pipeline, x, y, width, height, color));
    }

    public static void draw(Identifier texture, OwoUIGraphics context, PositionedRectangle rectangle) {
        ifPresent(texture, ninePatchTexture -> ninePatchTexture.draw(context, rectangle));
    }

    public static void draw(Identifier texture, OwoUIGraphics context, PositionedRectangle rectangle, Color color) {
        ifPresent(texture, ninePatchTexture -> ninePatchTexture.draw(context, rectangle, color));
    }

    private static void ifPresent(Identifier texture, Consumer<NinePatchTexture> action) {
        if (!MetadataLoader.LOADED_TEXTURES.containsKey(texture)) return;
        action.accept(MetadataLoader.LOADED_TEXTURES.get(texture));
    }

    /**
     * Vanilla Codec replacing owo's endec-based one; endec is not vendored. The JSON shape is
     * unchanged, so owo's own nine-patch metadata files still parse verbatim.
     */
    public static final Codec<NinePatchTexture> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Identifier.CODEC.fieldOf("texture").forGetter(texture -> texture.texture),
        Codec.INT.optionalFieldOf("u", 0).forGetter(texture -> texture.u),
        Codec.INT.optionalFieldOf("v", 0).forGetter(texture -> texture.v),
        Size.CODEC.optionalFieldOf("patch_size").forGetter(texture -> Optional.ofNullable(texture.patchSizing.patchSize())),
        Size.CODEC.optionalFieldOf("corner_patch_size").forGetter(texture -> Optional.ofNullable(texture.patchSizing.rawCornerPatchSize())),
        Size.CODEC.optionalFieldOf("center_patch_size").forGetter(texture -> Optional.ofNullable(texture.patchSizing.rawCenterPatchSize())),
        Codec.INT.fieldOf("texture_width").forGetter(texture -> texture.textureSize.width()),
        Codec.INT.fieldOf("texture_height").forGetter(texture -> texture.textureSize.height()),
        Codec.BOOL.fieldOf("repeat").forGetter(texture -> texture.repeat)
    ).apply(instance, (texture, u, v, patchSize, cornerPatchSize, centerPatchSize, width, height, repeat) ->
        new NinePatchTexture(
            texture, u, v,
            new PatchSizing(patchSize.orElse(null), cornerPatchSize.orElse(null), centerPatchSize.orElse(null)),
            Size.of(width, height), repeat)));

    public record PatchSizing(@Nullable Size patchSize, @Nullable Size cornerPatchSize, @Nullable Size centerPatchSize) {
        @Nullable Size rawCornerPatchSize() {
            return this.cornerPatchSize;
        }

        @Nullable Size rawCenterPatchSize() {
            return this.centerPatchSize;
        }

        public PatchSizing {
            if (patchSize == null) {
                if ((cornerPatchSize != null && centerPatchSize == null)) {
                    throw new IllegalStateException("Missing center Patch Size while providing corner Patch Size!");
                } else if ((cornerPatchSize == null && centerPatchSize != null)) {
                    throw new IllegalStateException("Missing corner Patch Size while providing center Patch Size!");
                } else if ((cornerPatchSize == null && centerPatchSize == null)) {
                    throw new IllegalStateException("Missing base patch Size or patch size for both corner and center!");
                }
            }
        }

        @NotNull
        @Override
        public Size cornerPatchSize() {
            return (this.cornerPatchSize != null) ? this.cornerPatchSize : this.patchSize;
        }

        @NotNull
        @Override
        public Size centerPatchSize() {
            return (this.centerPatchSize != null) ? this.centerPatchSize : this.patchSize;
        }
    }

    public static class MetadataLoader extends SimpleJsonResourceReloadListener<NinePatchTexture> implements IdentifiableResourceReloadListener {

        private static final Map<Identifier, NinePatchTexture> LOADED_TEXTURES = new HashMap<>();

        public MetadataLoader() {
            super(NinePatchTexture.CODEC, FileToIdConverter.json("nine_patch_textures"));
        }

        @Override
        public Identifier getFabricId() {
            return CivModernUI.id("nine_patch_metadata");
        }

        protected void apply(Map<Identifier, NinePatchTexture> prepared, ResourceManager manager, ProfilerFiller profiler) {
            LOADED_TEXTURES.putAll(prepared);
        }
    }

}
