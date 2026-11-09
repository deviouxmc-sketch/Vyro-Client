package gg.vyro.client.features;

import gg.vyro.client.config.CrosshairConfig;
import gg.vyro.client.config.VyroConfig;
import gg.vyro.client.ui.Theme;
import gg.vyro.core.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Draws the custom crosshair with plain fills, so it is pixel-exact at any GUI scale. */
public final class CrosshairRenderer {
    private CrosshairRenderer() {}

    public static final String[] STYLES = {"cross", "plus", "dot", "circle", "square"};

    /** In-game: centred on screen, hidden in third person like the vanilla crosshair. */
    public static void render(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.options.getCameraType().isFirstPerson()) return;
        draw(g, VyroConfig.get().crosshair, g.guiWidth() / 2, g.guiHeight() / 2);
    }

    public static void draw(GuiGraphics g, CrosshairConfig c, int cx, int cy) {
        int color = c.aura && VyroConfig.get().features.auraEnabled ? Colors.aura(Theme.time()) : c.color;
        color = Colors.scaleAlpha(color, c.opacity);
        int outline = Colors.withAlpha(0xFF000000, Math.round(200 * c.opacity));
        int t = Math.max(1, c.thickness);
        int s = Math.max(1, c.size);
        int gap = Math.max(0, c.gap);

        switch (c.style) {
            case "dot" -> {
                int d = Math.max(t, 2);
                if (c.outline) g.fill(cx - d / 2 - 1, cy - d / 2 - 1, cx - d / 2 + d + 1, cy - d / 2 + d + 1, outline);
                g.fill(cx - d / 2, cy - d / 2, cx - d / 2 + d, cy - d / 2 + d, color);
            }
            case "square" -> {
                int h = s + gap;
                if (c.outline) frame(g, cx - h - 1, cy - h - 1, cx + h + 1, cy + h + 1, t + 2, outline);
                frame(g, cx - h, cy - h, cx + h, cy + h, t, color);
            }
            case "circle" -> {
                int r = s + gap;
                for (int deg = 0; deg < 360; deg += 4) {
                    double a = Math.toRadians(deg);
                    int px = cx + (int) Math.round(Math.cos(a) * r);
                    int py = cy + (int) Math.round(Math.sin(a) * r);
                    if (c.outline) g.fill(px - 1, py - 1, px + t + 1, py + t + 1, outline);
                }
                for (int deg = 0; deg < 360; deg += 4) {
                    double a = Math.toRadians(deg);
                    int px = cx + (int) Math.round(Math.cos(a) * r);
                    int py = cy + (int) Math.round(Math.sin(a) * r);
                    g.fill(px, py, px + t, py + t, color);
                }
            }
            case "plus" -> arms(g, cx, cy, s, 0, t, color, outline, c.outline);
            default -> arms(g, cx, cy, s, gap, t, color, outline, c.outline);
        }
    }

    private static void arms(GuiGraphics g, int cx, int cy, int size, int gap, int t, int color, int outline, boolean withOutline) {
        int lo = -t / 2;
        int hi = lo + t;
        // up, down, left, right
        int[][] boxes = {
                {lo, -gap - size, hi, -gap},
                {lo, gap, hi, gap + size},
                {-gap - size, lo, -gap, hi},
                {gap, lo, gap + size, hi}
        };
        if (withOutline) {
            for (int[] b : boxes) g.fill(cx + b[0] - 1, cy + b[1] - 1, cx + b[2] + 1, cy + b[3] + 1, outline);
        }
        for (int[] b : boxes) g.fill(cx + b[0], cy + b[1], cx + b[2], cy + b[3], color);
        if (gap == 0) g.fill(cx + lo, cy + lo, cx + hi, cy + hi, color); // plus: fill the middle
    }

    private static void frame(GuiGraphics g, int x1, int y1, int x2, int y2, int t, int color) {
        g.fill(x1, y1, x2, y1 + t, color);
        g.fill(x1, y2 - t, x2, y2, color);
        g.fill(x1, y1 + t, x1 + t, y2 - t, color);
        g.fill(x2 - t, y1 + t, x2, y2 - t, color);
    }
}
