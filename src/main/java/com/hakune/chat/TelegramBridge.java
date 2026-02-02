package com.hakune.chat;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public final class TelegramBridge {
    private static final Gson GSON = new Gson();

    private final HakuneChatPlugin plugin;
    private final TelegramSettings settings;
    private final HttpClient client;
    private final AtomicLong lastUpdateId = new AtomicLong(0L);
    private BukkitTask pollTask;

    public TelegramBridge(HakuneChatPlugin plugin, TelegramSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
        this.client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    public boolean isEnabled() {
        return settings.isEnabled()
            && settings.getToken() != null
            && !settings.getToken().isBlank()
            && settings.getChatId() != null
            && !settings.getChatId().isBlank();
    }

    public void start() {
        if (!isEnabled()) {
            return;
        }
        int interval = Math.max(2, settings.getPollIntervalSeconds());
        pollTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::pollUpdates, 20L, interval * 20L);
    }

    public void stop() {
        if (pollTask != null) {
            pollTask.cancel();
            pollTask = null;
        }
    }

    public void sendFromMinecraft(Player player, String message, boolean global) {
        if (!isEnabled()) {
            return;
        }
        String type = global ? "G" : "L";
        String template = settings.getFormatToTelegram();
        String formatted = plugin.getPlaceholderHook().apply(player, template);
        formatted = formatted
            .replace("{type}", type)
            .replace("{player}", player.getName())
            .replace("{world}", player.getWorld().getName())
            .replace("{message}", message);

        sendMessage(formatted);
    }

    private void pollUpdates() {
        if (!isEnabled()) {
            return;
        }
        long offset = lastUpdateId.get() + 1;
        String url = "https://api.telegram.org/bot" + settings.getToken()
            + "/getUpdates?timeout=20&offset=" + offset;

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .GET()
            .build();

        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenAccept(this::handleUpdates)
            .exceptionally(ignored -> null);
    }

    private void handleUpdates(HttpResponse<String> response) {
        if (response.statusCode() != 200) {
            return;
        }
        TelegramResponse payload;
        try {
            payload = GSON.fromJson(response.body(), TelegramResponse.class);
        } catch (Exception ignored) {
            return;
        }
        if (payload == null || payload.result == null || payload.result.isEmpty()) {
            return;
        }

        for (Update update : payload.result) {
            if (update == null) {
                continue;
            }
            lastUpdateId.updateAndGet(current -> Math.max(current, update.updateId));
            if (update.message == null || update.message.text == null) {
                continue;
            }
            if (!settings.getChatId().equals(String.valueOf(update.message.chat.id))) {
                continue;
            }
            if (update.message.from != null && update.message.from.isBot) {
                continue;
            }

            String user = resolveUser(update.message.from);
            String text = update.message.text.trim();
            if (text.isEmpty()) {
                continue;
            }

            String formatted = settings.getFormatFromTelegram()
                .replace("{user}", user)
                .replace("{message}", text);

            plugin.broadcastTelegram(formatted);
        }
    }

    private void sendMessage(String text) {
        String url = "https://api.telegram.org/bot" + settings.getToken() + "/sendMessage";
        String body = "chat_id=" + encode(settings.getChatId())
            + "&text=" + encode(text);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(15))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        client.sendAsync(request, HttpResponse.BodyHandlers.discarding())
            .exceptionally(ignored -> null);
    }

    private String encode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8);
    }

    private String resolveUser(User from) {
        if (from == null) {
            return "Telegram";
        }
        if (from.username != null && !from.username.isBlank()) {
            return "@" + from.username;
        }
        String name = (from.firstName == null ? "" : from.firstName);
        String last = (from.lastName == null ? "" : " " + from.lastName);
        String full = (name + last).trim();
        return full.isBlank() ? "Telegram" : full;
    }

    private static final class TelegramResponse {
        boolean ok;
        List<Update> result;
    }

    private static final class Update {
        @SerializedName("update_id")
        long updateId;
        Message message;
    }

    private static final class Message {
        @SerializedName("message_id")
        long messageId;
        User from;
        Chat chat;
        String text;
    }

    private static final class Chat {
        long id;
    }

    private static final class User {
        long id;
        @SerializedName("is_bot")
        boolean isBot;
        String username;
        @SerializedName("first_name")
        String firstName;
        @SerializedName("last_name")
        String lastName;
    }
}
