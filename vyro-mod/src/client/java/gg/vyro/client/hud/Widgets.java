package gg.vyro.client.hud;

import gg.vyro.client.Metrics;
import gg.vyro.client.config.ElementConfig;
import gg.vyro.client.ui.Theme;
import gg.vyro.core.Colors;
import gg.vyro.core.Compass;
import gg.vyro.core.TimeFmt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/** All built-in HUD elements. */
public final class Widgets {
    private Widgets() {}

    private static final String[] ROMAN = {"", "", " II", " III", " IV", " V", " VI", " VII", " VIII", " IX", " X"};

    // ------------------------------------------------------------------ simple text elements

    static final class Text extends HudWidget {
        private final Function<Minecraft, List<String>> live;
        private final List<String> sample;

        Text(String id, String title, ElementConfig d, Function<Minecraft, List<String>> live, List<String> sample) {
            super(id, title, d);
            this.live = live;
            this.sample = sample;
        }

        private List<String> lines(Minecraft mc, boolean preview) {
            return preview || mc.player == null ? sample : live.apply(mc);
        }

        @Override
        public int contentWidth(Minecraft mc, boolean preview) {
            int w = 0;
            for (String s : lines(mc, preview)) w = Math.max(w, mc.font.width(s));
            return w;
        }

        @Override
        public int contentHeight(Minecraft mc, boolean preview) {
            return lines(mc, preview).size() * 10 - 1;
        }

        @Override
        public void render(GuiGraphics g, Minecraft mc, ElementConfig c, boolean preview, int x, int y) {
            int i = 0;
            for (String s : lines(mc, preview)) {
                g.drawString(mc.font, s, x, y + i * 10, tc(c, c.textColor), c.shadow);
                i++;
            }
        }
    }

    private static List<String> one(String s) {
        return List.of(s);
    }

    private static String pretty(String path) {
        StringBuilder sb = new StringBuilder();
        for (String part : path.split("_")) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    private static String biome(Minecraft mc) {
        if (mc.level == null || mc.player == null) return "?";
        return mc.level.getBiome(mc.player.blockPosition()).unwrapKey().map(k -> pretty(k.identifier().getPath())).orElse("?");
    }

    private static String ping(Minecraft mc) {
        if (mc.getConnection() == null || mc.player == null) return "--";
        PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
        if (info == null || info.getLatency() <= 0) return "--";
        return info.getLatency() + "ms";
    }

    private static String server(Minecraft mc) {
        ServerData sd = mc.getCurrentServer();
        return sd == null ? "Singleplayer" : sd.ip;
    }

    // ------------------------------------------------------------------ keystrokes

    static final class Keystrokes extends HudWidget {
        private static final int U = 16, GAP = 2;

        Keystrokes(ElementConfig d) {
            super("keystrokes", "Keystrokes", d);
        }

        @Override
        public int contentWidth(Minecraft mc, boolean preview) {
            return U * 3 + GAP * 2;
        }

        @Override
        public int contentHeight(Minecraft mc, boolean preview) {
            return U + GAP + U + GAP + U + GAP + 8;
        }

        private void key(GuiGraphics g, ElementConfig c, String label, int x, int y, int w, int h, boolean down) {
            int bg = down ? Colors.withAlpha(Colors.aura(Theme.time()), 200) : Colors.scaleAlpha(Colors.PANEL, c.opacity);
            Theme.roundRect(g, x, y, w, h, 2, bg);
            int color = down ? 0xFFFFFFFF : tc(c, c.textColor);
            int tx = x + (w - Theme.textWidth(label)) / 2;
            g.drawString(Minecraft.getInstance().font, label, tx, y + (h - 8) / 2, color, false);
        }

        @Override
        public void render(GuiGraphics g, Minecraft mc, ElementConfig c, boolean preview, int x, int y) {
            var o = mc.options;
            boolean live = !preview && mc.player != null;
            int cx = x + U + GAP;
            key(g, c, "W", cx, y, U, U, live ? o.keyUp.isDown() : true);
            key(g, c, "A", x, y + U + GAP, U, U, live && o.keyLeft.isDown());
            key(g, c, "S", cx, y + U + GAP, U, U, live && o.keyDown.isDown());
            key(g, c, "D", cx + U + GAP, y + U + GAP, U, U, live && o.keyRight.isDown());
            int my = y + (U + GAP) * 2;
            int half = (U * 3 + GAP * 2 - GAP) / 2;
            key(g, c, "LMB", x, my, half, U, live && o.keyAttack.isDown());
            key(g, c, "RMB", x + half + GAP, my, half, U, live && o.keyUse.isDown());
            int sy = my + U + GAP;
            boolean jump = live && o.keyJump.isDown();
            int w = U * 3 + GAP * 2;
            Theme.roundRect(g, x, sy, w, 8, 2, jump ? Colors.withAlpha(Colors.aura(Theme.time()), 200) : Colors.scaleAlpha(Colors.PANEL, c.opacity));
            g.fill(x + w / 2 - 8, sy + 3, x + w / 2 + 8, sy + 4, jump ? 0xFFFFFFFF : tc(c, c.textColor));
        }
    }

    // ------------------------------------------------------------------ armor & held item

    static final class Armor extends HudWidget {
        private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        private static final int ROW = 18;

        Armor(ElementConfig d) {
            super("armor", "Armor Status", d);
        }

        private ItemStack stack(Minecraft mc, boolean preview, int i) {
            if (preview || mc.player == null) {
                return new ItemStack(switch (i) {
                    case 0 -> Items.DIAMOND_HELMET;
                    case 1 -> Items.DIAMOND_CHESTPLATE;
                    case 2 -> Items.DIAMOND_LEGGINGS;
                    default -> Items.DIAMOND_BOOTS;
                });
            }
            return mc.player.getItemBySlot(SLOTS[i]);
        }

        @Override
        public int contentWidth(Minecraft mc, boolean preview) {
            return 20 + mc.font.width("0000/0000");
        }

        @Override
        public int contentHeight(Minecraft mc, boolean preview) {
            return ROW * 4 - 2;
        }

        @Override
        public void render(GuiGraphics g, Minecraft mc, ElementConfig c, boolean preview, int x, int y) {
            for (int i = 0; i < 4; i++) {
                ItemStack s = stack(mc, preview, i);
                if (s.isEmpty()) continue;
                int ry = y + i * ROW;
                g.renderItem(s, x, ry);
                if (s.isDamageableItem()) {
                    int max = s.getMaxDamage();
                    int left = max - s.getDamageValue();
                    int col = Colors.durability(left / (float) Math.max(1, max));
                    g.drawString(mc.font, left + "/" + max, x + 20, ry + 4, tc(c, col), c.shadow);
                }
            }
        }
    }

    // ------------------------------------------------------------------ potion effects

    static final class Potions extends HudWidget {
        Potions(ElementConfig d) {
            super("potion", "Potion Effects", d);
        }

        private List<String[]> rows(Minecraft mc, boolean preview) {
            List<String[]> out = new ArrayList<>();
            if (preview || mc.player == null) {
                out.add(new String[]{"Speed II", "4:20", "0"});
                out.add(new String[]{"Strength", "1:10", "0"});
                out.add(new String[]{"Fire Resistance", "0:08", "1"});
                return out;
            }
            Player p = mc.player;
            List<MobEffectInstance> list = new ArrayList<>(p.getActiveEffects());
            list.sort(Comparator.comparingInt(MobEffectInstance::getDuration).reversed());
            for (MobEffectInstance e : list) {
                String name = e.getEffect().value().getDisplayName().getString();
                int amp = e.getAmplifier();
                if (amp > 0) name += amp < ROMAN.length ? ROMAN[amp] : " " + (amp + 1);
                String time = e.isInfiniteDuration() ? "∞" : TimeFmt.mmss(e.getDuration());
                boolean low = !e.isInfiniteDuration() && e.getDuration() < 200;
                out.add(new String[]{name, time, low ? "1" : "0"});
            }
            return out;
        }

        @Override
        public int contentWidth(Minecraft mc, boolean preview) {
            int w = mc.font.width("Fire Resistance 0:00");
            for (String[] r : rows(mc, preview)) w = Math.max(w, mc.font.width(r[0]) + 8 + mc.font.width(r[1]));
            return w;
        }

        @Override
        public int contentHeight(Minecraft mc, boolean preview) {
            return Math.max(1, rows(mc, preview).size()) * 10 - 1;
        }

        @Override
        public void render(GuiGraphics g, Minecraft mc, ElementConfig c, boolean preview, int x, int y) {
            int w = contentWidth(mc, preview);
            int i = 0;
            for (String[] r : rows(mc, preview)) {
                int ry = y + i * 10;
                g.drawString(mc.font, r[0], x, ry, tc(c, c.textColor), c.shadow);
                int col = r[2].equals("1") ? Colors.RED : Colors.CYAN;
                g.drawString(mc.font, r[1], x + w - mc.font.width(r[1]), ry, tc(c, col), c.shadow);
                i++;
            }
        }
    }

    // ------------------------------------------------------------------ compass strip

    static final class CompassStrip extends HudWidget {
        private static final int HALF = 70;
        private static final float PX_PER_DEG = HALF / 60f;

        CompassStrip(ElementConfig d) {
            super("compass", "Compass", d);
        }

        @Override
        public int contentWidth(Minecraft mc, boolean preview) {
            return HALF * 2;
        }

        @Override
        public int contentHeight(Minecraft mc, boolean preview) {
            return 12;
        }

        @Override
        public void render(GuiGraphics g, Minecraft mc, ElementConfig c, boolean preview, int x, int y) {
            float yaw = preview || mc.player == null ? 20f : mc.player.getYRot();
            float heading = ((yaw + 180f) % 360f + 360f) % 360f; // 0 = north
            int cx = x + HALF;
            for (int a = 0; a < 360; a += 15) {
                float rel = ((a - heading + 540f) % 360f) - 180f;
                if (Math.abs(rel) > 60f) continue;
                int px = cx + Math.round(rel * PX_PER_DEG);
                String label = switch (a) {
                    case 0 -> "N";
                    case 90 -> "E";
                    case 180 -> "S";
                    case 270 -> "W";
                    default -> null;
                };
                float fade = 1f - Math.abs(rel) / 70f;
                if (label != null) {
                    int col = Colors.withAlpha(a == 0 ? Colors.RED : c.textColor, Math.round(255 * fade));
                    g.drawString(mc.font, label, px - mc.font.width(label) / 2, y, col, c.shadow);
                } else {
                    g.fill(px, y + 3, px + 1, y + 6, Colors.withAlpha(c.textColor, Math.round(150 * fade)));
                }
            }
            g.fill(cx, y + 9, cx + 1, y + 12, Colors.aura(Theme.time()));
        }
    }

    // ------------------------------------------------------------------ target info

    static final class Target extends HudWidget {
        Target(ElementConfig d) {
            super("target", "Target Info", d);
        }

        private boolean show(boolean preview) {
            return preview || System.currentTimeMillis() - Metrics.targetTimeMs < 4000;
        }

        @Override
        public int contentWidth(Minecraft mc, boolean preview) {
            String n = preview ? "Steve" : Metrics.targetName;
            return Math.max(80, mc.font.width(n));
        }

        @Override
        public int contentHeight(Minecraft mc, boolean preview) {
            return 18;
        }

        @Override
        public void render(GuiGraphics g, Minecraft mc, ElementConfig c, boolean preview, int x, int y) {
            if (!show(preview)) return;
            String name = preview ? "Steve" : Metrics.targetName;
            float hp = preview ? 14f : Metrics.targetHealth;
            float max = preview ? 20f : Metrics.targetMaxHealth;
            g.drawString(mc.font, name, x, y, tc(c, c.textColor), c.shadow);
            int w = contentWidth(mc, preview);
            Theme.roundRect(g, x, y + 12, w, 4, 1, Colors.withAlpha(0xFF000000, 120));
            if (max > 0) {
                float f = Math.max(0f, Math.min(1f, hp / max));
                Theme.roundRect(g, x, y + 12, Math.max(1, Math.round(w * f)), 4, 1, Colors.durability(f));
                String t = String.format("%.1f/%.0f", hp, max);
                g.drawString(mc.font, t, x + w - mc.font.width(t), y, tc(c, Colors.MUTED), false);
            }
        }
    }

    // ------------------------------------------------------------------ registry

    public static List<HudWidget> all() {
        List<HudWidget> l = new ArrayList<>();
        l.add(new Text("fps", "FPS", new ElementConfig(true, 0.010f, 0.020f), mc -> one("FPS " + Metrics.fps()), one("FPS 144")));
        l.add(new Text("cps", "CPS", new ElementConfig(true, 0.010f, 0.075f), mc -> one("CPS " + Metrics.leftCps() + " | " + Metrics.rightCps()), one("CPS 8 | 3")));
        l.add(new Text("coords", "Coordinates", new ElementConfig(true, 0.010f, 0.130f),
                mc -> List.of(String.format("X %.1f", mc.player.getX()), String.format("Y %.1f", mc.player.getY()), String.format("Z %.1f", mc.player.getZ())),
                List.of("X 128.5", "Y 64.0", "Z -302.2")));
        l.add(new Text("direction", "Direction", new ElementConfig(false, 0.010f, 0.260f),
                mc -> one(Compass.name(mc.player.getYRot()) + "  " + Compass.axis(mc.player.getYRot())), one("N  -Z")));
        l.add(new CompassStrip(pos(false, 0.410f, 0.020f)));
        l.add(new Text("ping", "Ping", new ElementConfig(false, 0.010f, 0.310f), mc -> one("Ping " + ping(mc)), one("Ping 42ms")));
        l.add(new Text("clock", "Clock", new ElementConfig(false, 0.010f, 0.360f),
                mc -> List.of(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")), "Day " + TimeFmt.mcClock(mc.level == null ? 0 : mc.level.getDayTime())),
                List.of("21:37", "Day 06:00")));
        l.add(new Text("speed", "Speedometer", new ElementConfig(false, 0.010f, 0.440f), mc -> one(String.format("%.1f b/s", Metrics.speed())), one("4.3 b/s")));
        l.add(new Text("biome", "Biome", new ElementConfig(false, 0.010f, 0.490f), mc -> one(biome(mc)), one("Dark Forest")));
        l.add(new Text("memory", "Memory", new ElementConfig(false, 0.010f, 0.540f), mc -> {
            Runtime r = Runtime.getRuntime();
            double used = (r.totalMemory() - r.freeMemory()) / 1048576.0;
            return one(String.format("RAM %.1f/%.1f GB", used / 1024.0, r.maxMemory() / 1073741824.0));
        }, one("RAM 1.8/4.0 GB")));
        l.add(new Text("server", "Server Info", new ElementConfig(false, 0.010f, 0.590f), mc -> one(server(mc)), one("play.example.net")));
        l.add(new Text("combo", "Combo Counter", new ElementConfig(false, 0.010f, 0.640f), mc -> one("Combo " + Metrics.combo()), one("Combo 4")));
        l.add(new Text("reach", "Reach Display", new ElementConfig(false, 0.010f, 0.690f), mc -> {
            boolean fresh = System.currentTimeMillis() - Metrics.lastReachMs < 5000 && Metrics.lastReach >= 0;
            return one(fresh ? String.format("Reach %.2f", Metrics.lastReach) : "Reach --");
        }, one("Reach 2.91")));
        l.add(new Text("sprint", "Sprint Status", new ElementConfig(false, 0.010f, 0.740f), mc -> {
            String s = mc.player.isSprinting() ? "Sprinting" : mc.player.isCrouching() ? "Sneaking" : "Walking";
            return one(s);
        }, one("Sprinting")));
        l.add(new Text("durability", "Held Item", new ElementConfig(false, 0.010f, 0.790f), mc -> {
            ItemStack s = mc.player.getMainHandItem();
            if (s.isEmpty()) return one("Empty hand");
            if (!s.isDamageableItem()) return one(s.getHoverName().getString());
            return one(s.getHoverName().getString() + " " + (s.getMaxDamage() - s.getDamageValue()) + "/" + s.getMaxDamage());
        }, one("Diamond Sword 1500/1561")));
        l.add(new Keystrokes(pos(true, 0.010f, 0.560f)));
        l.add(new Armor(pos(true, 0.930f, 0.380f)));
        l.add(new Potions(pos(true, 0.780f, 0.020f)));
        l.add(new Target(pos(false, 0.420f, 0.600f)));
        return l;
    }

    private static ElementConfig pos(boolean enabled, float x, float y) {
        return new ElementConfig(enabled, x, y);
    }
}
