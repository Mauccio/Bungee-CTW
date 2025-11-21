package com.mauccio.bctwl.files;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.mauccio.bctwl.CTWLobby;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

public class LangManager {

    private final CTWLobby ctwLobby;
    private final YamlConfiguration lang;
    private final String messagePrefix;

    public LangManager(CTWLobby plugin) {
        this.ctwLobby = plugin;
        lang = new YamlConfiguration();
        File langFile = new File(plugin.getDataFolder(), "messages.yml");

        if (!langFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }

        try {
            lang.load(langFile);
        } catch (IOException | InvalidConfigurationException ex) {
            plugin.getLogger().severe("Error loading messages.yml: " + ex.getMessage());
        }

        messagePrefix = ChatColor.translateAlternateColorCodes('&',
                lang.getString("message-prefix"));
    }

    private String translateUnicode(String input) {
        if (input == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < input.length();) {
            char c = input.charAt(i);
            if (c == '\\' && i + 5 < input.length() && input.charAt(i + 1) == 'u') {
                String hex = input.substring(i + 2, i + 6);
                try {
                    int code = Integer.parseInt(hex, 16);
                    sb.append((char) code);
                    i += 6;
                    continue;
                } catch (NumberFormatException ignored) {}
            }
            sb.append(c);
            i++;
        }
        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }


    public String getChar(String path) {
        String raw = lang.getString(path);
        return translateUnicode(raw);
    }

    public List<String> getStringList(String path) {
        List<String> list = lang.getStringList(path);
        List<String> colored = new ArrayList<>();
        for (String line : list) {
            colored.add(translateUnicode(line));
        }
        return colored;
    }

    public String getText(String label) {
        String text = lang.getString(label);
        if (text == null) {
            text = label;
        } else {
            text = ChatColor.translateAlternateColorCodes('&', text);
        }
        return text;
    }

    public String getMessage(String label) {
        return messagePrefix + " " + getText(label);
    }

    public void sendMessage(String label, Player player) {
        player.sendMessage(getMessage(label));
    }

    public void sendMessage(String label, CommandSender cs) {
        cs.sendMessage(getMessage(label));
    }

    public String getMessagePrefix() {
        return messagePrefix;
    }

    public void sendText(String baseLabel, Player player) {
        if (lang.getString(baseLabel) == null) {
            sendMessage(baseLabel, player);
            return;
        }
        for (String label : lang.getConfigurationSection(baseLabel).getKeys(false)) {
            sendMessage(baseLabel + "." + label, player);
        }
    }
    
    public String getTitleMessage(String label) {
        return this.getText(label);
    }

    public void sendVerbatimTextToWorld(String text, World world, Player filter) {
        for (Player receiver : world.getPlayers()) {
            if (filter != null && receiver.getName().equals(filter.getName())) {
                continue;
            }
            receiver.sendMessage(messagePrefix + " " + text);
        }
    }
}
