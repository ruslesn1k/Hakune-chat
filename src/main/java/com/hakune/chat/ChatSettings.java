package com.hakune.chat;

public final class ChatSettings {
    private final double localDistance;
    private final String globalSymbol;
    private final ChatFormat javaFormat;
    private final ChatFormat bedrockFormat;
    private final TelegramSettings telegramSettings;
    private final boolean separateBedrockFormat;
    private final boolean skinRestorerHeads;
    private final int skinRestorerRefreshSeconds;
    private final String skinRestorerUpdateMode;
    private final java.util.List<String> skinRestorerCommandTriggers;
    private final boolean joinMessageEnabled;
    private final String joinFormatJava;
    private final String joinFormatBedrock;
    private final boolean quitMessageEnabled;
    private final String quitFormatJava;
    private final String quitFormatBedrock;
    private final BedrockSkinSettings bedrockSkinSettings;
    private final String listenLocalFormatJava;
    private final String listenLocalFormatBedrock;
    private final String pmToJava;
    private final String pmFromJava;
    private final String pmToBedrock;
    private final String pmFromBedrock;
    private final boolean tttEnabled;
    private final boolean voiceIndicatorEnabled;
    private final String voiceIndicatorOn;
    private final String voiceIndicatorOff;
    private final boolean motdEnabled;
    private final java.util.List<String> motdLines;

    public ChatSettings(
        double localDistance,
        String globalSymbol,
        ChatFormat javaFormat,
        ChatFormat bedrockFormat,
        TelegramSettings telegramSettings,
        boolean separateBedrockFormat,
        boolean skinRestorerHeads,
        int skinRestorerRefreshSeconds,
        String skinRestorerUpdateMode,
        java.util.List<String> skinRestorerCommandTriggers,
        boolean joinMessageEnabled,
        String joinFormatJava,
        String joinFormatBedrock,
        boolean quitMessageEnabled,
        String quitFormatJava,
        String quitFormatBedrock,
        BedrockSkinSettings bedrockSkinSettings,
        String listenLocalFormatJava,
        String listenLocalFormatBedrock,
        String pmToJava,
        String pmFromJava,
        String pmToBedrock,
        String pmFromBedrock,
        boolean tttEnabled,
        boolean voiceIndicatorEnabled,
        String voiceIndicatorOn,
        String voiceIndicatorOff,
        boolean motdEnabled,
        java.util.List<String> motdLines
    ) {
        this.localDistance = localDistance;
        this.globalSymbol = globalSymbol;
        this.javaFormat = javaFormat;
        this.bedrockFormat = bedrockFormat;
        this.telegramSettings = telegramSettings;
        this.separateBedrockFormat = separateBedrockFormat;
        this.skinRestorerHeads = skinRestorerHeads;
        this.skinRestorerRefreshSeconds = skinRestorerRefreshSeconds;
        this.skinRestorerUpdateMode = skinRestorerUpdateMode;
        this.skinRestorerCommandTriggers = skinRestorerCommandTriggers;
        this.joinMessageEnabled = joinMessageEnabled;
        this.joinFormatJava = joinFormatJava;
        this.joinFormatBedrock = joinFormatBedrock;
        this.quitMessageEnabled = quitMessageEnabled;
        this.quitFormatJava = quitFormatJava;
        this.quitFormatBedrock = quitFormatBedrock;
        this.bedrockSkinSettings = bedrockSkinSettings;
        this.listenLocalFormatJava = listenLocalFormatJava;
        this.listenLocalFormatBedrock = listenLocalFormatBedrock;
        this.pmToJava = pmToJava;
        this.pmFromJava = pmFromJava;
        this.pmToBedrock = pmToBedrock;
        this.pmFromBedrock = pmFromBedrock;
        this.tttEnabled = tttEnabled;
        this.voiceIndicatorEnabled = voiceIndicatorEnabled;
        this.voiceIndicatorOn = voiceIndicatorOn;
        this.voiceIndicatorOff = voiceIndicatorOff;
        this.motdEnabled = motdEnabled;
        this.motdLines = motdLines;
    }

    public double getLocalDistance() {
        return localDistance;
    }

    public String getGlobalSymbol() {
        return globalSymbol;
    }

    public ChatFormat getJavaFormat() {
        return javaFormat;
    }

    public ChatFormat getBedrockFormat() {
        return bedrockFormat;
    }

    public TelegramSettings getTelegramSettings() {
        return telegramSettings;
    }

    public boolean isSeparateBedrockFormat() {
        return separateBedrockFormat;
    }

    public boolean isSkinRestorerHeads() {
        return skinRestorerHeads;
    }

    public int getSkinRestorerRefreshSeconds() {
        return skinRestorerRefreshSeconds;
    }

    public String getSkinRestorerUpdateMode() {
        return skinRestorerUpdateMode;
    }

    public java.util.List<String> getSkinRestorerCommandTriggers() {
        return skinRestorerCommandTriggers;
    }

    public boolean isJoinMessageEnabled() {
        return joinMessageEnabled;
    }

    public String getJoinFormatJava() {
        return joinFormatJava;
    }

    public String getJoinFormatBedrock() {
        return joinFormatBedrock;
    }

    public boolean isQuitMessageEnabled() {
        return quitMessageEnabled;
    }

    public String getQuitFormatJava() {
        return quitFormatJava;
    }

    public String getQuitFormatBedrock() {
        return quitFormatBedrock;
    }

    public BedrockSkinSettings getBedrockSkinSettings() {
        return bedrockSkinSettings;
    }

    public String getListenLocalFormatJava() {
        return listenLocalFormatJava;
    }

    public String getListenLocalFormatBedrock() {
        return listenLocalFormatBedrock;
    }

    public String getPmToJava() {
        return pmToJava;
    }

    public String getPmFromJava() {
        return pmFromJava;
    }

    public String getPmToBedrock() {
        return pmToBedrock;
    }

    public String getPmFromBedrock() {
        return pmFromBedrock;
    }

    public boolean isTttEnabled() {
        return tttEnabled;
    }

    public boolean isVoiceIndicatorEnabled() {
        return voiceIndicatorEnabled;
    }

    public String getVoiceIndicatorOn() {
        return voiceIndicatorOn;
    }

    public String getVoiceIndicatorOff() {
        return voiceIndicatorOff;
    }

    public boolean isMotdEnabled() {
        return motdEnabled;
    }

    public java.util.List<String> getMotdLines() {
        return motdLines;
    }
}
