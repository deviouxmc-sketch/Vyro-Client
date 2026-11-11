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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Everything that shows up as a card in the menu: HUD elements and gameplay/visual features. */
public final class Modules {
    private Modules() {}

    public static final String[] CATEGORIES = {"ALL", "HUD", "MECHANIC", "VISUAL"};

    /** Adds the option controls for a module and returns the y after the last one. */
    public interface OptionsBuilder {
        int build(List<Control> out, int x, int y, int w);
    }

    public record Module(String id, String title, String category, ItemStack icon,
                         BooleanSupplier enabled, Consumer<Boolean> setEnabled,
                         OptionsBuilder options, HudWidget hud) {}

    private static Item iconFor(String widgetId) {
        return switch (widgetId) {
            case "fps" -> Items.REDSTONE;
            case "cps" -> Items.STONE_BUTTON;
            case "coords" -> Items.MAP;
            case "direction" -> Items.COMPASS;
            case "compass" -> Items.RECOVERY_COMPASS;
            case "ping" -> Items.ENDER_PEARL;
            case "clock" -> Items.CLOCK;
            case "speed" -> Items.FEATHER;
            case "biome" -> Items.OAK_SAPLING;
            case "memory" -> Items.COMPARATOR;
            case "server" -> Items.LEVER;
            case "combo" -> Items.GOLDEN_SWORD;
            case "reach" -> Items.FISHING_ROD;
            case "sprint" -> Items.LEATHER_BOOTS;
            case "durability" -> Items.ANVIL;
            case "keystrokes" -> Items.OAK_BUTTON;
            case "armor" -> Items.DIAMOND_CHESTPLATE;
            case "potion" -> Items.POTION;
            case "target" -> Items.TARGET;
            default -> Items.PAPER;
        };
    }

    private static void copyInto(ElementConfig to, ElementConfig from) {
        to.enabled = from.enabled;
        to.x = from.x;
        to.y = from.y;
        to.scale = from.scale;
        to.opacity = from.opacity;
        to.textColor = from.textColor;
        to.bgColor = from.bgColor;
        to.background = from.background;
        to.shadow = from.shadow;
        to.aura = from.aura;
    }

    private static OptionsBuilder hudOptions(HudWidget w) {
        return (out, x, y, width) -> {
            VyroConfig cfg = VyroConfig.get();
            ElementConfig c = cfg.elements.get(w.id);
            out.add(new Slider(x, y, width, "Opacity", 0.1, 1.0, 0.05, "%.2f", () -> c.opacity, v -> { c.opacity = (float) v; cfg.save(); }));
            y += 28;
            out.add(new Slider(x, y, width, "Scale", 0.5, 3.0, 0.05, "%.2f", () -> c.scale, v -> { c.scale = (float) v; cfg.save(); }));
            y += 30;
            out.add(new Swatches(x, y, width, "Text colour", () -> c.textColor, v -> { c.textColor = v; cfg.save(); }));
            y += 32;
            out.add(new Swatches(x, y, width, "Background colour", () -> c.bgColor, v -> { c.bgColor = v; cfg.save(); }));
            y += 32;
            out.add(new Toggle(x, y, width, "Background panel", () -> c.background, v -> { c.background = v; cfg.save(); }));
            y += 20;
            out.add(new Toggle(x, y, width, "Text shadow", () -> c.shadow, v -> { c.shadow = v; cfg.save(); }));
            y += 20;
            out.add(new Toggle(x, y, width, "Aura accent", () -> c.aura, v -> { c.aura = v; cfg.save(); }));
            y += 26;
            out.add(new Button(x, y, 150, 18, () -> "Reset to defaults", false, () -> {
                ElementConfig d = w.defaults.copy();
                d.enabled = c.enabled;
                copyInto(c, d);
                cfg.save();
            }));
            return y + 26;
        };
    }

    private static Module feature(String id, String title, String cat, Item icon, BooleanSupplier on, Consumer<Boolean> set, OptionsBuilder opts) {
        return new Module(id, title, cat, new ItemStack(icon), on, set, opts, null);
    }

    public static List<Module> all() {
        VyroConfig cfg = VyroConfig.get();
        FeatureConfig f = cfg.features;
        CrosshairConfig ch = cfg.crosshair;
        List<Module> m = new ArrayList<>();

        for (HudWidget w : HudManager.WIDGETS) {
            ElementConfig c = cfg.elements.get(w.id);
            m.add(new Module(w.id, w.title, "HUD", new ItemStack(iconFor(w.id)),
                    () -> c.enabled, v -> { c.enabled = v; cfg.save(); }, hudOptions(w), w));
        }

        m.add(feature("zoom", "Zoom", "MECHANIC", Items.SPYGLASS, () -> f.zoomEnabled, v -> { f.zoomEnabled = v; cfg.save(); },
                (out, x, y, w) -> {
                    out.add(new Toggle(x, y, w, "Press to toggle (not hold)", () -> f.zoomToggleMode, v -> { f.zoomToggleMode = v; cfg.save(); }));
                    y += 22;
                    out.add(new Slider(x, y, w, "Magnification", 1.5, 10, 0.5, "%.1fx", () -> f.zoomFactor, v -> { f.zoomFactor = (float) v; cfg.save(); }));
                    y += 28;
                    out.add(new Label(x, y, () -> "Key: C (rebind in Options > Controls). Mouse wheel changes it while zooming.", gg.vyro.core.Colors.MUTED));
                    return y + 18;
                }));
        m.add(feature("freelook", "Freelook", "MECHANIC", Items.ENDER_EYE, () -> f.freelookEnabled, v -> { f.freelookEnabled = v; cfg.save(); },
                (out, x, y, w) -> {
                    out.add(new Toggle(x, y, w, "Press to toggle (not hold)", () -> f.freelookToggleMode, v -> { f.freelookToggleMode = v; cfg.save(); }));
                    y += 22;
                    out.add(new Toggle(x, y, w, "Third-person view while looking around", () -> f.freelookThirdPerson, v -> { f.freelookThirdPerson = v; cfg.save(); }));
                    y += 22;
                    out.add(new Label(x, y, () -> "Key: Left Alt (rebind in Options > Controls).", gg.vyro.core.Colors.MUTED));
                    return y + 18;
                }));
        m.add(feature("toggle-sprint", "Toggle Sprint", "MECHANIC", Items.LEATHER_BOOTS, () -> f.toggleSprint, v -> { f.toggleSprint = v; cfg.save(); },
                (out, x, y, w) -> {
                    out.add(new Label(x, y, () -> "Keeps you sprinting while you move forward.", gg.vyro.core.Colors.MUTED));
                    y += 14;
                    out.add(new Label(x, y, () -> "Optional keybinds for sprint/sneak toggles are in Options > Controls.", gg.vyro.core.Colors.MUTED));
                    return y + 18;
                }));
        m.add(feature("hit-sound", "Hit Sound", "MECHANIC", Items.NOTE_BLOCK, () -> f.hitSound, v -> { f.hitSound = v; cfg.save(); },
                (out, x, y, w) -> {
                    out.add(new Cycle(x, y, w, "Sound", Features.HIT_SOUNDS, () -> f.hitSoundType, v -> { f.hitSoundType = v; cfg.save(); Features.playPreview(v, f.hitSoundVolume); }));
                    y += 26;
                    out.add(new Slider(x, y, w, "Volume", 0.1, 1.0, 0.05, "%.2f", () -> f.hitSoundVolume, v -> { f.hitSoundVolume = (float) v; cfg.save(); }));
                    return y + 28;
                }));

        m.add(feature("crosshair", "Crosshair", "VISUAL", Items.TARGET, () -> ch.enabled, v -> { ch.enabled = v; cfg.save(); },
                (out, x, y, w) -> {
                    CrosshairConfig c = cfg.crosshair;
                    out.add(new Cycle(x, y, w, "Style", CrosshairRenderer.STYLES, () -> c.style, v -> { c.style = v; cfg.save(); }));
                    y += 26;
                    out.add(new Slider(x, y, w, "Size", 1, 16, 1, "%.0f", () -> c.size, v -> { c.size = (int) v; cfg.save(); }));
                    y += 28;
                    out.add(new Slider(x, y, w, "Thickness", 1, 5, 1, "%.0f", () -> c.thickness, v -> { c.thickness = (int) v; cfg.save(); }));
                    y += 28;
                    out.add(new Slider(x, y, w, "Gap", 0, 10, 1, "%.0f", () -> c.gap, v -> { c.gap = (int) v; cfg.save(); }));
                    y += 28;
                    out.add(new Slider(x, y, w, "Opacity", 0.2, 1.0, 0.05, "%.2f", () -> c.opacity, v -> { c.opacity = (float) v; cfg.save(); }));
                    y += 28;
                    out.add(new Swatches(x, y, w, "Colour", () -> c.color, v -> { c.color = v; cfg.save(); }));
                    y += 32;
                    out.add(new Toggle(x, y, w, "Outline", () -> c.outline, v -> { c.outline = v; cfg.save(); }));
                    y += 20;
                    out.add(new Toggle(x, y, w, "Aura colour cycle", () -> c.aura, v -> { c.aura = v; cfg.save(); }));
                    return y + 24;
                }));
        m.add(feature("no-tilt", "No Damage Tilt", "VISUAL", Items.SHIELD, () -> f.disableDamageTilt, v -> { f.disableDamageTilt = v; cfg.save(); },
                (out, x, y, w) -> {
                    out.add(new Label(x, y, () -> "Removes the camera shake when you take damage.", gg.vyro.core.Colors.MUTED));
                    return y + 18;
                }));
        m.add(feature("hide-fire", "Hide Fire Overlay", "VISUAL", Items.FLINT_AND_STEEL, () -> f.hideFireOverlay, v -> { f.hideFireOverlay = v; cfg.save(); },
                (out, x, y, w) -> {
                    out.add(new Label(x, y, () -> "Hides the fire drawn over your screen when you burn.", gg.vyro.core.Colors.MUTED));
                    return y + 18;
                }));
        m.add(feature("low-fire", "Low Fire", "VISUAL", Items.CAMPFIRE, () -> f.fireLowering > 0.0f, v -> { f.fireLowering = v ? 0.25f : 0.0f; cfg.save(); },
                (out, x, y, w) -> {
                    out.add(new Slider(x, y, w, "How far down", 0.02, 0.6, 0.02, "%.2f", () -> f.fireLowering, v -> { f.fireLowering = (float) v; cfg.save(); }));
                    return y + 28;
                }));
        m.add(feature("no-pumpkin", "No Pumpkin Overlay", "VISUAL", Items.CARVED_PUMPKIN, () -> f.noPumpkinOverlay, v -> { f.noPumpkinOverlay = v; cfg.save(); },
                (out, x, y, w) -> {
                    out.add(new Label(x, y, () -> "Removes the screen blocker when wearing a pumpkin.", gg.vyro.core.Colors.MUTED));
                    return y + 18;
                }));
        m.add(feature("hotbar", "Hotbar Layout", "VISUAL", Items.CHEST, () -> f.hotbarScale != 1.0f || f.hotbarOffsetY != 0.0f,
                v -> { if (v) { f.hotbarScale = 0.9f; } else { f.hotbarScale = 1.0f; f.hotbarOffsetY = 0.0f; } cfg.save(); },
                (out, x, y, w) -> {
                    out.add(new Slider(x, y, w, "Scale (hotbar, health, food, XP)", 0.5, 1.5, 0.05, "%.2f", () -> f.hotbarScale, v -> { f.hotbarScale = (float) v; cfg.save(); }));
                    y += 28;
                    out.add(new Slider(x, y, w, "Vertical offset", -0.1, 0.1, 0.005, "%.3f", () -> f.hotbarOffsetY, v -> { f.hotbarOffsetY = (float) v; cfg.save(); }));
                    return y + 28;
                }));
        m.add(feature("scoreboard", "Scoreboard Layout", "VISUAL", Items.OAK_SIGN, () -> f.scoreboardScale != 1.0f || f.scoreboardOffsetX != 0.0f || f.scoreboardOffsetY != 0.0f,
                v -> { if (v) { f.scoreboardScale = 0.9f; } else { f.scoreboardScale = 1.0f; f.scoreboardOffsetX = 0.0f; f.scoreboardOffsetY = 0.0f; } cfg.save(); },
                (out, x, y, w) -> {
                    out.add(new Slider(x, y, w, "Scale", 0.5, 1.5, 0.05, "%.2f", () -> f.scoreboardScale, v -> { f.scoreboardScale = (float) v; cfg.save(); }));
                    y += 28;
                    out.add(new Slider(x, y, w, "Offset X", -0.3, 0.1, 0.005, "%.3f", () -> f.scoreboardOffsetX, v -> { f.scoreboardOffsetX = (float) v; cfg.save(); }));
                    y += 28;
                    out.add(new Slider(x, y, w, "Offset Y", -0.4, 0.4, 0.005, "%.3f", () -> f.scoreboardOffsetY, v -> { f.scoreboardOffsetY = (float) v; cfg.save(); }));
                    return y + 28;
                }));
        m.add(feature("bossbar", "Boss Bar Layout", "VISUAL", Items.DRAGON_HEAD, () -> f.bossBarScale != 1.0f || f.bossBarOffsetY != 0.0f,
                v -> { if (v) { f.bossBarScale = 0.9f; } else { f.bossBarScale = 1.0f; f.bossBarOffsetY = 0.0f; } cfg.save(); },
                (out, x, y, w) -> {
                    out.add(new Slider(x, y, w, "Scale", 0.5, 1.5, 0.05, "%.2f", () -> f.bossBarScale, v -> { f.bossBarScale = (float) v; cfg.save(); }));
                    y += 28;
                    out.add(new Slider(x, y, w, "Vertical offset", -0.05, 0.3, 0.005, "%.3f", () -> f.bossBarOffsetY, v -> { f.bossBarOffsetY = (float) v; cfg.save(); }));
                    return y + 28;
                }));
        return m;
    }
}
