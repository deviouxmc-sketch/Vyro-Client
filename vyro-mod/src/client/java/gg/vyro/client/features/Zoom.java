package gg.vyro.client.features;

/** Smooth zoom: a multiplier on the field of view that eases towards its target. */
public final class Zoom {
    private Zoom() {}

    private static float current = 1.0f;
    private static float target = 1.0f;
    private static long lastNs = System.nanoTime();

    public static void setTarget(float multiplier) {
        target = Math.max(0.05f, Math.min(1.0f, multiplier));
    }

    /** FOV multiplier (1 = no zoom). Time based, so calling it several times per frame is harmless. */
    public static float multiplier() {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastNs) / 1.0e9f);
        lastNs = now;
        current += (target - current) * (1f - (float) Math.exp(-dt * 14f));
        if (Math.abs(current - target) < 0.002f) current = target;
        return current;
    }

    public static boolean active() {
        return current < 0.999f || target < 0.999f;
    }
}
