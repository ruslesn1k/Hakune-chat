package com.hakune.chat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.scheduler.BukkitTask;

public final class TabManager {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
        .character('&')
        .hexColors()
        .build();
    private static final LegacyComponentSerializer SECTION = LegacyComponentSerializer.legacySection();
    private static final String NAME_TAG_TEAM_PREFIX = "hcnt";

    private final HakuneChatPlugin plugin;
    private TabSettings settings;
    private BukkitTask task;

    public TabManager(HakuneChatPlugin plugin, TabSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
    }

    public void updateSettings(TabSettings settings) {
        this.settings = settings;
    }

    public void start() {
        stop();
        if (!settings.isEnabled()) {
            return;
        }
        int interval = Math.max(1, settings.getUpdateIntervalSeconds());
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, interval * 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        if (!settings.isEnabled()) {
            return;
        }

        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        Comparator<Player> comparator = buildComparator(players);
        players.sort(comparator);

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            updateHeaderFooter(viewer);
        }

        for (Player player : players) {
            updatePlayerName(player);
            updateNameTag(player);
        }
    }

    private void updateHeaderFooter(Player viewer) {
        String header = joinLines(resolveList(viewer, settings.getHeader(), true));
        String footer = joinLines(resolveList(viewer, settings.getFooter(), true));
        Component headerComponent = LEGACY.deserialize(normalizeHex(header));
        Component footerComponent = LEGACY.deserialize(normalizeHex(footer));
        viewer.sendPlayerListHeaderAndFooter(headerComponent, footerComponent);
    }

    private void updatePlayerName(Player player) {
        String format = resolvePlaceholders(player, normalizePlayerTemplate(settings.getPlayerFormat()), false);
        format = normalizeHex(format);
        Component headComponent = Component.empty();
        if (plugin.getSettings().isSkinRestorerHeads() && plugin.getSkinRestorerHeadHook() != null) {
            headComponent = plugin.getSkinRestorerHeadHook().getHeadComponent(player);
        }
        Component finalComponent = buildComponentWithTokens(
            format,
            headComponent,
            plugin.getStyledNameComponent(player)
        );
        player.playerListName(finalComponent);
    }

    private void updateNameTag(Player player) {
        if (!settings.isNameTagEnabled()) {
            player.customName(null);
            player.setCustomNameVisible(false);
            clearNameTagTeams(player);
            return;
        }
        String format = resolvePlaceholders(player, normalizePlayerTemplate(settings.getNameTagFormat()), false);
        if (plugin.getHeadMessageManager() != null) {
            format = plugin.getHeadMessageManager().applyNameTagOverlay(player, format);
        }
        format = normalizeHex(format);
        Component component = buildComponentWithTokens(format, null, Component.text(player.getName()));
        player.customName(component);
        player.setCustomNameVisible(true);
        applyScoreboardNameTag(player, format);
    }

    public void refreshNameTag(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        updateNameTag(player);
    }

    private Comparator<Player> buildComparator(List<Player> players) {
        List<SortRule> rules = parseRules(settings.getSortingTypes());
        Map<Player, SortKey> cache = new ConcurrentHashMap<>();
        for (Player player : players) {
            cache.put(player, buildKey(player, rules));
        }
        return (a, b) -> {
            SortKey ka = cache.get(a);
            SortKey kb = cache.get(b);
            int cmp = ka.compareTo(kb);
            if (cmp != 0) {
                return cmp;
            }
            return a.getName().compareToIgnoreCase(b.getName());
        };
    }

    private SortKey buildKey(Player player, List<SortRule> rules) {
        List<Comparable<?>> parts = new ArrayList<>();
        String groupPlaceholder = settings.getGroupPlaceholder();
        for (SortRule rule : rules) {
            if (rule.type == SortType.GROUPS) {
                String group = resolvePlaceholders(player, groupPlaceholder, true).toLowerCase(Locale.ROOT);
                int index = rule.groupOrder.indexOf(group);
                parts.add(index < 0 ? Integer.MAX_VALUE : index);
            } else if (rule.type == SortType.PLACEHOLDER_A_TO_Z) {
                String value = resolvePlaceholders(player, rule.placeholder, true).toLowerCase(Locale.ROOT);
                parts.add(value);
            }
        }
        return new SortKey(parts);
    }

    private List<SortRule> parseRules(List<String> raw) {
        List<SortRule> rules = new ArrayList<>();
        if (raw == null || raw.isEmpty()) {
            return rules;
        }
        for (String entry : raw) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            String trimmed = entry.trim();
            if (trimmed.toUpperCase(Locale.ROOT).startsWith("GROUPS:")) {
                String data = trimmed.substring("GROUPS:".length());
                String[] parts = data.split(",");
                List<String> order = new ArrayList<>();
                for (String part : parts) {
                    String name = part.trim().toLowerCase(Locale.ROOT);
                    if (!name.isEmpty()) {
                        order.add(name);
                    }
                }
                rules.add(SortRule.groups(order));
                continue;
            }
            if (trimmed.toUpperCase(Locale.ROOT).startsWith("PLACEHOLDER_A_TO_Z:")) {
                String placeholder = trimmed.substring("PLACEHOLDER_A_TO_Z:".length()).trim();
                if (placeholder.isEmpty()) {
                    placeholder = "%player%";
                }
                rules.add(SortRule.placeholder(placeholder));
            }
        }
        return rules;
    }

    private String resolvePlaceholders(Player player, String text, boolean replacePlayer) {
        String result = plugin.getPlaceholderHook().apply(player, text);
        if (result == null) {
            return "";
        }
        if (replacePlayer) {
            result = result.replace("{player}", player.getName());
        }
        return result
            .replace("{world}", player.getWorld().getName())
            .replace("{voice}", plugin.getVoiceDetector().getVoiceIndicator(player));
    }

    private List<String> resolveList(Player player, List<String> lines, boolean replacePlayer) {
        if (lines == null || lines.isEmpty()) {
            return List.of("");
        }
        List<String> result = new ArrayList<>();
        for (String line : lines) {
            result.add(resolvePlaceholders(player, line, replacePlayer));
        }
        return result;
    }

    private String joinLines(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return "";
        }
        return String.join("\n", lines);
    }

    private static String normalizePlayerTemplate(String template) {
        if (template == null || template.isEmpty()) {
            return "";
        }
        return template
            .replace("%player_name%", "{player}")
            .replace("%player%", "{player}");
    }

    private void applyScoreboardNameTag(Player subject, String format) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            Scoreboard board = viewer.getScoreboard();
            if (board == null) {
                continue;
            }
            Team team = board.getTeam(nameTagTeamName(subject));
            if (team == null) {
                team = board.registerNewTeam(nameTagTeamName(subject));
            }
            String[] parts = splitByPlayerToken(format);
            String prefix = toSectionLegacy(parts[0]);
            String suffix = toSectionLegacy(parts[1]);
            setTeamText(team, prefix, suffix);
            if (!team.hasEntry(subject.getName())) {
                team.addEntry(subject.getName());
            }
        }
    }

    private void clearNameTagTeams(Player subject) {
        String teamName = nameTagTeamName(subject);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            Scoreboard board = viewer.getScoreboard();
            if (board == null) {
                continue;
            }
            Team team = board.getTeam(teamName);
            if (team == null) {
                continue;
            }
            team.removeEntry(subject.getName());
            if (team.getEntries().isEmpty()) {
                team.unregister();
            }
        }
    }

    private static String[] splitByPlayerToken(String format) {
        if (format == null || format.isEmpty()) {
            return new String[] {"", ""};
        }
        int idx = format.indexOf("{player}");
        if (idx < 0) {
            return new String[] {format, ""};
        }
        String prefix = format.substring(0, idx);
        String suffix = format.substring(idx + "{player}".length());
        return new String[] {prefix, suffix};
    }

    private static String toSectionLegacy(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return SECTION.serialize(LEGACY.deserialize(text));
    }

    private static void setTeamText(Team team, String prefix, String suffix) {
        try {
            team.setPrefix(prefix);
        } catch (IllegalArgumentException ex) {
            team.setPrefix(prefix.length() > 64 ? prefix.substring(0, 64) : "");
        }
        try {
            team.setSuffix(suffix);
        } catch (IllegalArgumentException ex) {
            team.setSuffix(suffix.length() > 64 ? suffix.substring(0, 64) : "");
        }
    }

    private static String nameTagTeamName(Player player) {
        String compact = player.getUniqueId().toString().replace("-", "");
        return NAME_TAG_TEAM_PREFIX + compact.substring(0, 12);
    }

    private static String normalizeHex(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return text.replaceAll("(?i)(?<!&)#([0-9a-f]{6})", "&#$1");
    }

    private static Component buildComponentWithHead(String formatted, Component headComponent) {
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

    private static Component buildComponentWithTokens(
        String formatted,
        Component headComponent,
        Component playerComponent
    ) {
        if (formatted == null) {
            return Component.empty();
        }
        if (!formatted.contains("{head}") && !formatted.contains("{player}")) {
            return LEGACY.deserialize(formatted);
        }
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(\\{head\\}|\\{player\\})");
        java.util.regex.Matcher matcher = pattern.matcher(formatted);
        int last = 0;
        Component result = Component.empty();
        while (matcher.find()) {
            if (matcher.start() > last) {
                String part = formatted.substring(last, matcher.start());
                if (!part.isEmpty()) {
                    result = result.append(LEGACY.deserialize(part));
                }
            }
            String token = matcher.group(1);
            if ("{head}".equals(token) && headComponent != null) {
                result = result.append(headComponent);
            } else if ("{player}".equals(token) && playerComponent != null) {
                result = result.append(playerComponent);
            }
            last = matcher.end();
        }
        if (last < formatted.length()) {
            String tail = formatted.substring(last);
            if (!tail.isEmpty()) {
                result = result.append(LEGACY.deserialize(tail));
            }
        }
        return result;
    }

    private enum SortType {
        GROUPS,
        PLACEHOLDER_A_TO_Z
    }

    private static final class SortRule {
        private final SortType type;
        private final List<String> groupOrder;
        private final String placeholder;

        private SortRule(SortType type, List<String> groupOrder, String placeholder) {
            this.type = type;
            this.groupOrder = groupOrder;
            this.placeholder = placeholder;
        }

        private static SortRule groups(List<String> groupOrder) {
            return new SortRule(SortType.GROUPS, groupOrder, null);
        }

        private static SortRule placeholder(String placeholder) {
            return new SortRule(SortType.PLACEHOLDER_A_TO_Z, null, placeholder);
        }
    }

    private static final class SortKey implements Comparable<SortKey> {
        private final List<Comparable<?>> parts;

        private SortKey(List<Comparable<?>> parts) {
            this.parts = parts;
        }

        @Override
        public int compareTo(SortKey other) {
            int len = Math.min(this.parts.size(), other.parts.size());
            for (int i = 0; i < len; i++) {
                Comparable a = this.parts.get(i);
                Comparable b = other.parts.get(i);
                int cmp = a.compareTo(b);
                if (cmp != 0) {
                    return cmp;
                }
            }
            return Integer.compare(this.parts.size(), other.parts.size());
        }
    }
}
