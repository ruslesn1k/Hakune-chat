package com.hakune.chat;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
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
            if (plugin.getDiscordBridge() != null) {
                plugin.getDiscordBridge().sendFromMinecraft(player, message, true);
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
        sendListenLocal(player, recipients, resolvedMessage, headComponent);
        if (plugin.getTelegramBridge() != null) {
            plugin.getTelegramBridge().sendFromMinecraft(player, message, false);
        }
        if (plugin.getDiscordBridge() != null) {
            plugin.getDiscordBridge().sendFromMinecraft(player, message, false);
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

    private static Component replacePlayerPlaceholder(Component component, Component playerComponent) {
        return component.replaceText(builder -> builder.matchLiteral("{player}").replacement(playerComponent));
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
        formatted = formatted.replace("{voice}", plugin.getVoiceDetector().getVoiceIndicator(sender));

        formatted = formatted
            .replace("{world}", sender.getWorld().getName())
            .replace("{message}", resolvedMessage);

        Component component = buildComponentWithHead(formatted, headComponent);
        Component playerComponent = buildPlayerComponent(viewer, sender);
        return replacePlayerPlaceholder(component, playerComponent);
    }

    private void sendListenLocal(Player sender, List<Player> localRecipients, String resolvedMessage, Component headComponent) {
        ChatSettings settings = plugin.getSettings();
        java.util.Set<java.util.UUID> localSet = new java.util.HashSet<>();
        for (Player player : localRecipients) {
            localSet.add(player.getUniqueId());
        }
        for (java.util.UUID uuid : plugin.getListenLocal()) {
            if (localSet.contains(uuid)) {
                continue;
            }
            Player viewer = Bukkit.getPlayer(uuid);
            if (viewer == null || !viewer.isOnline()) {
                continue;
            }
            boolean viewerBedrock = plugin.getBedrockDetector().isBedrock(viewer.getUniqueId());
            String template = (settings.isSeparateBedrockFormat() && viewerBedrock)
                ? settings.getListenLocalFormatBedrock()
                : settings.getListenLocalFormatJava();

            String formatted = plugin.getPlaceholderHook().apply(sender, template);
            formatted = normalizeHex(formatted)
                .replace("{world}", sender.getWorld().getName())
                .replace("{message}", resolvedMessage)
                .replace("{voice}", plugin.getVoiceDetector().getVoiceIndicator(sender));

            Component component = buildComponentWithHead(formatted, headComponent);
            Component playerComponent = buildPlayerComponent(viewer, sender);
            component = replacePlayerPlaceholder(component, playerComponent);
            if (!viewerBedrock) {
                component = makeLlClickable(component, sender.getName());
            }
            viewer.sendMessage(component);
        }
    }

    private Component buildPlayerComponent(Player viewer, Player subject) {
        Component base = plugin.getStyledNameComponent(subject);
        if (viewer == null) {
            return base;
        }
        boolean viewerBedrock = plugin.getBedrockDetector().isBedrock(viewer.getUniqueId());
        if (viewerBedrock) {
            return base;
        }
        String name = subject.getName();
        return base.clickEvent(ClickEvent.suggestCommand("/msg " + name + " "))
            .hoverEvent(HoverEvent.showText(Component.text("Message " + name).color(NamedTextColor.GRAY)));
    }

    private Component makeLlClickable(Component component, String playerName) {
        return component.replaceText(builder -> builder.matchLiteral("[LL]")
            .replacement(Component.text("[LL]").color(NamedTextColor.YELLOW)
                .clickEvent(ClickEvent.runCommand("/tp " + playerName))
                .hoverEvent(HoverEvent.showText(Component.text("Teleport to " + playerName).color(NamedTextColor.GRAY)))));
    }
}
