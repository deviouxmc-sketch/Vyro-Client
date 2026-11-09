package gg.vyro.client;

import gg.vyro.core.Combo;
import gg.vyro.core.Cps;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/** Live numbers shown by the HUD. Mutated from the render thread and the client tick (same thread). */
public final class Metrics {
    private Metrics() {}

    private static final Cps LEFT = new Cps();
    private static final Cps RIGHT = new Cps();
    private static boolean leftWasDown;
    private static boolean rightWasDown;

    public static final Combo COMBO = new Combo(2500);

    // fps
    private static long fpsWindowStart = System.nanoTime();
    private static int frames;
    private static int fps;

    // movement
    private static double lastX, lastZ;
    private static boolean haveLast;
    private static double speed; // blocks per second, smoothed
    private static int lastHurtTime;

    // combat
    public static String targetName = "";
    public static float targetHealth;
    public static float targetMaxHealth;
    public static long targetTimeMs;
    public static double lastReach = -1;
    public static long lastReachMs;

    public static int fps() { return fps; }
    public static double speed() { return speed; }
    public static int leftCps() { return LEFT.get(System.currentTimeMillis()); }
    public static int rightCps() { return RIGHT.get(System.currentTimeMillis()); }
    public static int combo() { return COMBO.get(System.currentTimeMillis()); }

    /** Call once per rendered frame. */
    public static void frame(Minecraft mc) {
        frames++;
        long now = System.nanoTime();
        if (now - fpsWindowStart >= 1_000_000_000L) {
            fps = frames;
            frames = 0;
            fpsWindowStart = now;
        }
        // Mouse buttons are polled every frame (not every 50 ms tick) so fast clicking is counted properly.
        if (mc.screen == null && mc.getWindow() != null) {
            long handle = mc.getWindow().handle();
            boolean l = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
            boolean r = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
            long ms = System.currentTimeMillis();
            if (l && !leftWasDown) LEFT.click(ms);
            if (r && !rightWasDown) RIGHT.click(ms);
            leftWasDown = l;
            rightWasDown = r;
        } else {
            leftWasDown = false;
            rightWasDown = false;
        }
    }

    /** Call from the end of every client tick. */
    public static void tick(Minecraft mc) {
        Player p = mc.player;
        if (p == null) {
            haveLast = false;
            return;
        }
        if (haveLast) {
            double d = Math.hypot(p.getX() - lastX, p.getZ() - lastZ) * 20.0;
            speed += (d - speed) * 0.35;
        }
        lastX = p.getX();
        lastZ = p.getZ();
        haveLast = true;

        int hurt = p.hurtTime;
        if (hurt > 0 && lastHurtTime == 0) COMBO.reset(); // you got hit: combo over
        lastHurtTime = hurt;
    }

    public static void onAttack(Player player, Entity target) {
        long now = System.currentTimeMillis();
        COMBO.hit(now);
        Vec3 eye = player.getEyePosition();
        AABB bb = target.getBoundingBox();
        double dx = Math.max(Math.max(bb.minX - eye.x, 0), eye.x - bb.maxX);
        double dy = Math.max(Math.max(bb.minY - eye.y, 0), eye.y - bb.maxY);
        double dz = Math.max(Math.max(bb.minZ - eye.z, 0), eye.z - bb.maxZ);
        lastReach = Math.sqrt(dx * dx + dy * dy + dz * dz);
        lastReachMs = now;
        targetName = target.getName().getString();
        if (target instanceof LivingEntity le) {
            targetHealth = le.getHealth();
            targetMaxHealth = le.getMaxHealth();
        } else {
            targetHealth = 0;
            targetMaxHealth = 0;
        }
        targetTimeMs = now;
    }
}
