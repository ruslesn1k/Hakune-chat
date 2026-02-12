package com.hakune.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public final class HeadMessageManager {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
        .character('&')
        .hexColors()
        .build();
    private static final Pattern HEX_PATTERN = Pattern.compile("(?i)(?<!&)#([0-9a-f]{6})");

    private enum RenderMode {
        ARMOR_STAND,
        NAME_TAG
    }

    private enum ArmorStandFollowMode {
        PASSENGER,
        TELEPORT
    }

    private final HakuneChatPlugin plugin;
    private final Map<UUID, ActiveMessage> activeMessages = new ConcurrentHashMap<>();

    private boolean enabled;
    private String format;
    private int durationTicks;
    private double yOffset;
    private RenderMode renderMode;
    private ArmorStandFollowMode armorStandFollowMode;
    private String nameTagFormat;

    public HeadMessageManager(HakuneChatPlugin plugin) {
        this.plugin = plugin;
        this.enabled = true;
        this.format = "&f{player}&7: &f{message}";
        this.durationTicks = 60;
        this.yOffset = 2.2;
        this.renderMode = RenderMode.NAME_TAG;
        this.armorStandFollowMode = ArmorStandFollowMode.PASSENGER;
        this.nameTagFormat = "{base} &8| &f{message}";
    }

    public void configure(
        boolean enabled,
        String format,
        int durationTicks,
        double yOffset,
        String renderMode,
        String armorStandFollowMode,
        String nameTagFormat
    ) {
        this.enabled = enabled;
        this.format = (format == null || format.isBlank()) ? "&f{player}&7: &f{message}" : format;
        this.durationTicks = Math.max(20, durationTicks);
        this.yOffset = Math.max(1.5, yOffset);
        this.renderMode = "armorstand".equalsIgnoreCase(renderMode) ? RenderMode.ARMOR_STAND : RenderMode.NAME_TAG;
        this.armorStandFollowMode = "teleport".equalsIgnoreCase(armorStandFollowMode)
            ? ArmorStandFollowMode.TELEPORT
            : ArmorStandFollowMode.PASSENGER;
        this.nameTagFormat = (nameTagFormat == null || nameTagFormat.isBlank())
            ? "{base} &8| &f{message}"
            : nameTagFormat;
    }

    public void show(Player sender, String message) {
        if (!enabled || sender == null || !sender.isOnline()) {
            return;
        }
        if (!plugin.isHeadMessageEnabledFor(sender.getUniqueId())) {
            return;
        }
        clear(sender.getUniqueId());

        String template = normalizePlayerTemplate(format);
        String rendered = plugin.getPlaceholderHook().apply(sender, template);
        rendered = normalizeHex(rendered)
            .replace("{player}", sender.getName())
            .replace("{message}", message == null ? "" : message)
            .replace("{voice}", plugin.getVoiceDetector().getVoiceIndicator(sender))
            .replace("{world}", sender.getWorld().getName());

        if (renderMode == RenderMode.ARMOR_STAND) {
            spawnArmorStandMessage(sender, rendered);
            return;
        }

        String[] lines = rendered.split("\\R");
        String primaryLine = lines.length == 0 ? rendered : lines[0];
        boolean multiline = lines.length > 1;
        List<ArmorStand> extraLines = spawnNametagExtraLines(sender, lines);
        BukkitTask followTask = null;
        if (!extraLines.isEmpty()) {
            followTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
                if (!sender.isOnline()) {
                    clear(sender.getUniqueId());
                    return;
                }
                teleportExtraLines(sender, extraLines);
            }, 0L, 1L);
        }
        BukkitTask expireTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            clear(sender.getUniqueId());
            plugin.refreshPlayerNameTag(sender);
        }, durationTicks);
        activeMessages.put(sender.getUniqueId(), new ActiveMessage(primaryLine, multiline, null, extraLines, followTask, expireTask));
        plugin.refreshPlayerNameTag(sender);
    }

    public String applyNameTagOverlay(Player player, String baseFormat) {
        if (!enabled || renderMode != RenderMode.NAME_TAG || player == null) {
            return baseFormat;
        }
        ActiveMessage active = activeMessages.get(player.getUniqueId());
        if (active == null || active.message == null || active.message.isBlank()) {
            return baseFormat;
        }
        if (active.multiline) {
            return active.message;
        }
        return normalizePlayerTemplate(nameTagFormat)
            .replace("{base}", baseFormat == null ? "" : baseFormat)
            .replace("{player}", player.getName())
            .replace("{message}", active.message)
            .replace("{voice}", plugin.getVoiceDetector().getVoiceIndicator(player))
            .replace("{world}", player.getWorld().getName());
    }

    public void clear(UUID playerId) {
        ActiveMessage active = activeMessages.remove(playerId);
        if (active == null) {
            return;
        }
        if (active.followTask != null) {
            active.followTask.cancel();
        }
        if (active.expireTask != null) {
            active.expireTask.cancel();
        }
        if (active.stand != null && active.stand.isValid()) {
            active.stand.remove();
        }
        if (active.extraStands != null) {
            for (ArmorStand stand : active.extraStands) {
                if (stand != null && stand.isValid()) {
                    stand.remove();
                }
            }
        }
    }

    public void clearAll() {
        for (UUID uuid : activeMessages.keySet()) {
            clear(uuid);
        }
    }

    private void spawnArmorStandMessage(Player sender, String rendered) {
        String[] lines = rendered.split("\\R");
        List<String> cleanLines = new ArrayList<>();
        for (String line : lines) {
            if (line != null && !line.isBlank()) {
                cleanLines.add(line);
            }
        }
        if (cleanLines.isEmpty()) {
            return;
        }

        List<ArmorStand> stands = new ArrayList<>();
        int total = cleanLines.size();
        for (int i = 0; i < total; i++) {
            String line = cleanLines.get(i);
            int offsetIndex = (total - 1) - i;
            Location location = sender.getLocation().add(0.0, yOffset + (offsetIndex * 0.24), 0.0);
            stands.add(spawnLine(sender, line, location));
        }

        ArmorStand bottom = stands.get(total - 1);
        if (armorStandFollowMode == ArmorStandFollowMode.PASSENGER) {
            sender.addPassenger(bottom);
            ArmorStand vehicle = bottom;
            for (int i = total - 2; i >= 0; i--) {
                ArmorStand higher = stands.get(i);
                vehicle.addPassenger(higher);
                vehicle = higher;
            }
            BukkitTask expireTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                clear(sender.getUniqueId());
                plugin.refreshPlayerNameTag(sender);
            }, durationTicks);
            activeMessages.put(sender.getUniqueId(), new ActiveMessage(rendered, total > 1, bottom, stands, null, expireTask));
            return;
        }

        long[] elapsed = new long[] {0L};
        BukkitTask followTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!sender.isOnline()) {
                clear(sender.getUniqueId());
                return;
            }
            elapsed[0] += 1L;
            if (elapsed[0] >= durationTicks) {
                clear(sender.getUniqueId());
                return;
            }
            int currentTotal = stands.size();
            for (int i = 0; i < currentTotal; i++) {
                ArmorStand stand = stands.get(i);
                if (stand == null || !stand.isValid()) {
                    continue;
                }
                int offsetIndex = (currentTotal - 1) - i;
                Location next = sender.getLocation().add(0.0, yOffset + (offsetIndex * 0.24), 0.0);
                stand.teleport(next);
            }
        }, 0L, 1L);
        activeMessages.put(sender.getUniqueId(), new ActiveMessage(rendered, total > 1, bottom, stands, followTask, null));
    }

    private ArmorStand spawnLine(Player sender, String renderedLine, Location location) {
        Component text = LEGACY.deserialize(renderedLine);
        return sender.getWorld().spawn(location, ArmorStand.class, as -> {
            // Marker stands have zero height; passenger stacking collapses all lines to one level.
            as.setMarker(false);
            as.setInvisible(true);
            as.setInvulnerable(true);
            as.setGravity(false);
            as.setSmall(true);
            as.setSilent(true);
            as.setBasePlate(false);
            as.setArms(false);
            as.setCollidable(false);
            as.setCustomNameVisible(true);
            as.customName(text);
            as.setPersistent(false);
        });
    }

    private static String normalizeHex(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return HEX_PATTERN.matcher(text).replaceAll("&#$1");
    }

    private static String normalizePlayerTemplate(String template) {
        if (template == null || template.isEmpty()) {
            return "";
        }
        return template
            .replace("%player_name%", "{player}")
            .replace("%player%", "{player}");
    }

    private List<ArmorStand> spawnNametagExtraLines(Player sender, String[] lines) {
        if (lines == null || lines.length <= 1) {
            return List.of();
        }
        List<ArmorStand> stands = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line == null || line.isBlank()) {
                continue;
            }
            int offsetIndex = i - 1;
            Location location = sender.getLocation().add(0.0, yOffset - 0.25 - (offsetIndex * 0.24), 0.0);
            Component text = LEGACY.deserialize(line);
            ArmorStand stand = sender.getWorld().spawn(location, ArmorStand.class, as -> {
                as.setMarker(true);
                as.setInvisible(true);
                as.setInvulnerable(true);
                as.setGravity(false);
                as.setSmall(true);
                as.setSilent(true);
                as.setCustomNameVisible(true);
                as.customName(text);
                as.setPersistent(false);
            });
            stands.add(stand);
        }
        return stands;
    }

    private void teleportExtraLines(Player sender, List<ArmorStand> stands) {
        for (int i = 0; i < stands.size(); i++) {
            ArmorStand stand = stands.get(i);
            if (stand == null || !stand.isValid()) {
                continue;
            }
            Location next = sender.getLocation().add(0.0, yOffset - 0.25 - (i * 0.24), 0.0);
            stand.teleport(next);
        }
    }

    private record ActiveMessage(String message, boolean multiline, ArmorStand stand, List<ArmorStand> extraStands, BukkitTask followTask, BukkitTask expireTask) {
    }
}
