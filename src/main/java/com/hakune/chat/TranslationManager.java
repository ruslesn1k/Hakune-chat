package com.hakune.chat;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class TranslationManager {
    private final JavaPlugin plugin;
    private FileConfiguration active;
    private FileConfiguration fallback;
    private String language = "en";

    public TranslationManager(JavaPlugin plugin) {
        this.plugin = plugin;
        ensureFiles();
        this.fallback = loadLang("en");
        this.active = fallback;
    }

    public void reload(String language) {
        this.language = (language == null || language.isBlank()) ? "en" : language.toLowerCase();
        this.fallback = loadLang("en");
        this.active = loadLang(this.language);
    }

    public String getLanguage() {
        return language;
    }

    public String get(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        String value = active != null ? active.getString(key) : null;
        if (value == null && fallback != null) {
            value = fallback.getString(key);
        }
        return value == null ? key : value;
    }

    public String format(String key, String... pairs) {
        Map<String, String> vars = new HashMap<>();
        if (pairs != null) {
            for (int i = 0; i + 1 < pairs.length; i += 2) {
                vars.put(pairs[i], pairs[i + 1]);
            }
        }
        return format(key, vars);
    }

    public String format(String key, Map<String, String> vars) {
        String text = get(key);
        if (vars == null || vars.isEmpty()) {
            return text;
        }
        for (Map.Entry<String, String> entry : vars.entrySet()) {
            String value = entry.getValue() == null ? "" : entry.getValue();
            text = text.replace("{" + entry.getKey() + "}", value);
        }
        return text;
    }

    private void ensureFiles() {
        File dir = new File(plugin.getDataFolder(), "translations");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File en = new File(dir, "en.yml");
        if (!en.exists()) {
            plugin.saveResource("translations/en.yml", false);
        }
        File ru = new File(dir, "ru.yml");
        if (!ru.exists()) {
            plugin.saveResource("translations/ru.yml", false);
        }
    }

    private FileConfiguration loadLang(String lang) {
        File file = new File(plugin.getDataFolder(), "translations/" + lang + ".yml");
        if (!file.exists()) {
            plugin.saveResource("translations/" + lang + ".yml", false);
        }
        return YamlConfiguration.loadConfiguration(file);
    }
}
