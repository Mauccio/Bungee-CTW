package com.mauccio.bctwl.lobby;

import com.mauccio.bctwl.CTWLobby;
import com.mauccio.bctwl.bungee.ServerStatus;
import com.mauccio.bctwl.bungee.ServersListReceivedEvent;
import com.mauccio.bctwl.utils.LobbyItem;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scoreboard.*;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class LobbyManager implements Listener {

    private final CTWLobby ctwLobby;
    private final File lobbyFile;
    private final YamlConfiguration lobbyYml;
    private final Map<String, LobbyItem> items = new HashMap<>();
    private Scoreboard lobbyBoard;
    private List<String> lobbyTemplateLines = Collections.emptyList();
    private Location lobby;

    public LobbyManager(CTWLobby ctwLobby) {
        this.ctwLobby = ctwLobby;
        this.lobbyFile = new File(ctwLobby.getDataFolder()+File.separator+"lobby.yml");
        this.lobbyYml = YamlConfiguration.loadConfiguration(lobbyFile);
        registerEvents();
        loadItems();
        loadLobbyLoc();
    }

    public void loadItems() {
        items.clear();

        File file = new File(ctwLobby.getDataFolder(), "lobby.yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);

        if (!config.contains("items")) return;

        for (String id : config.getConfigurationSection("items").getKeys(false)) {
            String path = "items." + id + ".";

            String name = config.getString(path + "name", "&fItem");
            List<String> desc = config.getStringList(path + "description");
            Material type = Material.matchMaterial(config.getString(path + "type", "STONE"));
            int data = config.getInt(path + "data", 0);
            int amount = config.getInt(path + "amount", 1);
            int slot = config.getInt(path + "slot", 0);
            boolean glide = config.getBoolean(path + "glide", false);
            String command = config.getString(path + "command", "");

            LobbyItem item = new LobbyItem(id, name, desc, type, data, amount, slot, glide, command);
            items.put(id, item);
        }
    }

    public LobbyItem getItem(String id) {
        return items.get(id);
    }

    public Collection<LobbyItem> getAllItems() {
        return items.values();
    }

    public ItemStack[] getLobbyItems() {
        List<ItemStack> stacks = new ArrayList<>();
        for (LobbyItem lobbyItem : items.values()) {
            stacks.add(lobbyItem.toItemStack());
        }
        return stacks.toArray(new ItemStack[0]);
    }

    private void registerEvents() {
        PluginManager pm = ctwLobby.getServer().getPluginManager();
        pm.registerEvents(this, ctwLobby);
    }

    private String renderLine(String raw) {
        int totalRooms = ctwLobby.getServerStatusManager().getTotalServers();
        int activeRooms = ctwLobby.getServerStatusManager().getActiveServers();
        return raw
                .replace("%ROOMS%", String.valueOf(totalRooms))
                .replace("%ACTIVE_ROOMS%", String.valueOf(activeRooms))
                .replace("%PLAYERS%", String.valueOf(Bukkit.getOnlinePlayers().size()))
                .replace("%MAX_PLAYERS%", String.valueOf(Bukkit.getMaxPlayers()))
                .replace("%SERVER_IP%", ctwLobby.getLangManager().getText("server-ip"));
    }

    private List<String> uniquifyLines(List<String> rendered) {
        List<String> out = new ArrayList<>(rendered.size());
        Set<String> seen = new HashSet<>();
        for (String line : rendered) {
            String l = line.isEmpty() ? " " : line;
            while (seen.contains(l)) {
                l += ChatColor.RESET;
            }
            seen.add(l);
            out.add(l);
        }
        return out;
    }

    public void buildLobbyBoard() {
        ScoreboardManager sm = Bukkit.getScoreboardManager();
        lobbyBoard = sm.getNewScoreboard();

        Objective obj = lobbyBoard.registerNewObjective("lobby", "dummy");
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        obj.setDisplayName(ctwLobby.getLangManager().getText("lobby-scoreboard.title"));

        lobbyTemplateLines = ctwLobby.getLangManager().getStringList("lobby-scoreboard.lines");

        List<String> rendered = new ArrayList<>(lobbyTemplateLines.size());
        for (String raw : lobbyTemplateLines) rendered.add(renderLine(raw));
        List<String> unique = uniquifyLines(rendered);

        int score = unique.size();
        for (String line : unique) {
            obj.getScore(line).setScore(score--);
        }
    }

    public void refreshLobbyBoard() {
        if (lobbyBoard == null) return;

        Objective obj = lobbyBoard.getObjective("lobby");
        if (obj != null) {
            obj.unregister();
        }

        obj = lobbyBoard.registerNewObjective("lobby", "dummy");
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        obj.setDisplayName(ctwLobby.getLangManager().getText("lobby-scoreboard.title"));

        int totalRooms = ctwLobby.getServerStatusManager().getTotalServers();
        int activeRooms = ctwLobby.getServerStatusManager().getActiveServers();

        List<String> rendered = new ArrayList<>(lobbyTemplateLines.size());
        for (String raw : lobbyTemplateLines) {
            String line = raw
                    .replace("%ROOMS%", String.valueOf(totalRooms))
                    .replace("%ACTIVE_ROOMS%", String.valueOf(activeRooms))
                    .replace("%PLAYERS%", String.valueOf(Bukkit.getOnlinePlayers().size()))
                    .replace("%MAX_PLAYERS%", String.valueOf(Bukkit.getMaxPlayers()))
                    .replace("%SERVER_IP%", ctwLobby.getLangManager().getText("server-ip"));

            rendered.add(line.isEmpty() ? " " : line);
        }

        List<String> unique = new ArrayList<>(rendered.size());
        Set<String> seen = new HashSet<>();
        for (String line : rendered) {
            String l = line;
            while (seen.contains(l)) {
                l += ChatColor.RESET;
            }
            seen.add(l);
            unique.add(l);
        }

        int score = unique.size();
        for (String line : unique) {
            obj.getScore(line).setScore(score--);
        }
    }

    public Scoreboard getLobbyBoard() {
        return lobbyBoard;
    }

    public void assignLobbyBoard(Player player) {
        if (lobbyBoard == null) buildLobbyBoard();
        player.setScoreboard(lobbyBoard);
    }

    public boolean isRoomEnabled(ItemStack is) {
        if (is == null || !is.hasItemMeta()) return false;
        ItemMeta im = is.getItemMeta();
        if (!im.hasDisplayName()) return false;

        String title = ChatColor.stripColor(im.getDisplayName());

        String enabledPattern = ChatColor.stripColor(ctwLobby.getLangManager().getText("room-gui-enabled"));
        String disabledPattern = ChatColor.stripColor(ctwLobby.getLangManager().getText("room-gui-disabled"));

        String enabledRegex = "^" + enabledPattern.replace("%ROOM%", "(.+)") + "$";
        String disabledRegex = "^" + disabledPattern.replace("%ROOM%", "(.+)") + "$";

        if (title.matches(enabledRegex)) return true;
        if (title.matches(disabledRegex)) return false;

        return false;
    }

    public boolean isPluginGUI(Inventory inv) {
        if (inv == null) return false;
        String title = ChatColor.stripColor(inv.getTitle());
        return title.equals(ChatColor.stripColor(ctwLobby.getLangManager().getText("rooms-gui"))) ||
                title.equals(ChatColor.stripColor(ctwLobby.getLangManager().getText("menus.kits.title")));
    }

    public boolean isRoomItem(ItemStack is) {
        if (is == null || !is.hasItemMeta()) return false;
        String title = ChatColor.stripColor(is.getItemMeta().getDisplayName());

        String enabledPrefix = ChatColor.stripColor(ctwLobby.getLangManager().getText("room-gui-enabled")).split("%ROOM%")[0];
        String disabledPrefix = ChatColor.stripColor(ctwLobby.getLangManager().getText("room-gui-disabled")).split("%ROOM%")[0];

        return title.startsWith(enabledPrefix) || title.startsWith(disabledPrefix);
    }


    public Inventory getRoomsGUI(Player player, String[] servers) {
        Inventory inv = Bukkit.createInventory(null, 9, ctwLobby.getLangManager().getText("rooms-gui"));

        List<String> allowedServers = ctwLobby.getConfig().getStringList("game-servers");

        int slot = 0;
        for (String server : servers) {

            if (!allowedServers.contains(server)) continue;
            if (server.equalsIgnoreCase(ctwLobby.getConfig().getString("ctwlobby-server"))) continue;

            ServerStatus status = ctwLobby.getServerStatusManager().getStatus(server);

            ItemStack wool = new ItemStack(Material.WOOL, 1, status.getColorData());
            ItemMeta meta = wool.getItemMeta();

            String format = ctwLobby.getLangManager().getText("rooms-gui-format");
            String displayName = format.replace("%ROOM%", server);

            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', displayName));
            meta.setLore(Arrays.asList(status.getDescription()));
            wool.setItemMeta(meta);

            inv.setItem(slot++, wool);
        }
        return inv;
    }

    /*
        Lobby Guard
     */

    public void setLobbyLoc(Location loc) {
        this.lobby = loc;

        lobbyYml.set("lobby-location.world", loc.getWorld().getName());
        lobbyYml.set("lobby-location.x", loc.getX());
        lobbyYml.set("lobby-location.y", loc.getY());
        lobbyYml.set("lobby-location.z", loc.getZ());
        lobbyYml.set("lobby-location.yaw", loc.getYaw());
        lobbyYml.set("lobby-location.pitch", loc.getPitch());

        try {
            lobbyYml.save(lobbyFile);
        } catch (IOException e) {
            ctwLobby.getLogger().severe("No se pudo guardar lobby.yml: " + e.getMessage());
        }
    }

    public void loadLobbyLoc() {
        if (!lobbyYml.contains("lobby-location.world")) return;

        String worldName = lobbyYml.getString("lobby-location.world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            ctwLobby.getLogger().warning("El mundo " + worldName + " no está cargado.");
            return;
        }

        double x = lobbyYml.getDouble("lobby-location.x");
        double y = lobbyYml.getDouble("lobby-location.y");
        double z = lobbyYml.getDouble("lobby-location.z");
        float yaw = (float) lobbyYml.getDouble("lobby-location.yaw");
        float pitch = (float) lobbyYml.getDouble("lobby-location.pitch");

        this.lobby = new Location(world, x, y, z, yaw, pitch);
    }

    public Location getLobbyLoc() {
        return this.lobby;
    }

    public boolean isOnLobby(Player player) {
        return player.getLocation().getWorld() == getLobbyLoc().getWorld();
    }

    @EventHandler
    public void onServersListReceived(ServersListReceivedEvent e) {
        Player player = e.getPlayer();
        String[] servers = e.getServers();

        Inventory inv = getRoomsGUI(player, servers);
        player.openInventory(inv);
    }

    @EventHandler
    public void lobbyBlockPlace(BlockPlaceEvent e) {
        Player player = e.getPlayer();
        if(player.hasPermission("ctw.admin") || player.isOp()) return;
        if(isOnLobby(player)) {
            if(ctwLobby.getConfigManager().isLobbyGuardEnabled()) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void lobbyBreakPlace(BlockBreakEvent e) {
        Player player = e.getPlayer();
        if(player.hasPermission("ctw.admin") || player.isOp()) return;
        if(isOnLobby(player)) {
            if(ctwLobby.getConfigManager().isLobbyGuardEnabled()) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onLobbyHit(EntityDamageByEntityEvent e) {
        if(ctwLobby.getConfigManager().isLobbyGuardEnabled()) {
            if(e.getEntity() instanceof Player) {
                Player player = (Player) e.getEntity();
                if(player.hasPermission("ctw.admin") || player.isOp()) return;
                if(e.getDamager() instanceof Player) {
                    Player damager = (Player) e.getDamager();
                    if(damager.hasPermission("ctw.admin") || damager.isOp()) return;
                    if(isOnLobby(player)) {
                        if(isOnLobby(damager)) {
                            e.setCancelled(true);
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void lobbyHungry(FoodLevelChangeEvent e) {
        Player player = (Player) e.getEntity();
        if(player.hasPermission("ctw.admin") || player.isOp()) return;
        if(isOnLobby(player)) {
            if(ctwLobby.getConfigManager().isLobbyGuardEnabled()) {
                e.setFoodLevel(20);
                e.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void lobbyDamage(EntityDamageEvent e) {
        Player player = (Player) e.getEntity();
        if(player.hasPermission("ctw.admin") || player.isOp()) return;
        if(isOnLobby(player)) {
            if(ctwLobby.getConfigManager().isLobbyGuardEnabled()) {
                e.setCancelled(true);
            }
        }
    }
}
