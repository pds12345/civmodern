package sh.okx.civmodern.common.ui;

import net.minecraft.resources.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Replaces {@code io.wispforest.owo.Owo} for the vendored owo-ui tree: the pieces of it the
 * UI framework actually used were a logger, a debug flag and an {@link Identifier} factory.
 *
 * @see sh.okx.civmodern.common.ui.core.OwoUIPipelines for the render pipelines this UI needs
 */
public final class CivModernUI {

    /** Namespace for every asset the vendored UI loads (nine-patch metadata, textures, sounds). */
    public static final String NAMESPACE = "civmodern";

    public static final Logger LOGGER = LogManager.getLogger("civmodern/ui");

    /** Enables the layout inspector overlays. Mirrors owo's {@code -Dowo.debug} switch. */
    public static final boolean DEBUG = Boolean.getBoolean("civmodern.uiDebug");

    private CivModernUI() {}

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(NAMESPACE, path);
    }
}
