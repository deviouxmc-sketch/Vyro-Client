import gg.vyro.core.*;

/** Plain-Java checks for the Minecraft-free core (no JUnit needed): see tools/README. */
public class SelfCheck {
    static int failed = 0;
    static void eq(Object got, Object want, String what) {
        if (!java.util.Objects.equals(got, want)) { failed++; System.out.println("FAIL " + what + ": got " + got + " want " + want); }
        else System.out.println("ok   " + what);
    }
    public static void main(String[] a) {
        Cps c = new Cps();
        for (int i = 0; i < 5; i++) c.click(1000 + i * 100);
        eq(c.get(1450), 5, "cps window");
        eq(c.get(2050), 4, "cps drops old clicks");
        eq(c.get(5000), 0, "cps empties");

        Combo k = new Combo(2000);
        k.hit(0); k.hit(500);
        eq(k.hit(900), 3, "combo counts");
        eq(k.get(2900), 3, "combo within timeout");
        eq(k.get(2901), 0, "combo expires");
        eq(k.hit(10_000), 1, "combo restarts");
        k.reset(); eq(k.get(10_001), 0, "combo reset");

        eq(Compass.name(0f), "S", "yaw 0 south");
        eq(Compass.name(90f), "W", "yaw 90 west");
        eq(Compass.name(-90f), "E", "yaw -90 east");
        eq(Compass.name(180f), "N", "yaw 180 north");
        eq(Compass.name(-180f), "N", "yaw -180 north");
        eq(Compass.name(135f), "NW", "yaw 135 NW");
        eq(Compass.name(-45f), "SE", "yaw -45 SE");
        eq(Compass.axis(180f), "-Z", "north is -Z");

        eq(TimeFmt.mcClock(0), "06:00", "tick 0");
        eq(TimeFmt.mcClock(6000), "12:00", "noon");
        eq(TimeFmt.mcClock(18000), "00:00", "midnight");
        eq(TimeFmt.mcClock(13000), "19:00", "dusk");
        eq(TimeFmt.mcClock(24000 + 500), "06:30", "wraps day");
        eq(TimeFmt.mmss(1200), "1:00", "mmss 60s");
        eq(TimeFmt.mmss(-5), "0:00", "mmss negative");

        eq(Colors.lerp(0xFF000000, 0xFFFFFFFF, 0.5f) & 0xFF, 127, "lerp mid");
        eq(Colors.withAlpha(0xFFFFFFFF, 128) >>> 24, 128, "alpha");
        eq(Colors.durability(1f), Colors.GREEN, "dura full green");
        eq(Colors.durability(0f), Colors.RED, "dura empty red");
        eq(Colors.aura(0.0), Colors.PURPLE, "aura starts purple");

        eq(Geometry.contains(10, 10, 20, 20, 10, 10), true, "contains edge");
        eq(Geometry.contains(10, 10, 20, 20, 30, 15), false, "excludes far edge");
        eq(Geometry.clampX(500, 100, 400), 300f, "clampX");
        eq(Geometry.clampY(-5, 10, 100), 0f, "clampY");
        eq(Geometry.clampX(5, 500, 400), 0f, "box wider than screen");
        eq(Geometry.resizeScale(1f, 100, 50), 1.5f, "resize up");
        eq(Geometry.resizeScale(1f, 100, -500), 0.5f, "resize floor");
        eq(Geometry.resizeScale(1f, 100, 9999), 3.0f, "resize cap");

        System.out.println(failed == 0 ? "ALL PASSED" : failed + " FAILED");
        System.exit(failed == 0 ? 0 : 1);
    }
}
