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
}
