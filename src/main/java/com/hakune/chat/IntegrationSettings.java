package com.hakune.chat;

import java.util.List;

public final class IntegrationSettings {
    private final TelegramSettings telegram;
    private final DiscordSettings discord;
    private final NotificationSettings notifications;

    public IntegrationSettings(TelegramSettings telegram, DiscordSettings discord, NotificationSettings notifications) {
        this.telegram = telegram;
        this.discord = discord;
        this.notifications = notifications;
    }

    public TelegramSettings getTelegram() {
        return telegram;
    }

    public DiscordSettings getDiscord() {
        return discord;
    }

    public NotificationSettings getNotifications() {
        return notifications;
    }

    public static final class DiscordSettings {
        private final boolean enabled;
        private final String webhookUrl;
        private final String botToken;
        private final String channelId;
        private final int pollIntervalSeconds;
        private final String formatToDiscord;
        private final String formatFromDiscord;

        public DiscordSettings(
            boolean enabled,
            String webhookUrl,
            String botToken,
            String channelId,
            int pollIntervalSeconds,
            String formatToDiscord,
            String formatFromDiscord
        ) {
            this.enabled = enabled;
            this.webhookUrl = webhookUrl;
            this.botToken = botToken;
            this.channelId = channelId;
            this.pollIntervalSeconds = pollIntervalSeconds;
            this.formatToDiscord = formatToDiscord;
            this.formatFromDiscord = formatFromDiscord;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public String getWebhookUrl() {
            return webhookUrl;
        }

        public String getBotToken() {
            return botToken;
        }

        public String getChannelId() {
            return channelId;
        }

        public int getPollIntervalSeconds() {
            return pollIntervalSeconds;
        }

        public String getFormatToDiscord() {
            return formatToDiscord;
        }

        public String getFormatFromDiscord() {
            return formatFromDiscord;
        }
    }

    public static final class NotificationSettings {
        private final boolean enabled;
        private final int pollIntervalSeconds;
        private final String messageFormat;
        private final PlatformSettings twitch;
        private final PlatformSettings youtube;
        private final PlatformSettings tiktok;
        private final PlatformSettings vklive;

        public NotificationSettings(
            boolean enabled,
            int pollIntervalSeconds,
            String messageFormat,
            PlatformSettings twitch,
            PlatformSettings youtube,
            PlatformSettings tiktok,
            PlatformSettings vklive
        ) {
            this.enabled = enabled;
            this.pollIntervalSeconds = pollIntervalSeconds;
            this.messageFormat = messageFormat;
            this.twitch = twitch;
            this.youtube = youtube;
            this.tiktok = tiktok;
            this.vklive = vklive;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public int getPollIntervalSeconds() {
            return pollIntervalSeconds;
        }

        public String getMessageFormat() {
            return messageFormat;
        }

        public PlatformSettings getTwitch() {
            return twitch;
        }

        public PlatformSettings getYoutube() {
            return youtube;
        }

        public PlatformSettings getTiktok() {
            return tiktok;
        }

        public PlatformSettings getVklive() {
            return vklive;
        }
    }

    public static final class PlatformSettings {
        private final boolean enabled;
        private final String liveRegex;
        private final String titleRegex;
        private final List<ChannelTarget> channels;

        public PlatformSettings(boolean enabled, String liveRegex, String titleRegex, List<ChannelTarget> channels) {
            this.enabled = enabled;
            this.liveRegex = liveRegex;
            this.titleRegex = titleRegex;
            this.channels = channels;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public String getLiveRegex() {
            return liveRegex;
        }

        public String getTitleRegex() {
            return titleRegex;
        }

        public List<ChannelTarget> getChannels() {
            return channels;
        }
    }

    public static final class ChannelTarget {
        private final String name;
        private final String url;
        private final String liveRegex;
        private final String titleRegex;

        public ChannelTarget(String name, String url, String liveRegex, String titleRegex) {
            this.name = name;
            this.url = url;
            this.liveRegex = liveRegex;
            this.titleRegex = titleRegex;
        }

        public String getName() {
            return name;
        }

        public String getUrl() {
            return url;
        }

        public String getLiveRegex() {
            return liveRegex;
        }

        public String getTitleRegex() {
            return titleRegex;
        }
    }
}
