/*
 * Vendored from owo-lib 0.13.0+1.21.11 (https://github.com/wisp-forest/owo-lib).
 * Licensed under the MIT License; see NOTICE.md at the repository root for the
 * upstream copyright notice and full licence text.
 *
 * Remapped intermediary -> Mojang and relocated by tools/vendor-owo.js.
 * Keep edits minimal so future owo-lib releases stay diffable.
 */
package sh.okx.civmodern.common.ui.core;

import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.DyeColor;
import net.minecraft.util.Mth;

public record Color(float red, float green, float blue, float alpha) implements Animatable<Color> {

    public static final Color BLACK = Color.ofRgb(0);
    public static final Color WHITE = Color.ofRgb(0xFFFFFF);
    public static final Color RED = Color.ofRgb(0xFF0000);
    public static final Color GREEN = Color.ofRgb(0x00FF00);
    public static final Color BLUE = Color.ofRgb(0x0000FF);

    private static final Map<String, Color> NAMED_TEXT_COLORS = Stream.of(ChatFormatting.values())
            .filter(ChatFormatting::isColor)
            .collect(ImmutableMap.toImmutableMap(formatting -> {
                return formatting.getName().toLowerCase(Locale.ROOT).replace("_", "-");
            }, Color::ofFormatting));

    public Color(float red, float green, float blue) {
        this(red, green, blue, 1f);
    }

    public static Color ofArgb(int argb) {
        return new Color(
                ((argb >> 16) & 0xFF) / 255f,
                ((argb >> 8) & 0xFF) / 255f,
                (argb & 0xFF) / 255f,
                (argb >>> 24) / 255f
        );
    }

    public static Color ofRgb(int rgb) {
        return new Color(
                ((rgb >> 16) & 0xFF) / 255f,
                ((rgb >> 8) & 0xFF) / 255f,
                (rgb & 0xFF) / 255f,
                1f
        );
    }

    public static Color ofHsv(float hue, float saturation, float value) {
        // we call .5e-7f the magic "do not turn a hue value of 1f into yellow" constant
        return ofRgb(Mth.hsvToRgb(hue - .5e-7f, saturation, value));
    }

    public static Color ofHsv(float hue, float saturation, float value, float alpha) {
        // we call .5e-7f the magic "do not turn a hue value of 1f into yellow" constant
        return ofArgb((int) (alpha * 255) << 24 | Mth.hsvToRgb(hue - .5e-7f, saturation, value));
    }

    public static Color ofFormatting(@NotNull ChatFormatting formatting) {
        var colorValue = formatting.getColor();
        return ofRgb(colorValue == null ? 0 : colorValue);
    }

    public static Color ofDye(@NotNull DyeColor dyeColor) {
        return ofArgb(dyeColor.getTextureDiffuseColor());
    }

    /**
     * Generates a random color
     * @apiNote Don't tell glisco about this
     * @author chyzman
     */
    public static Color random() {
        return ofArgb((int) (Math.random() * 0xFFFFFF) | 0xFF000000);
    }

    public int rgb() {
        return (int) (this.red * 255) << 16
                | (int) (this.green * 255) << 8
                | (int) (this.blue * 255);
    }

    public int argb() {
        return (int) (this.alpha * 255) << 24
                | (int) (this.red * 255) << 16
                | (int) (this.green * 255) << 8
                | (int) (this.blue * 255);
    }

    public float[] hsv() {
        float hue, saturation, value;

        float cmax = Math.max(Math.max(this.red, this.green), this.blue);
        float cmin = Math.min(Math.min(this.red, this.green), this.blue);

        value = cmax;
        if (cmax != 0) {
            saturation = (cmax - cmin) / cmax;
        } else {
            saturation = 0;
        }

        if (saturation == 0) {
            hue = 0;
        } else {
            float redc = (cmax - this.red) / (cmax - cmin);
            float greenc = (cmax - this.green) / (cmax - cmin);
            float bluec = (cmax - this.blue) / (cmax - cmin);

            if (this.red == cmax) {
                hue = bluec - greenc;
            } else if (this.green == cmax)
                hue = 2.0f + redc - bluec;
            else {
                hue = 4.0f + greenc - redc;
            }

            hue = hue / 6.0f;
            if (hue < 0) hue = hue + 1.0f;
        }

        return new float[]{hue, saturation, value, this.alpha};
    }

    public String asHexString(boolean includeAlpha) {
        return includeAlpha
                ? String.format("#%08X", this.argb())
                : String.format("#%06X", this.rgb());
    }


    @Override
    public Color interpolate(Color next, float delta) {
        return new Color(
                Mth.lerp(delta, this.red, next.red),
                Mth.lerp(delta, this.green, next.green),
                Mth.lerp(delta, this.blue, next.blue),
                Mth.lerp(delta, this.alpha, next.alpha)
        );
    }

}
