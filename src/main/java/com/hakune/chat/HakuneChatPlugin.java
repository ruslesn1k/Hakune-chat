package com.hakune.chat;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bstats.bukkit.Metrics;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class HakuneChatPlugin extends JavaPlugin {
    private static final int BSTATS_PLUGIN_ID = 29477;
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
        .character('&')
        .hexColors()
        .build();
    private static final Pattern URL_PATTERN = Pattern.compile("(?i)\\b((?:https?://|www\\.)[^\\s<]+)");
    private static final Pattern MARKDOWN_LINK_PATTERN = Pattern.compile("(?i)\\[([^\\]\\r\\n]+)]\\(((?:https?://|www\\.)[^\\s)]+)\\)");

    private ChatSettings settings;
    private BedrockDetector bedrockDetector;
    private PlaceholderHook placeholderHook;
    private TelegramBridge telegramBridge;
    private SkinRestorerHeadHook skinRestorerHeadHook;
    private TabManager tabManager;
    private TabSettings tabSettings;
    private BedrockSkinBridge bedrockSkinBridge;
    private final java.util.Set<UUID> listenLocal = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Map<UUID, UUID> lastPm = new ConcurrentHashMap<>();
    private TttManager tttManager;
    private long tttInviteTtlMillis = 60000L;
    private VoiceDetector voiceDetector;
    private IntegrationSettings integrationSettings;
    private DiscordBridge discordBridge;
    private LiveNotifier liveNotifier;
    private TranslationManager translationManager;
    private HeadMessageManager headMessageManager;
    private final java.util.Set<UUID> msgHeadDisabled = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private boolean headMessageEnabled = true;
    private String headMessageFormat = "&f{player}&7: &f{message}";
    private int headMessageDurationTicks = 60;
    private double headMessageYOffset = 2.2;
    private String headMessageRenderMode = "nametag";
    private String headMessageArmorStandFollowMode = "passenger";
    private String headMessageNameTagFormat = "{base} &8| &f{message}";
    private String manualStreamFormat = "&d[STREAM] &f{name}&7: &b{url}";
    private NickColorManager nickColorManager;

    @Override
    public void onEnable() {
        getLogger().info("Data folder: " + getDataFolder().getAbsolutePath());
        saveDefaultConfig();
        saveResource("formatting.yml", false);
        saveResource("integration.yml", false);
        saveResource("translations/en.yml", false);
        saveResource("translations/ru.yml", false);
        this.translationManager = new TranslationManager(this);
        reloadSettings();

        this.bedrockDetector = new BedrockDetector();
        this.placeholderHook = new PlaceholderHook(getServer().getPluginManager());
        this.skinRestorerHeadHook = new SkinRestorerHeadHook(this, getServer().getPluginManager());
        this.bedrockSkinBridge = new BedrockSkinBridge(this, loadBedrockSkinSettings(), this.bedrockDetector);
        this.tttManager = new TttManager(this);
        this.voiceDetector = new VoiceDetector(this, getServer().getPluginManager());
        this.nickColorManager = new NickColorManager(this);
        this.headMessageManager = new HeadMessageManager(this);
        this.nickColorManager.applyToOnlinePlayers();
        loadMsgHeadToggles();
        this.headMessageManager.configure(
            headMessageEnabled,
            headMessageFormat,
            headMessageDurationTicks,
            headMessageYOffset,
            headMessageRenderMode,
            headMessageArmorStandFollowMode,
            headMessageNameTagFormat
        );

        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        getServer().getPluginManager().registerEvents(new JoinListener(this), this);
        getServer().getPluginManager().registerEvents(new MotdListener(this), this);
        getServer().getPluginManager().registerEvents(this.skinRestorerHeadHook, this);
        getServer().getPluginManager().registerEvents(this.bedrockSkinBridge, this);
        this.skinRestorerHeadHook.warmupOnlinePlayers();
        if (this.settings.isSkinRestorerHeads()) {
            this.skinRestorerHeadHook.configure(
                this.settings.getSkinRestorerUpdateMode(),
                this.settings.getSkinRestorerRefreshSeconds(),
                this.settings.getSkinRestorerCommandTriggers()
            );
        }
        if (this.bedrockSkinBridge != null) {
            this.bedrockSkinBridge.configure(this.settings.getBedrockSkinSettings());
        }
        initMetrics();

        // Tab manager is created in reloadSettings() to avoid double scheduling.
    }

    private void initMetrics() {
        FileConfiguration config = getConfig();
        if (!config.getBoolean("bstats.enabled", true)) {
            getLogger().info("bStats metrics disabled in config.");
            return;
        }

        try {
            new Metrics(this, BSTATS_PLUGIN_ID);
            getLogger().info("bStats metrics enabled.");
        } catch (Exception ex) {
            getLogger().log(Level.WARNING, "Failed to initialize bStats metrics.", ex);
        }
    }

    public ChatSettings getSettings() {
        return settings;
    }

    public BedrockDetector getBedrockDetector() {
        return bedrockDetector;
    }

    public PlaceholderHook getPlaceholderHook() {
        return placeholderHook;
    }

    public TelegramBridge getTelegramBridge() {
        return telegramBridge;
    }

    public SkinRestorerHeadHook getSkinRestorerHeadHook() {
        return skinRestorerHeadHook;
    }

    public TabSettings getTabSettings() {
        return tabSettings;
    }

    public BedrockSkinBridge getBedrockSkinBridge() {
        return bedrockSkinBridge;
    }

    public VoiceDetector getVoiceDetector() {
        return voiceDetector;
    }

    public DiscordBridge getDiscordBridge() {
        return discordBridge;
    }

    public IntegrationSettings getIntegrationSettings() {
        return integrationSettings;
    }

    public NickColorManager getNickColorManager() {
        return nickColorManager;
    }

    public boolean isHeadMessageEnabledFor(UUID uuid) {
        return !msgHeadDisabled.contains(uuid);
    }

    public void showHeadMessage(org.bukkit.entity.Player sender, String message) {
        if (headMessageManager == null) {
            return;
        }
        headMessageManager.show(sender, message);
    }

    public HeadMessageManager getHeadMessageManager() {
        return headMessageManager;
    }

    public void refreshPlayerNameTag(org.bukkit.entity.Player player) {
        if (tabManager == null || player == null || !player.isOnline()) {
            return;
        }
        tabManager.refreshNameTag(player);
    }

    public String tr(String key) {
        if (translationManager == null) {
            return key;
        }
        return translationManager.get(key);
    }

    public String trf(String key, String... pairs) {
        if (translationManager == null) {
            return key;
        }
        return translationManager.format(key, pairs);
    }

    public Component getStyledNameComponent(org.bukkit.entity.Player player) {
        if (nickColorManager == null) {
            return player.displayName() != null ? player.displayName() : Component.text(player.getName());
        }
        return nickColorManager.getNameComponent(player);
    }

    public String getStyledNameLegacy(org.bukkit.entity.Player player) {
        if (player == null) {
            return "";
        }
        Component styled = getStyledNameComponent(player);
        String legacy = LEGACY.serialize(styled);
        if (legacy == null || legacy.isEmpty()) {
            return player.getName();
        }
        return legacy;
    }

    public String getVisibleName(org.bukkit.entity.Player player) {
        if (player == null) {
            return "";
        }
        Component styled = getStyledNameComponent(player);
        String plain = PlainTextComponentSerializer.plainText().serialize(styled);
        if (plain == null || plain.isEmpty()) {
            return player.getName();
        }
        return plain;
    }

    public java.util.Set<UUID> getListenLocal() {
        return listenLocal;
    }

    public void setLastPm(UUID player, UUID target) {
        lastPm.put(player, target);
    }

    public UUID getLastPm(UUID player) {
        return lastPm.get(player);
    }

    public void reloadSettings() {
        reloadConfig();
        FileConfiguration config = getConfig();
        String language = config.getString("translations.language", "ru");
        if (!config.isSet("translations.language")) {
            config.set("translations.language", language);
        }
        if (translationManager == null) {
            translationManager = new TranslationManager(this);
        }
        translationManager.reload(language);

        double localDistance = config.getDouble("chat.local-distance", 100.0);
        String globalSymbol = config.getString("chat.global-symbol", "!");

        FileConfiguration formatting = loadFormattingConfig();
        ConfigurationSection formats = formatting.getConfigurationSection("chat.formats");
        ChatFormat javaFormat = loadFormat(formats, "java");
        ChatFormat bedrockFormat = loadFormat(formats, "bedrock");
        String joinJava = formatting.getString("join.formats.java", "&a+ &f{player}");
        String joinBedrock = formatting.getString("join.formats.bedrock", "&a+ &f{player}");
        String quitJava = formatting.getString("quit.formats.java", "&c- &f{player}");
        String quitBedrock = formatting.getString("quit.formats.bedrock", "&c- &f{player}");
        String listenJava = formatting.getString("listenlocal.formats.java", "{head}&7[&eLL&7] &f{player}&7: &f{message}");
        String listenBedrock = formatting.getString("listenlocal.formats.bedrock", "&7[&eLL&7] &f{player}&7: &f{message}");
        String pmToJava = formatting.getString("private.formats.java.to", "&7[&dPM&7] &fВы -> {player}&7: &f{message}");
        String pmFromJava = formatting.getString("private.formats.java.from", "&7[&dPM&7] &f{player} -> Вы&7: &f{message}");
        String pmToBedrock = formatting.getString("private.formats.bedrock.to", "&7[&dPM&7] &fВы -> {player}&7: &f{message}");
        String pmFromBedrock = formatting.getString("private.formats.bedrock.from", "&7[&dPM&7] &f{player} -> Вы&7: &f{message}");
        String notifyFormat = readTextBlock(formatting, "notifications.format", "&d[{platform}] &f{name}&7: &b{url}");
        String manualNotifyFormat = readTextBlock(formatting, "notifications.manual-format", "&d[STREAM] &f{name}&7: &b{url}");
        ConfigurationSection headMessageSection = formatting.getConfigurationSection("head-message");
        this.headMessageEnabled = headMessageSection == null || headMessageSection.getBoolean("enabled", true);
        this.headMessageFormat = readTextBlock(formatting, "head-message.format", "&f{player}&7: &f{message}");
        this.headMessageDurationTicks = headMessageSection == null ? 60 : headMessageSection.getInt("duration-ticks", 60);
        this.headMessageYOffset = headMessageSection == null ? 2.2 : headMessageSection.getDouble("y-offset", 2.2);
        this.headMessageRenderMode = headMessageSection == null ? "nametag" : headMessageSection.getString("render-mode", "nametag");
        this.headMessageArmorStandFollowMode = headMessageSection == null
            ? "passenger"
            : headMessageSection.getString("armorstand-follow-mode", "passenger");
        this.headMessageNameTagFormat = readTextBlock(formatting, "head-message.name-tag-format", "{base} &8| &f{message}");

        this.integrationSettings = loadIntegrationSettings(notifyFormat);
        this.manualStreamFormat = manualNotifyFormat;
        TelegramSettings telegramSettings = integrationSettings.getTelegram();

        boolean separateBedrockFormat = config.getBoolean("features.separate-bedrock-format", true);
        boolean skinRestorerHeads = config.getBoolean("features.skinrestorer-heads", true);
        int skinRestorerRefreshSeconds = config.getInt("features.skinrestorer-refresh-seconds", 60);
        String skinRestorerUpdateMode = config.getString("features.skinrestorer-update-mode", "interval");
        java.util.List<String> skinRestorerCommandTriggers =
            config.getStringList("features.skinrestorer-command-triggers");
        boolean joinMessageEnabled = config.getBoolean("features.join-message-enabled", true);
        boolean quitMessageEnabled = config.getBoolean("features.quit-message-enabled", true);
        boolean tttEnabled = config.getBoolean("minigames.ttt-enabled", true);
        ConfigurationSection voiceSection = config.getConfigurationSection("voice-indicator");
        boolean voiceEnabled = voiceSection != null && voiceSection.getBoolean("enabled", false);
        String voiceDetectionMode = voiceSection != null ? voiceSection.getString("detection", "auto") : "auto";
        String voiceOn = voiceSection != null ? voiceSection.getString("on", "") : "";
        String voiceOff = voiceSection != null ? voiceSection.getString("off", "") : "";
        if (voiceSection == null) {
            config.set("voice-indicator.enabled", false);
        }
        if (voiceDetectionMode == null || voiceDetectionMode.isBlank()) {
            voiceDetectionMode = "auto";
            config.set("voice-indicator.detection", voiceDetectionMode);
        }
        if (voiceOn == null || voiceOn.isBlank()) {
            voiceOn = "&a[VC]";
            config.set("voice-indicator.on", voiceOn);
        }
        if (voiceOff == null || voiceOff.isBlank()) {
            voiceOff = "&c[NO]";
            config.set("voice-indicator.off", voiceOff);
        }
        boolean motdEnabled = config.getBoolean("motd.enabled", true);
        java.util.List<String> motdLines = readLines(config, "motd.lines");
        if (!config.isSet("motd.lines") || motdLines.isEmpty() || (motdLines.size() == 1 && motdLines.get(0).isBlank())) {
            motdLines = java.util.List.of("&aHakune", "&7Welcome to the server");
            config.set("motd.lines", motdLines);
        }
        if (!config.isSet("motd.enabled")) {
            config.set("motd.enabled", motdEnabled);
        }
        saveConfig();
        int tttInviteTtlSeconds = config.getInt("minigames.ttt-invite-ttl-seconds", 60);
        this.tttInviteTtlMillis = Math.max(5, tttInviteTtlSeconds) * 1000L;
        getLogger().info(trf(
            "log.voice-indicator-loaded",
            "enabled", String.valueOf(voiceEnabled),
            "detection", String.valueOf(voiceDetectionMode),
            "on", String.valueOf(voiceOn),
            "off", String.valueOf(voiceOff)
        ));

        this.settings = new ChatSettings(
            localDistance,
            globalSymbol,
            javaFormat,
            bedrockFormat,
            telegramSettings,
            separateBedrockFormat,
            skinRestorerHeads,
            skinRestorerRefreshSeconds,
            skinRestorerUpdateMode,
            skinRestorerCommandTriggers,
            joinMessageEnabled,
            joinJava,
            joinBedrock,
            quitMessageEnabled,
            quitJava,
            quitBedrock,
            loadBedrockSkinSettings(),
            listenJava,
            listenBedrock,
            pmToJava,
            pmFromJava,
            pmToBedrock,
            pmFromBedrock,
            tttEnabled,
            voiceEnabled,
            voiceDetectionMode,
            voiceOn,
            voiceOff,
            motdEnabled,
            motdLines
        );

        this.voiceDetector = new VoiceDetector(this, getServer().getPluginManager());

        if (this.telegramBridge != null) {
            this.telegramBridge.stop();
        }
        this.telegramBridge = new TelegramBridge(this, telegramSettings);
        this.telegramBridge.start();

        if (this.discordBridge != null) {
            this.discordBridge.stop();
        }
        this.discordBridge = new DiscordBridge(this, integrationSettings.getDiscord());
        this.discordBridge.start();

        if (this.liveNotifier != null) {
            this.liveNotifier.stop();
        }
        this.liveNotifier = new LiveNotifier(this, integrationSettings.getNotifications());
        this.liveNotifier.start();

        if (this.skinRestorerHeadHook != null) {
            this.skinRestorerHeadHook.stop();
            if (skinRestorerHeads) {
                this.skinRestorerHeadHook.configure(
                    skinRestorerUpdateMode,
                    skinRestorerRefreshSeconds,
                    skinRestorerCommandTriggers
                );
            }
        }
        if (this.bedrockSkinBridge != null) {
            this.bedrockSkinBridge.configure(this.settings.getBedrockSkinSettings());
        }

        if (this.tabManager != null) {
            this.tabManager.stop();
            this.tabManager = null;
        }
        this.tabSettings = loadTabSettings();
        this.tabManager = new TabManager(this, tabSettings);
        this.tabManager.start();

        if (this.headMessageManager != null) {
            this.headMessageManager.configure(
                headMessageEnabled,
                headMessageFormat,
                headMessageDurationTicks,
                headMessageYOffset,
                headMessageRenderMode,
                headMessageArmorStandFollowMode,
                headMessageNameTagFormat
            );
        }
    }

    private ChatFormat loadFormat(ConfigurationSection root, String key) {
        if (root == null) {
            return new ChatFormat("&7[&aL&7] &f{player}&7: &f{message}",
                "&7[&bG&7] &f{player}&7: &f{message}");
        }
        ConfigurationSection section = root.getConfigurationSection(key);
        if (section == null) {
            return new ChatFormat("&7[&aL&7] &f{player}&7: &f{message}",
                "&7[&bG&7] &f{player}&7: &f{message}");
        }
        String local = section.getString("local", "&7[&aL&7] &f{player}&7: &f{message}");
        String global = section.getString("global", "&7[&bG&7] &f{player}&7: &f{message}");
        return new ChatFormat(local, global);
    }

    private FileConfiguration loadFormattingConfig() {
        File file = new File(getDataFolder(), "formatting.yml");
        if (!file.exists()) {
            saveResource("formatting.yml", false);
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    private TabSettings loadTabSettings() {
        File file = new File(getDataFolder(), "tab.yml");
        if (!file.exists()) {
            saveResource("tab.yml", false);
        }
        FileConfiguration tab = YamlConfiguration.loadConfiguration(file);
        boolean enabled = tab.getBoolean("tab.enabled", true);
        int interval = tab.getInt("tab.update-interval-seconds", 2);
        java.util.List<String> header = readLines(tab, "tab.header");
        java.util.List<String> footer = readLines(tab, "tab.footer");
        String playerFormat = tab.getString("tab.player-format", "{player}");
        String groupPlaceholder = tab.getString("tab.group-placeholder", "%luckperms_primary_group_name%");
        java.util.List<String> sorting = tab.getStringList("tab.sorting-types");
        boolean nameTagEnabled = tab.getBoolean("tab.name-tag.enabled", true);
        String nameTagFormat = tab.getString("tab.name-tag.format", "{player}");
        return new TabSettings(enabled, interval, header, footer, playerFormat, groupPlaceholder, sorting, nameTagEnabled, nameTagFormat);
    }

    private static java.util.List<String> readLines(FileConfiguration config, String path) {
        if (config.isList(path)) {
            java.util.List<String> list = config.getStringList(path);
            return list.isEmpty() ? java.util.List.of("") : list;
        }
        String value = config.getString(path, "");
        return java.util.List.of(value == null ? "" : value);
    }

    private static String readTextBlock(FileConfiguration config, String path, String fallback) {
        if (config == null) {
            return fallback;
        }
        if (config.isList(path)) {
            java.util.List<String> lines = config.getStringList(path);
            return lines.isEmpty() ? "" : String.join("\n", lines);
        }
        String value = config.getString(path);
        return value == null ? fallback : value;
    }

    private void loadMsgHeadToggles() {
        msgHeadDisabled.clear();
        File file = new File(getDataFolder(), "msghead.yml");
        if (!file.exists()) {
            YamlConfiguration fresh = new YamlConfiguration();
            fresh.set("disabled", java.util.List.of());
            try {
                fresh.save(file);
            } catch (Exception ignored) {
            }
            return;
        }
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        for (String raw : cfg.getStringList("disabled")) {
            try {
                msgHeadDisabled.add(UUID.fromString(raw));
            } catch (Exception ignored) {
            }
        }
    }

    private void saveMsgHeadToggles() {
        File file = new File(getDataFolder(), "msghead.yml");
        YamlConfiguration cfg = new YamlConfiguration();
        java.util.List<String> list = new java.util.ArrayList<>();
        for (UUID uuid : msgHeadDisabled) {
            list.add(uuid.toString());
        }
        cfg.set("disabled", list);
        try {
            cfg.save(file);
        } catch (Exception ignored) {
        }
    }

    private TelegramSettings loadTelegramSettings(FileConfiguration config) {
        boolean enabled = config.getBoolean("telegram.enabled", false);
        String token = config.getString("telegram.token", "");
        String chatId = config.getString("telegram.chat-id", "");
        int pollInterval = config.getInt("telegram.poll-interval-seconds", 5);
        String formatTo = config.getString("telegram.format-to-telegram", "[{type}] {player}: {message}");
        String formatFrom = config.getString("telegram.format-from-telegram", "&7[&dTG&7] &f{user}&7: &f{message}");
        return new TelegramSettings(enabled, token, chatId, pollInterval, formatTo, formatFrom);
    }

    public void broadcastExternal(String legacyText) {
        Component component = withClickableLinks(LEGACY.deserialize(legacyText));
        Bukkit.getScheduler().runTask(this, () -> {
            Bukkit.getOnlinePlayers().forEach(player -> player.sendMessage(component));
            Bukkit.getConsoleSender().sendMessage(component);
        });
    }

    public void broadcastTelegram(String legacyText) {
        broadcastExternal(legacyText);
    }

    public void broadcastDiscord(String legacyText) {
        broadcastExternal(legacyText);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase();
        if (name.equals("chatreload")) {
            if (!sender.hasPermission("hakunechat.reload")) {
                sender.sendMessage(ChatColor.RED + tr("error.no-permission"));
                return true;
            }
            reloadSettings();
            sender.sendMessage(ChatColor.GREEN + tr("info.config-reloaded"));
            return true;
        }

        if (name.equals("hakunechat") || name.equals("hchat")) {
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                if (!sender.hasPermission("hakunechat.reload")) {
                    sender.sendMessage(ChatColor.RED + tr("error.no-permission"));
                    return true;
                }
                reloadSettings();
                sender.sendMessage(ChatColor.GREEN + tr("info.config-reloaded"));
                return true;
            }
            sender.sendMessage(ChatColor.YELLOW + trf("command.hakunechat-usage", "label", label));
            return true;
        }

        if (name.equals("listenlocal")) {
            if (!(sender instanceof org.bukkit.entity.Player player)) {
                sender.sendMessage(ChatColor.RED + tr("error.players-only"));
                return true;
            }
            if (!sender.hasPermission("hakune.listenlocal")) {
                sender.sendMessage(ChatColor.RED + tr("error.no-permission"));
                return true;
            }
            boolean enabled = listenLocal.add(player.getUniqueId());
            if (!enabled) {
                listenLocal.remove(player.getUniqueId());
            }
            sender.sendMessage(ChatColor.GREEN + trf(
                "info.listen-local",
                "state", tr(enabled ? "state.enabled" : "state.disabled")
            ));
            return true;
        }

        if (name.equals("msghead")) {
            if (!(sender instanceof org.bukkit.entity.Player player)) {
                sender.sendMessage(ChatColor.RED + tr("error.players-only"));
                return true;
            }
            if (!sender.hasPermission("hakune.msghead")) {
                sender.sendMessage(ChatColor.RED + tr("error.no-permission"));
                return true;
            }
            if (args.length != 1) {
                sender.sendMessage(ChatColor.YELLOW + tr("command.msghead-usage"));
                return true;
            }
            String mode = args[0].toLowerCase(java.util.Locale.ROOT);
            if ("on".equals(mode)) {
                msgHeadDisabled.remove(player.getUniqueId());
                saveMsgHeadToggles();
                sender.sendMessage(ChatColor.GREEN + tr("info.msghead-on"));
                return true;
            }
            if ("off".equals(mode)) {
                msgHeadDisabled.add(player.getUniqueId());
                if (headMessageManager != null) {
                    headMessageManager.clear(player.getUniqueId());
                }
                saveMsgHeadToggles();
                sender.sendMessage(ChatColor.YELLOW + tr("info.msghead-off"));
                return true;
            }
            sender.sendMessage(ChatColor.YELLOW + tr("command.msghead-usage"));
            return true;
        }

        if (name.equals("msg") || name.equals("tell") || name.equals("w") || name.equals("pm")) {
            if (!(sender instanceof org.bukkit.entity.Player player)) {
                sender.sendMessage(ChatColor.RED + tr("error.players-only"));
                return true;
            }
            if (!sender.hasPermission("hakune.pm")) {
                sender.sendMessage(ChatColor.RED + tr("error.no-permission"));
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(ChatColor.YELLOW + trf("command.msg-usage", "label", label));
                return true;
            }
            org.bukkit.entity.Player target = getServer().getPlayerExact(args[0]);
            if (target == null || !target.isOnline()) {
                sender.sendMessage(ChatColor.RED + tr("error.player-not-found"));
                return true;
            }
            String message = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
            sendPrivateMessage(player, target, message);
            return true;
        }

        if (name.equals("reply") || name.equals("r")) {
            if (!(sender instanceof org.bukkit.entity.Player player)) {
                sender.sendMessage(ChatColor.RED + tr("error.players-only"));
                return true;
            }
            if (!sender.hasPermission("hakune.pm")) {
                sender.sendMessage(ChatColor.RED + tr("error.no-permission"));
                return true;
            }
            if (args.length < 1) {
                sender.sendMessage(ChatColor.YELLOW + trf("command.reply-usage", "label", label));
                return true;
            }
            UUID last = getLastPm(player.getUniqueId());
            if (last == null) {
                sender.sendMessage(ChatColor.RED + tr("error.reply-none"));
                return true;
            }
            org.bukkit.entity.Player target = getServer().getPlayer(last);
            if (target == null || !target.isOnline()) {
                sender.sendMessage(ChatColor.RED + tr("error.player-not-found"));
                return true;
            }
            String message = String.join(" ", args);
            sendPrivateMessage(player, target, message);
            return true;
        }

        if (name.equals("ttt")) {
            if (!(sender instanceof org.bukkit.entity.Player player)) {
                sender.sendMessage(ChatColor.RED + tr("error.players-only"));
                return true;
            }
            if (!sender.hasPermission("hakune.ttt")) {
                sender.sendMessage(ChatColor.RED + tr("error.no-permission"));
                return true;
            }
            if (!getSettings().isTttEnabled()) {
                sender.sendMessage(ChatColor.RED + tr("error.ttt-disabled"));
                return true;
            }
            if (args.length == 0) {
                sender.sendMessage(ChatColor.YELLOW + tr("command.ttt-usage"));
                return true;
            }
            String sub = args[0].toLowerCase();
            if (sub.equals("accept") || sub.equals("decline")) {
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.YELLOW + trf("command.ttt-accept-usage", "sub", sub));
                    return true;
                }
                org.bukkit.entity.Player target = getServer().getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage(ChatColor.RED + tr("error.player-not-found"));
                    return true;
                }
                TttManager.Invite invite = tttManager.getInvite(player.getUniqueId());
                if (invite != null && invite.isExpired()) {
                    tttManager.removeInvite(player.getUniqueId());
                    sender.sendMessage(ChatColor.RED + tr("error.invite-expired"));
                    return true;
                }
                if (sub.equals("accept")) {
                    if (!tttManager.hasInvite(player, target)) {
                        sender.sendMessage(ChatColor.RED + tr("error.no-invite-from-player"));
                        return true;
                    }
                    tttManager.acceptInvite(player, target);
                    sender.sendMessage(ChatColor.GREEN + tr("info.invite-accepted"));
                    target.sendMessage(ChatColor.GREEN + trf("info.invite-accepted-target", "player", player.getName()));
                    handleTttState(tttManager.getGame(player.getUniqueId()));
                    return true;
                }
                if (!tttManager.declineInvite(player, target)) {
                    sender.sendMessage(ChatColor.RED + tr("error.no-invite-from-player"));
                    return true;
                }
                sender.sendMessage(ChatColor.YELLOW + tr("info.invite-declined"));
                target.sendMessage(ChatColor.YELLOW + trf("info.invite-declined-target", "player", player.getName()));
                return true;
            }
            if (sub.equals("move")) {
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.YELLOW + tr("command.ttt-move-usage"));
                    return true;
                }
                int pos;
                try {
                    pos = Integer.parseInt(args[1]);
                } catch (NumberFormatException ex) {
                    sender.sendMessage(ChatColor.RED + tr("error.invalid-position"));
                    return true;
                }
                TttManager.Game game = tttManager.getGame(player.getUniqueId());
                if (game == null) {
                    sender.sendMessage(ChatColor.RED + tr("error.not-in-game"));
                    return true;
                }
                if (!tttManager.makeMove(player, pos)) {
                    sender.sendMessage(ChatColor.RED + tr("error.invalid-move"));
                    return true;
                }
                handleTttState(game);
                return true;
            }
            org.bukkit.entity.Player target = getServer().getPlayerExact(args[0]);
            if (target == null || !target.isOnline()) {
                sender.sendMessage(ChatColor.RED + tr("error.player-not-found"));
                return true;
            }
            if (target.getUniqueId().equals(player.getUniqueId())) {
                sender.sendMessage(ChatColor.RED + tr("error.cannot-invite-yourself"));
                return true;
            }
            if (tttManager.isInGame(player.getUniqueId()) || tttManager.isInGame(target.getUniqueId())) {
                sender.sendMessage(ChatColor.RED + tr("error.someone-in-game"));
                return true;
            }
            if (!tttManager.sendInvite(player, target, System.currentTimeMillis() + tttInviteTtlMillis)) {
                sender.sendMessage(ChatColor.RED + tr("error.could-not-send-invite"));
                return true;
            }
            sender.sendMessage(ChatColor.GREEN + trf("info.invite-sent", "player", target.getName()));
            sendTttInvite(target, player);
            scheduleTttInviteExpiry(player, target);
            return true;
        }

        if (name.equals("stream")) {
            if (!sender.hasPermission("hakune.live")) {
                sender.sendMessage(ChatColor.RED + tr("error.no-permission"));
                return true;
            }
            if (args.length < 1) {
                sender.sendMessage(ChatColor.YELLOW + tr("command.stream-usage"));
                return true;
            }
            String url = args[0];
            String nameLabel = (sender instanceof org.bukkit.entity.Player player) ? player.getName() : sender.getName();
            String formatted = manualStreamFormat
                .replace("{platform}", tr("common.stream-platform"))
                .replace("{name}", nameLabel)
                .replace("{url}", url)
                .replace("{title}", "");
            broadcastExternal(formatted);
            return true;
        }

        if (name.equals("nickcolor")) {
            if (!sender.hasPermission("hakune.nickcolor")) {
                sender.sendMessage(ChatColor.RED + tr("error.no-permission"));
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(ChatColor.YELLOW + tr("command.nickcolor-usage-color"));
                sender.sendMessage(ChatColor.YELLOW + tr("command.nickcolor-usage-gradient"));
                sender.sendMessage(ChatColor.YELLOW + tr("command.nickcolor-usage-reset"));
                return true;
            }
            org.bukkit.entity.Player target = getServer().getPlayerExact(args[0]);
            if (target == null || !target.isOnline()) {
                sender.sendMessage(ChatColor.RED + tr("error.player-not-found"));
                return true;
            }
            String mode = args[1].toLowerCase();
            if (mode.equals("reset")) {
                nickColorManager.reset(target);
                sender.sendMessage(ChatColor.GREEN + trf("info.nickname-reset", "player", target.getName()));
                return true;
            }
            if (mode.equals("color")) {
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.YELLOW + tr("command.nickcolor-usage-color"));
                    return true;
                }
                String color = args[2];
                if (!nickColorManager.setColor(target, color)) {
                    sender.sendMessage(ChatColor.RED + tr("error.invalid-color"));
                    return true;
                }
                sender.sendMessage(ChatColor.GREEN + trf("info.nickname-updated", "player", target.getName()));
                return true;
            }
            if (mode.equals("gradient") || mode.equals("gardient")) {
                if (args.length < 5 || !"to".equalsIgnoreCase(args[3])) {
                    sender.sendMessage(ChatColor.YELLOW + tr("command.nickcolor-usage-gradient"));
                    return true;
                }
                String from = args[2];
                String to = args[4];
                if (!nickColorManager.setGradient(target, from, to)) {
                    sender.sendMessage(ChatColor.RED + tr("error.invalid-gradient-colors"));
                    return true;
                }
                sender.sendMessage(ChatColor.GREEN + trf("info.nickname-gradient-updated", "player", target.getName()));
                return true;
            }
            sender.sendMessage(ChatColor.YELLOW + tr("command.nickcolor-usage-color"));
            sender.sendMessage(ChatColor.YELLOW + tr("command.nickcolor-usage-gradient"));
            sender.sendMessage(ChatColor.YELLOW + tr("command.nickcolor-usage-reset"));
            return true;
        }

        return false;
    }

    private void sendPrivateMessage(org.bukkit.entity.Player sender, org.bukkit.entity.Player target, String message) {
        ChatSettings settings = getSettings();
        String resolvedMessage = getPlaceholderHook().apply(sender, message);
        resolvedMessage = normalizeHex(resolvedMessage);

        Component headComponent = Component.empty();
        if (settings.isSkinRestorerHeads() && getSkinRestorerHeadHook() != null) {
            headComponent = getSkinRestorerHeadHook().getHeadComponent(sender);
        }

        Component toSender = buildPrivateComponent(sender, target, resolvedMessage, true, headComponent);
        Component toTarget = buildPrivateComponent(target, sender, resolvedMessage, false, headComponent);

        sender.sendMessage(toSender);
        target.sendMessage(toTarget);

        setLastPm(sender.getUniqueId(), target.getUniqueId());
        setLastPm(target.getUniqueId(), sender.getUniqueId());
    }

    private Component buildPrivateComponent(
        org.bukkit.entity.Player viewer,
        org.bukkit.entity.Player other,
        String message,
        boolean outgoing,
        Component headComponent
    ) {
        ChatSettings settings = getSettings();
        boolean viewerBedrock = getBedrockDetector().isBedrock(viewer.getUniqueId());
        String template;
        if (viewerBedrock) {
            template = outgoing ? settings.getPmToBedrock() : settings.getPmFromBedrock();
        } else {
            template = outgoing ? settings.getPmToJava() : settings.getPmFromJava();
        }

        String formatted = getPlaceholderHook().apply(viewer, template);
        formatted = normalizeHex(formatted)
            .replace("{message}", message)
            .replace("{world}", viewer.getWorld().getName())
            .replace("{voice}", getVoiceDetector().getVoiceIndicator(other));

        Component component = buildComponentWithHead(formatted, headComponent);
        component = replacePlayerPlaceholder(component, buildPlayerComponent(viewer, other));

        if (!viewerBedrock) {
            Component replyButton = Component.text(" ")
                .append(Component.text(tr("ui.reply-button")).color(net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE)
                    .clickEvent(net.kyori.adventure.text.event.ClickEvent.suggestCommand("/reply "))
                    .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(Component.text(tr("ui.reply-hover")))));
            component = component.append(replyButton);
        }
        return withClickableLinks(component);
    }

    private Component buildPlayerComponent(org.bukkit.entity.Player viewer, org.bukkit.entity.Player subject) {
        Component base = getStyledNameComponent(subject);
        boolean viewerBedrock = getBedrockDetector().isBedrock(viewer.getUniqueId());
        if (viewerBedrock) {
            return base;
        }
        String name = subject.getName();
        return base.clickEvent(net.kyori.adventure.text.event.ClickEvent.suggestCommand("/msg " + name + " "))
            .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(Component.text(trf("ui.message-hover", "name", name))));
    }

    private Component buildComponentWithHead(String formatted, Component headComponent) {
        if (formatted == null) {
            return Component.empty();
        }
        if (!formatted.contains("{head}")) {
            return LEGACY.deserialize(formatted);
        }
        String[] parts = formatted.split("\\{head\\}", -1);
        Component result = Component.empty();
        for (int i = 0; i < parts.length; i++) {
            if (!parts[i].isEmpty()) {
                result = result.append(LEGACY.deserialize(parts[i]));
            }
            if (i < parts.length - 1 && headComponent != null) {
                result = result.append(headComponent);
            }
        }
        return result;
    }

    private Component replacePlayerPlaceholder(Component component, Component playerComponent) {
        return component.replaceText(builder -> builder.matchLiteral("{player}").replacement(playerComponent));
    }

    private String normalizeHex(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return text.replaceAll("(?i)(?<!&)#([0-9a-f]{6})", "&#$1");
    }

    public Component withClickableLinks(Component component) {
        Component withMarkdownLinks = component.replaceText(builder -> builder
            .match(MARKDOWN_LINK_PATTERN)
            .replacement((result, textBuilder) -> {
                String label = result.group(1);
                String rawUrl = result.group(2);
                String url = toOpenableUrl(rawUrl);
                return textBuilder.content(label)
                    .clickEvent(ClickEvent.openUrl(url))
                    .hoverEvent(HoverEvent.showText(Component.text(trf("ui.open-link-hover", "url", rawUrl))))
                    .build();
            }));
        return withMarkdownLinks.replaceText(builder -> builder
            .match(URL_PATTERN)
            .replacement((result, textBuilder) -> {
                String raw = result.group(1);
                String url = toOpenableUrl(raw);
                return textBuilder.content(raw)
                    .clickEvent(ClickEvent.openUrl(url))
                    .hoverEvent(HoverEvent.showText(Component.text(trf("ui.open-link-hover", "url", raw))))
                    .build();
            }));
    }

    private static String toOpenableUrl(String rawUrl) {
        if (rawUrl == null) {
            return "";
        }
        String lower = rawUrl.toLowerCase(java.util.Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return rawUrl;
        }
        return "https://" + rawUrl;
    }

    @Override
    public void onDisable() {
        saveMsgHeadToggles();
        if (headMessageManager != null) {
            headMessageManager.clearAll();
        }
        if (telegramBridge != null) {
            telegramBridge.stop();
        }
        if (discordBridge != null) {
            discordBridge.stop();
        }
        if (liveNotifier != null) {
            liveNotifier.stop();
        }
        if (skinRestorerHeadHook != null) {
            skinRestorerHeadHook.stop();
        }
        if (bedrockSkinBridge != null) {
            bedrockSkinBridge.stop();
        }
        if (tabManager != null) {
            tabManager.stop();
        }
    }

    private void sendTttInvite(org.bukkit.entity.Player target, org.bukkit.entity.Player sender) {
        boolean bedrock = getBedrockDetector().isBedrock(target.getUniqueId());
        if (bedrock) {
            target.sendMessage(ChatColor.YELLOW + trf("ttt.invite-line1", "player", sender.getName()));
            target.sendMessage(ChatColor.YELLOW + trf("ttt.invite-line2", "player", sender.getName()));
            return;
        }
        Component accept = Component.text(tr("ui.ttt-accept-button")).color(net.kyori.adventure.text.format.NamedTextColor.GREEN)
            .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/ttt accept " + sender.getName()))
            .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(Component.text(tr("ui.ttt-accept-hover"))));
        Component decline = Component.text(tr("ui.ttt-decline-button")).color(net.kyori.adventure.text.format.NamedTextColor.RED)
            .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/ttt decline " + sender.getName()))
            .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(Component.text(tr("ui.ttt-decline-hover"))));
        Component message = Component.text(trf("ttt.invite-component", "player", sender.getName()))
            .color(net.kyori.adventure.text.format.NamedTextColor.YELLOW)
            .append(accept).append(Component.space()).append(decline);
        target.sendMessage(message);
    }

    private void handleTttState(TttManager.Game game) {
        if (game.isWin('X') || game.isWin('O')) {
            UUID winnerId = game.isWin('X') ? getPlayerIdBySymbol(game, 'X') : getPlayerIdBySymbol(game, 'O');
            if (winnerId != null) {
                org.bukkit.entity.Player winner = getServer().getPlayer(winnerId);
                org.bukkit.entity.Player loser = getServer().getPlayer(winnerId.equals(game.x) ? game.o : game.x);
                if (winner != null) {
                    winner.sendMessage(ChatColor.GREEN + tr("ttt.you-win"));
                }
                if (loser != null) {
                    loser.sendMessage(ChatColor.RED + tr("ttt.you-lose"));
                }
            }
            tttManager.endGame(game);
            return;
        }
        if (game.isDraw()) {
            org.bukkit.entity.Player x = getServer().getPlayer(game.x);
            org.bukkit.entity.Player o = getServer().getPlayer(game.o);
            if (x != null) {
                x.sendMessage(ChatColor.YELLOW + tr("ttt.draw"));
            }
            if (o != null) {
                o.sendMessage(ChatColor.YELLOW + tr("ttt.draw"));
            }
            tttManager.endGame(game);
            return;
        }
        sendTurnInfo(game);
        tttManager.sendBoard(game);
    }

    private UUID getPlayerIdBySymbol(TttManager.Game game, char symbol) {
        return symbol == 'X' ? game.x : game.o;
    }

    private void sendTurnInfo(TttManager.Game game) {
        org.bukkit.entity.Player x = getServer().getPlayer(game.x);
        org.bukkit.entity.Player o = getServer().getPlayer(game.o);
        if (x != null) {
            x.sendMessage(ChatColor.AQUA + trf(
                "ttt.turn-info",
                "symbol", "X",
                "turn", tr(game.isTurn(x.getUniqueId()) ? "ttt.turn-you" : "ttt.turn-opponent")
            ));
        }
        if (o != null) {
            o.sendMessage(ChatColor.AQUA + trf(
                "ttt.turn-info",
                "symbol", "O",
                "turn", tr(game.isTurn(o.getUniqueId()) ? "ttt.turn-you" : "ttt.turn-opponent")
            ));
        }
    }

    private void scheduleTttInviteExpiry(org.bukkit.entity.Player sender, org.bukkit.entity.Player target) {
        long delayTicks = Math.max(5L, tttInviteTtlMillis / 1000L) * 20L;
        getServer().getScheduler().runTaskLater(this, () -> {
            TttManager.Invite invite = tttManager.getInvite(target.getUniqueId());
            if (invite == null || !invite.sender.equals(sender.getUniqueId())) {
                return;
            }
            if (!invite.isExpired()) {
                return;
            }
            tttManager.removeInvite(target.getUniqueId());
            sender.sendMessage(ChatColor.YELLOW + trf("ttt.invite-expired-sender", "player", target.getName()));
            target.sendMessage(ChatColor.YELLOW + trf("ttt.invite-expired-target", "player", sender.getName()));
        }, delayTicks);
    }

    private BedrockSkinSettings loadBedrockSkinSettings() {
        FileConfiguration config = getConfig();
        boolean enabled = config.getBoolean("bedrock-skins.enabled", true);
        String mode = config.getString("bedrock-skins.update-mode", "interval");
        int interval = config.getInt("bedrock-skins.update-interval-seconds", 60);
        java.util.List<String> triggers = config.getStringList("bedrock-skins.command-triggers");
        boolean applyToPlayers = config.getBoolean("bedrock-skins.apply-to-players", true);
        boolean useForHeads = config.getBoolean("bedrock-skins.use-for-heads", true);
        return new BedrockSkinSettings(enabled, mode, interval, triggers, applyToPlayers, useForHeads);
    }

    private IntegrationSettings loadIntegrationSettings(String notificationFormat) {
        File file = new File(getDataFolder(), "integration.yml");
        if (!file.exists()) {
            saveResource("integration.yml", false);
        }
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = cfg.getConfigurationSection("integration");
        if (root == null) {
            return new IntegrationSettings(
                new TelegramSettings(false, "", "", 5, "", ""),
                new IntegrationSettings.DiscordSettings(false, "", "", "", 5, "", ""),
                new IntegrationSettings.NotificationSettings(false, 60, "", null, null, null, null)
            );
        }

        ConfigurationSection tg = root.getConfigurationSection("telegram");
        TelegramSettings telegram = new TelegramSettings(
            tg != null && tg.getBoolean("enabled", false),
            tg != null ? tg.getString("token", "") : "",
            tg != null ? tg.getString("chat-id", "") : "",
            tg != null ? tg.getInt("poll-interval-seconds", 5) : 5,
            tg != null ? tg.getString("format-to-telegram", "[{type}] {player}: {message}") : "",
            tg != null ? tg.getString("format-from-telegram", "&7[&dTG&7] &f{user}&7: &f{message}") : ""
        );

        ConfigurationSection dc = root.getConfigurationSection("discord");
        IntegrationSettings.DiscordSettings discord = new IntegrationSettings.DiscordSettings(
            dc != null && dc.getBoolean("enabled", false),
            dc != null ? dc.getString("webhook-url", "") : "",
            dc != null ? dc.getString("bot-token", "") : "",
            dc != null ? dc.getString("channel-id", "") : "",
            dc != null ? dc.getInt("poll-interval-seconds", 5) : 5,
            dc != null ? dc.getString("format-to-discord", "[{type}] {player}: {message}") : "",
            dc != null ? dc.getString("format-from-discord", "&7[&9DC&7] &f{user}&7: &f{message}") : ""
        );

        ConfigurationSection notif = root.getConfigurationSection("notifications");
        IntegrationSettings.NotificationSettings notifications = new IntegrationSettings.NotificationSettings(
            notif != null && notif.getBoolean("enabled", false),
            notif != null ? notif.getInt("poll-interval-seconds", 60) : 60,
            notificationFormat,
            loadPlatform(notif, "twitch"),
            loadPlatform(notif, "youtube"),
            loadPlatform(notif, "tiktok"),
            loadPlatform(notif, "vklive")
        );

        return new IntegrationSettings(telegram, discord, notifications);
    }

    private IntegrationSettings.PlatformSettings loadPlatform(ConfigurationSection root, String key) {
        if (root == null) {
            return null;
        }
        ConfigurationSection section = root.getConfigurationSection(key);
        if (section == null) {
            return null;
        }
        boolean enabled = section.getBoolean("enabled", false);
        String liveRegex = section.getString("live-regex", "");
        String titleRegex = section.getString("title-regex", "");
        java.util.List<IntegrationSettings.ChannelTarget> channels = new java.util.ArrayList<>();
        java.util.List<?> raw = section.getList("channels");
        if (raw != null) {
            for (Object item : raw) {
                if (item instanceof String str) {
                    String[] parts = str.split(":", 2);
                    String name = parts.length > 1 ? parts[0].trim() : str.trim();
                    String url = parts.length > 1 ? parts[1].trim() : str.trim();
                    channels.add(new IntegrationSettings.ChannelTarget(name, url, null, null));
                } else if (item instanceof java.util.Map<?, ?> map) {
                    Object nameObj = map.get("name");
                    Object urlObj = map.get("url");
                    String name = nameObj == null ? "" : nameObj.toString();
                    String url = urlObj == null ? "" : urlObj.toString();
                    String lr = map.get("live-regex") == null ? null : map.get("live-regex").toString();
                    String tr = map.get("title-regex") == null ? null : map.get("title-regex").toString();
                    channels.add(new IntegrationSettings.ChannelTarget(name, url, lr, tr));
                }
            }
        }
        return new IntegrationSettings.PlatformSettings(enabled, liveRegex, titleRegex, channels);
    }
}
