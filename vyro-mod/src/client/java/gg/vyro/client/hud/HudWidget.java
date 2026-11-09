package gg.vyro.client.hud;

import gg.vyro.client.config.ElementConfig;
import gg.vyro.core.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** One draggable HUD element. Sizes are in unscaled GUI pixels, without the panel padding. */
public abstract class HudWidget {
    public final String id;
    public final String title;
    public final ElementConfig defaults;

    protected HudWidget(String id, String title, ElementConfig defaults) {
        this.id = id;
        this.title = title;
        this.defaults = defaults;
    }

    public abstract int contentWidth(Minecraft mc, boolean preview);

    public abstract int contentHeight(Minecraft mc, boolean preview);

    /** Draw the content with its top-left at (x, y). {@code preview} = use sample data (editor/menu). */
    public abstract void render(GuiGraphics g, Minecraft mc, ElementConfig c, boolean preview, int x, int y);

    /** Text colour with the element's opacity applied (kept readable even at low opacity). */
    protected static int tc(ElementConfig c, int color) {
        float a = 0.45f + 0.55f * Math.max(0f, Math.min(1f, c.opacity));
        return Colors.withAlpha(color, Math.round(255 * a));
    }
}
