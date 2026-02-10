package com.hakune.chat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

public final class LiveNotifier {
    private final HakuneChatPlugin plugin;
    private final IntegrationSettings.NotificationSettings settings;
    private final HttpClient client;
    private BukkitTask task;
    private final Map<String, Boolean> liveStates = new HashMap<>();

    public LiveNotifier(HakuneChatPlugin plugin, IntegrationSettings.NotificationSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public void start() {
        stop();
        if (settings == null || !settings.isEnabled()) {
            return;
        }
        int interval = Math.max(15, settings.getPollIntervalSeconds());
        task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::pollAll, 40L, interval * 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void pollAll() {
        checkPlatform("Twitch", settings.getTwitch());
        checkPlatform("YouTube", settings.getYoutube());
        checkPlatform("TikTok", settings.getTiktok());
        checkPlatform("VkLive", settings.getVklive());
    }

    private void checkPlatform(String platform, IntegrationSettings.PlatformSettings platformSettings) {
        if (platformSettings == null || !platformSettings.isEnabled()) {
            return;
        }
        List<IntegrationSettings.ChannelTarget> channels = platformSettings.getChannels();
        if (channels == null || channels.isEmpty()) {
            return;
        }
        for (IntegrationSettings.ChannelTarget channel : channels) {
            String url = channel.getUrl();
            if (url == null || url.isBlank()) {
                continue;
            }
            String liveRegex = channel.getLiveRegex() != null ? channel.getLiveRegex() : platformSettings.getLiveRegex();
            String titleRegex = channel.getTitleRegex() != null ? channel.getTitleRegex() : platformSettings.getTitleRegex();
            fetchPage(platform, channel, liveRegex, titleRegex);
        }
    }

    private void fetchPage(String platform, IntegrationSettings.ChannelTarget channel, String liveRegex, String titleRegex) {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(channel.getUrl()))
            .timeout(Duration.ofSeconds(20))
            .GET()
            .build();

        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenAccept(response -> handlePage(platform, channel, liveRegex, titleRegex, response))
            .exceptionally(ignored -> null);
    }

    private void handlePage(String platform, IntegrationSettings.ChannelTarget channel, String liveRegex, String titleRegex, HttpResponse<String> response) {
        if (response.statusCode() != 200) {
            return;
        }
        String body = response.body();
        boolean live = matches(body, liveRegex);
        String key = platform + ":" + channel.getName();
        boolean wasLive = liveStates.getOrDefault(key, false);
        liveStates.put(key, live);
        if (!wasLive && live) {
            String title = extract(body, titleRegex);
            sendLiveMessage(platform, channel.getName(), channel.getUrl(), title);
        }
    }

    private void sendLiveMessage(String platform, String name, String url, String title) {
        String msg = settings.getMessageFormat()
            .replace("{platform}", platform)
            .replace("{name}", name)
            .replace("{url}", url)
            .replace("{title}", title == null ? name : title);
        plugin.broadcastExternal(msg);
    }

    private boolean matches(String body, String regex) {
        if (regex == null || regex.isBlank()) {
            return false;
        }
        try {
            return Pattern.compile(regex).matcher(body).find();
        } catch (Exception ignored) {
            return false;
        }
    }

    private String extract(String body, String regex) {
        if (regex == null || regex.isBlank()) {
            return null;
        }
        try {
            Matcher matcher = Pattern.compile(regex).matcher(body);
            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
