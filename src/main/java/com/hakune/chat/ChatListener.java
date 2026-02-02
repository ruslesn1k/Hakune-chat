package com.hakune.chat;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import java.util.regex.Pattern;

public final class ChatListener implements Listener {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
        .character('&')
        .hexColors()
        .build();
    private static final Pattern HEX_PATTERN = Pattern.compile("(?i)(?<!&)#([0-9a-f]{6})");

    private final HakuneChatPlugin plugin;

    public ChatListener(HakuneChatPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncChatEvent event) {
        if (event.isCancelled()) {
            return;
        }

        Player player = event.getPlayer();
        String rawMessage = PlainTextComponentSerializer.plainText().serialize(event.message());

        ChatSettings settings = plugin.getSettings();
        String globalSymbol = settings.getGlobalSymbol();
        boolean isGlobal = !globalSymbol.isEmpty() && rawMessage.startsWith(globalSymbol);
        String content = rawMessage;
        if (isGlobal) {
            content = rawMessage.substring(globalSymbol.length()).trim();
        }

        if (content.isEmpty()) {
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);
        String finalContent = content;
        Bukkit.getScheduler().runTask(plugin, () -> sendChat(player, finalContent, isGlobal));
    }

    private void sendChat(Player player, String message, boolean global) {
        ChatSettings settings = plugin.getSettings();
        String resolvedMessage = plugin.getPlaceholderHook().apply(player, message);
        resolvedMessage = normalizeHex(resolvedMessage);

        Component headComponent = Component.empty();
        if (settings.isSkinRestorerHeads() && plugin.getSkinRestorerHeadHook() != null) {
            headComponent = plugin.getSkinRestorerHeadHook().getHeadComponent(player);
        }

        if (global) {
            for (Player target : Bukkit.getOnlinePlayers()) {
                Component component = buildComponentForViewer(player, target, resolvedMessage, global, headComponent);
                target.sendMessage(component);
            }
            Component consoleComponent = buildComponentForViewer(player, null, resolvedMessage, global, headComponent);
            Bukkit.getConsoleSender().sendMessage(consoleComponent);
            if (plugin.getTelegramBridge() != null) {
                plugin.getTelegramBridge().sendFromMinecraft(player, message, true);
            }
            return;
        }

        double maxDistance = Math.max(0.0, settings.getLocalDistance());
        double maxDistanceSq = maxDistance * maxDistance;
        List<Player> recipients = new ArrayList<>();
        for (Player target : player.getWorld().getPlayers()) {
            if (target.getLocation().distanceSquared(player.getLocation()) <= maxDistanceSq) {
                recipients.add(target);
            }
        }

        for (Player target : recipients) {
            Component component = buildComponentForViewer(player, target, resolvedMessage, false, headComponent);
            target.sendMessage(component);
        }
        Component consoleComponent = buildComponentForViewer(player, null, resolvedMessage, false, headComponent);
        Bukkit.getConsoleSender().sendMessage(consoleComponent);
        if (plugin.getTelegramBridge() != null) {
            plugin.getTelegramBridge().sendFromMinecraft(player, message, false);
        }
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
        Player sender,
        Player viewer,
        String resolvedMessage,
        boolean global,
        Component headComponent
    ) {
        ChatSettings settings = plugin.getSettings();
        boolean viewerBedrock = viewer != null && plugin.getBedrockDetector().isBedrock(viewer.getUniqueId());
        ChatFormat format = (settings.isSeparateBedrockFormat() && viewerBedrock)
            ? settings.getBedrockFormat()
            : settings.getJavaFormat();
        String template = global ? format.getGlobal() : format.getLocal();

        String formatted = plugin.getPlaceholderHook().apply(sender, template);
        formatted = normalizeHex(formatted);

        formatted = formatted
            .replace("{player}", sender.getName())
            .replace("{world}", sender.getWorld().getName())
            .replace("{message}", resolvedMessage);

        return buildComponentWithHead(formatted, headComponent);
    }
}
