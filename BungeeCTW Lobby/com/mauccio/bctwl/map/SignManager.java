package com.mauccio.bctwl.map;

import java.io.File;
import java.io.IOException;
import java.util.TreeMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.mauccio.bctwl.CTWLobby;
import com.mauccio.bctwl.bungee.ServerStatus;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Sign;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public class SignManager {

    private final CTWLobby plugin;
    private final YamlConfiguration signConfig;
    private final File signFile;
    private final TreeMap<String, Location> signs;
    private final Lock _signs_mutex;
    private String joinSignText;

    public SignManager(CTWLobby plugin) {
        this.plugin = plugin;
        signFile = new File(plugin.getDataFolder(), "signs.yml");
        signConfig = new YamlConfiguration();
        signs = new TreeMap<>();
        _signs_mutex = new ReentrantLock(true);
        load();
    }

    public void load() {
        if (signFile.exists()) {
            try {
                signConfig.load(signFile);
            } catch (IOException | InvalidConfigurationException ex) {
                plugin.getLogger().severe(ex.toString());
            }
        }
        for (String serverName : signConfig.getKeys(false)) {
            String worldName = signConfig.getString(serverName + ".world");
            if (worldName == null) continue;

            World world = Bukkit.getWorld(worldName);
            if (world == null) continue;

            Location loc = new Location(world,
                    signConfig.getInt(serverName + ".x"),
                    signConfig.getInt(serverName + ".y"),
                    signConfig.getInt(serverName + ".z"));

            if (loc.getBlock().getType() == Material.WALL_SIGN
                    || loc.getBlock().getType() == Material.SIGN_POST) {
                _signs_mutex.lock();
                try {
                    signs.put(serverName, loc);
                } finally {
                    _signs_mutex.unlock();
                }
                updateSign(loc);
            }
        }
        joinSignText = plugin.getLangManager().getText("sign-format.1");
    }

    public void persists() {
        _signs_mutex.lock();
        try {
            for (String roomName : signs.keySet()) {
                Location loc = signs.get(roomName);
                signConfig.set(roomName + ".x", loc.getBlockX());
                signConfig.set(roomName + ".y", loc.getBlockY());
                signConfig.set(roomName + ".z", loc.getBlockZ());
                signConfig.set(roomName + ".world", loc.getWorld().getName());
            }
        } finally {
            _signs_mutex.unlock();
        }
        try {
            signConfig.save(signFile);
        } catch (IOException ex) {
            plugin.getLogger().severe(ex.toString());
        }
    }

    public void updateSigns(String roomName) {
        Location loc = signs.get(roomName);
        if (loc != null) {
            updateSign(loc);
        }
    }

    private void updateSign(Location loc) {
        if (loc.getBlock().getType() == Material.WALL_SIGN
                || loc.getBlock().getType() == Material.SIGN_POST) {
            updateSign((Sign) loc.getBlock().getState());
        }
    }

    private void updateSign(Sign sign) {
        String serverName = sign.getLine(1);

        ServerStatus status = plugin.getServerStatusManager().getStatus(serverName);

        if (status != null) {
            int currentPlayers = plugin.getServerStatusManager().getCurrentPlayers(serverName);
            int maxPlayers = plugin.getServerStatusManager().getMaxPlayers(serverName);

            applyFormat(sign, serverName, currentPlayers, maxPlayers, status == ServerStatus.ACTIVE_GAME);

            _signs_mutex.lock();
            try {
                signs.put(serverName, sign.getLocation());
            } finally {
                _signs_mutex.unlock();
            }
        } else {
            sign.setLine(0, plugin.getLangManager().getText("sign-format.invalid-room"));
        }
        sign.update();
    }


    private void applyFormat(Sign sign, String serverName,
                             int currentPlayers, int maxPlayers, boolean enabled) {
        for (int i = 1; i <= 4; i++) {
            String raw = plugin.getLangManager().getText("sign-format." + i);
            if (raw == null) raw = "";

            String formatted = raw
                    .replace("%ROOM%", serverName != null ? serverName : "")
                    .replace("%CURRENT_MAP%", enabled
                            ? plugin.getLangManager().getText("sign-format.active")
                            : plugin.getLangManager().getText("sign-format.disabled"))
                    .replace("%CURRENT_PLAYERS%", String.valueOf(currentPlayers))
                    .replace("%MAX_PLAYERS%", String.valueOf(maxPlayers));

            sign.setLine(i - 1, ChatColor.translateAlternateColorCodes('&', formatted));
        }
    }

    public void checkForPlayerJoin(PlayerInteractEvent e) {
        Sign sign = (Sign) e.getClickedBlock().getState();
        if (sign.getLine(0).equals(joinSignText)) {
            e.setCancelled(true);
            String serverName = sign.getLine(1);
            ServerStatus status = plugin.getServerStatusManager().getStatus(serverName);

            if (status == ServerStatus.ACTIVE_GAME) {
                plugin.getBungeeCommunicator().sendPlayerToServer(e.getPlayer(), serverName);
            } else {
                plugin.getLangManager().sendMessage("room-is-disabled", e.getPlayer());
            }
        }
    }


    public void checkForGameInPost(SignChangeEvent e) {
        if (e.getLine(0).toLowerCase().equalsIgnoreCase(plugin.getConfigManager().getSignFirstLine())) {
            e.setLine(0, joinSignText);
            Location loc = e.getBlock().getLocation();
            Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
                @Override
                public void run() {
                    updateSign(loc);
                }
            }, 10L);
        }
    }
}