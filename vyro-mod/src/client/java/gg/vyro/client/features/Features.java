package gg.vyro.client.features;

import com.mojang.blaze3d.platform.InputConstants;
import gg.vyro.client.Metrics;
import gg.vyro.client.config.FeatureConfig;
import gg.vyro.client.config.VyroConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWScrollCallback;

/** Gameplay features: zoom, freelook, toggle sprint/sneak, hit sounds, and per-tick bookkeeping. */
public final class Features {
    private Features() {}

    public static KeyMapping openMenu;
    private static KeyMapping zoomKey;
    private static KeyMapping freelookKey;
    private static KeyMapping sneakToggleKey;
    private static KeyMapping sprintToggleKey;

    private static boolean sneakLatched;
    private static boolean sprintWasForced;
    private static boolean zoomToggled;
    private static boolean freelookToggled;
    private static boolean zoomOn;
    private static boolean zoomDirty;
    private static boolean scrollHooked;
    private static GLFWScrollCallback previousScroll;

    public static final String[] HIT_SOUNDS = {"ding", "arrow", "levelup", "anvil"};

    public static void init() {
        KeyMapping.Category cat = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("vyro", "main"));
        openMenu = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.vyro.menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, cat));
        zoomKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.vyro.zoom", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, cat));
        freelookKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.vyro.freelook", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, cat));
        sprintToggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.vyro.toggle_sprint", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, cat));
        sneakToggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.vyro.toggle_sneak", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, cat));

        ClientTickEvents.END_CLIENT_TICK.register(Features::tick);

        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (level.isClientSide()) {
                Metrics.onAttack(player, entity);
                playHitSound();
            }
            return InteractionResult.PASS;
        });
    }

    private static void tick(Minecraft mc) {
        Metrics.tick(mc);
        FeatureConfig f = VyroConfig.get().features;

        if (openMenu.consumeClick() && mc.screen == null) {
            mc.setScreen(new gg.vyro.client.ui.VyroMenuScreen(null));
        }

        while (sprintToggleKey.consumeClick()) {
            f.toggleSprint = !f.toggleSprint;
            VyroConfig.get().save();
            if (mc.player != null) mc.player.displayClientMessage(Component.literal("[VYRO] Toggle sprint " + (f.toggleSprint ? "on" : "off")), true);
        }
        while (sneakToggleKey.consumeClick()) {
            sneakLatched = !sneakLatched;
            if (!sneakLatched) mc.options.keyShift.setDown(false);
            if (mc.player != null) mc.player.displayClientMessage(Component.literal("[VYRO] Toggle sneak " + (sneakLatched ? "on" : "off")), true);
        }

        boolean inGame = mc.player != null && mc.screen == null;
        if (inGame) {
            if (f.toggleSprint) {
                mc.options.keySprint.setDown(true);
                sprintWasForced = true;
            } else if (sprintWasForced) {
                mc.options.keySprint.setDown(false);
                sprintWasForced = false;
            }
            if (sneakLatched) mc.options.keyShift.setDown(true);
        }

        if (!scrollHooked && mc.getWindow() != null) {
            // Chain our own scroll callback in front of Minecraft's so zoom can use the wheel.
            previousScroll = GLFW.glfwSetScrollCallback(mc.getWindow().handle(), Features::onScroll);
            scrollHooked = true;
        }

        // Zoom: hold the key, or press once when toggle mode is on. The FOV mixin applies it smoothly.
        if (f.zoomEnabled && inGame) {
            if (f.zoomToggleMode) {
                while (zoomKey.consumeClick()) zoomToggled = !zoomToggled;
                zoomOn = zoomToggled;
            } else {
                while (zoomKey.consumeClick()) { /* drain */ }
                zoomToggled = false;
                zoomOn = zoomKey.isDown();
            }
        } else {
            zoomToggled = false;
            zoomOn = false;
        }
        Zoom.setTarget(zoomOn ? 1.0f / Math.max(1.1f, f.zoomFactor) : 1.0f);
        if (!zoomOn && zoomDirty) {
            VyroConfig.get().save(); // magnification changed by scrolling is remembered
            zoomDirty = false;
        }

        // Freelook: look around without turning the player.
        boolean wantFreelook;
        if (f.freelookEnabled && inGame) {
            if (f.freelookToggleMode) {
                while (freelookKey.consumeClick()) freelookToggled = !freelookToggled;
                wantFreelook = freelookToggled;
            } else {
                while (freelookKey.consumeClick()) { /* drain */ }
                freelookToggled = false;
                wantFreelook = freelookKey.isDown();
            }
        } else {
            freelookToggled = false;
            wantFreelook = false;
        }
        if (wantFreelook && !Freelook.requested()) {
            Freelook.start(mc.player.getYRot(), mc.player.getXRot());
        } else if (!wantFreelook && Freelook.requested()) {
            Freelook.stop();
        }
    }

    /** Mouse wheel: while zoomed it changes magnification (and is not passed on, so the hotbar stays put). */
    private static void onScroll(long window, double xOffset, double yOffset) {
        Minecraft mc = Minecraft.getInstance();
        if (zoomOn && mc.screen == null && yOffset != 0) {
            FeatureConfig f = VyroConfig.get().features;
            f.zoomFactor = Math.max(1.5f, Math.min(10.0f, f.zoomFactor + (float) Math.signum(yOffset) * 0.5f));
            zoomDirty = true;
            return;
        }
        if (previousScroll != null) previousScroll.invoke(window, xOffset, yOffset);
    }

    public static SoundEvent soundFor(String type) {
        return switch (type) {
            case "arrow" -> SoundEvents.ARROW_HIT_PLAYER;
            case "levelup" -> SoundEvents.PLAYER_LEVELUP;
            case "anvil" -> SoundEvents.ANVIL_LAND;
            default -> SoundEvents.EXPERIENCE_ORB_PICKUP;
        };
    }

    public static void playHitSound() {
        FeatureConfig f = VyroConfig.get().features;
        if (!f.hitSound) return;
        playPreview(f.hitSoundType, f.hitSoundVolume);
    }

    public static void playPreview(String type, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(soundFor(type), 1.0f, volume));
    }
}
