package gg.vyro.client.features;

/**
 * Look around without turning the player. While active, mouse movement changes this camera
 * rotation instead of the player's, and the camera mixin renders from it. When released the
 * camera eases back to the player's rotation instead of snapping.
 */
public final class Freelook {
    private Freelook() {}

    public record Rot(float yaw, float pitch) {}

    private static boolean requested;
    private static boolean returning;
    /** Once the camera hook has run even once, we know mouse input can safely be redirected. */
    private static boolean hookEverRan;
    private static long lastNs = System.nanoTime();
    public static float yaw;
    public static float pitch;

    public static void start(float playerYaw, float playerPitch) {
        if (!returning) {
            yaw = playerYaw;
            pitch = playerPitch;
        } // if we were still easing back, continue from where the camera is
        requested = true;
        returning = false;
    }

    public static void stop() {
        if (requested) returning = true;
        requested = false;
    }

    /** The camera mixin reports in each frame it actually ran. */
    public static void cameraHookRan() {
        hookEverRan = true;
    }

    /**
     * Mouse input is only taken from the player once the camera is confirmed to follow us.
     * If the camera hook failed to apply, holding the key does nothing instead of freezing the view.
     */
    public static boolean active() {
        return requested && hookEverRan;
    }

    /** True while the camera should be pulled back (active, or still easing home). */
    public static boolean wantsThirdPerson() {
        return (active() || returning) && gg.vyro.client.config.VyroConfig.get().features.freelookThirdPerson;
    }

    public static boolean requested() {
        return requested;
    }

    public static boolean returning() {
        return returning;
    }

    public static void turn(double dYaw, double dPitch) {
        yaw += (float) dYaw * 0.15f;
        pitch = Math.max(-90f, Math.min(90f, pitch + (float) dPitch * 0.15f));
    }

    private static float wrap(float deg) {
        deg %= 360f;
        if (deg > 180f) deg -= 360f;
        if (deg < -180f) deg += 360f;
        return deg;
    }

    /** One easing step towards the player's real rotation; ends the return when close enough. */
    public static Rot returnStep(float targetYaw, float targetPitch) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastNs) / 1.0e9f);
        lastNs = now;
        float k = 1f - (float) Math.exp(-dt * 16f);
        float dy = wrap(targetYaw - yaw);
        float dp = targetPitch - pitch;
        yaw += dy * k;
        pitch += dp * k;
        if (Math.abs(dy) < 0.3f && Math.abs(dp) < 0.3f) {
            returning = false;
            return new Rot(targetYaw, targetPitch);
        }
        return new Rot(yaw, pitch);
    }

    /** Keeps the easing clock fresh while we are not returning, so the first step is never huge. */
    public static void touchClock() {
        lastNs = System.nanoTime();
    }
}
