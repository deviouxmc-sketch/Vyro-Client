package gg.vyro.client.ui;

import gg.vyro.client.config.VyroConfig;
import gg.vyro.client.features.CrosshairRenderer;
import gg.vyro.client.ui.Controls.*;
import gg.vyro.client.ui.Modules.Module;
import gg.vyro.core.Colors;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The in-game VYRO menu (default key: Right Shift): a card grid of every module, a profile sidebar
 * (saved HUD layouts), category chips and search, and an options page per module.
 */
public class VyroMenuScreen extends Screen {
    private static final String[] TABS = {"MODS", "SETTINGS", "ABOUT"};
    private static final int CARD_H = 94;
    private static final int GAP = 8;
    private static final int HEADER_H = 30;
    private static final int SIDE_W = 106;

    private static int lastTab = 0;
    private static String lastCategory = "ALL";
    private static String lastProfile = "";

    private final Screen parent;
    private int tab = lastTab;
    private String category = lastCategory;
    private String search = "";
    private boolean searchFocused;
    private Module opened;
    private List<Module> modules = new ArrayList<>();
    private List<String> profiles = new ArrayList<>();
    private String confirmDelete;
    private final List<Control> controls = new ArrayList<>();
    private Control dragging;
    private int scroll;
    private int controlsHeight;
    private long openedAt;

    private int px, py, pw, ph, cx, cw, viewBottom;

    public VyroMenuScreen(Screen parent) {
        super(Component.literal("VYRO"));
        this.parent = parent;
    }

    // ------------------------------------------------------------------ layout

    @Override
    protected void init() {
        openedAt = System.currentTimeMillis();
        pw = Math.min(width - 16, 600);
        ph = Math.min(height - 16, 340);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        cx = px + SIDE_W + 10;
        cw = pw - SIDE_W - 20;
        viewBottom = py + ph - 10;
        refreshAll();
    }

    private void refreshAll() {
        modules = Modules.all();
        profiles = VyroConfig.get().presets();
        opened = null;
        rebuildControls();
    }

    private int viewTop() {
        if (opened != null) return py + HEADER_H + 30;
        return tab == 0 ? py + HEADER_H + 28 : py + HEADER_H + 10;
    }

    private int viewH() {
        return viewBottom - viewTop();
    }

    private void rebuildControls() {
        controls.clear();
        scroll = 0;
        controlsHeight = 0;
        VyroConfig cfg = VyroConfig.get();
        if (opened != null) {
            int w = cw - 10 - ("crosshair".equals(opened.id()) ? 84 : 0);
            controlsHeight = opened.options().build(controls, cx, 0, w);
        } else if (tab == 1) {
            int w = cw - 10, y = 0;
            controls.add(new Toggle(cx, y, w, "Aura animation (glow and moving accents)", () -> cfg.features.auraEnabled, v -> { cfg.features.auraEnabled = v; cfg.save(); }));
            y += 26;
            controls.add(new Label(cx, y, () -> "Keybinds", Colors.MUTED));
            y += 14;
            controls.add(new Label(cx, y, () -> "Open this menu: Right Shift", Colors.TEXT));
            y += 12;
            controls.add(new Label(cx, y, () -> "Zoom: C    Freelook: Left Alt", Colors.TEXT));
            y += 12;
            controls.add(new Label(cx, y, () -> "Change any of them in Options > Controls > VYRO Client.", Colors.MUTED));
            y += 24;
            controls.add(new Label(cx, y, () -> "Config files: .minecraft/config/vyro/", Colors.MUTED));
            controlsHeight = y + 14;
        } else if (tab == 2) {
            int y = 0;
            for (String line : new String[]{"VYRO Client mod 0.1.0", "For Minecraft 1.21.11 (Fabric).", "",
                    "Not an official Minecraft product.", "Not approved by or associated with Mojang or Microsoft."}) {
                final String t = line;
                controls.add(new Label(cx, y, () -> t, line.startsWith("VYRO") ? Colors.TEXT : Colors.MUTED));
                y += 13;
            }
            controlsHeight = y;
        }
    }

    // ------------------------------------------------------------------ grid helpers

    private List<Module> filtered() {
        String q = search.toLowerCase();
        List<Module> out = new ArrayList<>();
        for (Module m : modules) {
            if (!"ALL".equals(category) && !m.category().equals(category)) continue;
            if (!q.isEmpty() && !m.title().toLowerCase().contains(q)) continue;
            out.add(m);
        }
        return out;
    }

    private int cols() {
        return Math.max(2, Math.min(4, cw / 140));
    }

    private int cardW() {
        return (cw - 10 - GAP * (cols() - 1)) / cols();
    }

    private int gridHeight(int count) {
        int rows = (count + cols() - 1) / cols();
        return rows * (CARD_H + GAP);
    }

    private int totalHeight() {
        if (opened != null || tab != 0) return controlsHeight;
        return gridHeight(filtered().size());
    }

    private int maxScroll() {
        return Math.max(0, totalHeight() - viewH());
    }

    private int cardX(int i) {
        return cx + (i % cols()) * (cardW() + GAP);
    }

    private int cardY(int i) {
        return viewTop() + (i / cols()) * (CARD_H + GAP) - scroll;
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        double t = Theme.time();
        int aura = Colors.aura(t);
        boolean auraOn = VyroConfig.get().features.auraEnabled;
        float open = Math.min(1f, (System.currentTimeMillis() - openedAt) / 180f);
        float e = 1f - (1f - open) * (1f - open);

        g.fill(0, 0, width, height, Colors.withAlpha(0xFF000000, Math.round(150 * e)));
        if (auraOn) Theme.glow(g, px, py, pw, ph, 8, aura, 1.2f * e);
        Theme.roundRect(g, px, py, pw, ph, 8, Colors.scaleAlpha(Colors.BG, 0.98f * e));

        int top = viewTop();
        // ---- scrolling content
        g.pose().pushMatrix();
        if (opened != null || tab != 0) {
            g.pose().translate(0f, top - scroll);
            int smy = mouseY - top + scroll;
            for (Control c : controls) {
                if (c.y + c.h < scroll || c.y > scroll + viewH()) continue;
                c.render(g, mouseX, smy);
            }
        }
        g.pose().popMatrix();
        if (opened == null && tab == 0) drawCards(g, mouseX, mouseY, aura);
        if (opened != null && "crosshair".equals(opened.id())) drawCrosshairPreview(g, top);

        // cover anything that scrolled past the edges
        int bg = Colors.scaleAlpha(Colors.BG, 0.98f * e);
        g.fill(cx - 6, py + HEADER_H, px + pw - 2, top, bg);
        g.fill(cx - 6, viewBottom, px + pw - 2, py + ph - 8, bg);

        // ---- header
        Theme.roundRect(g, px, py, pw, HEADER_H, 8, Colors.scaleAlpha(Colors.PANEL, e));
        g.fill(px, py + 8, px + pw, py + HEADER_H, Colors.scaleAlpha(Colors.PANEL, e));
        g.fill(px, py + HEADER_H, px + pw, py + HEADER_H + 1, Colors.withAlpha(0xFFFFFFFF, 18));
        Theme.logo(g, px + 10, py + 7, 16);
        Theme.text(g, "VYRO", px + 32, py + 11, Colors.TEXT, false);
        Theme.text(g, "CLIENT", px + 32 + Theme.textWidth("VYRO "), py + 11, auraOn ? aura : Colors.CYAN, false);
        int tabW = 70, tabGap = 6;
        int tabsX = px + (pw - (TABS.length * tabW + (TABS.length - 1) * tabGap)) / 2 + 20;
        for (int i = 0; i < TABS.length; i++) {
            int tx = tabsX + i * (tabW + tabGap);
            boolean sel = i == tab, over = Theme.inside(mouseX, mouseY, tx, py + 6, tabW, 18);
            Theme.roundRect(g, tx, py + 6, tabW, 18, 4, sel ? Colors.withAlpha(Colors.PURPLE, 110) : over ? Colors.withAlpha(0xFFFFFFFF, 20) : Colors.PANEL_2);
            if (sel) g.fill(tx + 6, py + 22, tx + tabW - 6, py + 24, auraOn ? aura : Colors.CYAN);
            Theme.text(g, TABS[i], tx + (tabW - Theme.textWidth(TABS[i])) / 2, py + 11, sel ? Colors.TEXT : Colors.MUTED, false);
        }
        boolean overClose = Theme.inside(mouseX, mouseY, px + pw - 24, py + 7, 16, 16);
        Theme.roundRect(g, px + pw - 24, py + 7, 16, 16, 4, overClose ? Colors.withAlpha(Colors.RED, 200) : Colors.PANEL_2);
        Theme.text(g, "X", px + pw - 24 + 5, py + 11, Colors.TEXT, false);

        drawSidebar(g, mouseX, mouseY, aura, e);

        // ---- sub-header row (chips + search, or back button)
        if (opened != null) {
            boolean over = Theme.inside(mouseX, mouseY, cx, py + HEADER_H + 7, 58, 16);
            Theme.roundRect(g, cx, py + HEADER_H + 7, 58, 16, 4, over ? Colors.withAlpha(0xFFFFFFFF, 30) : Colors.PANEL_2);
            Theme.text(g, "< BACK", cx + 9, py + HEADER_H + 11, Colors.TEXT, false);
            Theme.text(g, opened.title(), cx + 70, py + HEADER_H + 11, Colors.TEXT, false);
            boolean on = opened.enabled().getAsBoolean();
            drawStatusBar(g, px + pw - 110, py + HEADER_H + 7, 96, 16, on, Theme.inside(mouseX, mouseY, px + pw - 110, py + HEADER_H + 7, 96, 16));
        } else if (tab == 0) {
            drawChips(g, mouseX, mouseY, aura);
        } else {
            Theme.text(g, TABS[tab], cx, py + HEADER_H + 11, Colors.TEXT, false);
        }

        // ---- scroll bar
        if (maxScroll() > 0) {
            int bh = Math.max(16, viewH() * viewH() / totalHeight());
            int by = top + (int) ((viewH() - bh) * (scroll / (float) maxScroll()));
            Theme.roundRect(g, px + pw - 7, by, 3, bh, 1, Colors.withAlpha(0xFFFFFFFF, 70));
        }
    }

    private void drawStatusBar(GuiGraphics g, int x, int y, int w, int h, boolean on, boolean hover) {
        int base = on ? Colors.GREEN : Colors.RED;
        Theme.roundRect(g, x, y, w, h, 3, Colors.withAlpha(Colors.lerp(base, 0xFFFFFFFF, hover ? 0.18f : 0f), 215));
        String s = on ? "ENABLED" : "DISABLED";
        Theme.text(g, s, x + (w - Theme.textWidth(s)) / 2, y + (h - 8) / 2 + 1, 0xFFFFFFFF, false);
    }

    private void drawCards(GuiGraphics g, int mx, int my, int aura) {
        List<Module> list = filtered();
        int cw2 = cardW();
        int top = viewTop();
        for (int i = 0; i < list.size(); i++) {
            int x = cardX(i), y = cardY(i);
            if (y + CARD_H < top || y > viewBottom) continue;
            Module m = list.get(i);
            boolean on = m.enabled().getAsBoolean();
            boolean hover = Theme.inside(mx, my, x, y, cw2, CARD_H) && my >= top && my < viewBottom;
            Theme.roundRect(g, x, y, cw2, CARD_H, 5, Colors.withAlpha(hover ? Colors.lerp(Colors.PANEL_2, 0xFFFFFFFF, 0.06f) : Colors.PANEL_2, 235));
            if (hover) {
                g.fill(x + 4, y, x + cw2 - 4, y + 1, aura);
            }
            // icon (vanilla item drawn at 2x)
            g.pose().pushMatrix();
            g.pose().translate(x + cw2 / 2f - 16f, y + 8f);
            g.pose().scale(2f);
            g.renderItem(m.icon(), 0, 0);
            g.pose().popMatrix();
            if (!on) Theme.roundRect(g, x + cw2 / 2 - 17, y + 7, 34, 34, 4, Colors.withAlpha(Colors.PANEL_2, 150));
            String title = Theme.fit(m.title(), cw2 - 8);
            Theme.text(g, title, x + (cw2 - Theme.textWidth(title)) / 2, y + 46, on ? Colors.TEXT : Colors.MUTED, false);

            int by = y + 60;
            boolean hasMove = m.hud() != null;
            int optW = hasMove ? cw2 - 22 : cw2 - 8;
            boolean overOpt = Theme.inside(mx, my, x + 4, by, optW, 16);
            Theme.roundRect(g, x + 4, by, optW, 16, 3, overOpt ? Colors.lerp(Colors.PANEL, 0xFFFFFFFF, 0.14f) : Colors.PANEL);
            Theme.text(g, "OPTIONS", x + 4 + (optW - Theme.textWidth("OPTIONS")) / 2, by + 4, Colors.TEXT, false);
            if (hasMove) {
                int gx = x + cw2 - 18;
                boolean overMove = Theme.inside(mx, my, gx, by, 14, 16);
                Theme.roundRect(g, gx, by, 14, 16, 3, overMove ? Colors.lerp(Colors.PANEL, 0xFFFFFFFF, 0.14f) : Colors.PANEL);
                g.fill(gx + 3, by + 8, gx + 11, by + 9, Colors.TEXT); // move/edit icon: a small plus
                g.fill(gx + 7, by + 4, gx + 8, by + 12, Colors.TEXT);
            }
            drawStatusBar(g, x + 4, y + 78, cw2 - 8, 13, on, Theme.inside(mx, my, x + 4, y + 78, cw2 - 8, 13));
        }
        if (list.isEmpty()) {
            Theme.text(g, "No modules match.", cx, top + 10, Colors.MUTED, false);
        }
    }

    private void drawChips(GuiGraphics g, int mx, int my, int aura) {
        int x = cx, y = py + HEADER_H + 7;
        for (String c : Modules.CATEGORIES) {
            int w = Theme.textWidth(c) + 14;
            boolean sel = c.equals(category), over = Theme.inside(mx, my, x, y, w, 16);
            Theme.roundRect(g, x, y, w, 16, 4, sel ? Colors.withAlpha(Colors.PURPLE, 140) : over ? Colors.withAlpha(0xFFFFFFFF, 26) : Colors.PANEL_2);
            if (sel) g.fill(x + 4, y + 14, x + w - 4, y + 15, aura);
            Theme.text(g, c, x + 7, y + 4, sel ? Colors.TEXT : Colors.MUTED, false);
            x += w + 4;
        }
        int sw = Math.min(130, px + pw - 14 - x - 8);
        if (sw > 50) {
            int sx = px + pw - 14 - sw;
            Theme.roundRect(g, sx, y, sw, 16, 4, Colors.PANEL_2);
            if (searchFocused) g.fill(sx + 2, y + 15, sx + sw - 2, y + 16, aura);
            String shown = search.isEmpty() && !searchFocused ? "search..." : Theme.fit(search, sw - 14) + (searchFocused && (System.currentTimeMillis() / 500) % 2 == 0 ? "|" : "");
            Theme.text(g, shown, sx + 6, y + 4, search.isEmpty() ? Colors.MUTED : Colors.TEXT, false);
        }
    }

    private void drawSidebar(GuiGraphics g, int mx, int my, int aura, float e) {
        int side = Colors.scaleAlpha(0xFF0F0F16, e);
        Theme.roundRect(g, px, py + HEADER_H, SIDE_W, ph - HEADER_H, 8, side);
        g.fill(px, py + HEADER_H + 1, px + SIDE_W, py + HEADER_H + 9, side);
        g.fill(px + SIDE_W, py + HEADER_H + 1, px + SIDE_W + 1, py + ph - 8, Colors.withAlpha(0xFFFFFFFF, 14));

        int y = py + HEADER_H + 9;
        Theme.text(g, "PROFILES", px + 10, y, Colors.MUTED, false);
        y += 14;
        int maxRows = Math.max(1, (py + ph - 62 - y) / 20);
        for (int i = 0; i < Math.min(maxRows, profiles.size()); i++) {
            String name = profiles.get(i);
            boolean active = name.equals(lastProfile);
            boolean over = Theme.inside(mx, my, px + 6, y, SIDE_W - 12, 18);
            Theme.roundRect(g, px + 6, y, SIDE_W - 12, 18, 4, active ? Colors.withAlpha(Colors.PURPLE, 90) : over ? Colors.withAlpha(0xFFFFFFFF, 20) : Colors.PANEL_2);
            Theme.text(g, Theme.fit(name, SIDE_W - 40), px + 11, y + 5, active ? Colors.TEXT : Colors.MUTED, false);
            boolean delOver = Theme.inside(mx, my, px + SIDE_W - 24, y, 18, 18);
            boolean pending = name.equals(confirmDelete);
            Theme.text(g, pending ? "?" : "x", px + SIDE_W - 18, y + 5, pending ? Colors.RED : delOver ? Colors.TEXT : Colors.MUTED, false);
            y += 20;
        }
        if (profiles.isEmpty()) {
            Theme.text(g, "No profiles yet.", px + 10, y + 2, Colors.MUTED, false);
            Theme.text(g, "Save your HUD", px + 10, y + 14, Colors.MUTED, false);
            Theme.text(g, "layout below.", px + 10, y + 26, Colors.MUTED, false);
        }
        int by = py + ph - 52;
        boolean o1 = Theme.inside(mx, my, px + 8, by, SIDE_W - 16, 18);
        Theme.roundRect(g, px + 8, by, SIDE_W - 16, 18, 4, o1 ? Colors.lerp(Colors.PANEL_2, 0xFFFFFFFF, 0.12f) : Colors.PANEL_2);
        String a = "SAVE PROFILE";
        Theme.text(g, a, px + 8 + (SIDE_W - 16 - Theme.textWidth(a)) / 2, by + 5, Colors.TEXT, false);
        int by2 = py + ph - 30;
        boolean o2 = Theme.inside(mx, my, px + 8, by2, SIDE_W - 16, 18);
        Theme.roundRect(g, px + 8, by2, SIDE_W - 16, 18, 4, Colors.lerp(Colors.lerp(Colors.PURPLE, Colors.BLUE, 0.5f), 0xFFFFFFFF, o2 ? 0.15f : 0f));
        String b = "EDIT HUD LAYOUT";
        Theme.text(g, b, px + 8 + (SIDE_W - 16 - Theme.textWidth(b)) / 2, by2 + 5, 0xFFFFFFFF, false);
    }

    private void drawCrosshairPreview(GuiGraphics g, int top) {
        int bx = px + pw - 92, by = top + 2;
        Theme.roundRect(g, bx, by, 72, 56, 4, 0xFF1C2B1C);
        CrosshairRenderer.draw(g, VyroConfig.get().crosshair, bx + 36, by + 28);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        searchFocused = false;
        VyroConfig cfg = VyroConfig.get();

        if (Theme.inside(mx, my, px + pw - 24, py + 7, 16, 16)) {
            onClose();
            return true;
        }
        int tabW = 70, tabGap = 6;
        int tabsX = px + (pw - (TABS.length * tabW + (TABS.length - 1) * tabGap)) / 2 + 20;
        for (int i = 0; i < TABS.length; i++) {
            if (Theme.inside(mx, my, tabsX + i * (tabW + tabGap), py + 6, tabW, 18)) {
                tab = lastTab = i;
                opened = null;
                rebuildControls();
                return true;
            }
        }

        // sidebar
        int y = py + HEADER_H + 9 + 14;
        int maxRows = Math.max(1, (py + ph - 62 - y) / 20);
        for (int i = 0; i < Math.min(maxRows, profiles.size()); i++, y += 20) {
            if (!Theme.inside(mx, my, px + 6, y, SIDE_W - 12, 18)) continue;
            String name = profiles.get(i);
            if (Theme.inside(mx, my, px + SIDE_W - 24, y, 18, 18)) {
                if (name.equals(confirmDelete)) {
                    cfg.deletePreset(name);
                    confirmDelete = null;
                    if (name.equals(lastProfile)) lastProfile = "";
                    profiles = cfg.presets();
                } else {
                    confirmDelete = name;
                }
            } else {
                confirmDelete = null;
                if (cfg.loadPreset(name)) {
                    lastProfile = name;
                    refreshAll();
                }
            }
            return true;
        }
        confirmDelete = null;
        if (Theme.inside(mx, my, px + 8, py + ph - 52, SIDE_W - 16, 18)) {
            String name = cfg.nextPresetName();
            cfg.savePreset(name);
            lastProfile = name;
            profiles = cfg.presets();
            return true;
        }
        if (Theme.inside(mx, my, px + 8, py + ph - 30, SIDE_W - 16, 18)) {
            minecraft.setScreen(new HudEditorScreen(this));
            return true;
        }

        if (opened != null) {
            if (Theme.inside(mx, my, cx, py + HEADER_H + 7, 58, 16)) {
                opened = null;
                rebuildControls();
                return true;
            }
            if (Theme.inside(mx, my, px + pw - 110, py + HEADER_H + 7, 96, 16)) {
                opened.setEnabled().accept(!opened.enabled().getAsBoolean());
                return true;
            }
            return clickControls(mx, my);
        }
        if (tab != 0) return clickControls(mx, my);

        // MODS tab: chips, search, cards
        int chipX = cx, chipY = py + HEADER_H + 7;
        for (String c : Modules.CATEGORIES) {
            int w = Theme.textWidth(c) + 14;
            if (Theme.inside(mx, my, chipX, chipY, w, 16)) {
                category = lastCategory = c;
                scroll = 0;
                return true;
            }
            chipX += w + 4;
        }
        int sw = Math.min(130, px + pw - 14 - chipX - 8);
        if (sw > 50 && Theme.inside(mx, my, px + pw - 14 - sw, chipY, sw, 16)) {
            searchFocused = true;
            return true;
        }
        int top = viewTop();
        if (my >= top && my < viewBottom) {
            List<Module> list = filtered();
            int w2 = cardW();
            for (int i = 0; i < list.size(); i++) {
                int x = cardX(i), yy = cardY(i);
                if (!Theme.inside(mx, my, x, yy, w2, CARD_H)) continue;
                Module m = list.get(i);
                int by = yy + 60;
                boolean hasMove = m.hud() != null;
                int optW = hasMove ? w2 - 22 : w2 - 8;
                if (Theme.inside(mx, my, x + 4, by, optW, 16)) {
                    opened = m;
                    rebuildControls();
                } else if (hasMove && Theme.inside(mx, my, x + w2 - 18, by, 14, 16)) {
                    minecraft.setScreen(new HudEditorScreen(this, m.hud()));
                } else if (Theme.inside(mx, my, x + 4, yy + 78, w2 - 8, 13)) {
                    m.setEnabled().accept(!m.enabled().getAsBoolean());
                }
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private boolean clickControls(double mx, double my) {
        if (my < viewTop() || my >= viewBottom) return false;
        double smy = my - viewTop() + scroll;
        for (Control c : controls) {
            if (c.hit(mx, smy) && c.click(mx, smy)) {
                dragging = c;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging != null) {
            dragging.drag(event.x(), event.y() - viewTop() + scroll);
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
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) (sy * 18)));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int k = event.key();
        if (searchFocused) {
            if (k == 256) {
                if (search.isEmpty()) searchFocused = false; else search = "";
                scroll = 0;
                return true;
            }
            if (k == 259) { // backspace
                if (!search.isEmpty()) search = search.substring(0, search.length() - 1);
                scroll = 0;
                return true;
            }
            if (search.length() < 24) {
                if (k >= 65 && k <= 90) search += (char) (k + 32);
                else if ((k >= 48 && k <= 57) || k == 32) search += (char) k;
                else return super.keyPressed(event);
                scroll = 0;
                return true;
            }
        }
        if (k == 256) { // ESC
            if (opened != null) {
                opened = null;
                rebuildControls();
            } else {
                onClose();
            }
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        lastTab = tab;
        VyroConfig.get().save();
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
