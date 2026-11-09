package gg.vyro.client.features;

import com.mojang.blaze3d.platform.InputConstants;
import gg.vyro.client.Metrics;
import gg.vyro.client.config.FeatureConfig;
import gg.vyro.client.config.VyroConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
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

/** Gameplay features: zoom, toggle sprint/sneak, hit sounds, and the per-tick bookkeeping. */
public final class Features {
    private Features() {}

    public static KeyMapping openMenu;
    private static KeyMapping zoomKey;
    private static KeyMapping sneakToggleKey;
    private static KeyMapping sprintToggleKey;

    private static boolean zooming;
    private static int savedFov;
    private static boolean sneakLatched;
    private static boolean sprintWasForced;

    public static final String[] HIT_SOUNDS = {"ding", "arrow", "levelup", "anvil"};

    public static void init() {
        KeyMapping.Category cat = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("vyro", "main"));
        openMenu = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.vyro.menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, cat));
        zoomKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.vyro.zoom", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, cat));
        sprintToggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.vyro.toggle_sprint", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, cat));
        sneakToggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.vyro.toggle_sneak", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, cat));

        ClientTickEvents.END_CLIENT_TICK.register(Features::tick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> restoreFov(mc));

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

        // sprint toggle key flips the saved setting; chat feedback so it is never a mystery
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

        if (mc.player != null && mc.screen == null) {
            if (f.toggleSprint) {
                mc.options.keySprint.setDown(true);
                sprintWasForced = true;
            } else if (sprintWasForced) {
                mc.options.keySprint.setDown(false);
                sprintWasForced = false;
            }
            if (sneakLatched) mc.options.keyShift.setDown(true);
        }

        // Zoom: drive the FOV option while the key is held, restore on release.
        boolean want = zoomKey.isDown() && mc.screen == null && mc.player != null;
        if (want && !zooming) {
            savedFov = mc.options.fov().get();
            zooming = true;
        }
        if (want) {
            mc.options.fov().set(Math.max(30, Math.min(110, f.zoomFov)));
        } else if (zooming) {
            restoreFov(mc);
        }
    }

    private static void restoreFov(Minecraft mc) {
        if (zooming) {
            mc.options.fov().set(savedFov);
            zooming = false;
        }
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
