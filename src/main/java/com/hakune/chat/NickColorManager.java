package com.hakune.chat;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class NickColorManager {
    private final JavaPlugin plugin;
    private final File file;
    private final FileConfiguration config;
    private final Map<UUID, NameStyle> styles = new ConcurrentHashMap<>();

    public NickColorManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "nickcolors.yml");
        this.config = YamlConfiguration.loadConfiguration(file);
        load();
    }

    public Component getNameComponent(Player player) {
        NameStyle style = styles.get(player.getUniqueId());
        if (style == null) {
            return Component.text(player.getName()).color(NamedTextColor.WHITE);
        }
        return style.apply(player.getName());
    }

    public boolean setColor(Player player, String input) {
        TextColor color = parseColor(input);
        if (color == null) {
            return false;
        }
        styles.put(player.getUniqueId(), new SolidStyle(color));
        saveStyle(player.getUniqueId(), "color", toHex(color), null);
        applyToPlayer(player);
        return true;
    }

    public boolean setGradient(Player player, String fromInput, String toInput) {
        TextColor from = parseColor(fromInput);
        TextColor to = parseColor(toInput);
        if (from == null || to == null) {
            return false;
        }
        styles.put(player.getUniqueId(), new GradientStyle(from, to));
        saveStyle(player.getUniqueId(), "gradient", toHex(from), toHex(to));
        applyToPlayer(player);
        return true;
    }

    public void reset(Player player) {
        styles.remove(player.getUniqueId());
        config.set(player.getUniqueId().toString(), null);
        try {
            config.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to save nickcolors.yml: " + ex.getMessage());
        }
        applyToPlayer(player);
    }

    public void applyToPlayer(Player player) {
        Component name = getNameComponent(player);
        player.displayName(name);
        player.playerListName(name);
    }

    public void applyToOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            applyToPlayer(player);
        }
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        for (String key : config.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(key);
            } catch (IllegalArgumentException ex) {
                continue;
            }
            String type = config.getString(key + ".type", "");
            if ("color".equalsIgnoreCase(type)) {
                String value = config.getString(key + ".color", "");
                TextColor color = parseColor(value);
                if (color != null) {
                    styles.put(uuid, new SolidStyle(color));
                }
            } else if ("gradient".equalsIgnoreCase(type)) {
                String from = config.getString(key + ".from", "");
                String to = config.getString(key + ".to", "");
                TextColor fromColor = parseColor(from);
                TextColor toColor = parseColor(to);
                if (fromColor != null && toColor != null) {
                    styles.put(uuid, new GradientStyle(fromColor, toColor));
                }
            }
        }
    }

    private void saveStyle(UUID uuid, String type, String color, String to) {
        String base = uuid.toString();
        config.set(base + ".type", type);
        config.set(base + ".color", null);
        config.set(base + ".from", null);
        config.set(base + ".to", null);
        if ("color".equalsIgnoreCase(type)) {
            config.set(base + ".color", color);
        } else if ("gradient".equalsIgnoreCase(type)) {
            config.set(base + ".from", color);
            config.set(base + ".to", to);
        }
        try {
            config.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to save nickcolors.yml: " + ex.getMessage());
        }
    }

    private static TextColor parseColor(String input) {
        if (input == null) {
            return null;
        }
        String raw = input.trim();
        if (raw.isEmpty()) {
            return null;
        }
        String lower = raw.toLowerCase(Locale.ROOT);
        if (lower.startsWith("&#")) {
            lower = lower.substring(1);
        }
        if (lower.startsWith("#")) {
            String hex = lower.substring(1);
            if (hex.length() == 6) {
                try {
                    int value = Integer.parseInt(hex, 16);
                    return TextColor.color(value);
                } catch (NumberFormatException ex) {
                    return null;
                }
            }
        }
        if (lower.startsWith("0x") && lower.length() == 8) {
            try {
                int value = Integer.parseInt(lower.substring(2), 16);
                return TextColor.color(value);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        if (lower.startsWith("&") && lower.length() == 2) {
            ChatColor chatColor = ChatColor.getByChar(lower.charAt(1));
            if (chatColor != null && chatColor.isColor()) {
                return NamedTextColor.NAMES.value(chatColor.name().toLowerCase(Locale.ROOT));
            }
        }
        return NamedTextColor.NAMES.value(lower);
    }

    private static String toHex(TextColor color) {
        int value = color.value();
        return String.format("#%06X", value & 0xFFFFFF);
    }

    private interface NameStyle {
        Component apply(String name);
    }

    private static final class SolidStyle implements NameStyle {
        private final TextColor color;

        private SolidStyle(TextColor color) {
            this.color = color;
        }

        @Override
        public Component apply(String name) {
            return Component.text(name).color(color);
        }
    }

    private static final class GradientStyle implements NameStyle {
        private final TextColor from;
        private final TextColor to;

        private GradientStyle(TextColor from, TextColor to) {
            this.from = from;
            this.to = to;
        }

        @Override
        public Component apply(String name) {
            if (name == null || name.isEmpty()) {
                return Component.empty();
            }
            int len = name.length();
            Component result = Component.empty();
            for (int i = 0; i < len; i++) {
                float t = len == 1 ? 0f : (float) i / (len - 1);
                TextColor color = TextColor.color(
                    lerp(from.red(), to.red(), t),
                    lerp(from.green(), to.green(), t),
                    lerp(from.blue(), to.blue(), t)
                );
                result = result.append(Component.text(String.valueOf(name.charAt(i))).color(color));
            }
            return result;
        }

        private int lerp(int a, int b, float t) {
            return Math.round(a + (b - a) * t);
        }
    }
}
