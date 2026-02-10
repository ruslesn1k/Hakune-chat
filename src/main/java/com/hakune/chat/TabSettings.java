package com.hakune.chat;

import java.util.List;

public final class TabSettings {
    private final boolean enabled;
    private final int updateIntervalSeconds;
    private final List<String> header;
    private final List<String> footer;
    private final String playerFormat;
    private final String groupPlaceholder;
    private final List<String> sortingTypes;
    private final boolean nameTagEnabled;
    private final String nameTagFormat;

    public TabSettings(
        boolean enabled,
        int updateIntervalSeconds,
        List<String> header,
        List<String> footer,
        String playerFormat,
        String groupPlaceholder,
        List<String> sortingTypes,
        boolean nameTagEnabled,
        String nameTagFormat
    ) {
        this.enabled = enabled;
        this.updateIntervalSeconds = updateIntervalSeconds;
        this.header = header;
        this.footer = footer;
        this.playerFormat = playerFormat;
        this.groupPlaceholder = groupPlaceholder;
        this.sortingTypes = sortingTypes;
        this.nameTagEnabled = nameTagEnabled;
        this.nameTagFormat = nameTagFormat;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getUpdateIntervalSeconds() {
        return updateIntervalSeconds;
    }

    public List<String> getHeader() {
        return header;
    }

    public List<String> getFooter() {
        return footer;
    }

    public String getPlayerFormat() {
        return playerFormat;
    }

    public String getGroupPlaceholder() {
        return groupPlaceholder;
    }

    public List<String> getSortingTypes() {
        return sortingTypes;
    }

    public boolean isNameTagEnabled() {
        return nameTagEnabled;
    }

    public String getNameTagFormat() {
        return nameTagFormat;
    }
}
