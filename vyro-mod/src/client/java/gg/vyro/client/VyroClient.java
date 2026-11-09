package gg.vyro.client;

import gg.vyro.client.config.VyroConfig;
import gg.vyro.client.features.Features;
import gg.vyro.client.hud.HudManager;
import net.fabricmc.api.ClientModInitializer;

public class VyroClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        VyroConfig.load();
        HudManager.init();
        Features.init();
    }
}
