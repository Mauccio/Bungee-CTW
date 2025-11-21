package com.mauccio.bctwl.commands;

import com.mauccio.bctwl.CTWLobby;
import com.mauccio.bctwl.utils.PlayerStats;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class CommandManager implements CommandExecutor {

    private final CTWLobby ctwLobby;
    private final TreeSet<String> allowedInGameCmds;

    public CommandManager(CTWLobby ctwLobby) {
        this.ctwLobby = ctwLobby;
        this.allowedInGameCmds = new TreeSet<>();
        register();
    }

    private void register() {
        ctwLobby.saveResource("plugin.yml", true);
        File file = new File(ctwLobby.getDataFolder(), "plugin.yml");
        YamlConfiguration pluginYml = new YamlConfiguration();
        try {
            pluginYml.load(file);
        } catch (IOException | InvalidConfigurationException ex) {
            ctwLobby.getLogger().severe(ex.toString());
            ctwLobby.getPluginLoader().disablePlugin(ctwLobby);
            return;
        } finally {
            if (file.exists()) file.delete();
        }

        if (pluginYml.getConfigurationSection("commands") != null) {
            for (String commandName : pluginYml.getConfigurationSection("commands").getKeys(false)) {
                PluginCommand pc = ctwLobby.getCommand(commandName);
                if (pc != null) pc.setExecutor(this);
                allowedInGameCmds.add(commandName);
            }
        }
    }

    private Player getPlayerOrNotify(CommandSender cs) {
        if (cs instanceof Player) return (Player) cs;
        ctwLobby.getLogger().info("Este comando es de jugador.");
        return null;
    }

    private void openRoomsGUI(Player player) {
        ctwLobby.getBungeeCommunicator().requestServerList(player);
        ctwLobby.getSoundManager().playGuiSound(player);
    }


    @Override
    public boolean onCommand(CommandSender cs, Command cmnd, String label, String[] args) {
        switch (cmnd.getName().toLowerCase()) {
            case "ctw":          return handleCtw(cs, args);
            case "stats":        return handleStats(cs, args);
            case "rooms":        return handleRooms(cs, args);
            case "createworld":
            case "alert":        return handleAlert(cs, args);
            case "setlobby":     return handleSetLobby(cs, args);
            default:             return true;
        }
    }

    private boolean handleCtw(CommandSender cs, String[] args) {
        Player player = getPlayerOrNotify(cs);
        if (args.length != 1) {
            if (player != null) {
                ctwLobby.getLangManager().sendText("commands.ctw", player);
                ctwLobby.getSoundManager().playTipSound(player);
            } else {
                ctwLobby.getLogger().info("Uso: /ctw [reload|save|mapcycle]");
            }
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "reload":
                ctwLobby.reload();
                ctwLobby.getLangManager().sendMessage("cmd-success", cs);
                return true;
            case "save":
                ctwLobby.save();
                ctwLobby.getLangManager().sendMessage("cmd-success", cs);
                return true;
            default:
                if (player != null) {
                    ctwLobby.getLangManager().sendText("commands.ctw", player);
                    ctwLobby.getSoundManager().playTipSound(player);
                }
                return true;
        }
    }

    private boolean handleStats(CommandSender cs, String[] args) {
        Player player = getPlayerOrNotify(cs);
        if (player == null) return true;
        try {
            if (ctwLobby.getDBManager() == null) {
                ctwLobby.getLangManager().sendMessage("stats-not-enabled", player);
                ctwLobby.getSoundManager().playErrorSound(player);
                return true;
            }
            PlayerStats cached = ctwLobby.getDBManager().getPlayerStats(player.getName());
            enviarStats(player, cached);
            ctwLobby.getSoundManager().playYourStatsSound(player);
        } catch (Exception e) {
            ctwLobby.getLangManager().sendMessage("stats-not-enabled", player);
            ctwLobby.getSoundManager().playErrorSound(player);
        }
        return true;
    }

    private boolean handleSetLobby(CommandSender cs, String[] args) {
        Player player = getPlayerOrNotify(cs);
        if (player == null) return true;
        ctwLobby.getLobbyManager().setLobbyLoc(player.getLocation());
        ctwLobby.getLobbyManager().loadLobbyLoc();
        return true;
    }

    private void enviarStats(Player player, PlayerStats stats) {
        player.sendMessage(ctwLobby.getConfig().getString("message-decorator"));
        player.sendMessage(ctwLobby.getLangManager().getText("stats.title"));
        player.sendMessage(ctwLobby.getLangManager().getText("stats.points").replace("%SCORE%", String.valueOf(stats.score)));
        player.sendMessage(ctwLobby.getLangManager().getText("stats.kills").replace("%KILLS%", String.valueOf(stats.kills)));
        player.sendMessage(ctwLobby.getLangManager().getText("stats.deaths").replace("%DEATHS%", String.valueOf(stats.deaths)));
        player.sendMessage(ctwLobby.getLangManager().getText("stats.placed-wools").replace("%WOOLS_PLACED%", String.valueOf(stats.wools)));
        if(ctwLobby.getEconomy() != null) {
            player.sendMessage(ctwLobby.getLangManager().getText("stats.coins").replace("%COINS%", String.valueOf(ctwLobby.getEconomy().getBalance(player))));
        }
        player.sendMessage(ctwLobby.getConfig().getString("message-decorator"));
    }

    private boolean handleRooms(CommandSender cs, String[] args) {
        Player player = getPlayerOrNotify(cs);
        if (player == null) return true;
        if(!ctwLobby.getLobbyManager().isOnLobby(player)) {
            ctwLobby.getLangManager().sendMessage("", player);
            return true;
        }
        openRoomsGUI(player);
        return true;
    }

    private boolean handleAlert(CommandSender cs, String[] args) {
        if (args.length == 0) {
            if (cs instanceof Player) {
                Player player = (Player) cs;
                ctwLobby.getLangManager().sendText("commands.alert", player);
                ctwLobby.getSoundManager().playTipSound(player);
            } else {
                ctwLobby.getLogger().info("Uso: /alert [mensaje]");
            }
            return true;
        }
        String message = String.join(" ", args) + " ";
        for (Player receiver : Bukkit.getOnlinePlayers()) {
            receiver.sendMessage(ctwLobby.getLangManager().getText("alert-prefix") + " " + message);
            ctwLobby.getSoundManager().playAlertSound(receiver);
        }
        return true;
    }
}