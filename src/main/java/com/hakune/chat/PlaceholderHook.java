package com.hakune.chat;

import java.lang.reflect.Method;

import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;

public final class PlaceholderHook {
    private final boolean available;
    private final Method setPlaceholdersMethod;

    public PlaceholderHook(PluginManager pluginManager) {
        Method method = null;
        boolean ok = false;
        if (pluginManager.isPluginEnabled("PlaceholderAPI")) {
            try {
                Class<?> apiClass = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                method = apiClass.getMethod("setPlaceholders", Player.class, String.class);
                ok = true;
            } catch (ClassNotFoundException | NoSuchMethodException ignored) {
                ok = false;
            }
        }
        this.available = ok;
        this.setPlaceholdersMethod = method;
    }

    public String apply(Player player, String text) {
        if (!available || text == null) {
            return text;
        }
        try {
            Object result = setPlaceholdersMethod.invoke(null, player, text);
            return result instanceof String ? (String) result : text;
        } catch (Exception ignored) {
            return text;
        }
    }
}
