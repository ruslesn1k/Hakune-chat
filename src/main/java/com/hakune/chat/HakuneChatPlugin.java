package com.hakune.chat;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;

public final class HakuneChatPlugin extends JavaPlugin {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
        .character('&')
        .hexColors()
        .build();

    private ChatSettings settings;
    private BedrockDetector bedrockDetector;
    private PlaceholderHook placeholderHook;
    private TelegramBridge telegramBridge;
    private SkinRestorerHeadHook skinRestorerHeadHook;
    private TabManager tabManager;
    private TabSettings tabSettings;
    private BedrockSkinBridge bedrockSkinBridge;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("formatting.yml", false);
        reloadSettings();

        this.bedrockDetector = new BedrockDetector();
        this.placeholderHook = new PlaceholderHook(getServer().getPluginManager());
        this.skinRestorerHeadHook = new SkinRestorerHeadHook(this, getServer().getPluginManager());
        this.bedrockSkinBridge = new BedrockSkinBridge(this, loadBedrockSkinSettings(), this.bedrockDetector);

        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        getServer().getPluginManager().registerEvents(new JoinListener(this), this);
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

        // Tab manager is created in reloadSettings() to avoid double scheduling.
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

    public void reloadSettings() {
        reloadConfig();
        FileConfiguration config = getConfig();

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

        TelegramSettings telegramSettings = loadTelegramSettings(config);

        boolean separateBedrockFormat = config.getBoolean("features.separate-bedrock-format", true);
        boolean skinRestorerHeads = config.getBoolean("features.skinrestorer-heads", true);
        int skinRestorerRefreshSeconds = config.getInt("features.skinrestorer-refresh-seconds", 60);
        String skinRestorerUpdateMode = config.getString("features.skinrestorer-update-mode", "interval");
        java.util.List<String> skinRestorerCommandTriggers =
            config.getStringList("features.skinrestorer-command-triggers");
        boolean joinMessageEnabled = config.getBoolean("features.join-message-enabled", true);
        boolean quitMessageEnabled = config.getBoolean("features.quit-message-enabled", true);

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
            loadBedrockSkinSettings()
        );

        if (this.telegramBridge != null) {
            this.telegramBridge.stop();
        }
        this.telegramBridge = new TelegramBridge(this, telegramSettings);
        this.telegramBridge.start();

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
        return new TabSettings(enabled, interval, header, footer, playerFormat, groupPlaceholder, sorting);
    }

    private static java.util.List<String> readLines(FileConfiguration config, String path) {
        if (config.isList(path)) {
            java.util.List<String> list = config.getStringList(path);
            return list.isEmpty() ? java.util.List.of("") : list;
        }
        String value = config.getString(path, "");
        return java.util.List.of(value == null ? "" : value);
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

    public void broadcastTelegram(String legacyText) {
        Component component = LEGACY.deserialize(legacyText);
        Bukkit.getScheduler().runTask(this, () -> {
            Bukkit.getOnlinePlayers().forEach(player -> player.sendMessage(component));
            Bukkit.getConsoleSender().sendMessage(component);
        });
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase();
        if (name.equals("chatreload")) {
            if (!sender.hasPermission("hakunechat.reload")) {
                sender.sendMessage(ChatColor.RED + "No permission.");
                return true;
            }
            reloadSettings();
            sender.sendMessage(ChatColor.GREEN + "HakuneChat config reloaded.");
            return true;
        }

        if (name.equals("hakunechat") || name.equals("hchat")) {
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                if (!sender.hasPermission("hakunechat.reload")) {
                    sender.sendMessage(ChatColor.RED + "No permission.");
                    return true;
                }
                reloadSettings();
                sender.sendMessage(ChatColor.GREEN + "HakuneChat config reloaded.");
                return true;
            }
            sender.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " reload");
            return true;
        }

        return false;
    }

    @Override
    public void onDisable() {
        if (telegramBridge != null) {
            telegramBridge.stop();
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
}
