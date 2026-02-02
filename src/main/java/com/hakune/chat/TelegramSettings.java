package com.hakune.chat;

public final class TelegramSettings {
    private final boolean enabled;
    private final String token;
    private final String chatId;
    private final int pollIntervalSeconds;
    private final String formatToTelegram;
    private final String formatFromTelegram;

    public TelegramSettings(
        boolean enabled,
        String token,
        String chatId,
        int pollIntervalSeconds,
        String formatToTelegram,
        String formatFromTelegram
    ) {
        this.enabled = enabled;
        this.token = token;
        this.chatId = chatId;
        this.pollIntervalSeconds = pollIntervalSeconds;
        this.formatToTelegram = formatToTelegram;
        this.formatFromTelegram = formatFromTelegram;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getToken() {
        return token;
    }

    public String getChatId() {
        return chatId;
    }

    public int getPollIntervalSeconds() {
        return pollIntervalSeconds;
    }

    public String getFormatToTelegram() {
        return formatToTelegram;
    }

    public String getFormatFromTelegram() {
        return formatFromTelegram;
    }
}
