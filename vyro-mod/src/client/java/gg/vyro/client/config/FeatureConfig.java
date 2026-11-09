package gg.vyro.client.config;

public class FeatureConfig {
    public boolean toggleSprint = false;
    public boolean toggleSneak = false;
    public int zoomFov = 30;
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
