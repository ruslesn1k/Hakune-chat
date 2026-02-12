package com.hakune.chat;

import java.util.List;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;

public final class MotdListener implements Listener {
    private static final LegacyComponentSerializer AMP_SERIALIZER = LegacyComponentSerializer.builder()
        .character('&')
        .hexColors()
        .build();
    private static final LegacyComponentSerializer SECTION_SERIALIZER = LegacyComponentSerializer.legacySection();
    private static final Pattern HEX_PATTERN = Pattern.compile("(?i)(?<!&)#([0-9a-f]{6})");

    private final HakuneChatPlugin plugin;

    public MotdListener(HakuneChatPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPing(ServerListPingEvent event) {
        ChatSettings settings = plugin.getSettings();
        if (!settings.isMotdEnabled()) {
            return;
        }
        List<String> lines = settings.getMotdLines();
        if (lines == null || lines.isEmpty()) {
            return;
        }
        String joined = String.join("\n", lines);
        joined = normalizeHex(joined)
            .replace("{online}", String.valueOf(event.getNumPlayers()))
            .replace("{max}", String.valueOf(event.getMaxPlayers()));
        joined = plugin.getPlaceholderHook().apply(null, joined);
        Component component = AMP_SERIALIZER.deserialize(joined);
        event.setMotd(SECTION_SERIALIZER.serialize(component));
    }

    private static String normalizeHex(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return HEX_PATTERN.matcher(text).replaceAll("&#$1");
    }
}
