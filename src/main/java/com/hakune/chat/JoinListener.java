package com.hakune.chat;

import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class JoinListener implements Listener {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
        .character('&')
        .hexColors()
        .build();
    private static final Pattern HEX_PATTERN = Pattern.compile("(?i)(?<!&)#([0-9a-f]{6})");

    private final HakuneChatPlugin plugin;

    public JoinListener(HakuneChatPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        ChatSettings settings = plugin.getSettings();
        if (!settings.isJoinMessageEnabled()) {
            event.joinMessage(null);
            return;
        }

        Player player = event.getPlayer();
        Component headComponent = Component.empty();
        if (settings.isSkinRestorerHeads() && plugin.getSkinRestorerHeadHook() != null) {
            headComponent = plugin.getSkinRestorerHeadHook().getHeadComponent(player);
        }

        event.joinMessage(null);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            Component component = buildComponentForViewer(player, viewer, true, headComponent);
            viewer.sendMessage(component);
        }
        Component consoleComponent = buildComponentForViewer(player, null, true, headComponent);
        Bukkit.getConsoleSender().sendMessage(consoleComponent);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        ChatSettings settings = plugin.getSettings();
        if (!settings.isQuitMessageEnabled()) {
            event.quitMessage(null);
            return;
        }

        Player player = event.getPlayer();
        Component headComponent = Component.empty();
        if (settings.isSkinRestorerHeads() && plugin.getSkinRestorerHeadHook() != null) {
            headComponent = plugin.getSkinRestorerHeadHook().getHeadComponent(player);
        }

        event.quitMessage(null);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }
            Component component = buildComponentForViewer(player, viewer, false, headComponent);
            viewer.sendMessage(component);
        }
        Component consoleComponent = buildComponentForViewer(player, null, false, headComponent);
        Bukkit.getConsoleSender().sendMessage(consoleComponent);
    }

    private static String normalizeHex(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return HEX_PATTERN.matcher(text).replaceAll("&#$1");
    }

    private static Component buildComponentWithHead(String formatted, Component headComponent) {
        if (formatted == null) {
            return Component.empty();
        }
        if (!formatted.contains("{head}")) {
            return LEGACY.deserialize(formatted);
        }
        String[] parts = formatted.split("\\{head\\}", -1);
        Component result = Component.empty();
        for (int i = 0; i < parts.length; i++) {
            if (!parts[i].isEmpty()) {
                result = result.append(LEGACY.deserialize(parts[i]));
            }
            if (i < parts.length - 1 && headComponent != null) {
                result = result.append(headComponent);
            }
        }
        return result;
    }

    private Component buildComponentForViewer(
        Player subject,
        Player viewer,
        boolean join,
        Component headComponent
    ) {
        ChatSettings settings = plugin.getSettings();
        boolean viewerBedrock = viewer != null && plugin.getBedrockDetector().isBedrock(viewer.getUniqueId());
        String template = (settings.isSeparateBedrockFormat() && viewerBedrock)
            ? (join ? settings.getJoinFormatBedrock() : settings.getQuitFormatBedrock())
            : (join ? settings.getJoinFormatJava() : settings.getQuitFormatJava());

        String formatted = plugin.getPlaceholderHook().apply(subject, template);
        formatted = normalizeHex(formatted)
            .replace("{voice}", plugin.getVoiceDetector().getVoiceIndicator(subject))
            .replace("{world}", subject.getWorld().getName());

        Component component = buildComponentWithHead(formatted, headComponent);
        return component.replaceText(builder -> builder.matchLiteral("{player}")
            .replacement(plugin.getStyledNameComponent(subject)));
    }
}
