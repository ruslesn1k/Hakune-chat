package com.hakune.chat;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.object.ObjectContents;
import net.kyori.adventure.text.object.PlayerHeadObjectContents;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitTask;

public final class SkinRestorerHeadHook implements Listener {
    private final HakuneChatPlugin plugin;
    private final Map<UUID, SkinData> cache = new ConcurrentHashMap<>();
    private BukkitTask refreshTask;
    private volatile UpdateMode updateMode = UpdateMode.INTERVAL;
    private volatile java.util.List<String> commandTriggers = java.util.List.of();

    private final boolean available;
    private final Method srGetMethod;
    private final Method getPlayerStorageMethod;
    private final Method getSkinForPlayerMethod;
    private final Method skinGetValueMethod;
    private final Method skinGetSignatureMethod;

    public SkinRestorerHeadHook(HakuneChatPlugin plugin, PluginManager pluginManager) {
        this.plugin = plugin;

        Method srGet = null;
        Method getPlayerStorage = null;
        Method getSkinForPlayer = null;
        Method skinGetValue = null;
        Method skinGetSignature = null;
        boolean ok = false;

        if (pluginManager.isPluginEnabled("SkinsRestorer")) {
            try {
                Class<?> providerClass = Class.forName("net.skinsrestorer.api.SkinsRestorerProvider");
                Class<?> apiClass = Class.forName("net.skinsrestorer.api.SkinsRestorer");
                Class<?> playerStorageClass = Class.forName("net.skinsrestorer.api.storage.PlayerStorage");
                Class<?> skinPropertyClass = Class.forName("net.skinsrestorer.api.property.SkinProperty");

                srGet = providerClass.getMethod("get");
                getPlayerStorage = apiClass.getMethod("getPlayerStorage");
                getSkinForPlayer = playerStorageClass.getMethod("getSkinForPlayer", UUID.class, String.class);
                skinGetValue = skinPropertyClass.getMethod("getValue");
                skinGetSignature = skinPropertyClass.getMethod("getSignature");
                ok = true;
            } catch (ClassNotFoundException | NoSuchMethodException ignored) {
                ok = false;
            }
        }

        this.available = ok;
        this.srGetMethod = srGet;
        this.getPlayerStorageMethod = getPlayerStorage;
        this.getSkinForPlayerMethod = getSkinForPlayer;
        this.skinGetValueMethod = skinGetValue;
        this.skinGetSignatureMethod = skinGetSignature;
    }

    public boolean isAvailable() {
        return available;
    }

    public void warmupOnlinePlayers() {
        if (!available) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            warmup(player);
        }
    }

    public void configure(String mode, int refreshSeconds, java.util.List<String> triggers) {
        this.updateMode = UpdateMode.from(mode);
        this.commandTriggers = normalizeTriggers(triggers);
        stop();
        if (this.updateMode == UpdateMode.INTERVAL || this.updateMode == UpdateMode.BOTH) {
            startInterval(refreshSeconds);
        }
    }

    private void startInterval(int refreshSeconds) {
        if (!available) {
            return;
        }
        int interval = Math.max(10, refreshSeconds);
        if (refreshTask != null) {
            refreshTask.cancel();
        }
        refreshTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
            plugin,
            this::refreshOnlinePlayers,
            interval * 20L,
            interval * 20L
        );
    }

    public void stop() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
    }

    private void refreshOnlinePlayers() {
        if (!available) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            warmup(player);
        }
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!available) {
            return;
        }
        if (updateMode != UpdateMode.COMMAND && updateMode != UpdateMode.BOTH) {
            return;
        }
        String message = event.getMessage().trim();
        if (message.isEmpty()) {
            return;
        }
        String root = message.split("\\s+", 2)[0].toLowerCase();
        if (!commandTriggers.contains(root)) {
            return;
        }
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> warmup(player), 20L);
    }

    public Component getHeadComponent(Player player) {
        if (!available) {
            return Component.empty();
        }

        if (plugin.getBedrockDetector().isBedrock(player.getUniqueId())) {
            BedrockSkinBridge bridge = plugin.getBedrockSkinBridge();
            if (bridge != null) {
                BedrockSkinSettings settings = plugin.getSettings().getBedrockSkinSettings();
                if (settings != null && settings.isEnabled() && settings.isUseForHeads()) {
                    BedrockSkinBridge.SkinPropertyData data = bridge.getSkinProperty(player.getUniqueId());
                    if (data != null && data.getValue() != null && !data.getValue().isBlank()) {
                        return bridge.buildHeadComponent(player);
                    }
                }
            }
        }

        SkinData skinData = cache.get(player.getUniqueId());
        if (skinData == null || skinData.value == null || skinData.value.isBlank()) {
            return Component.object(ObjectContents.playerHead(player.getUniqueId()));
        }

        PlayerHeadObjectContents headContents = ObjectContents.playerHead()
            .profileProperty(PlayerHeadObjectContents.property("textures", skinData.value, skinData.signature))
            .build();
        return Component.object(headContents);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        warmup(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cache.remove(event.getPlayer().getUniqueId());
    }

    private void warmup(Player player) {
        if (!available) {
            return;
        }
        UUID uuid = player.getUniqueId();
        String name = player.getName();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            SkinData data = loadSkinData(uuid, name);
            if (data == null) {
                cache.remove(uuid);
                return;
            }
            cache.put(uuid, data);
        });
    }

    private SkinData loadSkinData(UUID uuid, String name) {
        try {
            Object skinRestorerApi = srGetMethod.invoke(null);
            Object playerStorage = getPlayerStorageMethod.invoke(skinRestorerApi);
            Object optionalProperty = getSkinForPlayerMethod.invoke(playerStorage, uuid, name);

            if (!(optionalProperty instanceof Optional<?> optional) || optional.isEmpty()) {
                return null;
            }

            Object property = optional.get();
            String value = (String) skinGetValueMethod.invoke(property);
            String signature = (String) skinGetSignatureMethod.invoke(property);

            if (value == null || value.isBlank()) {
                return null;
            }
            return new SkinData(value, signature);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static final class SkinData {
        private final String value;
        private final String signature;

        private SkinData(String value, String signature) {
            this.value = value;
            this.signature = signature;
        }
    }

    private enum UpdateMode {
        INTERVAL,
        COMMAND,
        BOTH;

        private static UpdateMode from(String value) {
            if (value == null) {
                return INTERVAL;
            }
            return switch (value.toLowerCase()) {
                case "command" -> COMMAND;
                case "both" -> BOTH;
                default -> INTERVAL;
            };
        }
    }

    private static java.util.List<String> normalizeTriggers(java.util.List<String> triggers) {
        if (triggers == null || triggers.isEmpty()) {
            return java.util.List.of("/skin", "/sr");
        }
        java.util.List<String> normalized = new java.util.ArrayList<>();
        for (String trigger : triggers) {
            if (trigger == null || trigger.isBlank()) {
                continue;
            }
            String t = trigger.trim().toLowerCase();
            if (!t.startsWith("/")) {
                t = "/" + t;
            }
            normalized.add(t);
        }
        return normalized.isEmpty() ? java.util.List.of("/skin", "/sr") : java.util.List.copyOf(normalized);
    }
}
