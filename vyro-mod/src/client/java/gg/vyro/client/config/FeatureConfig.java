package gg.vyro.client.config;

public class FeatureConfig {
    public boolean toggleSprint = false;
    public boolean toggleSneak = false;
    /** Old setting, kept so existing config files still load. */
    public int zoomFov = 30;
    /** Zoom magnification while the zoom key is held (2x .. 10x). */
    public float zoomFactor = 4.0f;
    public boolean zoomEnabled = true;
    /** true = press once to zoom, press again to stop; false = hold. */
    public boolean zoomToggleMode = false;
    public boolean freelookEnabled = true;
    public boolean freelookToggleMode = false;
    /** Pull the camera back to third person while freelooking (otherwise it stays first person). */
    public boolean freelookThirdPerson = true;
    public boolean disableDamageTilt = false;
    public boolean hideFireOverlay = false;
    /** 0 = vanilla; larger values push the first-person fire overlay further down. */
    public float fireLowering = 0.0f;
    public boolean noPumpkinOverlay = false;
    public boolean hitSound = false;
    /** One of: ding, bell, pling, hat, snare */
    public String hitSoundType = "ding";
    public float hitSoundVolume = 0.7f;
    /** Menu/HUD aura animation globally on/off. */
    public boolean auraEnabled = true;
    // Vanilla HUD pieces: offset (fraction of screen) and scale
    public float hotbarScale = 1.0f;
    public float hotbarOffsetY = 0.0f;
    public float scoreboardScale = 1.0f;
    public float scoreboardOffsetX = 0.0f;
    public float scoreboardOffsetY = 0.0f;
    public float bossBarScale = 1.0f;
    public float bossBarOffsetY = 0.0f;
}
