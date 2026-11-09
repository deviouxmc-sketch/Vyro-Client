package gg.vyro.client.ui;

import gg.vyro.core.Colors;
import net.minecraft.client.gui.GuiGraphics;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** Tiny retained-mode controls drawn with the VYRO theme. Coordinates are in the owning panel's space. */
public final class Controls {
    private Controls() {}

    public abstract static class Control {
        public int x, y, w, h;

        protected Control(int x, int y, int w, int h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        public abstract void render(GuiGraphics g, int mx, int my);

        public boolean click(double mx, double my) { return false; }

        public void drag(double mx, double my) {}

        public void release() {}

        public boolean hit(double mx, double my) {
            return Theme.inside(mx, my, x, y, w, h);
        }
    }

    public static final class Label extends Control {
        private final Supplier<String> text;
        private final int color;

        public Label(int x, int y, Supplier<String> text, int color) {
            super(x, y, 10, 10);
            this.text = text;
            this.color = color;
        }

        @Override
        public void render(GuiGraphics g, int mx, int my) {
            Theme.text(g, text.get(), x, y, color, false);
        }
    }

    public static final class Button extends Control {
        private final Supplier<String> label;
        private final Runnable action;
        private final boolean primary;

        public Button(int x, int y, int w, int h, Supplier<String> label, boolean primary, Runnable action) {
            super(x, y, w, h);
            this.label = label;
            this.primary = primary;
            this.action = action;
        }

        @Override
        public void render(GuiGraphics g, int mx, int my) {
            boolean over = hit(mx, my);
            int bg = primary ? Colors.lerp(Colors.PURPLE, Colors.BLUE, 0.5f) : Colors.PANEL_2;
            if (over) bg = Colors.lerp(bg, 0xFFFFFFFF, 0.12f);
            Theme.roundRect(g, x, y, w, h, 3, bg);
            String s = label.get();
            Theme.text(g, s, x + (w - Theme.textWidth(s)) / 2, y + (h - 8) / 2, Colors.TEXT, false);
        }

        @Override
        public boolean click(double mx, double my) {
            action.run();
            return true;
        }
    }

    public static final class Toggle extends Control {
        private final String label;
        private final BooleanSupplier get;
        private final Consumer<Boolean> set;

        public Toggle(int x, int y, int w, String label, BooleanSupplier get, Consumer<Boolean> set) {
            super(x, y, w, 16);
            this.label = label;
            this.get = get;
            this.set = set;
        }

        @Override
        public void render(GuiGraphics g, int mx, int my) {
            boolean on = get.getAsBoolean();
            Theme.text(g, label, x, y + 4, Colors.TEXT, false);
            int sx = x + w - 30;
            Theme.roundRect(g, sx, y + 2, 28, 12, 6, on ? Colors.lerp(Colors.PURPLE, Colors.CYAN, 0.35f) : Colors.PANEL_2);
            int knob = on ? sx + 16 : sx + 2;
            Theme.roundRect(g, knob, y + 4, 8, 8, 4, 0xFFFFFFFF);
        }

        @Override
        public boolean click(double mx, double my) {
            set.accept(!get.getAsBoolean());
            return true;
        }
    }

    public static final class Slider extends Control {
        private final String label;
        private final double min, max, step;
        private final DoubleSupplier get;
        private final DoubleConsumer set;
        private final String fmt;

        public Slider(int x, int y, int w, String label, double min, double max, double step, String fmt, DoubleSupplier get, DoubleConsumer set) {
            super(x, y, w, 22);
            this.label = label;
            this.min = min;
            this.max = max;
            this.step = step;
            this.fmt = fmt;
            this.get = get;
            this.set = set;
        }

        private void setFrom(double mx) {
            double f = Math.max(0, Math.min(1, (mx - x) / (double) w));
            double v = min + f * (max - min);
            if (step > 0) v = Math.round(v / step) * step;
            set.accept(Math.max(min, Math.min(max, v)));
        }

        @Override
        public void render(GuiGraphics g, int mx, int my) {
            double v = get.getAsDouble();
            Theme.text(g, label, x, y, Colors.TEXT, false);
            String val = String.format(fmt, v);
            Theme.text(g, val, x + w - Theme.textWidth(val), y, Colors.MUTED, false);
            int ty = y + 14;
            Theme.roundRect(g, x, ty, w, 4, 2, Colors.PANEL_2);
            int fill = (int) Math.round((v - min) / (max - min) * w);
            Theme.roundRect(g, x, ty, Math.max(2, fill), 4, 2, Colors.lerp(Colors.PURPLE, Colors.CYAN, (float) ((v - min) / (max - min))));
            Theme.roundRect(g, x + Math.max(0, Math.min(w - 6, fill - 3)), ty - 2, 6, 8, 3, 0xFFFFFFFF);
        }

        @Override
        public boolean click(double mx, double my) {
            setFrom(mx);
            return true;
        }

        @Override
        public void drag(double mx, double my) {
            setFrom(mx);
        }
    }

    /** Click to cycle through a list of values. */
    public static final class Cycle extends Control {
        private final String label;
        private final String[] values;
        private final Supplier<String> get;
        private final Consumer<String> set;

        public Cycle(int x, int y, int w, String label, String[] values, Supplier<String> get, Consumer<String> set) {
            super(x, y, w, 20);
            this.label = label;
            this.values = values;
            this.get = get;
            this.set = set;
        }

        @Override
        public void render(GuiGraphics g, int mx, int my) {
            Theme.text(g, label, x, y + 6, Colors.TEXT, false);
            int bw = 92;
            int bx = x + w - bw;
            Theme.roundRect(g, bx, y, bw, 18, 3, hit(mx, my) && mx >= bx ? Colors.lerp(Colors.PANEL_2, 0xFFFFFFFF, 0.1f) : Colors.PANEL_2);
            String s = get.get();
            Theme.text(g, s, bx + (bw - Theme.textWidth(s)) / 2, y + 5, Colors.CYAN, false);
        }

        @Override
        public boolean click(double mx, double my) {
            String cur = get.get();
            int i = 0;
            for (int k = 0; k < values.length; k++) if (values[k].equals(cur)) i = k;
            set.accept(values[(i + 1) % values.length]);
            return true;
        }
    }

    /** A row of colour swatches. */
    public static final class Swatches extends Control {
        public static final int[] PALETTE = {
                0xFFECECF4, 0xFF8B5CF6, 0xFF3B82F6, 0xFF22D3EE, 0xFF34D399, 0xFFFBBF24, 0xFFEF4444, 0xFFF472B6, 0xFF0B0B10
        };
        private final String label;
        private final IntSupplier get;
        private final IntConsumer set;

        public Swatches(int x, int y, int w, String label, IntSupplier get, IntConsumer set) {
            super(x, y, w, 26);
            this.label = label;
            this.get = get;
            this.set = set;
        }

        @Override
        public void render(GuiGraphics g, int mx, int my) {
            Theme.text(g, label, x, y, Colors.TEXT, false);
            for (int i = 0; i < PALETTE.length; i++) {
                int sx = x + i * 18;
                boolean sel = (PALETTE[i] & 0xFFFFFF) == (get.getAsInt() & 0xFFFFFF);
                if (sel) Theme.roundRect(g, sx - 1, y + 11, 16, 16, 4, 0xFFFFFFFF);
                Theme.roundRect(g, sx, y + 12, 14, 14, 3, PALETTE[i]);
            }
        }

        @Override
        public boolean click(double mx, double my) {
            int i = (int) ((mx - x) / 18);
            if (i >= 0 && i < PALETTE.length && my >= y + 12) {
                set.accept(PALETTE[i]);
                return true;
            }
            return false;
        }
    }
}
