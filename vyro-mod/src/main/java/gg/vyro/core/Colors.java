package gg.vyro.core;

/** ARGB helpers plus the VYRO palette. */
public final class Colors {
    public static final int BG = 0xFF0B0B10;
    public static final int PANEL = 0xFF13131B;
    public static final int PANEL_2 = 0xFF1A1A25;
    public static final int TEXT = 0xFFECECF4;
    public static final int MUTED = 0xFF8B8BA3;
    public static final int PURPLE = 0xFF8B5CF6;
    public static final int BLUE = 0xFF3B82F6;
    public static final int CYAN = 0xFF22D3EE;
    public static final int GREEN = 0xFF34D399;
    public static final int YELLOW = 0xFFFBBF24;
    public static final int RED = 0xFFEF4444;

    private Colors() {}

    public static int withAlpha(int argb, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (argb & 0x00FFFFFF);
    }

    public static int scaleAlpha(int argb, float factor) {
        int a = (argb >>> 24);
        return withAlpha(argb, Math.round(a * Math.max(0f, Math.min(1f, factor))));
    }

    public static int lerp(int from, int to, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int a = (int) (((from >>> 24) & 255) + (((to >>> 24) & 255) - ((from >>> 24) & 255)) * t);
        int r = (int) (((from >> 16) & 255) + (((to >> 16) & 255) - ((from >> 16) & 255)) * t);
        int g = (int) (((from >> 8) & 255) + (((to >> 8) & 255) - ((from >> 8) & 255)) * t);
        int b = (int) ((from & 255) + ((to & 255) - (from & 255)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** The "aura": a slow loop purple -> blue -> cyan -> blue -> purple. {@code phase} in seconds. */
    public static int aura(double phase) {
        double x = (phase % 6.0) / 6.0 * 3.0; // 0..3 across three segments
        int seg = (int) Math.floor(x);
        float t = (float) (x - seg);
        t = t * t * (3 - 2 * t); // smoothstep
        return switch (seg % 3) {
            case 0 -> lerp(PURPLE, BLUE, t);
            case 1 -> lerp(BLUE, CYAN, t);
            default -> lerp(CYAN, PURPLE, t);
        };
    }

    /** Green (full) -> yellow -> red (almost broken). */
    public static int durability(float fraction) {
        float f = Math.max(0f, Math.min(1f, fraction));
        return f > 0.5f ? lerp(YELLOW, GREEN, (f - 0.5f) * 2f) : lerp(RED, YELLOW, f * 2f);
    }
}
