package gg.vyro.client.hud;

import gg.vyro.client.Metrics;
import gg.vyro.client.config.ElementConfig;
import gg.vyro.client.config.FeatureConfig;
import gg.vyro.client.config.VyroConfig;
import gg.vyro.client.features.CrosshairRenderer;
import gg.vyro.client.ui.Theme;
import gg.vyro.core.Colors;
import gg.vyro.core.Geometry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;

import java.util.List;

public final class HudManager {
    private HudManager() {}

    public static final List<HudWidget> WIDGETS = Widgets.all();
    private static final int PAD = 4;

    /** Screen-space box of a drawn element (already includes the element's scale). */
    public record Rect(float x, float y, float w, float h) {}

    public static HudWidget byId(String id) {
        for (HudWidget w : WIDGETS) if (w.id.equals(id)) return w;
        return null;
    }

    public static void init() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("vyro", "hud"), HudManager::renderHud);

        // Hotbar customizer: everything that sits at the bottom of the screen moves/scales together.
        for (Identifier id : new Identifier[]{
                VanillaHudElements.HOTBAR, VanillaHudElements.ARMOR_BAR, VanillaHudElements.HEALTH_BAR,
                VanillaHudElements.FOOD_BAR, VanillaHudElements.AIR_BAR, VanillaHudElements.MOUNT_HEALTH,
                VanillaHudElements.INFO_BAR, VanillaHudElements.EXPERIENCE_LEVEL, VanillaHudElements.HELD_ITEM_TOOLTIP}) {
            HudElementRegistry.replaceElement(id, orig -> (g, dt) -> bottom(g, dt, orig));
        }
        HudElementRegistry.replaceElement(VanillaHudElements.SCOREBOARD, orig -> (g, dt) -> scoreboard(g, dt, orig));
        HudElementRegistry.replaceElement(VanillaHudElements.BOSS_BAR, orig -> (g, dt) -> bossBar(g, dt, orig));
        HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, orig -> (g, dt) -> {
            if (VyroConfig.get().crosshair.enabled) CrosshairRenderer.render(g);
            else orig.render(g, dt);
        });
    }

    private static void renderHud(GuiGraphics g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        Metrics.frame(mc);
        VyroConfig cfg = VyroConfig.get();
        int sw = g.guiWidth(), sh = g.guiHeight();
        for (HudWidget w : WIDGETS) {
            ElementConfig c = cfg.elements.get(w.id);
            if (c != null && c.enabled) draw(g, mc, w, c, false, sw, sh);
        }
    }

    public static Rect measure(Minecraft mc, HudWidget w, ElementConfig c, boolean preview, int sw, int sh) {
        int bar = c.aura ? 2 : 0;
        int bw = w.contentWidth(mc, preview) + PAD * 2 + bar;
        int bh = w.contentHeight(mc, preview) + PAD * 2;
        float sx = Geometry.clampX(c.x * sw, bw * c.scale, sw);
        float sy = Geometry.clampY(c.y * sh, bh * c.scale, sh);
        return new Rect(sx, sy, bw * c.scale, bh * c.scale);
    }

    /** Draws one element and returns where it ended up on screen. */
    public static Rect draw(GuiGraphics g, Minecraft mc, HudWidget w, ElementConfig c, boolean preview, int sw, int sh) {
        Rect r = measure(mc, w, c, preview, sw, sh);
        int bar = c.aura ? 2 : 0;
        int bw = Math.round(r.w() / c.scale);
        int bh = Math.round(r.h() / c.scale);
        boolean auraOn = c.aura && VyroConfig.get().features.auraEnabled;

        g.pose().pushMatrix();
        g.pose().translate(r.x(), r.y());
        g.pose().scale(c.scale);
        if (c.background) {
            Theme.roundRect(g, 0, 0, bw, bh, 3, Colors.scaleAlpha(c.bgColor, c.opacity));
        }
        if (auraOn) {
            double t = Theme.time();
            Theme.vGradient(g, 0, 1, bar, Math.max(1, bh - 2), Colors.aura(t), Colors.aura(t + 1.5));
        }
        w.render(g, mc, c, preview, PAD + bar, PAD);
        g.pose().popMatrix();
        return r;
    }

    // ---------------------------------------------------------------- vanilla element transforms

    private static void bottom(GuiGraphics g, DeltaTracker dt, HudElement orig) {
        FeatureConfig f = VyroConfig.get().features;
        if (f.hotbarScale == 1.0f && f.hotbarOffsetY == 0.0f) {
            orig.render(g, dt);
            return;
        }
        float w = g.guiWidth(), h = g.guiHeight();
        g.pose().pushMatrix();
        g.pose().translate(w / 2f, h + f.hotbarOffsetY * h); // pivot: bottom centre
        g.pose().scale(f.hotbarScale);
        g.pose().translate(-w / 2f, -h);
        orig.render(g, dt);
        g.pose().popMatrix();
    }

    private static void scoreboard(GuiGraphics g, DeltaTracker dt, HudElement orig) {
        FeatureConfig f = VyroConfig.get().features;
        if (f.scoreboardScale == 1.0f && f.scoreboardOffsetX == 0.0f && f.scoreboardOffsetY == 0.0f) {
            orig.render(g, dt);
            return;
        }
        float w = g.guiWidth(), h = g.guiHeight();
        g.pose().pushMatrix();
        g.pose().translate(w + f.scoreboardOffsetX * w, h / 2f + f.scoreboardOffsetY * h); // pivot: right edge, middle
        g.pose().scale(f.scoreboardScale);
        g.pose().translate(-w, -h / 2f);
        orig.render(g, dt);
        g.pose().popMatrix();
    }

    private static void bossBar(GuiGraphics g, DeltaTracker dt, HudElement orig) {
        FeatureConfig f = VyroConfig.get().features;
        if (f.bossBarScale == 1.0f && f.bossBarOffsetY == 0.0f) {
            orig.render(g, dt);
            return;
        }
        float w = g.guiWidth(), h = g.guiHeight();
        g.pose().pushMatrix();
        g.pose().translate(w / 2f, f.bossBarOffsetY * h); // pivot: top centre
        g.pose().scale(f.bossBarScale);
        g.pose().translate(-w / 2f, 0);
        orig.render(g, dt);
        g.pose().popMatrix();
    }
}
