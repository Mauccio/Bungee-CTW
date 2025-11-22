package com.mauccio.bctws;

import com.mauccio.bctws.files.*;
import com.mauccio.bctws.game.*;
import com.mauccio.bctws.map.*;
import com.mauccio.bctws.listeners.*;
import com.mauccio.bctws.commands.*;
import com.mauccio.bctws.game.NametagManager;
import com.mauccio.bctws.utils.PlaceholderCTW;
import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import net.milkbowl.vault.economy.Economy;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.SQLException;

public class CTWServer extends JavaPlugin {

    public static class Scores {
        public int death;
        public int kill;
        public int capture;
        public int coins_capture;
        public int coins_kill;
    }

    private LangManager lm;
    private MapManager mm;
    private TeamManager tm;
    private PlayerManager pm;
    private ConfigManager cf;
    private DBManager db;
    private GameManager gm;
    private CommandManager cm;
    private PoolManager po;
    private WorldManager wm;
    private EventManager em;
    private WorldEditPlugin we;
    private Scores scores;
    private KitManager km;
    private NametagManager nm;
    private SoundManager so;
    private TitleManager ts;
    private Economy econ;

    @Override
    public void onEnable() {
        getLogger().info("Plugin enabled");
        if(getConfig().getString("lobby-server") == null) {
            getLogger().severe("Lobby server not set in config.yml! Disabling plugin...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.lm = new LangManager(this);
        we = (WorldEditPlugin) getServer().getPluginManager().getPlugin("WorldEdit");
        if (we == null) {
            alert(lm.getText("we-not-enabled"));
            return;
        }

        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new PlaceholderCTW(this).register();
        }
        setupEconomy();

        this.cf = new ConfigManager(this);
        this.wm = new WorldManager(this);
        this.removeAllItems();
        this.cm = new CommandManager(this);
        this.em = new EventManager(this);
        this.mm = new MapManager(this);
        this.cf.verifyEditorMode();
        if (cf.isEditMode()) {
            lm.getText("editor-mode-enabled");
            this.so = new SoundManager(this);
            return;
        }

        this.tm = new TeamManager(this);
        this.pm = new PlayerManager(this);
        this.po = new PoolManager(this);
        this.nm = new NametagManager(this, tm);
        this.gm = new GameManager(this);

        this.po.init();
        for (String poolName : po.getPools()) {
            PoolManager.Pool pool = po.getPool(poolName);
            for (String mapName : pool.getMaps()) {
                File uidFile = new File(Bukkit.getWorldContainer(), "maps/" + mapName + "/uid.dat");
                if (uidFile.exists()) uidFile.delete();

                World world = Bukkit.createWorld(new WorldCreator("maps/" + mapName));
                if (world != null) {
                    getLogger().info("Loaded world: maps/" + mapName);
                }
            }
        }

        PoolManager.Pool active = po.getActivePool();
        if (active != null) gm.addGame(active.getName());
        MapManager.MapData data = mm.getMapData(po.getCurrentMap());
        String mapName = active.getCurrentMap();
        World clonedWorld = Bukkit.getWorld("maps/" + mapName);
        po.removeWools(mapName, clonedWorld);
        this.km = new KitManager(this);
        this.so = new SoundManager(this);
        this.ts = new TitleManager(this);

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
        } catch (IOException | InvalidConfigurationException |
                 SQLException ex) {
            alert(ex.getMessage());
            db = null;
        }
        so.loadSounds();


        getLogger().info("Checking for updates...");
        runYamlChecker("messages.yml");
        runYamlChecker("config.yml");
    }

    @Override
    public void onDisable() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
            p.getInventory().clear();
        }
        getLogger().info("Plugin disabled");
        save();
    }

    private void setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            getLogger().info(lm.getText("va-not-enabled"));
            return;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            getLogger().info("There's no economy extension for Vault.");
            return;
        }
        econ = rsp.getProvider();
        getLogger().info("Economy connected with: " + econ.getName());
    }

    public Economy getEconomy() {
        return econ;
    }

    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        return wm.getEmptyWorldGenerator();
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
        mm.load();
        po.load();
        km.reloadKits();
    }

    public void save() {

        if (mm != null) {
            mm.persist();
        }

        if (po != null) {
            po.persist();
        }
    }

    public MapManager getMapManager() {
        return mm;
    }

    public TeamManager getTeamManager() {
        return tm;
    }

    public PlayerManager getPlayerManager() {
        return pm;
    }

    public ConfigManager getConfigManager() {
        return cf;
    }

    public GameManager getGameManager() {
        return gm;
    }

    public WorldManager getWorldManager() {
        return wm;
    }

    public DBManager getDBManager() {
        return db;
    }

    public PoolManager getPoolManager() {
        return po;
    }

    public KitManager getKitManager() {
        return km;
    }

    public NametagManager getNametagManager() {
        return nm;
    }

    public LangManager getLangManager() {
        return lm;
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

    public TitleManager getTitleManager () {
        return ts;
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
            getLogger().info("Commented in: " + file.getName());
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
            getLogger().info("\"" + fileName + "\": Updated!");
            getLogger().info("You're updated, enjoy your game!");
        }
    }
}
