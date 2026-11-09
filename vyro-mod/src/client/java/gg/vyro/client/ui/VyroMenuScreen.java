package gg.vyro.client.ui;

import gg.vyro.client.config.CrosshairConfig;
import gg.vyro.client.config.ElementConfig;
import gg.vyro.client.config.FeatureConfig;
import gg.vyro.client.config.VyroConfig;
import gg.vyro.client.features.CrosshairRenderer;
import gg.vyro.client.features.Features;
import gg.vyro.client.hud.HudManager;
import gg.vyro.client.hud.HudWidget;
import gg.vyro.client.ui.Controls.*;
import gg.vyro.core.Colors;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** The in-game VYRO menu (default key: Right Shift). Fully custom drawn so it matches the launcher. */
public class VyroMenuScreen extends Screen {
    private static final String[] TABS = {"HUD", "Features", "Crosshair", "About"};
    private static int lastTab = 0;

    private final Screen parent;
    private int tab = lastTab;
    private final List<Control> controls = new ArrayList<>();
    private Control dragging;
    private int scroll;
    private int contentHeight;
    private long openedAt;

    // panel geometry (computed in init)
    private int px, py, pw, ph, sideW = 104, cx, cy, cw, ch;

    public VyroMenuScreen(Screen parent) {
        super(Component.literal("VYRO"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        openedAt = System.currentTimeMillis();
        pw = Math.min(width - 20, 460);
        ph = Math.min(height - 20, 300);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        cx = px + sideW + 10;
        cy = py + 34;
        cw = pw - sideW - 20;
        ch = ph - 44;
        rebuild();
    }

    private void rebuild() {
        controls.clear();
        scroll = 0;
        VyroConfig cfg = VyroConfig.get();
        int x = cx, w = cw - 8, y = 0; // y is relative to the scroll area
        switch (tab) {
            case 0 -> {
                controls.add(new Button(x, y, w, 20, () -> "Open HUD Editor", true, () -> minecraft.setScreen(new HudEditorScreen(this))));
                y += 28;
                controls.add(new Label(x, y, () -> "Elements", Colors.MUTED));
                y += 14;
                for (HudWidget wd : HudManager.WIDGETS) {
                    ElementConfig c = cfg.elements.get(wd.id);
                    controls.add(new Toggle(x, y, w, wd.title, () -> c.enabled, v -> { c.enabled = v; cfg.save(); }));
                    y += 18;
                }
                y += 8;
                controls.add(new Label(x, y, () -> "Layout presets", Colors.MUTED));
                y += 14;
                controls.add(new Button(x, y, w, 20, () -> "Save current layout as new preset", false, () -> { cfg.savePreset(cfg.nextPresetName()); rebuild(); }));
                y += 26;
                for (String name : cfg.presets()) {
                    int half = (w - 4) / 2;
                    controls.add(new Button(x, y, half, 18, () -> "Load: " + name, false, () -> { cfg.loadPreset(name); rebuild(); }));
                    controls.add(new Button(x + half + 4, y, half, 18, () -> "Delete", false, () -> { cfg.deletePreset(name); rebuild(); }));
                    y += 22;
                }
            }
            case 1 -> {
                FeatureConfig f = cfg.features;
                controls.add(new Toggle(x, y, w, "Toggle sprint", () -> f.toggleSprint, v -> { f.toggleSprint = v; cfg.save(); }));
                y += 20;
                controls.add(new Slider(x, y, w, "Zoom FOV (hold C)", 30, 90, 1, "%.0f", () -> f.zoomFov, v -> { f.zoomFov = (int) v; cfg.save(); }));
                y += 30;
                controls.add(new Toggle(x, y, w, "Hit sound", () -> f.hitSound, v -> { f.hitSound = v; cfg.save(); }));
                y += 20;
                controls.add(new Cycle(x, y, w, "Sound", Features.HIT_SOUNDS, () -> f.hitSoundType, v -> { f.hitSoundType = v; cfg.save(); Features.playPreview(v, f.hitSoundVolume); }));
                y += 26;
                controls.add(new Slider(x, y, w, "Hit sound volume", 0.1, 1.0, 0.05, "%.2f", () -> f.hitSoundVolume, v -> { f.hitSoundVolume = (float) v; cfg.save(); }));
                y += 30;
                controls.add(new Toggle(x, y, w, "Aura animation", () -> f.auraEnabled, v -> { f.auraEnabled = v; cfg.save(); }));
                y += 28;
                controls.add(new Label(x, y, () -> "Vanilla HUD", Colors.MUTED));
                y += 14;
                controls.add(new Slider(x, y, w, "Hotbar + bars scale", 0.5, 1.5, 0.05, "%.2f", () -> f.hotbarScale, v -> { f.hotbarScale = (float) v; cfg.save(); }));
                y += 28;
                controls.add(new Slider(x, y, w, "Hotbar + bars offset", -0.1, 0.1, 0.005, "%.3f", () -> f.hotbarOffsetY, v -> { f.hotbarOffsetY = (float) v; cfg.save(); }));
                y += 28;
                controls.add(new Slider(x, y, w, "Scoreboard scale", 0.5, 1.5, 0.05, "%.2f", () -> f.scoreboardScale, v -> { f.scoreboardScale = (float) v; cfg.save(); }));
                y += 28;
                controls.add(new Slider(x, y, w, "Scoreboard offset X", -0.3, 0.1, 0.005, "%.3f", () -> f.scoreboardOffsetX, v -> { f.scoreboardOffsetX = (float) v; cfg.save(); }));
                y += 28;
                controls.add(new Slider(x, y, w, "Scoreboard offset Y", -0.4, 0.4, 0.005, "%.3f", () -> f.scoreboardOffsetY, v -> { f.scoreboardOffsetY = (float) v; cfg.save(); }));
                y += 28;
                controls.add(new Slider(x, y, w, "Boss bar scale", 0.5, 1.5, 0.05, "%.2f", () -> f.bossBarScale, v -> { f.bossBarScale = (float) v; cfg.save(); }));
                y += 28;
                controls.add(new Slider(x, y, w, "Boss bar offset", -0.05, 0.3, 0.005, "%.3f", () -> f.bossBarOffsetY, v -> { f.bossBarOffsetY = (float) v; cfg.save(); }));
                y += 28;
            }
            case 2 -> {
                CrosshairConfig c = cfg.crosshair;
                controls.add(new Toggle(x, y, w, "Use custom crosshair", () -> c.enabled, v -> { c.enabled = v; cfg.save(); }));
                y += 20;
                controls.add(new Cycle(x, y, w, "Style", CrosshairRenderer.STYLES, () -> c.style, v -> { c.style = v; cfg.save(); }));
                y += 26;
                controls.add(new Slider(x, y, w, "Size", 1, 16, 1, "%.0f", () -> c.size, v -> { c.size = (int) v; cfg.save(); }));
                y += 28;
                controls.add(new Slider(x, y, w, "Thickness", 1, 5, 1, "%.0f", () -> c.thickness, v -> { c.thickness = (int) v; cfg.save(); }));
                y += 28;
                controls.add(new Slider(x, y, w, "Gap", 0, 10, 1, "%.0f", () -> c.gap, v -> { c.gap = (int) v; cfg.save(); }));
                y += 28;
                controls.add(new Slider(x, y, w, "Opacity", 0.2, 1.0, 0.05, "%.2f", () -> c.opacity, v -> { c.opacity = (float) v; cfg.save(); }));
                y += 28;
                controls.add(new Swatches(x, y, w, "Colour", () -> c.color, v -> { c.color = v; cfg.save(); }));
                y += 32;
                controls.add(new Toggle(x, y, w, "Outline", () -> c.outline, v -> { c.outline = v; cfg.save(); }));
                y += 20;
                controls.add(new Toggle(x, y, w, "Aura colour cycle", () -> c.aura, v -> { c.aura = v; cfg.save(); }));
                y += 24;
            }
            default -> {
                controls.add(new Label(x, y, () -> "VYRO Client mod 0.1.0", Colors.TEXT));
                y += 14;
                controls.add(new Label(x, y, () -> "For Minecraft 1.21.11 (Fabric)", Colors.MUTED));
                y += 14;
                controls.add(new Label(x, y, () -> "Not an official Minecraft product.", Colors.MUTED));
                y += 14;
                controls.add(new Label(x, y, () -> "Not approved by or associated with Mojang or Microsoft.", Colors.MUTED));
                y += 20;
                controls.add(new Label(x, y, () -> "Config: .minecraft/config/vyro/", Colors.MUTED));
                y += 14;
            }
        }
        contentHeight = y;
    }

    private int maxScroll() {
        return Math.max(0, contentHeight - ch);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        double t = Theme.time();
        float open = Math.min(1f, (System.currentTimeMillis() - openedAt) / 180f); // fade-in
        float e = 1f - (1f - open) * (1f - open);

        g.fill(0, 0, width, height, Colors.withAlpha(0xFF000000, Math.round(150 * e)));
        int aura = Colors.aura(t);
        if (VyroConfig.get().features.auraEnabled) Theme.glow(g, px, py, pw, ph, 8, aura, 1.2f * e);
        Theme.roundRect(g, px, py, pw, ph, 8, Colors.scaleAlpha(Colors.BG, 0.97f * e));

        // sidebar
        Theme.roundRect(g, px, py, sideW, ph, 8, Colors.scaleAlpha(Colors.PANEL, e));
        Theme.text(g, "VYRO", px + 14, py + 12, Colors.TEXT, false);
        Theme.text(g, "CLIENT", px + 14 + Theme.textWidth("VYRO "), py + 12, aura, false);
        Theme.vGradient(g, px + 14, py + 24, 30, 2, Colors.PURPLE, Colors.CYAN);
        for (int i = 0; i < TABS.length; i++) {
            int ty = py + 40 + i * 24;
            boolean sel = i == tab;
            boolean over = Theme.inside(mouseX, mouseY, px + 8, ty, sideW - 16, 20);
            if (sel) {
                Theme.roundRect(g, px + 8, ty, sideW - 16, 20, 4, Colors.withAlpha(Colors.PURPLE, 70));
                Theme.vGradient(g, px + 8, ty + 3, 2, 14, aura, Colors.aura(t + 1.5));
            } else if (over) {
                Theme.roundRect(g, px + 8, ty, sideW - 16, 20, 4, Colors.withAlpha(0xFFFFFFFF, 16));
            }
            Theme.text(g, TABS[i], px + 20, ty + 6, sel ? Colors.TEXT : Colors.MUTED, false);
        }

        // title
        Theme.text(g, TABS[tab], cx, py + 14, Colors.TEXT, false);
        g.fill(cx, py + 28, cx + cw, py + 29, Colors.withAlpha(0xFFFFFFFF, 20));

        // scrolling content (rows outside the viewport are skipped)
        g.pose().pushMatrix();
        g.pose().translate(0f, cy - scroll);
        int smx = mouseX, smy = mouseY - cy + scroll;
        for (Control c : controls) {
            if (c.y + c.h < scroll || c.y > scroll + ch) continue;
            c.render(g, smx, smy);
        }
        g.pose().popMatrix();

        if (tab == 2) drawCrosshairPreview(g);

        // cover overflow above/below the viewport, then the scroll bar
        g.fill(cx - 2, py + 29, cx + cw + 4, cy - 1, Colors.scaleAlpha(Colors.BG, 0.97f * e));
        g.fill(cx - 2, cy + ch, cx + cw + 4, py + ph - 2, Colors.scaleAlpha(Colors.BG, 0.97f * e));
        if (maxScroll() > 0) {
            int bh = Math.max(16, ch * ch / contentHeight);
            int by = cy + (int) ((ch - bh) * (scroll / (float) maxScroll()));
            Theme.roundRect(g, cx + cw + 2, by, 3, bh, 1, Colors.withAlpha(0xFFFFFFFF, 60));
        }
    }

    private void drawCrosshairPreview(GuiGraphics g) {
        int bx = cx + cw - 70, by = py + 8, s = 60;
        Theme.roundRect(g, bx, by, s, s - 12, 4, 0xFF1C2B1C);
        CrosshairRenderer.draw(g, VyroConfig.get().crosshair, bx + s / 2, by + (s - 12) / 2);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        for (int i = 0; i < TABS.length; i++) {
            if (Theme.inside(mx, my, px + 8, py + 40 + i * 24, sideW - 16, 20)) {
                tab = lastTab = i;
                rebuild();
                return true;
            }
        }
        if (Theme.inside(mx, my, cx - 2, cy, cw + 6, ch)) {
            double smy = my - cy + scroll;
            for (Control c : controls) {
                if (c.hit(mx, smy) && c.click(mx, smy)) {
                    dragging = c;
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging != null) {
            dragging.drag(event.x(), event.y() - cy + scroll);
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging != null) {
            dragging.release();
            dragging = null;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double sx, double sy) {
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) (sy * 14)));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == 256) { // ESC
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        VyroConfig.get().save();
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
