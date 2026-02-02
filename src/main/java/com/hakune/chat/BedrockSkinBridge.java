package com.hakune.chat;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.object.ObjectContents;
import net.kyori.adventure.text.object.PlayerHeadObjectContents;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitTask;

public final class BedrockSkinBridge implements Listener {
    private static final Gson GSON = new Gson();
    private static final String GEYSER_API = "https://api.geysermc.org/v2/skin/";

    private final HakuneChatPlugin plugin;
    private final BedrockDetector bedrockDetector;
    private final FloodgateAccess floodgateAccess;
    private final SkinRestorerApplier skinRestorerApplier;
    private final HttpClient client;

    private final Map<UUID, SkinPropertyData> cache = new ConcurrentHashMap<>();
    private BukkitTask refreshTask;
    private volatile BedrockSkinSettings settings;

    public BedrockSkinBridge(HakuneChatPlugin plugin, BedrockSkinSettings settings, BedrockDetector bedrockDetector) {
        this.plugin = plugin;
        this.settings = settings;
        this.bedrockDetector = bedrockDetector;
        this.floodgateAccess = new FloodgateAccess(plugin.getServer().getPluginManager());
        this.skinRestorerApplier = new SkinRestorerApplier(plugin.getServer().getPluginManager());
        this.client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    public void configure(BedrockSkinSettings settings) {
        this.settings = settings;
        stop();
        start();
    }

    public void start() {
        if (!settings.isEnabled()) {
            return;
        }
        if (settings.isApplyToPlayers() && skinRestorerApplier.isAvailable()) {
            refreshAllOnline();
        }
        UpdateMode mode = UpdateMode.from(settings.getUpdateMode());
        if (mode == UpdateMode.INTERVAL || mode == UpdateMode.BOTH) {
            startInterval(settings.getUpdateIntervalSeconds());
        }
    }

    public void stop() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
    }

    public SkinPropertyData getSkinProperty(UUID uuid) {
        return cache.get(uuid);
    }

    public Component buildHeadComponent(Player player) {
        SkinPropertyData data = cache.get(player.getUniqueId());
        if (data == null || data.value == null || data.value.isBlank()) {
            return Component.object(ObjectContents.playerHead(player.getUniqueId()));
        }
        PlayerHeadObjectContents headContents = ObjectContents.playerHead()
            .profileProperty(PlayerHeadObjectContents.property("textures", data.value, data.signature))
            .build();
        return Component.object(headContents);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!settings.isEnabled()) {
            return;
        }
        Player player = event.getPlayer();
        if (!bedrockDetector.isBedrock(player.getUniqueId())) {
            return;
        }
        refreshPlayer(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cache.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!settings.isEnabled()) {
            return;
        }
        UpdateMode mode = UpdateMode.from(settings.getUpdateMode());
        if (mode != UpdateMode.COMMAND && mode != UpdateMode.BOTH) {
            return;
        }
        Player player = event.getPlayer();
        if (!bedrockDetector.isBedrock(player.getUniqueId())) {
            return;
        }
        String root = event.getMessage().trim().split("\\s+", 2)[0].toLowerCase();
        if (!normalizeTriggers(settings.getCommandTriggers()).contains(root)) {
            return;
        }
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> refreshPlayer(player), 20L);
    }

    private void startInterval(int seconds) {
        int interval = Math.max(10, seconds);
        refreshTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
            plugin,
            this::refreshAllOnline,
            interval * 20L,
            interval * 20L
        );
    }

    private void refreshAllOnline() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (bedrockDetector.isBedrock(player.getUniqueId())) {
                refreshPlayer(player);
            }
        }
    }

    private void refreshPlayer(Player player) {
        SkinPropertyData direct = floodgateAccess.getSkinProperty(player.getUniqueId());
        if (direct != null && direct.value != null && !direct.value.isBlank()) {
            applyAndCache(player, direct);
            return;
        }

        String xuid = floodgateAccess.getXuid(player.getUniqueId());
        if (xuid == null || xuid.isBlank()) {
            return;
        }
        fetchSkin(xuid).thenAccept(skin -> {
            if (skin == null || skin.value == null || skin.value.isBlank()) {
                return;
            }
            applyAndCache(player, skin);
        });
    }

    private java.util.concurrent.CompletableFuture<SkinPropertyData> fetchSkin(String xuid) {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(GEYSER_API + xuid))
            .timeout(Duration.ofSeconds(15))
            .GET()
            .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(response -> {
                if (response.statusCode() != 200) {
                    return null;
                }
                try {
                    GeyserSkinResponse skin = GSON.fromJson(response.body(), GeyserSkinResponse.class);
                    if (skin == null || skin.value == null || skin.value.isBlank()) {
                        return null;
                    }
                    return new SkinPropertyData(skin.value, skin.signature, skin.hash, skin.textureId);
                } catch (Exception ignored) {
                    return null;
                }
            })
            .exceptionally(ignored -> null);
    }

    private void applyAndCache(Player player, SkinPropertyData skin) {
        SkinPropertyData existing = cache.get(player.getUniqueId());
        if (existing != null) {
            if (existing.hash != null && skin.hash != null && existing.hash.equals(skin.hash)) {
                return;
            }
            if (existing.hash == null || skin.hash == null) {
                String existingValue = existing.value == null ? "" : existing.value;
                String newValue = skin.value == null ? "" : skin.value;
                String existingSig = existing.signature == null ? "" : existing.signature;
                String newSig = skin.signature == null ? "" : skin.signature;
                if (existingValue.equals(newValue) && existingSig.equals(newSig)) {
                    return;
                }
            }
        }
        cache.put(player.getUniqueId(), skin);
        if (settings.isApplyToPlayers()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                boolean applied = skinRestorerApplier.apply(player, skin);
                applyProfile(player, skin);
                if (!applied) {
                    applyProfile(player, skin);
                }
            });
        }
    }

    private void applyProfile(Player player, SkinPropertyData data) {
        if (data == null || data.value == null || data.value.isBlank()) {
            return;
        }
        try {
            PlayerProfile profile = player.getPlayerProfile();
            String signature = data.signature == null ? null : data.signature;
            profile.setProperty(new ProfileProperty("textures", data.value, signature));
            player.setPlayerProfile(profile);
        } catch (Exception ignored) {
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

    public static final class SkinPropertyData {
        private final String value;
        private final String signature;
        private final String hash;
        private final String textureId;

        private SkinPropertyData(String value, String signature, String hash, String textureId) {
            this.value = value;
            this.signature = signature;
            this.hash = hash;
            this.textureId = textureId;
        }

        public String getValue() {
            return value;
        }

        public String getSignature() {
            return signature;
        }

        public String getHash() {
            return hash;
        }

        public String getTextureId() {
            return textureId;
        }
    }

    private static final class GeyserSkinResponse {
        String value;
        String signature;
        String hash;
        @SerializedName("texture_id")
        String textureId;
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

    private static final class FloodgateAccess {
        private final boolean available;
        private final java.lang.reflect.Method getInstanceMethod;
        private final java.lang.reflect.Method getPlayerMethod;
        private final java.lang.reflect.Method getXuidMethod;
        private final java.lang.reflect.Method getSkinMethod;
        private final java.lang.reflect.Method skinGetValueMethod;
        private final java.lang.reflect.Method skinGetSignatureMethod;
        private final java.lang.reflect.Method skinGetSkinIdMethod;

        private FloodgateAccess(PluginManager pluginManager) {
            boolean ok = false;
            java.lang.reflect.Method getInstance = null;
            java.lang.reflect.Method getPlayer = null;
            java.lang.reflect.Method getXuid = null;
            java.lang.reflect.Method getSkin = null;
            java.lang.reflect.Method skinGetValue = null;
            java.lang.reflect.Method skinGetSignature = null;
            java.lang.reflect.Method skinGetSkinId = null;

            if (pluginManager.isPluginEnabled("floodgate")) {
                try {
                    Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
                    Class<?> playerClass = Class.forName("org.geysermc.floodgate.api.player.FloodgatePlayer");
                    getInstance = apiClass.getMethod("getInstance");
                    getPlayer = apiClass.getMethod("getPlayer", java.util.UUID.class);
                    getXuid = playerClass.getMethod("getXuid");
                    ok = true;

                    try {
                        Class<?> skinClass = Class.forName("org.geysermc.floodgate.api.player.SkinData");
                        getSkin = playerClass.getMethod("getSkin");
                        skinGetValue = skinClass.getMethod("getValue");
                        skinGetSignature = skinClass.getMethod("getSignature");
                        skinGetSkinId = skinClass.getMethod("getSkinId");
                    } catch (ClassNotFoundException | NoSuchMethodException ignored) {
                        // Skin access is optional; still allow XUID-based updates.
                    }
                } catch (ClassNotFoundException | NoSuchMethodException ignored) {
                    ok = false;
                }
            }

            this.available = ok;
            this.getInstanceMethod = getInstance;
            this.getPlayerMethod = getPlayer;
            this.getXuidMethod = getXuid;
            this.getSkinMethod = getSkin;
            this.skinGetValueMethod = skinGetValue;
            this.skinGetSignatureMethod = skinGetSignature;
            this.skinGetSkinIdMethod = skinGetSkinId;
        }

        public String getXuid(UUID uuid) {
            if (!available) {
                return null;
            }
            try {
                Object api = getInstanceMethod.invoke(null);
                Object floodgatePlayer = getPlayerMethod.invoke(api, uuid);
                if (floodgatePlayer == null) {
                    return null;
                }
                Object result = getXuidMethod.invoke(floodgatePlayer);
                return result == null ? null : result.toString();
            } catch (Exception ignored) {
                return null;
            }
        }

        public SkinPropertyData getSkinProperty(UUID uuid) {
            if (!available || getSkinMethod == null || skinGetValueMethod == null || skinGetSignatureMethod == null) {
                return null;
            }
            try {
                Object api = getInstanceMethod.invoke(null);
                Object floodgatePlayer = getPlayerMethod.invoke(api, uuid);
                if (floodgatePlayer == null) {
                    return null;
                }
                Object skin = getSkinMethod.invoke(floodgatePlayer);
                if (skin == null) {
                    return null;
                }
                String value = (String) skinGetValueMethod.invoke(skin);
                String signature = (String) skinGetSignatureMethod.invoke(skin);
                String hash = null;
                if (skinGetSkinIdMethod != null) {
                    Object skinId = skinGetSkinIdMethod.invoke(skin);
                    hash = skinId == null ? null : skinId.toString();
                }
                if (value == null || value.isBlank()) {
                    return null;
                }
                return new SkinPropertyData(value, signature, hash, null);
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    private static final class SkinRestorerApplier {
        private final boolean available;
        private final java.lang.reflect.Method getInstanceMethod;
        private final java.lang.reflect.Method getSkinApplierMethod;
        private final java.lang.reflect.Method applySkinMethod;
        private final java.lang.reflect.Method skinPropertyOfMethod;

        private SkinRestorerApplier(PluginManager pluginManager) {
            boolean ok = false;
            java.lang.reflect.Method getInstance = null;
            java.lang.reflect.Method getSkinApplier = null;
            java.lang.reflect.Method applySkin = null;
            java.lang.reflect.Method skinPropertyOf = null;

            if (pluginManager.isPluginEnabled("SkinsRestorer")) {
                try {
                    Class<?> providerClass = Class.forName("net.skinsrestorer.api.SkinsRestorerProvider");
                    Class<?> apiClass = Class.forName("net.skinsrestorer.api.SkinsRestorer");
                    Class<?> applierClass = Class.forName("net.skinsrestorer.api.SkinApplier");
                    Class<?> skinPropertyClass = Class.forName("net.skinsrestorer.api.property.SkinProperty");

                    getInstance = providerClass.getMethod("get");
                    getSkinApplier = apiClass.getMethod("getSkinApplier");
                    skinPropertyOf = skinPropertyClass.getMethod("of", String.class, String.class);
                    applySkin = applierClass.getMethod("applySkin", org.bukkit.entity.Player.class, skinPropertyClass);
                    ok = true;
                } catch (ClassNotFoundException | NoSuchMethodException ignored) {
                    ok = false;
                }
            }

            this.available = ok;
            this.getInstanceMethod = getInstance;
            this.getSkinApplierMethod = getSkinApplier;
            this.applySkinMethod = applySkin;
            this.skinPropertyOfMethod = skinPropertyOf;
        }

        public boolean isAvailable() {
            return available;
        }

        public boolean apply(Player player, SkinPropertyData data) {
            if (!available || data == null || data.value == null || data.value.isBlank()) {
                return false;
            }
            try {
                Object api = getInstanceMethod.invoke(null);
                Object applier = getSkinApplierMethod.invoke(api);
                String signature = data.signature;
                Object property = skinPropertyOfMethod.invoke(null, data.value, signature);
                applySkinMethod.invoke(applier, player, property);
                return true;
            } catch (Exception ignored) {
                return false;
            }
        }
    }
}
