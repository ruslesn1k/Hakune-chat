package com.hakune.chat;

public final class ChatFormat {
    private final String local;
    private final String global;

    public ChatFormat(String local, String global) {
        this.local = local;
        this.global = global;
    }

    public String getLocal() {
        return local;
    }

    public String getGlobal() {
        return global;
    }
}
