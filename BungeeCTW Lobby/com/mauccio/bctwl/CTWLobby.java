package com.mauccio.bctwl;

import com.mauccio.bctwl.bungee.BungeeCommunicator;
import com.mauccio.bctwl.bungee.ServerStatusManager;
import com.mauccio.bctwl.files.*;
import com.mauccio.bctwl.map.*;
import com.mauccio.bctwl.listeners.*;
import com.mauccio.bctwl.commands.*;
import com.mauccio.bctwl.lobby.LobbyManager;
import com.mauccio.bctwl.utils.PlaceholderCTW;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import net.milkbowl.vault.economy.Economy;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.SQLException;

public class CTWLobby extends JavaPlugin {

    public static class Scores {
        public int death;
        public int kill;
        public int capture;
        public int coins_capture;
        public int coins_kill;
    }

    private LangManager lm;
    private SignManager sm;
    private ConfigManager cf;
    private DBManager db;
    private CommandManager cm;
    private EventManager em;
    private Scores scores;
    private LobbyManager lb;
    private SoundManager so;
    private BungeeCommunicator bc;
    private ServerStatusManager ssm;
    private Economy econ;

    @Override
    public void onEnable() {
        getLogger().info("Plugin enabled");

        this.bc = new BungeeCommunicator(this);
        this.ssm = new ServerStatusManager();

        this.lm = new LangManager(this);
        this.cf = new ConfigManager(this);

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new PlaceholderCTW(this).register();
        }
        setupEconomy();

        this.cm = new CommandManager(this);
        this.em = new EventManager(this);
        Bukkit.getPluginManager().registerEvents(em, this); // registrar EventManager

        this.sm = new SignManager(this);
        this.lb = new LobbyManager(this);
        this.so = new SoundManager(this);
        removeAllItems();
        scores = new Scores();

        File statsFile = new File(getDataFolder(), "stats.yml");
        if (!statsFile.exists()) {
            saveResource("stats.yml", true);
        }

        YamlConfiguration stats = new YamlConfiguration();
        try {
            stats.load(statsFile);
            if (stats.getBoolean("enable")) {
                String host = stats.getString("database.host");
                String database = stats.getString("database.name");
                int port = stats.getInt("database.port");
                String user = stats.getString("database.user");
                String password = stats.getString("database.pass");
                if (stats.getString("database.type").equalsIgnoreCase("mysql")) {
                    db = new DBManager(this, DBManager.DBType.MySQL, host, port, database, user, password);
                } else {
                    db = new DBManager(this, DBManager.DBType.SQLITE, null, 0, null, null, null);
                }
                scores.capture = stats.getInt("scores.capture");
                scores.kill = stats.getInt("scores.kill");
                scores.death = stats.getInt("scores.death");
                scores.coins_capture = stats.getInt("coins.capture");
                scores.coins_kill = stats.getInt("coins.kill");
            }
        } catch (IOException | InvalidConfigurationException | SQLException ex) {
            alert(ex.getMessage());
            db = null;
        }

        so.loadSounds();

        getLogger().info("[MaucciosCTW]: Checking for updates...");
        runYamlChecker("messages.yml");
        runYamlChecker("config.yml");
    }

    @Override
    public void onDisable() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
        }
        getLogger().info("Plugin disabled");
        save();
    }

    private void setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            getLogger().info("Vault no encontrado, deshabilitando soporte de economía.");
            return;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            getLogger().info("No se encontró proveedor de economía en Vault.");
            return;
        }
        econ = rsp.getProvider();
        getLogger().info("Economía conectada con Vault: " + econ.getName());
    }

    public Economy getEconomy() {
        return econ;
    }

    public boolean hasPermission(Player player, String permission) {
        return player.hasPermission("ctw." + permission);
    }

    public void alert(String message) {
        String prefix = ChatColor.YELLOW + "["
                + ChatColor.GOLD + ChatColor.BOLD + this.getName()
                + ChatColor.YELLOW + "]";
        String prefixedMessage = prefix + " " + ChatColor.RED + "(alert) " + message;
        getServer().getConsoleSender().sendMessage(prefixedMessage);
        for (Player player : getServer().getOnlinePlayers()) {
            if (hasPermission(player, "receive-alerts")) {
                player.sendMessage(prefixedMessage);
            }
        }
    }

    public void removeAllItems() {
        for (World world : getServer().getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getType() != EntityType.PLAYER
                        && entity.getType() != EntityType.ITEM_FRAME
                        && entity.getType() != EntityType.UNKNOWN) {
                    entity.remove();
                }
            }
        }
    }

    public void reload() {
        cf.load();
    }

    public void save() {

        if (sm != null) {
            sm.persists();
        }
    }

    public ConfigManager getConfigManager() {
        return cf;
    }

    public DBManager getDBManager() {
        return db;
    }

    public LobbyManager getLobbyManager() {
        return lb;
    }

    public LangManager getLangManager() {
        return lm;
    }

    public SignManager getSignManager() {
        return sm;
    }

    public CommandManager getCommandManager() {
        return cm;
    }

    public EventManager getEventManager() {
        return em;
    }

    public SoundManager getSoundManager() {
        return so;
    }

    public BungeeCommunicator getBungeeCommunicator() {
        return bc;
    }

    public ServerStatusManager getServerStatusManager() {
        return ssm;
    }

    public Scores getScores() {
        return scores;
    }

    /*
        Update Checker
     */

    private void runYamlChecker(String fileName) {
        File file = new File(getDataFolder(), fileName);
        if (!file.exists()) {
            saveResource(fileName, false);
        }
        YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                new InputStreamReader(getResource(fileName))
        );
        YamlConfiguration currentConfig = YamlConfiguration.loadConfiguration(file);

        boolean updated = false;
        for (String key : defaultConfig.getKeys(true)) {
            if (!currentConfig.isSet(key)) {
                currentConfig.set(key, defaultConfig.get(key));
                getLogger().info("\"" + fileName + "\": + " + key);
                updated = true;
            }
        }

        try (FileWriter fw = new FileWriter(file, true)) {
            fw.write(System.lineSeparator());
            fw.write("# Path(s) added automatically for updates" + System.lineSeparator());
            getLogger().info("Comentario añadido al final de " + file.getName());
        } catch (IOException e) {
            getLogger().severe("Error writing comment: " + e.getMessage());
        }


        try {
            currentConfig.save(file);
        } catch (IOException e) {
            getLogger().severe("Error saving " + fileName + ": " + e.getMessage());
        }

        if (updated) {
            getLogger().info("\"" + fileName + "\": Updated!");
            getLogger().info("Restart your server for apply changes!");
        } else {
            getLogger().info(" \"" + fileName + "\": Updated!");
            getLogger().info("You're updated, enjoy your game!");
        }
    }
}
