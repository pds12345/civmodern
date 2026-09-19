package sh.okx.civmodern.common.map;

/**
 * The one formula behind the four "waypoint size" config numbers, shared by the map screen, the
 * minimap and the size preview screen so they can never disagree.
 */
public final class WaypointScaling {

    private WaypointScaling() {
    }

    /**
     * How much smaller than native size an icon is drawn at {@code zoom} (blocks per pixel).
     * Full size at or below {@code baseZoom}; every further multiplication of the zoom by
     * {@code logBase} adds one step, and the icon is drawn at {@code 1 / (1 + steps)}.
     */
    public static float scale(float zoom, float baseZoom, float logBase) {
        float zoomSteps = (float) (Math.log(zoom / baseZoom) / Math.log(logBase));
        return 1f / (1f + Math.max(0f, zoomSteps));
    }
}
