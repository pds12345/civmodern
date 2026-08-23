/*
 * Vendored from owo-lib 0.13.0+1.21.11 (https://github.com/wisp-forest/owo-lib).
 * Licensed under the MIT License; see NOTICE.md at the repository root for the
 * upstream copyright notice and full licence text.
 *
 * Remapped intermediary -> Mojang and relocated by tools/vendor-owo.js.
 * Keep edits minimal so future owo-lib releases stay diffable.
 */
package sh.okx.civmodern.common.ui.util;

import sh.okx.civmodern.common.ui.CivModernUI;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public class SpriteUtilInvoker {
    private static final MethodHandle MARK_SPRITE_ACTIVE = getMarkSpriteActive();

    public static void markSpriteActive(TextureAtlasSprite sprite) {
        try {
            MARK_SPRITE_ACTIVE.invoke((TextureAtlasSprite) sprite);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    private static MethodHandle getMarkSpriteActive() {
        if (FabricLoader.getInstance().isModLoaded("sodium")) {
            try {
                Class<?> spriteUtil = Class.forName("me.jellysquid.mods.sodium.client.render.texture.SpriteUtil");
                var m = spriteUtil.getMethod("markSpriteActive", TextureAtlasSprite.class);
                m.setAccessible(true);
                return MethodHandles.lookup().unreflect(m);
            } catch (Exception e) {
                CivModernUI.LOGGER.error("Couldn't get SpriteUtil.markSpriteActive from Sodium", e);
            }
        }

        return MethodHandles.empty(MethodType.methodType(void.class, TextureAtlasSprite.class));
    }
}
