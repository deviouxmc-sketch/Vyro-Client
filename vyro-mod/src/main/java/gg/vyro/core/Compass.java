package gg.vyro.core;

/** Minecraft yaw: 0 = south (+Z), 90 = west (-X), 180/-180 = north (-Z), -90 = east (+X). */
public final class Compass {
    private static final String[] NAMES = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
    private static final String[] AXIS = {"+Z", "-X +Z", "-X", "-X -Z", "-Z", "+X -Z", "+X", "+X +Z"};

    private Compass() {}

    private static int index(float yaw) {
        float y = yaw % 360f;
        if (y < 0) y += 360f;
        return Math.round(y / 45f) % 8;
    }

    public static String name(float yaw) {
        return NAMES[index(yaw)];
    }

    public static String axis(float yaw) {
        return AXIS[index(yaw)];
    }
}
