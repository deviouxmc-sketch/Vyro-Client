package gg.vyro.client.ui;

import gg.vyro.client.config.ElementConfig;
import gg.vyro.client.config.VyroConfig;
import gg.vyro.client.hud.HudManager;
import gg.vyro.client.hud.HudManager.Rect;
import gg.vyro.client.hud.HudWidget;
import gg.vyro.client.ui.Controls.*;
import gg.vyro.core.Colors;
import gg.vyro.core.Geometry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Drag-and-drop HUD editor with live preview. Positions are saved as screen fractions. */
public class HudEditorScreen extends Screen {
    private enum Mode { NONE, MOVE, RESIZE }

    private static final int HANDLE = 7;

    private final Screen parent;
    private final List<Control> palette = new ArrayList<>();
    private final List<Control> props = new ArrayList<>();
    private final Map<String, Rect> rects = new HashMap<>();
    private HudWidget selected;
    private Mode mode = Mode.NONE;
    private Control dragging;
    private double grabDx, grabDy, startMouseX;
    private float startScale;
    private boolean guideX, guideY;
    private int propX, propY, propW, propH;

    private HudWidget initial;

    public HudEditorScreen(Screen parent) {
        super(Component.literal("VYRO HUD Editor"));
        this.parent = parent;
    }

    public HudEditorScreen(Screen parent, HudWidget select) {
        this(parent);
        this.initial = select;
    }

    @Override
    protected void init() {
        propW = Math.min(width - 20, 440);
        propH = 118;
        propX = (width - propW) / 2;
        propY = height - propH - 8;
        if (initial != null) {
            ElementConfig ic = cfg().elements.get(initial.id);
            if (ic != null) ic.enabled = true;
            selected = initial;
            initial = null;
        }
        rebuildPalette();
        rebuildProps();
    }

    private VyroConfig cfg() {
        return VyroConfig.get();
    }

    private void rebuildPalette() {
        palette.clear();
        int x = 8, y = 22;
        for (HudWidget w : HudManager.WIDGETS) {
            ElementConfig c = cfg().elements.get(w.id);
            if (c == null || c.enabled) continue;
            int bw = Theme.textWidth(w.title) + 14;
            if (x + bw > width - 8) {
                x = 8;
                y += 20;
            }
            if (y > 62) break; // keep the strip compact; the rest is in the menu
            palette.add(new Button(x, y, bw, 16, () -> w.title, false, () -> {
                c.enabled = true;
                selected = w;
                cfg().save();
                rebuildPalette();
                rebuildProps();
            }));
            x += bw + 4;
        }
    }

    private void rebuildProps() {
        props.clear();
        if (selected == null) return;
        ElementConfig c = cfg().elements.get(selected.id);
        int lx = propX + 12, lw = 200, rx = propX + 226, rw = propW - 238;
        props.add(new Slider(lx, propY + 24, lw, "Opacity", 0.1, 1.0, 0.05, "%.2f", () -> c.opacity, v -> c.opacity = (float) v));
        props.add(new Slider(lx, propY + 52, lw, "Scale", 0.5, 3.0, 0.05, "%.2f", () -> c.scale, v -> c.scale = (float) v));
        props.add(new Button(lx, propY + 86, 96, 18, () -> "Reset", false, () -> {
            ElementConfig d = selected.defaults.copy();
            d.enabled = true;
            cfg().elements.put(selected.id, d);
            rebuildProps();
        }));
        props.add(new Button(lx + 104, propY + 86, 96, 18, () -> "Disable", false, () -> {
            c.enabled = false;
            selected = null;
            cfg().save();
            rebuildPalette();
            rebuildProps();
        }));
        props.add(new Swatches(rx, propY + 22, rw, "Text colour", () -> c.textColor, v -> c.textColor = v));
        props.add(new Swatches(rx, propY + 50, rw, "Background", () -> c.bgColor, v -> c.bgColor = v));
        props.add(new Toggle(rx, propY + 82, 100, "Panel", () -> c.background, v -> c.background = v));
        props.add(new Toggle(rx + 108, propY + 82, 100, "Shadow", () -> c.shadow, v -> c.shadow = v));
        props.add(new Toggle(rx, propY + 98, 100, "Aura", () -> c.aura, v -> c.aura = v));
    }

    private static void frame(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        double t = Theme.time();
        int aura = Colors.aura(t);
        g.fill(0, 0, width, height, Colors.withAlpha(0xFF000000, 110));

        // snap guides
        if (guideX) g.fill(width / 2, 0, width / 2 + 1, height, Colors.withAlpha(aura, 160));
        if (guideY) g.fill(0, height / 2, width, height / 2 + 1, Colors.withAlpha(aura, 160));

        // elements (sample data, so you can place things even when they are empty in-game)
        rects.clear();
        for (HudWidget w : HudManager.WIDGETS) {
            ElementConfig c = cfg().elements.get(w.id);
            if (c == null || !c.enabled) continue;
            rects.put(w.id, HudManager.draw(g, minecraft, w, c, true, width, height));
        }
        for (HudWidget w : HudManager.WIDGETS) {
            Rect r = rects.get(w.id);
            if (r == null) continue;
            boolean sel = w == selected;
            boolean over = Theme.inside(mouseX, mouseY, (int) r.x(), (int) r.y(), (int) r.w(), (int) r.h());
            if (sel) {
                frame(g, (int) r.x() - 2, (int) r.y() - 2, (int) r.w() + 4, (int) r.h() + 4, aura);
                Theme.roundRect(g, (int) (r.x() + r.w()) - HANDLE + 2, (int) (r.y() + r.h()) - HANDLE + 2, HANDLE, HANDLE, 2, aura);
            } else if (over) {
                frame(g, (int) r.x() - 1, (int) r.y() - 1, (int) r.w() + 2, (int) r.h() + 2, Colors.withAlpha(0xFFFFFFFF, 90));
            }
        }

        // top bar: palette of disabled elements
        g.fill(0, 0, width, palette.isEmpty() ? 18 : Math.max(40, paletteBottom()), Colors.withAlpha(Colors.BG, 215));
        Theme.text(g, "HUD EDITOR", 8, 6, Colors.TEXT, false);
        String hint = "Drag to move  |  corner handle or scroll to resize  |  ESC saves";
        Theme.text(g, hint, width - Theme.textWidth(hint) - 8, 6, Colors.MUTED, false);
        if (!palette.isEmpty()) Theme.text(g, "Add:", 8, 12, Colors.MUTED, false);
        for (Control c : palette) c.render(g, mouseX, mouseY);

        // property panel
        Theme.glow(g, propX, propY, propW, propH, 6, aura, 0.8f);
        Theme.roundRect(g, propX, propY, propW, propH, 6, Colors.withAlpha(Colors.BG, 238));
        if (selected == null) {
            String s = "Select an element to edit it";
            Theme.text(g, s, propX + (propW - Theme.textWidth(s)) / 2, propY + propH / 2 - 4, Colors.MUTED, false);
        } else {
            Theme.text(g, selected.title, propX + 12, propY + 8, Colors.TEXT, false);
            for (Control c : props) c.render(g, mouseX, mouseY);
        }
    }

    private int paletteBottom() {
        int b = 0;
        for (Control c : palette) b = Math.max(b, c.y + c.h);
        return b + 6;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        for (Control c : palette) {
            if (c.hit(mx, my) && c.click(mx, my)) return true;
        }
        if (Theme.inside(mx, my, propX, propY, propW, propH)) {
            for (Control c : props) {
                if (c.hit(mx, my) && c.click(mx, my)) {
                    dragging = c;
                    return true;
                }
            }
            return true; // clicks on the panel never fall through to elements
        }
        // resize handle of the selected element
        if (selected != null) {
            Rect r = rects.get(selected.id);
            if (r != null && Theme.inside(mx, my, (int) (r.x() + r.w()) - HANDLE, (int) (r.y() + r.h()) - HANDLE, HANDLE + 4, HANDLE + 4)) {
                mode = Mode.RESIZE;
                startMouseX = mx;
                startScale = cfg().elements.get(selected.id).scale;
                return true;
            }
        }
        // topmost (last drawn) element under the cursor
        for (int i = HudManager.WIDGETS.size() - 1; i >= 0; i--) {
            HudWidget w = HudManager.WIDGETS.get(i);
            Rect r = rects.get(w.id);
            if (r != null && Geometry.contains(r.x(), r.y(), r.w(), r.h(), mx, my)) {
                selected = w;
                mode = Mode.MOVE;
                grabDx = mx - r.x();
                grabDy = my - r.y();
                rebuildProps();
                return true;
            }
        }
        selected = null;
        rebuildProps();
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging != null) {
            dragging.drag(event.x(), event.y());
            return true;
        }
        if (selected == null || mode == Mode.NONE) return super.mouseDragged(event, dx, dy);
        ElementConfig c = cfg().elements.get(selected.id);
        Rect r = rects.get(selected.id);
        if (c == null || r == null) return true;
        if (mode == Mode.MOVE) {
            float nx = Geometry.clampX((float) (event.x() - grabDx), r.w(), width);
            float ny = Geometry.clampY((float) (event.y() - grabDy), r.h(), height);
            guideX = Math.abs(nx + r.w() / 2f - width / 2f) < 4;
            guideY = Math.abs(ny + r.h() / 2f - height / 2f) < 4;
            if (guideX) nx = width / 2f - r.w() / 2f;
            if (guideY) ny = height / 2f - r.h() / 2f;
            c.x = nx / width;
            c.y = ny / height;
        } else {
            float baseW = r.w() / Math.max(0.01f, c.scale);
            c.scale = Geometry.resizeScale(startScale, baseW, (float) (event.x() - startMouseX));
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        boolean used = dragging != null || mode != Mode.NONE;
        dragging = null;
        mode = Mode.NONE;
        guideX = false;
        guideY = false;
        if (used) cfg().save();
        return used || super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double sx, double sy) {
        if (selected != null) {
            ElementConfig c = cfg().elements.get(selected.id);
            if (c != null) c.scale = Geometry.clamp(c.scale + (float) sy * 0.05f, 0.5f, 3.0f);
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == 256) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        cfg().save();
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
