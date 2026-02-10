package com.hakune.chat;

import java.lang.reflect.Method;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;

public final class VoiceDetector {
    private final HakuneChatPlugin plugin;
    private final boolean available;
    private final Method providerGetMethod;
    private final Method getServerMethod;
    private final Method getPlayerManagerMethod;
    private final Method getPlayerMethod;
    private final Method hasVoiceMethod;

    public VoiceDetector(HakuneChatPlugin plugin, PluginManager pluginManager) {
        this.plugin = plugin;
        Method providerGet = null;
        Method getServer = null;
        Method getPlayerManager = null;
        Method getPlayer = null;
        Method hasVoice = null;
        boolean ok = false;

        if (pluginManager.isPluginEnabled("PlasmoVoice")) {
            try {
                Class<?> providerClass = Class.forName("su.plo.voice.api.server.PlasmoVoiceServerProvider");
                Class<?> serverClass = Class.forName("su.plo.voice.api.server.PlasmoVoiceServer");
                Class<?> playerManagerClass = Class.forName("su.plo.voice.api.server.player.ServerPlayerManager");
                Class<?> serverPlayerClass = Class.forName("su.plo.voice.api.server.player.ServerPlayer");

                providerGet = providerClass.getMethod("get");
                getServer = providerClass.getMethod("get");
                getPlayerManager = serverClass.getMethod("getPlayerManager");
                getPlayer = playerManagerClass.getMethod("getPlayer", UUID.class);

                hasVoice = findMethod(serverPlayerClass, "hasVoiceChat", "isVoiceEnabled", "isConnected", "isVoiceChatEnabled");
                ok = true;
            } catch (ClassNotFoundException | NoSuchMethodException ignored) {
                ok = false;
            }
        }

        this.available = ok;
        this.providerGetMethod = providerGet;
        this.getServerMethod = getServer;
        this.getPlayerManagerMethod = getPlayerManager;
        this.getPlayerMethod = getPlayer;
        this.hasVoiceMethod = hasVoice;
    }

    public String getVoiceIndicator(Player player) {
        ChatSettings settings = plugin.getSettings();
        if (settings == null || !settings.isVoiceIndicatorEnabled()) {
            return "";
        }

        String placeholder = plugin.getPlaceholderHook().apply(player, "%plasmovoice_hasVoiceChat%");
        if (placeholder != null) {
            String value = placeholder.trim().toLowerCase();
            if (value.equals("true") || value.equals("yes")) {
                return settings.getVoiceIndicatorOn();
            }
            if (value.equals("false") || value.equals("no")) {
                return settings.getVoiceIndicatorOff();
            }
        }

        boolean hasVoice = hasVoice(player.getUniqueId());
        return hasVoice ? settings.getVoiceIndicatorOn() : settings.getVoiceIndicatorOff();
    }

    public boolean hasVoice(UUID uuid) {
        if (!available || hasVoiceMethod == null) {
            return false;
        }
        try {
            Object server = providerGetMethod.invoke(null);
            if (server == null) {
                return false;
            }
            Object manager = getPlayerManagerMethod.invoke(server);
            if (manager == null) {
                return false;
            }
            Object serverPlayer = getPlayerMethod.invoke(manager, uuid);
            if (serverPlayer == null) {
                return false;
            }
            Object result = hasVoiceMethod.invoke(serverPlayer);
            return result instanceof Boolean && (Boolean) result;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static Method findMethod(Class<?> type, String... names) {
        for (String name : names) {
            try {
                return type.getMethod(name);
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }
}
