package com.mauccio.bctwl.files;

import com.mauccio.bctwl.CTWLobby;
import org.bukkit.configuration.file.*;

public class ConfigManager {
    private final CTWLobby ctwLobby;
    private FileConfiguration config;

    public ConfigManager(CTWLobby ctwLobby) {
        this.ctwLobby = ctwLobby;
        this.config = ctwLobby.getConfig();
        ctwLobby.saveDefaultConfig();
        this.load(false);
    }

    public void load() {
        this.load(true);
    }

    public void persists() {
        this.ctwLobby.saveConfig();
    }

    private void load(boolean reload) {
        if (reload) {
            this.ctwLobby.reloadConfig();
        }
        this.validateSignText(this.getSignFirstLine(), "signs.first-line-text", "ctw");
    }

    private void validateSignText(String text, String key, String defaultValue) {
        if (text.isEmpty() || text.length() > 16) {
            this.ctwLobby.getLogger().warning("Config value \"".concat(key).concat("\" is incorrect."));
            this.config.set(key, (Object)defaultValue);
            this.ctwLobby.getLogger().info("Config value \"".concat(key).concat("\" has been changed to \"").concat(defaultValue).concat("\"."));
        }
    }

    public String getSignFirstLine() {
        return this.config.getString("signs.first-line-text");
    }

    public boolean isKitSQL() {
        return this.ctwLobby.getConfig().getBoolean("use-sql-for-kits", false);
    }

    public boolean isSoundsEnabled() {
        return this.ctwLobby.getConfig().getBoolean("sounds.enabled", true);
    }

    public boolean isLobbyGuardEnabled() {
        return this.ctwLobby.getConfig().getBoolean("lobby.guard", false);
    }

    public boolean isLobbyItemsEnabled() {
        return this.ctwLobby.getConfig().getBoolean("lobby.items", true);
    }

    public boolean isLobbyBoardEnabled() {
        return this.ctwLobby.getConfig().getBoolean("lobby.scoreboard", true);
    }
}