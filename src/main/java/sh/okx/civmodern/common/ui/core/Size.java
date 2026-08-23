/*
 * Vendored from owo-lib 0.13.0+1.21.11 (https://github.com/wisp-forest/owo-lib).
 * Licensed under the MIT License; see NOTICE.md at the repository root for the
 * upstream copyright notice and full licence text.
 *
 * Remapped intermediary -> Mojang and relocated by tools/vendor-owo.js.
 * Keep edits minimal so future owo-lib releases stay diffable.
 */
package sh.okx.civmodern.common.ui.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.ApiStatus;

/**
 * Represents a two-dimensional value, used for
 * describing position-less rectangles in 2D-space
 *
 * @param width  The width of the rectangle
 * @param height The height of the rectangle
 */
public record Size(int width, int height) {

    public static final Codec<Size> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("width").forGetter(Size::width),
            Codec.INT.fieldOf("height").forGetter(Size::height)
    ).apply(instance, Size::of));

    private static final Size ZERO = new Size(0, 0);

    @ApiStatus.Internal
    @Deprecated(forRemoval = true)
    public Size {}

    public static Size of(int width, int height) {
        return new Size(width, height);
    }

    public static Size square(int sideLength) {
        return new Size(sideLength, sideLength);
    }

    /**
     * @return A size with both values equal to 0
     */
    public static Size zero() {
        return ZERO;
    }
}
