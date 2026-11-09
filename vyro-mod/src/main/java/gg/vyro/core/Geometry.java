package gg.vyro.core;

/** Hit-testing and drag/resize maths shared by the HUD editor. */
public final class Geometry {
    private Geometry() {}

    public static boolean contains(float x, float y, float w, float h, double px, double py) {
        return px >= x && px < x + w && py >= y && py < y + h;
    }

    public static float clamp(float v, float lo, float hi) {
        return hi < lo ? lo : Math.max(lo, Math.min(hi, v));
    }

    /** Keep a w*h box fully on a screen of sw*sh. Returns the corrected top-left x. */
    public static float clampX(float x, float w, float sw) {
        return clamp(x, 0, sw - w);
    }

    public static float clampY(float y, float h, float sh) {
        return clamp(y, 0, sh - h);
    }

    /** New scale after dragging the bottom-right handle by {@code dx} pixels (box was {@code baseW} wide at scale 1). */
    public static float resizeScale(float startScale, float baseW, float dx) {
        if (baseW <= 0) return startScale;
        return clamp(startScale + dx / baseW, 0.5f, 3.0f);
    }
}
