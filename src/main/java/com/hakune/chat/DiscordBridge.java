package com.hakune.chat;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public final class DiscordBridge {
    private static final Gson GSON = new Gson();

    private final HakuneChatPlugin plugin;
    private final IntegrationSettings.DiscordSettings settings;
    private final HttpClient client;
    private BukkitTask pollTask;
    private final AtomicReference<String> lastMessageId = new AtomicReference<>(null);

    public DiscordBridge(HakuneChatPlugin plugin, IntegrationSettings.DiscordSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public boolean isEnabled() {
        return settings.isEnabled();
    }

    public void start() {
        if (!isEnabled()) {
            return;
        }
        if (settings.getBotToken() != null && !settings.getBotToken().isBlank()
            && settings.getChannelId() != null && !settings.getChannelId().isBlank()) {
            int interval = Math.max(2, settings.getPollIntervalSeconds());
            pollTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::pollMessages, 40L, interval * 20L);
        }
    }

    public void stop() {
        if (pollTask != null) {
            pollTask.cancel();
            pollTask = null;
        }
    }

    public void sendFromMinecraft(Player player, String message, boolean global) {
        if (!isEnabled() || settings.getWebhookUrl() == null || settings.getWebhookUrl().isBlank()) {
            return;
        }
        String type = global ? "G" : "L";
        String template = settings.getFormatToDiscord();
        String formatted = plugin.getPlaceholderHook().apply(player, template);
        formatted = formatted
            .replace("{type}", type)
            .replace("{player}", player.getName())
            .replace("{world}", player.getWorld().getName())
            .replace("{message}", message);

        sendWebhook(formatted);
    }

    private void sendWebhook(String text) {
        String body = GSON.toJson(new WebhookPayload(text));
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(settings.getWebhookUrl()))
            .timeout(Duration.ofSeconds(15))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build();

        client.sendAsync(request, HttpResponse.BodyHandlers.discarding())
            .exceptionally(ignored -> null);
    }

    private void pollMessages() {
        if (!isEnabled()) {
            return;
        }
        String channelId = settings.getChannelId();
        String token = settings.getBotToken();
        if (channelId == null || channelId.isBlank() || token == null || token.isBlank()) {
            return;
        }
        String url = "https://discord.com/api/v10/channels/" + channelId + "/messages?limit=5";
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(15))
            .header("Authorization", "Bot " + token)
            .GET()
            .build();

        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenAccept(this::handleMessages)
            .exceptionally(ignored -> null);
    }

    private void handleMessages(HttpResponse<String> response) {
        if (response.statusCode() != 200) {
            return;
        }
        DiscordMessage[] messages;
        try {
            messages = GSON.fromJson(response.body(), DiscordMessage[].class);
        } catch (Exception ignored) {
            return;
        }
        if (messages == null || messages.length == 0) {
            return;
        }
        for (int i = messages.length - 1; i >= 0; i--) {
            DiscordMessage msg = messages[i];
            if (msg == null || msg.author == null || msg.content == null) {
                continue;
            }
            if (Boolean.TRUE.equals(msg.author.bot)) {
                continue;
            }
            String last = lastMessageId.get();
            if (last != null && msg.id.compareTo(last) <= 0) {
                continue;
            }
            lastMessageId.set(msg.id);
            String formatted = settings.getFormatFromDiscord()
                .replace("{user}", msg.author.username)
                .replace("{message}", msg.content);
            plugin.broadcastExternal(formatted);
        }
    }

    private static final class WebhookPayload {
        final String content;
        private WebhookPayload(String content) {
            this.content = content;
        }
    }

    private static final class DiscordMessage {
        String id;
        String content;
        Author author;
    }

    private static final class Author {
        String username;
        @SerializedName("bot")
        Boolean bot;
    }
}
