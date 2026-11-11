package gg.vyro.client.ui;

import gg.vyro.core.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Drawing helpers for the VYRO look. Only uses plain fills, so it works at any GUI scale. */
public final class Theme {
    private Theme() {}

    public static double time() {
        return System.nanoTime() / 1.0e9;
    }

    public static void text(GuiGraphics g, String s, int x, int y, int color, boolean shadow) {
        g.drawString(Minecraft.getInstance().font, s, x, y, color, shadow);
    }

    public static int textWidth(String s) {
        return Minecraft.getInstance().font.width(s);
    }

    /** Rectangle with quarter-circle corners made from row fills. */
    public static void roundRect(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
        if ((color >>> 24) == 0 || w <= 0 || h <= 0) return;
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 0) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = r - (int) Math.round(Math.sqrt(r * r - dy * dy));
            g.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
            g.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, color);
        }
        g.fill(x, y + r, x + w, y + h - r, color);
    }

    /** Vertical gradient made of one fill per row (fine for the small areas we use it on). */
    public static void vGradient(GuiGraphics g, int x, int y, int w, int h, int top, int bottom) {
        for (int i = 0; i < h; i++) {
            g.fill(x, y + i, x + w, y + i + 1, Colors.lerp(top, bottom, h <= 1 ? 0f : i / (float) (h - 1)));
        }
    }

    /** Soft outer glow: a few translucent rounded rings. Draw it *before* the panel it surrounds. */
    public static void glow(GuiGraphics g, int x, int y, int w, int h, int r, int color, float strength) {
        for (int k = 5; k >= 1; k--) {
            int a = Math.round(strength * 30f / k);
            roundRect(g, x - k, y - k, w + 2 * k, h + 2 * k, r + k, Colors.withAlpha(color, a));
        }
    }

    public static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** The VYRO "V" mark drawn with row fills (matches public/logo.svg). */
    public static void logo(GuiGraphics g, int x, int y, int size) {
        float k = size / 128f;
        for (int row = 0; row < size; row++) {
            float yy = row / k;
            if (yy < 26f || yy > 104f) continue;
            float t = (yy - 26f) / 78f;
            float outerL = 20f + t * 32f;
            float outerR = 108f - t * 32f;
            int color = Colors.lerp(Colors.PURPLE, Colors.BLUE, t);
            int ry = y + row;
            if (yy <= 66f) {
                float innerL = 46f + (yy - 26f) * 18f / 40f;
                float innerR = 82f - (yy - 26f) * 18f / 40f;
                g.fill(x + Math.round(outerL * k), ry, x + Math.round(innerL * k), ry + 1, color);
                g.fill(x + Math.round(innerR * k), ry, x + Math.round(outerR * k), ry + 1, color);
            } else {
                g.fill(x + Math.round(outerL * k), ry, x + Math.round(outerR * k), ry + 1, color);
            }
            if (yy >= 78f) { // cyan notch at the tip
                float half = (yy - 78f) / 26f * 12f;
                g.fill(x + Math.round((64f - half) * k), ry, x + Math.round((64f + half) * k), ry + 1, Colors.CYAN);
            }
        }
    }

    /** Cut text to fit {@code maxW} pixels, adding an ellipsis. */
    public static String fit(String s, int maxW) {
        if (textWidth(s) <= maxW) return s;
        while (s.length() > 1 && textWidth(s + "...") > maxW) s = s.substring(0, s.length() - 1);
        return s + "...";
    }
}
