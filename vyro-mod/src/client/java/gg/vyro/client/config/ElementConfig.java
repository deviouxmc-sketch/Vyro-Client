package gg.vyro.client.config;

/** Per-HUD-element settings. Plain fields so Gson can read/write them directly. */
public class ElementConfig {
    public boolean enabled = false;
    /** Top-left corner as a fraction of the GUI-scaled screen, so layouts survive resizing. */
    public float x = 0.02f;
    public float y = 0.02f;
    public float scale = 1.0f;
    public float opacity = 0.85f;
    public int textColor = 0xFFECECF4;
    public int bgColor = 0xFF0B0B10;
    public boolean background = true;
    public boolean shadow = true;
    /** Animated purple/blue/cyan accent on the element. */
    public boolean aura = true;

    public ElementConfig() {}

    public ElementConfig(boolean enabled, float x, float y) {
        this.enabled = enabled;
        this.x = x;
        this.y = y;
    }

    public ElementConfig copy() {
        ElementConfig c = new ElementConfig(enabled, x, y);
        c.scale = scale;
        c.opacity = opacity;
        c.textColor = textColor;
        c.bgColor = bgColor;
        c.background = background;
        c.shadow = shadow;
        c.aura = aura;
        return c;
    }
}
