package com.hakune.chat;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;

public final class VoiceDetector {
    private enum Provider {
        NONE,
        PLASMO_VOICE,
        SIMPLE_VOICE_CHAT
    }

    private final HakuneChatPlugin plugin;
    private final Provider provider;

    private final Method plasmoProviderGetMethod;
    private final Method plasmoGetPlayerManagerMethod;
    private final Method plasmoGetPlayerMethod;
    private final Method plasmoHasVoiceMethod;

    private final Class<?> svcServerApiClass;
    private final Method svcGetConnectionOfMethod;
    private final Method svcInstalledMethod;
    private final Method svcApiInstanceMethod;

    public VoiceDetector(HakuneChatPlugin plugin, PluginManager pluginManager) {
        this.plugin = plugin;

        String configuredMode = "auto";
        ChatSettings settings = plugin.getSettings();
        if (settings != null && settings.getVoiceDetectionMode() != null) {
            configuredMode = settings.getVoiceDetectionMode().trim().toLowerCase(Locale.ROOT);
        }

        Provider selected = selectProvider(configuredMode, pluginManager);

        Method pProviderGet = null;
        Method pGetPlayerManager = null;
        Method pGetPlayer = null;
        Method pHasVoice = null;
        Class<?> sServerApiClass = null;
        Method sGetConnectionOf = null;
        Method sInstalled = null;
        Method sApiInstance = null;

        if (selected == Provider.PLASMO_VOICE) {
            try {
                Class<?> providerClass = Class.forName("su.plo.voice.api.server.PlasmoVoiceServerProvider");
                Class<?> serverClass = Class.forName("su.plo.voice.api.server.PlasmoVoiceServer");
                Class<?> playerManagerClass = Class.forName("su.plo.voice.api.server.player.ServerPlayerManager");
                Class<?> serverPlayerClass = Class.forName("su.plo.voice.api.server.player.ServerPlayer");

                pProviderGet = providerClass.getMethod("get");
                pGetPlayerManager = serverClass.getMethod("getPlayerManager");
                pGetPlayer = playerManagerClass.getMethod("getPlayer", UUID.class);
                pHasVoice = findMethod(serverPlayerClass, "hasVoiceChat", "isVoiceEnabled", "isConnected", "isVoiceChatEnabled");
                if (pHasVoice == null) {
                    selected = Provider.NONE;
                }
            } catch (ClassNotFoundException | NoSuchMethodException ignored) {
                selected = Provider.NONE;
            }
        }

        if (selected == Provider.SIMPLE_VOICE_CHAT) {
            try {
                sServerApiClass = Class.forName("de.maxhenkel.voicechat.api.VoicechatServerApi");
                Class<?> connectionClass = Class.forName("de.maxhenkel.voicechat.api.VoicechatConnection");
                sGetConnectionOf = sServerApiClass.getMethod("getConnectionOf", UUID.class);
                sInstalled = findMethod(connectionClass, "isInstalled", "isConnected", "hasVoicechat");

                Class<?> implClass = Class.forName("de.maxhenkel.voicechat.plugins.impl.VoicechatServerApiImpl");
                sApiInstance = findMethod(implClass, "instance");

                if (sInstalled == null || sGetConnectionOf == null) {
                    selected = Provider.NONE;
                }
            } catch (ClassNotFoundException | NoSuchMethodException ignored) {
                selected = Provider.NONE;
            }
        }

        this.provider = selected;
        this.plasmoProviderGetMethod = pProviderGet;
        this.plasmoGetPlayerManagerMethod = pGetPlayerManager;
        this.plasmoGetPlayerMethod = pGetPlayer;
        this.plasmoHasVoiceMethod = pHasVoice;

        this.svcServerApiClass = sServerApiClass;
        this.svcGetConnectionOfMethod = sGetConnectionOf;
        this.svcInstalledMethod = sInstalled;
        this.svcApiInstanceMethod = sApiInstance;
    }

    public String getVoiceIndicator(Player player) {
        ChatSettings settings = plugin.getSettings();
        if (settings == null || !settings.isVoiceIndicatorEnabled()) {
            return "";
        }

        Boolean placeholderValue = readPlaceholderState(player);
        if (placeholderValue != null) {
            return placeholderValue ? settings.getVoiceIndicatorOn() : settings.getVoiceIndicatorOff();
        }

        boolean hasVoice = hasVoice(player.getUniqueId());
        return hasVoice ? settings.getVoiceIndicatorOn() : settings.getVoiceIndicatorOff();
    }

    public boolean hasVoice(UUID uuid) {
        if (provider == Provider.PLASMO_VOICE) {
            return hasPlasmoVoice(uuid);
        }
        if (provider == Provider.SIMPLE_VOICE_CHAT) {
            return hasSimpleVoiceChat(uuid);
        }
        return false;
    }

    private Provider selectProvider(String configuredMode, PluginManager pluginManager) {
        boolean hasPlasmo = pluginManager.isPluginEnabled("PlasmoVoice");
        boolean hasSimple = pluginManager.isPluginEnabled("SimpleVoiceChat")
            || pluginManager.isPluginEnabled("voicechat");

        if ("plasmovoice".equals(configuredMode)) {
            return hasPlasmo ? Provider.PLASMO_VOICE : Provider.NONE;
        }
        if ("simplevoicechat".equals(configuredMode)) {
            return hasSimple ? Provider.SIMPLE_VOICE_CHAT : Provider.NONE;
        }
        if (hasPlasmo) {
            return Provider.PLASMO_VOICE;
        }
        if (hasSimple) {
            return Provider.SIMPLE_VOICE_CHAT;
        }
        return Provider.NONE;
    }

    private Boolean readPlaceholderState(Player player) {
        if (provider == Provider.PLASMO_VOICE) {
            return parseBoolean(plugin.getPlaceholderHook().apply(player, "%plasmovoice_hasVoiceChat%"));
        }
        if (provider == Provider.SIMPLE_VOICE_CHAT) {
            Boolean value = parseBoolean(plugin.getPlaceholderHook().apply(player, "%simplevoicechat_is_installed%"));
            if (value != null) {
                return value;
            }
            return parseBoolean(plugin.getPlaceholderHook().apply(player, "%voicechat_is_installed%"));
        }

        Boolean plasmo = parseBoolean(plugin.getPlaceholderHook().apply(player, "%plasmovoice_hasVoiceChat%"));
        if (plasmo != null) {
            return plasmo;
        }
        Boolean simple = parseBoolean(plugin.getPlaceholderHook().apply(player, "%simplevoicechat_is_installed%"));
        if (simple != null) {
            return simple;
        }
        return parseBoolean(plugin.getPlaceholderHook().apply(player, "%voicechat_is_installed%"));
    }

    private static Boolean parseBoolean(String input) {
        if (input == null) {
            return null;
        }
        String value = input.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty() || value.contains("%")) {
            return null;
        }
        if (value.equals("true") || value.equals("yes") || value.equals("on") || value.equals("1")) {
            return true;
        }
        if (value.equals("false") || value.equals("no") || value.equals("off") || value.equals("0")) {
            return false;
        }
        return null;
    }

    private boolean hasPlasmoVoice(UUID uuid) {
        if (plasmoHasVoiceMethod == null) {
            return false;
        }
        try {
            Object server = plasmoProviderGetMethod.invoke(null);
            if (server == null) {
                return false;
            }
            Object manager = plasmoGetPlayerManagerMethod.invoke(server);
            if (manager == null) {
                return false;
            }
            Object serverPlayer = plasmoGetPlayerMethod.invoke(manager, uuid);
            if (serverPlayer == null) {
                return false;
            }
            Object result = plasmoHasVoiceMethod.invoke(serverPlayer);
            return result instanceof Boolean && (Boolean) result;
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean hasSimpleVoiceChat(UUID uuid) {
        if (svcServerApiClass == null || svcInstalledMethod == null || svcGetConnectionOfMethod == null) {
            return false;
        }
        try {
            Object serverApi = Bukkit.getServicesManager().load((Class) svcServerApiClass);
            if (serverApi == null && svcApiInstanceMethod != null) {
                serverApi = svcApiInstanceMethod.invoke(null);
            }
            if (serverApi == null) {
                return false;
            }
            Object connection = svcGetConnectionOfMethod.invoke(serverApi, uuid);
            if (connection == null) {
                return false;
            }
            Object result = svcInstalledMethod.invoke(connection);
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
