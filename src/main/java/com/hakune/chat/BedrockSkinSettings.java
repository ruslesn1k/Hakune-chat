package com.hakune.chat;

import java.util.List;

public final class BedrockSkinSettings {
    private final boolean enabled;
    private final String updateMode;
    private final int updateIntervalSeconds;
    private final List<String> commandTriggers;
    private final boolean applyToPlayers;
    private final boolean useForHeads;

    public BedrockSkinSettings(
        boolean enabled,
        String updateMode,
        int updateIntervalSeconds,
        List<String> commandTriggers,
        boolean applyToPlayers,
        boolean useForHeads
    ) {
        this.enabled = enabled;
        this.updateMode = updateMode;
        this.updateIntervalSeconds = updateIntervalSeconds;
        this.commandTriggers = commandTriggers;
        this.applyToPlayers = applyToPlayers;
        this.useForHeads = useForHeads;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getUpdateMode() {
        return updateMode;
    }

    public int getUpdateIntervalSeconds() {
        return updateIntervalSeconds;
    }

    public List<String> getCommandTriggers() {
        return commandTriggers;
    }

    public boolean isApplyToPlayers() {
        return applyToPlayers;
    }

    public boolean isUseForHeads() {
        return useForHeads;
    }
}
