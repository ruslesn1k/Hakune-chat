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
import org.bukkit.scheduler.BukkitTask;

public final class TabManager {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
        .character('&')
        .hexColors()
        .build();

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
        }
    }

    private void updateHeaderFooter(Player viewer) {
        String header = joinLines(resolveList(viewer, settings.getHeader()));
        String footer = joinLines(resolveList(viewer, settings.getFooter()));
        Component headerComponent = LEGACY.deserialize(normalizeHex(header));
        Component footerComponent = LEGACY.deserialize(normalizeHex(footer));
        viewer.sendPlayerListHeaderAndFooter(headerComponent, footerComponent);
    }

    private void updatePlayerName(Player player) {
        String format = resolvePlaceholders(player, settings.getPlayerFormat());
        format = normalizeHex(format);
        Component headComponent = Component.empty();
        if (plugin.getSettings().isSkinRestorerHeads() && plugin.getSkinRestorerHeadHook() != null) {
            headComponent = plugin.getSkinRestorerHeadHook().getHeadComponent(player);
        }
        Component finalComponent = buildComponentWithHead(format, headComponent)
            .replaceText(builder -> builder.matchLiteral("{player}").replacement(Component.text(player.getName())));
        player.playerListName(finalComponent);
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
                String group = resolvePlaceholders(player, groupPlaceholder).toLowerCase(Locale.ROOT);
                int index = rule.groupOrder.indexOf(group);
                parts.add(index < 0 ? Integer.MAX_VALUE : index);
            } else if (rule.type == SortType.PLACEHOLDER_A_TO_Z) {
                String value = resolvePlaceholders(player, rule.placeholder).toLowerCase(Locale.ROOT);
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

    private String resolvePlaceholders(Player player, String text) {
        String result = plugin.getPlaceholderHook().apply(player, text);
        if (result == null) {
            return "";
        }
        return result
            .replace("{player}", player.getName())
            .replace("{world}", player.getWorld().getName());
    }

    private List<String> resolveList(Player player, List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return List.of("");
        }
        List<String> result = new ArrayList<>();
        for (String line : lines) {
            result.add(resolvePlaceholders(player, line));
        }
        return result;
    }

    private String joinLines(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return "";
        }
        return String.join("\n", lines);
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
