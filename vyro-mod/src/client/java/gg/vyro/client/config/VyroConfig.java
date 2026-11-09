package gg.vyro.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import gg.vyro.client.hud.HudManager;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Everything that is saved to disk: HUD layout, crosshair, feature toggles, and layout presets. */
public final class VyroConfig {
    public Map<String, ElementConfig> elements = new LinkedHashMap<>();
    public CrosshairConfig crosshair = new CrosshairConfig();
    public FeatureConfig features = new FeatureConfig();

    /** Shape of a preset file: layout only, not feature toggles. */
    public static final class Preset {
        public Map<String, ElementConfig> elements = new LinkedHashMap<>();
        public CrosshairConfig crosshair = new CrosshairConfig();
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static VyroConfig instance = new VyroConfig();

    public static VyroConfig get() {
        return instance;
    }

    public static Path dir() {
        return FabricLoader.getInstance().getConfigDir().resolve("vyro");
    }

    private static Path file() {
        return dir().resolve("config.json");
    }

    private static Path presetsDir() {
        return dir().resolve("presets");
    }

    public static void load() {
        VyroConfig loaded = null;
        try {
            if (Files.isRegularFile(file())) {
                loaded = GSON.fromJson(Files.readString(file(), StandardCharsets.UTF_8), VyroConfig.class);
            }
        } catch (Exception e) {
            // A corrupt file must never stop the game: keep a copy and start from defaults.
            try {
                Files.move(file(), dir().resolve("config.broken.json"), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
            }
            System.err.println("[VYRO] config.json unreadable, using defaults: " + e);
        }
        instance = loaded != null ? loaded : new VyroConfig();
        instance.fillMissing();
    }

    /** Make sure every known widget has an entry (new mod versions add widgets). */
    public void fillMissing() {
        if (elements == null) elements = new LinkedHashMap<>();
        if (crosshair == null) crosshair = new CrosshairConfig();
        if (features == null) features = new FeatureConfig();
        HudManager.WIDGETS.forEach(w -> elements.computeIfAbsent(w.id, k -> w.defaults.copy()));
    }

    public void save() {
        writeAtomic(file(), GSON.toJson(this));
    }

    private static void writeAtomic(Path target, String json) {
        try {
            Files.createDirectories(target.getParent());
            Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
            Files.writeString(tmp, json, StandardCharsets.UTF_8);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            System.err.println("[VYRO] could not save " + target + ": " + e);
        }
    }

    // ---------------------------------------------------------------- presets

    public static String cleanName(String name) {
        String n = name.replaceAll("[^A-Za-z0-9 _-]", "").trim();
        return n.isEmpty() ? "Preset" : (n.length() > 32 ? n.substring(0, 32) : n);
    }

    public List<String> presets() {
        List<String> out = new ArrayList<>();
        if (!Files.isDirectory(presetsDir())) return out;
        try (Stream<Path> s = Files.list(presetsDir())) {
            s.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".json"))
                    .map(n -> n.substring(0, n.length() - 5))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .forEach(out::add);
        } catch (IOException ignored) {
        }
        return out;
    }

    public String nextPresetName() {
        List<String> have = presets();
        for (int i = 1; ; i++) {
            String n = "Preset " + i;
            if (!have.contains(n)) return n;
        }
    }

    public void savePreset(String name) {
        Preset p = new Preset();
        elements.forEach((k, v) -> p.elements.put(k, v.copy()));
        p.crosshair = GSON.fromJson(GSON.toJson(crosshair), CrosshairConfig.class);
        writeAtomic(presetsDir().resolve(cleanName(name) + ".json"), GSON.toJson(p));
    }

    public boolean loadPreset(String name) {
        try {
            Path f = presetsDir().resolve(cleanName(name) + ".json");
            Preset p = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), Preset.class);
            if (p == null || p.elements == null) return false;
            elements = new LinkedHashMap<>(p.elements);
            if (p.crosshair != null) crosshair = p.crosshair;
            fillMissing();
            save();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void deletePreset(String name) {
        try {
            Files.deleteIfExists(presetsDir().resolve(cleanName(name) + ".json"));
        } catch (IOException ignored) {
        }
    }
}
