package com.hakune.chat;

import java.lang.reflect.Method;
import java.util.UUID;

public final class BedrockDetector {
    private final boolean available;
    private final Method getInstanceMethod;
    private final Method isFloodgatePlayerMethod;

    public BedrockDetector() {
        Method getInstance = null;
        Method isFloodgatePlayer = null;
        boolean ok = false;
        try {
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            getInstance = apiClass.getMethod("getInstance");
            isFloodgatePlayer = apiClass.getMethod("isFloodgatePlayer", UUID.class);
            ok = true;
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            ok = false;
        }
        this.available = ok;
        this.getInstanceMethod = getInstance;
        this.isFloodgatePlayerMethod = isFloodgatePlayer;
    }

    public boolean isBedrock(UUID uuid) {
        if (!available) {
            return false;
        }
        try {
            Object api = getInstanceMethod.invoke(null);
            Object result = isFloodgatePlayerMethod.invoke(api, uuid);
            return result instanceof Boolean && (Boolean) result;
        } catch (Exception ignored) {
            return false;
        }
    }
}
