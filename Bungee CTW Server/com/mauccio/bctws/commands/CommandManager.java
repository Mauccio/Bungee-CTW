package com.mauccio.bctws.commands;

import com.mauccio.bctws.CTWServer;
import com.mauccio.bctws.game.TeamManager;
import com.mauccio.bctws.utils.PlayerStats;
import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldedit.bukkit.selections.Selection;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;

public class CommandManager implements CommandExecutor {

    private final CTWServer ctwServer;
    private final TreeSet<String> allowedInGameCmds;

    public CommandManager(CTWServer ctwServer) {
        this.ctwServer = ctwServer;
        this.allowedInGameCmds = new TreeSet<>();
        register();
    }

    private void register() {
        ctwServer.saveResource("plugin.yml", true);
        File file = new File(ctwServer.getDataFolder(), "plugin.yml");
        YamlConfiguration pluginYml = new YamlConfiguration();
        try {
            pluginYml.load(file);
        } catch (IOException | InvalidConfigurationException ex) {
            ctwServer.getLogger().severe(ex.toString());
            ctwServer.getPluginLoader().disablePlugin(ctwServer);
            return;
        } finally {
            if (file.exists()) file.delete();
        }

        if (pluginYml.getConfigurationSection("commands") != null) {
            for (String commandName : pluginYml.getConfigurationSection("commands").getKeys(false)) {
                PluginCommand pc = ctwServer.getCommand(commandName);
                if (pc != null) pc.setExecutor(this);
                allowedInGameCmds.add(commandName);
            }
        }
    }

    private static Object getPrivateField(Object object, String field)
            throws NoSuchFieldException, IllegalAccessException {
        Class<?> clazz = object.getClass();
        Field objectField = clazz.getDeclaredField(field);
        objectField.setAccessible(true);
        Object result = objectField.get(object);
        objectField.setAccessible(false);
        return result;
    }

    private void unRegisterBukkitCommand(PluginCommand cmd) {
        if (cmd == null) return;
        try {
            Object pluginManager = ctwServer.getServer().getPluginManager();
            Field fCommandMap = pluginManager.getClass().getDeclaredField("commandMap");
            fCommandMap.setAccessible(true);
            Object commandMap = fCommandMap.get(pluginManager);
            fCommandMap.setAccessible(false);

            Field fKnown = commandMap.getClass().getDeclaredField("knownCommands");
            fKnown.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Command> knownCommands = (Map<String, Command>) fKnown.get(commandMap);
            fKnown.setAccessible(false);

            knownCommands.remove(cmd.getName());

            for (String alias : cmd.getAliases()) {
                Command existing = knownCommands.get(alias);
                if (existing != null && existing.toString().contains(ctwServer.getName())) {
                    knownCommands.remove(alias);
                }
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            ctwServer.getLogger().severe("No se pudo desregistrar el comando: " + e.getMessage());
        }
    }

    private Player getPlayerOrNotify(CommandSender cs) {
        if (cs instanceof Player) return (Player) cs;
        ctwServer.getLogger().info("Este comando es de jugador.");
        return null;
    }

    private boolean requireInPoolMap(Player player) {
        if (ctwServer.getPlayerManager().getTeamId(player) == null) {
            ctwServer.getLangManager().sendMessage("not-in-room-cmd", player);
            ctwServer.getSoundManager().playErrorSound(player);
            return false;
        }
        return true;
    }

    private boolean requireSetupPerm(Player player) {
        if (!player.hasPermission("ctw.setup")) {
            ctwServer.getLangManager().sendMessage("incorrect-parameters", player);
            ctwServer.getSoundManager().playErrorSound(player);
            return false;
        }
        return true;
    }

    private boolean requireWorldEdit(Player player) {
        if (ctwServer.getServer().getPluginManager().getPlugin("WorldEdit") == null
                || !ctwServer.getServer().getPluginManager().getPlugin("WorldEdit").isEnabled()) {
            ctwServer.getLangManager().sendMessage("we-not-enabled", player);
            ctwServer.getSoundManager().playErrorSound(player);
            return false;
        }
        return true;
    }

    private Selection getSelection(Player player) {
        WorldEditPlugin we = (WorldEditPlugin) ctwServer.getServer().getPluginManager().getPlugin("WorldEdit");
        return (we != null) ? we.getSelection(player) : null;
    }

    private boolean requireSelectionOrTip(Player player) {
        Selection sel = getSelection(player);
        if (sel == null) {
            ctwServer.getLangManager().sendMessage("area-not-selected", player);
            ctwServer.getSoundManager().playErrorSound(player);
            return false;
        }
        return true;
    }
    
    public boolean isEditMode() {
        return ctwServer.getConfigManager().isEditMode();
    }

    @Override
    public boolean onCommand(CommandSender cs, Command cmnd, String label, String[] args) {
        switch (cmnd.getName().toLowerCase()) {
            case "ctw":          return handleCtw(cs, args);
            case "stats":        return handleStats(cs, args);
            case "kit":          return handleKit(cs, args);
            case "saveglobalkit":return handleSaveGlobalKit(cs, args);
            case "kiteditor":    return handleKitEditor(cs, args);
            case "ctwsetup":     return handleCtwSetup(cs, args);
            case "createworld":
            case "gotoworld":    return handleWorldNav(cs, cmnd.getName(), args);
            case "savekit":      return handleSaveKit(cs, args);
            case "g":            return handleG(cs, args);
            case "toggle":       return handleToggle(cs, args);
            case "join":         return handleJoin(cs, args);
            case "leave":        return handleLeave(cs, args);
            case "alert":        return handleAlert(cs, args);
            default:             return true;
        }
    }

    private boolean handleCtw(CommandSender cs, String[] args) {
        Player player = getPlayerOrNotify(cs);
        if (args.length != 1) {
            if (player != null) {
                ctwServer.getLangManager().sendText("commands.ctw", player);
                ctwServer.getSoundManager().playTipSound(player);
            } else {
                ctwServer.getLogger().info("Uso: /ctw [reload|save|mapcycle]");
            }
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "reload":
                ctwServer.reload();
                ctwServer.getLangManager().sendMessage("cmd-success", cs);
                return true;
            case "save":
                ctwServer.save();
                ctwServer.getLangManager().sendMessage("cmd-success", cs);
                return true;
            case "mapcycle":
                if (player == null) return true;
                if (isEditMode()) return true;
                ctwServer.getGameManager().advanceGame(player.getWorld());
                return true;
            default:
                if (player != null) {
                    ctwServer.getLangManager().sendText("commands.ctw", player);
                    ctwServer.getSoundManager().playTipSound(player);
                }
                return true;
        }
    }

    private boolean handleStats(CommandSender cs, String[] args) {
        Player player = getPlayerOrNotify(cs);
        if (player == null) return true;
        try {
            if (ctwServer.getDBManager() == null) {
                ctwServer.getLangManager().sendMessage("stats-not-enabled", player);
                ctwServer.getSoundManager().playErrorSound(player);
                return true;
            }
            PlayerStats cached = ctwServer.getDBManager().getPlayerStats(player.getName());
            enviarStats(player, cached);
            ctwServer.getSoundManager().playYourStatsSound(player);
        } catch (Exception e) {
            ctwServer.getLangManager().sendMessage("stats-not-enabled", player);
            ctwServer.getSoundManager().playErrorSound(player);
        }
        return true;
    }

    private boolean handleLeave(CommandSender cs, String[] args) {
        Player player = getPlayerOrNotify(cs);
        if (player == null) return true;
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(b);

        try {
            out.writeUTF("Connect");
            out.writeUTF(ctwServer.getConfig().getString("lobby-server"));
        } catch (IOException e) {
            e.printStackTrace();
            return true;
        }

        player.sendPluginMessage(ctwServer, "BungeeCord", b.toByteArray());
        return true;
    }

    private void enviarStats(Player player, PlayerStats stats) {
        player.sendMessage(ctwServer.getConfig().getString("message-decorator"));
        player.sendMessage(ctwServer.getLangManager().getText("stats.title"));
        player.sendMessage(ctwServer.getLangManager().getText("stats.points").replace("%SCORE%", String.valueOf(stats.score)));
        player.sendMessage(ctwServer.getLangManager().getText("stats.kills").replace("%KILLS%", String.valueOf(stats.kills)));
        player.sendMessage(ctwServer.getLangManager().getText("stats.deaths").replace("%DEATHS%", String.valueOf(stats.deaths)));
        player.sendMessage(ctwServer.getLangManager().getText("stats.placed-wools").replace("%WOOLS_PLACED%", String.valueOf(stats.wools)));
        if(ctwServer.getEconomy() != null) {
            player.sendMessage(ctwServer.getLangManager().getText("stats.coins").replace("%COINS%", String.valueOf(ctwServer.getEconomy().getBalance(player))));
        }
        player.sendMessage(ctwServer.getConfig().getString("message-decorator"));
    }

    private boolean handleKit(CommandSender cs, String[] args) {
        Player player = getPlayerOrNotify(cs);
        if (player == null) return true;
        if(ctwServer.getPlayerManager().getTeamId(player) == null
                || ctwServer.getPlayerManager().getTeamId(player) == TeamManager.TeamId.SPECTATOR) {
            ctwServer.getLangManager().sendMessage("not-in-game-cmd", player);
            return true;
        }
        if (!ctwServer.getConfigManager().isKitMenuEnabled()) {
            ctwServer.getLangManager().sendMessage("kits-not-enabled", player);
            ctwServer.getSoundManager().playErrorSound(player);
            return true;
        }
        ctwServer.getKitManager().openKitGUI(player);
        ctwServer.getSoundManager().playGuiSound(player);
        return true;
    }

    private boolean handleSaveGlobalKit(CommandSender cs, String[] args) {
        Player player = getPlayerOrNotify(cs);
        if (player == null) return true;
        if (ctwServer.getPlayerManager().getTeamId(player) != null) {
            ctwServer.getLangManager().sendMessage("not-in-lobby-cmd", player);
            ctwServer.getSoundManager().playErrorSound(player);
            return true;
        }
        try {
            ctwServer.getKitManager().saveGlobalKitYAML(player.getInventory().getContents());
            ctwServer.getLangManager().sendMessage("starting-kit-set", player);
        } catch (IOException e) {
            ctwServer.getLangManager().sendMessage("error-at-save-kit", player);
            ctwServer.getSoundManager().playErrorSound(player);
        }
        return true;
    }

    private boolean handleKitEditor(CommandSender cs, String[] args) {
        Player player = getPlayerOrNotify(cs);
        if (player == null) return true;
        if (ctwServer.getPlayerManager().getTeamId(player) != TeamManager.TeamId.SPECTATOR) return true;

        ctwServer.getKitManager().invSaver(player, player.getUniqueId());
        ItemStack[] globalKit = ctwServer.getKitManager().getGlobalKitYAML();
        player.getInventory().clear();
        player.getInventory().setContents(globalKit);
        ctwServer.getLangManager().sendMessage("edit-your-kit", player);
        ctwServer.getLangManager().sendMessage("save-your-kit-with", player);
        return true;
    }

    private boolean handleCtwSetup(CommandSender cs, String[] args) {
        Player player = getPlayerOrNotify(cs);
        if (player == null) return true;
        if(!isEditMode()) {
            ctwServer.getLangManager().sendMessage("not-in-edit-mode", player);
            return true;
        }
        if (args.length > 1) {
            processCtwSetup(player, args);
            return true;
        }
        if (args.length == 0) {
            ctwServer.getLangManager().sendText("commands.ctwsetup", player);
            ctwServer.getSoundManager().playTipSound(player);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "lobby":      ctwServer.getLangManager().sendText("commands.ctwsetup-lobby", player); ctwServer.getSoundManager().playTipSound(player); return true;
            case "map":        ctwServer.getLangManager().sendText("commands.ctwsetup-map", player); ctwServer.getSoundManager().playTipSound(player); return true;
            case "mapconfig":  ctwServer.getLangManager().sendText("commands.ctwsetup-mapconfig", player); ctwServer.getSoundManager().playTipSound(player); return true;
            case "room":       ctwServer.getLangManager().sendText("commands.ctwsetup-pool", player); ctwServer.getSoundManager().playTipSound(player); return true;
            default:           ctwServer.getLangManager().sendText("commands.ctwsetup", player); ctwServer.getSoundManager().playTipSound(player); return true;
        }
    }

    private boolean handleWorldNav(CommandSender cs, String name, String[] args) {
        Player player = getPlayerOrNotify(cs);
        if (player == null) return true;
        if (args.length != 1) {
            ctwServer.getLangManager().sendMessage("incorrect-parameters", cs);
            ctwServer.getSoundManager().playErrorSound(player);
            ctwServer.getLangManager().sendText("commands." + name, player);
            return true;
        }
        World world = name.equals("createworld")
                ? ctwServer.getWorldManager().createEmptyWorld(args[0])
                : ctwServer.getWorldManager().loadWorld(args[0]);

        if (world == null) {
            ctwServer.getLangManager().sendMessage("world-doesnot-exists", cs);
            ctwServer.getSoundManager().playErrorSound(player);
        } else {
            player.teleport(world.getSpawnLocation());
            ctwServer.getLangManager().sendMessage("cmd-success", player);
        }
        return true;
    }

    private boolean handleSaveKit(CommandSender cs, String[] args) {
        Player player = getPlayerOrNotify(cs);
        if (player == null) return true;
        if (ctwServer.getPlayerManager().getTeamId(player) != TeamManager.TeamId.SPECTATOR) {
            ctwServer.getLangManager().sendMessage("not-in-lobby-cmd", player);
            ctwServer.getSoundManager().playErrorSound(player);
            return true;
        }
        ctwServer.getKitManager().saveKit(player, player.getInventory().getContents());
        player.getInventory().clear();
        ctwServer.getKitManager().invRecover(player, player.getUniqueId());
        return true;
    }

    private boolean handleG(CommandSender cs, String[] args) {
        Player player = getPlayerOrNotify(cs);
        if (player == null) return true;
        if (args.length == 0) {
            ctwServer.getLangManager().sendText("commands.g", player);
            ctwServer.getSoundManager().playTipSound(player);
            return true;
        }
        if (ctwServer.getPlayerManager().getTeamId(player) != null) {
            ChatColor cc = ctwServer.getPlayerManager().getChatColor(player);
            String message = String.join(" ", args).trim();
            String senderName = player.getDisplayName().replace(player.getName(), cc + player.getName());
            for (Player receiver : player.getWorld().getPlayers()) {
                receiver.sendMessage(senderName + ChatColor.RESET + ": " + message);
            }
        } else {
            ctwServer.getLangManager().sendMessage("not-in-room-cmd", player);
            ctwServer.getSoundManager().playErrorSound(player);
        }
        return true;
    }

    private boolean handleToggle(CommandSender cs, String[] args) {
        Player player = getPlayerOrNotify(cs);
        if (player == null) return true;
        if (args.length != 1) {
            ctwServer.getLangManager().sendText("commands.toggle", player);
            ctwServer.getSoundManager().playTipSound(player);
            return true;
        }
        if (ctwServer.getPlayerManager().getTeamId(player) == null) return true;

        switch (args[0].toLowerCase()) {
            case "obs":
                if (ctwServer.getPlayerManager().toggleSeeOthersSpectators(player)) {
                    ctwServer.getLangManager().sendMessage("obs-true", player);
                } else {
                    ctwServer.getLangManager().sendMessage("obs-false", player);
                }
                ctwServer.getSoundManager().playTipSound(player);
                break;
            case "dms":
                if (ctwServer.getPlayerManager().toogleOthersDeathMessages(player)) {
                    ctwServer.getLangManager().sendMessage("dms-true", player);
                } else {
                    ctwServer.getLangManager().sendMessage("dms-false", player);
                }
                ctwServer.getSoundManager().playTipSound(player);
                break;
            case "blood":
                if (ctwServer.getPlayerManager().toggleBloodEffect(player)) {
                    ctwServer.getLangManager().sendMessage("blood-true", player);
                } else {
                    ctwServer.getLangManager().sendMessage("blood-false", player);
                }
                ctwServer.getSoundManager().playTipSound(player);
                break;
            default:
                ctwServer.getLangManager().sendText("commands.toggle", player);
                ctwServer.getSoundManager().playTipSound(player);
                break;
        }
        return true;
    }

    private boolean handleJoin(CommandSender cs, String[] args) {
        Player player = getPlayerOrNotify(cs);
        if (player == null) return true;
        TeamManager.TeamId teamId = ctwServer.getPlayerManager().getTeamId(player);
        if (teamId != TeamManager.TeamId.SPECTATOR) {
            ctwServer.getLangManager().sendMessage("join-in-team", player);
            ctwServer.getSoundManager().playErrorSound(player);
            return true;
        }
        player.openInventory(ctwServer.getTeamManager().getMenuInv());
        ctwServer.getSoundManager().playJoinCommandSound(player);
        return true;
    }

    private boolean handleAlert(CommandSender cs, String[] args) {
        if (args.length == 0) {
            if (cs instanceof Player) {
                Player player = (Player) cs;
                ctwServer.getLangManager().sendText("commands.alert", player);
                ctwServer.getSoundManager().playTipSound(player);
            } else {
                ctwServer.getLogger().info("Uso: /alert [mensaje]");
            }
            return true;
        }
        String message = String.join(" ", args) + " ";
        for (Player receiver : Bukkit.getOnlinePlayers()) {
            receiver.sendMessage(ctwServer.getLangManager().getText("alert-prefix") + " " + message);
            ctwServer.getSoundManager().playAlertSound(receiver);
        }
        return true;
    }

    private void processCtwSetup(Player player, String[] args) {
        if (!requireSetupPerm(player)) return;
        if(!isEditMode()) return;
        String section = args[0].toLowerCase();
        if ("map".equals(section)) {
            if (args.length < 2) {
                ctwServer.getLangManager().sendText("commands.ctwsetup-map", player);
                ctwServer.getSoundManager().playTipSound(player);
                return;
            }
            String action = args[1].toLowerCase();
            switch (action) {
                case "add": {
                    World w = player.getWorld();
                    if (Bukkit.getWorlds().get(0) == w) {
                        ctwServer.getLangManager().sendMessage("map-cannot-be-lobby", player);
                        ctwServer.getSoundManager().playErrorSound(player);
                        return;
                    }
                    if (ctwServer.getMapManager().add(w)) {
                        ctwServer.getLangManager().sendMessage(ctwServer.getLangManager().getText("map-successfully-added").replace("%MAP%", w.getName()), player);
                    } else {
                        ctwServer.getLangManager().sendMessage(ctwServer.getLangManager().getText("map-already-exists").replace("%MAP%", w.getName()), player);
                    }
                    ctwServer.getMapManager().setupTip(player);
                    return;
                }
                case "remove": {
                    World w = player.getWorld();
                    if (!ctwServer.getMapManager().exists(w.getName())) {
                        ctwServer.getLangManager().sendMessage("not-in-map", player);
                        return;
                    }
                    ctwServer.getMapManager().deleteMap(w);
                    ctwServer.getLangManager().sendMessage("map-deleted", player);
                    return;
                }
                case "list": {
                    Set<String> maps = ctwServer.getMapManager().getMaps();
                    if (maps.isEmpty()) {
                        ctwServer.getLangManager().sendMessage("map-list-empty", player);
                        return;
                    }
                    ctwServer.getLangManager().sendMessage("available-maps", player);
                    for (String mapName : maps) {
                        player.sendMessage(ChatColor.GREEN + " - " + ChatColor.AQUA + mapName);
                    }
                    return;
                }
                case "copy": {
                    if (args.length < 3) {
                        ctwServer.getLangManager().sendMessage("incorrect-parameters", player);
                        ctwServer.getSoundManager().playErrorSound(player);
                        return;
                    }
                    World source = player.getWorld();
                    String dest = args[2];
                    World cloned = ctwServer.getWorldManager().cloneWorld(source, dest);
                    if (cloned == null) {
                        ctwServer.getLangManager().sendMessage("incorrect-parameters", player);
                        ctwServer.getSoundManager().playErrorSound(player);
                        return;
                    }
                    ctwServer.getMapManager().cloneMap(source, cloned);
                    ctwServer.getLangManager().sendMessage("world-created", player);
                    return;
                }
                default:
                    ctwServer.getLangManager().sendText("commands.ctwsetup-map", player);
                    ctwServer.getSoundManager().playTipSound(player);
                    return;
            }
        }
        if ("mapconfig".equals(section)) {
            if (args.length < 2) {
                ctwServer.getLangManager().sendText("commands.ctwsetup-mapconfig", player);
                ctwServer.getSoundManager().playTipSound(player);
                return;
            }
            String action = args[1].toLowerCase();
            World w = player.getWorld();
            if (!ctwServer.getMapManager().exists(w.getName())) {
                ctwServer.getLangManager().sendMessage("not-in-map", player);
                return;
            }


            switch (action) {
                case "spawn": {
                    ctwServer.getMapManager().setSpawn(player.getLocation());
                    ctwServer.getLangManager().sendMessage("mapspawn-set", player);
                    ctwServer.getMapManager().setupTip(player);
                    return;
                }
                case "redspawn": {
                    ctwServer.getMapManager().setRedSpawn(player.getLocation());
                    ctwServer.getLangManager().sendMessage("redspawn-set", player);
                    ctwServer.getMapManager().setupTip(player);
                    return;
                }
                case "bluespawn": {
                    ctwServer.getMapManager().setBlueSpawn(player.getLocation());
                    ctwServer.getLangManager().sendMessage("bluespawn-set", player);
                    ctwServer.getMapManager().setupTip(player);
                    return;
                }
                case "maxplayers": {
                    if (args.length < 3) {
                        ctwServer.getLangManager().sendMessage("incorrect-parameters", player);
                        ctwServer.getSoundManager().playErrorSound(player);
                        return;
                    }
                    try {
                        int max = Integer.parseInt(args[2]);
                        if (max < 2) {
                            ctwServer.getLangManager().sendMessage("incorrect-parameters", player);
                            ctwServer.getSoundManager().playErrorSound(player);
                            return;
                        }
                        ctwServer.getMapManager().setMaxPlayers(w, max);
                        ctwServer.getLangManager().sendMessage("maxplayers-set", player);
                        ctwServer.getMapManager().setupTip(player);
                    } catch (NumberFormatException ex) {
                        ctwServer.getLangManager().sendMessage("incorrect-parameters", player);
                        ctwServer.getSoundManager().playErrorSound(player);
                    }
                    return;
                }
                case "redwinwool": {
                    ctwServer.getEventManager().registerSetupEvents(player, com.mauccio.bctws.listeners.EventManager.SetUpAction.RED_WIN_WOOL);
                    ctwServer.getLangManager().sendMessage("add-red-wool-winpoint.description", player);
                    ctwServer.getLangManager().sendMessage("add-red-wool-winpoint.help-0", player);
                    ctwServer.getLangManager().sendMessage("add-red-wool-winpoint.help-1", player);
                    ctwServer.getMapManager().setupTip(player);
                    return;
                }
                case "bluewinwool": {
                    ctwServer.getEventManager().registerSetupEvents(player, com.mauccio.bctws.listeners.EventManager.SetUpAction.BLUE_WIN_WOOL);
                    ctwServer.getLangManager().sendMessage("add-blue-wool-winpoint.description", player);
                    ctwServer.getLangManager().sendMessage("add-blue-wool-winpoint.help-0", player);
                    ctwServer.getLangManager().sendMessage("add-blue-wool-winpoint.help-1", player);
                    ctwServer.getMapManager().setupTip(player);

                    return;
                }
                case "rednoaccess": {
                    if (!requireWorldEdit(player) || !requireSelectionOrTip(player)) return;
                    Selection sel = getSelection(player);
                    if (ctwServer.getMapManager().isRedNoAccessArea(w, sel)) {
                        ctwServer.getLangManager().sendMessage("area-na-already-red", player);
                    } else {
                        ctwServer.getMapManager().addRedNoAccessArea(w, sel);
                        ctwServer.getLangManager().sendMessage("area-na-done", player);
                    }
                    return;
                }
                case "bluenoaccess": {
                    if (!requireWorldEdit(player) || !requireSelectionOrTip(player)) return;
                    Selection sel = getSelection(player);
                    if (ctwServer.getMapManager().isBlueNoAccessArea(w, sel)) {
                        ctwServer.getLangManager().sendMessage("area-na-already-blue", player);
                    } else {
                        ctwServer.getMapManager().addBlueNoAccessArea(w, sel);
                        ctwServer.getLangManager().sendMessage("area-na-done", player);
                    }
                    return;
                }
                case "protected": {
                    if (!requireWorldEdit(player) || !requireSelectionOrTip(player)) return;
                    Selection sel = getSelection(player);
                    if (ctwServer.getMapManager().isProtectedArea(w, sel)) {
                        ctwServer.getLangManager().sendMessage("area-na-already-protected", player);
                    } else {
                        ctwServer.getMapManager().addProtectedArea(w, sel);
                        ctwServer.getLangManager().sendMessage("area-na-done", player);
                    }
                    return;
                }
                case "weather": {
                    if (args.length < 3) {
                        ctwServer.getLangManager().sendMessage("incorrect-parameters", player);
                        ctwServer.getSoundManager().playErrorSound(player);
                        return;
                    }
                    String param = args[2].toLowerCase();
                    if (param.startsWith("fixed=sun")) {
                        ctwServer.getMapManager().setWeather(w, true, false);
                        ctwServer.getLangManager().sendMessage("sunny-set", player);
                        ctwServer.getMapManager().setupTip(player);
                    } else if (param.startsWith("fixed=storm")) {
                        ctwServer.getMapManager().setWeather(w, true, true);
                        ctwServer.getLangManager().sendMessage("storm-set", player);
                        ctwServer.getMapManager().setupTip(player);
                    } else if (param.startsWith("random")) {
                        ctwServer.getMapManager().setWeather(w, false, false);
                        ctwServer.getLangManager().sendMessage("random-set", player);
                        ctwServer.getMapManager().setupTip(player);
                    } else {
                        ctwServer.getLangManager().sendMessage("incorrect-parameters", player);
                        ctwServer.getSoundManager().playErrorSound(player);
                    }
                    return;
                }
                case "woolspawner": {
                    ctwServer.getEventManager().registerSetupEvents(player, com.mauccio.bctws.listeners.EventManager.SetUpAction.WOOL_SPAWNER);
                    ctwServer.getLangManager().sendMessage("add-wool-spawners.description", player);
                    ctwServer.getLangManager().sendMessage("add-wool-spawners.help-0", player);
                    ctwServer.getLangManager().sendMessage("add-wool-spawners.help-1", player);
                    ctwServer.getMapManager().setupTip(player);
                    return;
                }
                case "restore": {
                    if (!requireWorldEdit(player) || !requireSelectionOrTip(player)) return;
                    Selection sel = getSelection(player);
                    if(sel == null) return;
                    ctwServer.getMapManager().setRestaurationArea(sel);
                    ctwServer.getMapManager().setupTip(player);
                    return;
                }
                case "continue": {
                    ctwServer.getEventManager().unregisterSetUpEvents(player);
                    ctwServer.getLangManager().sendMessage("cmd-success", player);
                    ctwServer.getMapManager().setupTip(player);
                    return;
                }
                case "toggleleather": {
                    boolean active = !ctwServer.getMapManager().getKitarmour(w);
                    ctwServer.getMapManager().setKitarmour(w, active);
                    ctwServer.getLangManager().sendMessage(active ? "default-armour-on" : "default-armour-off", player);
                    ctwServer.getMapManager().setupTip(player);
                    return;
                }
                case "removeregion": {
                    if (!requireWorldEdit(player)) return;
                    ctwServer.getMapManager().removeRegion(player);
                    ctwServer.getMapManager().setupTip(player);
                    return;
                }
                case "no-drop": {
                    if (ctwServer.getMapManager().setNoDrop(player)) {
                        ctwServer.getLangManager().sendMessage("repeated-material-ok", player);
                        ctwServer.getMapManager().setupTip(player);
                    }
                    return;
                }
                default:
                    ctwServer.getLangManager().sendText("commands.ctwsetup-mapconfig", player);
                    ctwServer.getSoundManager().playTipSound(player);
                    return;
            }
        }
        if ("pool".equals(section)) {
            if (args.length < 2) {
                ctwServer.getLangManager().sendText("commands.ctwsetup-pool", player);
                ctwServer.getSoundManager().playTipSound(player);
                return;
            }
            String action = args[1].toLowerCase();
            switch (action) {
                case "add": {
                    if (args.length < 3) {
                        ctwServer.getLangManager().sendMessage("incorrect-parameters", player);
                        ctwServer.getSoundManager().playErrorSound(player);
                        return;
                    }
                    String poolName = args[2];
                    if (ctwServer.getPoolManager().addPool(poolName)) {
                        ctwServer.getLangManager().sendMessage("room-added.message-0", player);
                        ctwServer.getLangManager().sendMessage("room-added.help-1", player);
                    } else {
                        ctwServer.getLangManager().sendMessage("duplicated-room", player);
                    }
                    return;
                }
                case "remove": {
                    if (args.length < 3) {
                        ctwServer.getLangManager().sendMessage("incorrect-parameters", player);
                        ctwServer.getSoundManager().playErrorSound(player);
                        return;
                    }
                    String poolName = args[2];
                    if (!ctwServer.getPoolManager().exists(poolName)) {
                        ctwServer.getLangManager().sendMessage("room-doesnot-exists", player);
                        return;
                    }
                    if (ctwServer.getPoolManager().removePool(poolName)) {
                        ctwServer.getLangManager().sendMessage("cmd-success", player);
                    } else {
                        ctwServer.getLangManager().sendMessage("incorrect-parameters", player);
                        ctwServer.getSoundManager().playErrorSound(player);
                    }
                    return;
                }
                case "list": {
                    List<String> list = ctwServer.getPoolManager().listPools();
                    if (list.isEmpty()) {
                        ctwServer.getLangManager().sendMessage("room-list-empty", player);
                        return;
                    }
                    for (String line : list) player.sendMessage(line);
                    return;
                }
                case "addmap": {
                    if (args.length < 4) {
                        ctwServer.getLangManager().sendMessage("incorrect-parameters", player);
                        ctwServer.getSoundManager().playErrorSound(player);
                        return;
                    }
                    String poolName = args[2];
                    String mapName = args[3];
                    if (!ctwServer.getPoolManager().exists(poolName)) {
                        ctwServer.getLangManager().sendMessage("room-doesnot-exists", player);
                        return;
                    }
                    World map = ctwServer.getServer().getWorld(mapName);
                    if (map == null || !ctwServer.getMapManager().exists(mapName)) {
                        ctwServer.getLangManager().sendMessage("world-doesnot-exists", player);
                        return;
                    }
                    if (ctwServer.getPoolManager().hasMap(poolName, mapName)) {
                        ctwServer.getLangManager().sendMessage("room-already-has-this-map", player);
                        return;
                    }
                    if (ctwServer.getPoolManager().addMapToPool(poolName, mapName)) {
                        ctwServer.getLangManager().sendMessage("cmd-success", player);
                    } else {
                        ctwServer.getLangManager().sendMessage("incorrect-parameters", player);
                        ctwServer.getSoundManager().playErrorSound(player);
                    }
                    return;
                }
                case "removemap": {
                    if (args.length < 4) {
                        ctwServer.getLangManager().sendMessage("incorrect-parameters", player);
                        ctwServer.getSoundManager().playErrorSound(player);
                        return;
                    }
                    String poolName = args[2];
                    String mapName = args[3];
                    if (!ctwServer.getPoolManager().exists(poolName)) {
                        ctwServer.getLangManager().sendMessage("room-doesnot-exists", player);
                        return;
                    }
                    World map = ctwServer.getServer().getWorld(mapName);
                    if (map == null) {
                        ctwServer.getLangManager().sendMessage("world-doesnot-exists", player);
                        return;
                    }
                    if (!ctwServer.getPoolManager().hasMap(poolName, mapName)) {
                        ctwServer.getLangManager().sendMessage("room-doesnot-has-this-map", player);
                        return;
                    }
                    if (ctwServer.getPoolManager().removeMapFromPool(poolName, mapName)) {
                        ctwServer.getLangManager().sendMessage("cmd-success", player);
                    } else {
                        ctwServer.getLangManager().sendMessage("incorrect-parameters", player);
                        ctwServer.getSoundManager().playErrorSound(player);
                    }
                    return;
                }
                default:
                    ctwServer.getLangManager().sendText("commands.ctwsetup-pool", player);
                    ctwServer.getSoundManager().playTipSound(player);
                    return;
            }
        }
        if ("kit".equals(section)) {
            if (args.length < 2) {
                ctwServer.getLangManager().sendText("commands.ctwsetup-kit", player);
                ctwServer.getSoundManager().playTipSound(player);
                return;
            }
            if(!ctwServer.getConfigManager().isKitMenuEnabled()) {
                ctwServer.getLangManager().sendMessage("kits-not-enabled", player);
                ctwServer.getSoundManager().playErrorSound(player);
                return;
            }
            String action = args[1].toLowerCase();
            switch (action) {
                case "create": {
                    if (args.length < 3) {
                        ctwServer.getLangManager().sendMessage("incorrect-parameters", player);
                        ctwServer.getSoundManager().playErrorSound(player);
                        return;
                    }
                    String kitName = args[2];
                    if (ctwServer.getKitManager().kitExists(kitName)) {
                        ctwServer.getLangManager().sendMessage("kits-already-exists", player);
                        return;
                    }
                    ctwServer.getKitManager().createKit(kitName, player);
                    ctwServer.getLangManager().sendMessage("kit-edit-tip", player);
                    return;
                }
                case "edit": {
                    if (args.length < 3) {
                        ctwServer.getLangManager().sendMessage("incorrect-parameters", player);
                        ctwServer.getSoundManager().playErrorSound(player);
                        return;
                    }
                    String kitName = args[2];
                    if (!ctwServer.getKitManager().kitExists(kitName)) {
                        ctwServer.getLangManager().sendMessage("kit-doesnot-exists", player);
                        return;
                    }
                    ctwServer.getKitManager().openKitEditor(kitName, player);
                    ctwServer.getLangManager().sendMessage("cmd-success", player);
                    return;
                }
                case "delete": {
                    if (args.length < 3) {
                        ctwServer.getLangManager().sendMessage("incorrect-parameters", player);
                        ctwServer.getSoundManager().playErrorSound(player);
                        return;
                    }
                    String kitName = args[2];
                    if (!ctwServer.getKitManager().kitExists(kitName)) {
                        ctwServer.getLangManager().sendMessage("kit-doesnot-exists", player);
                        return;
                    }
                    ctwServer.getKitManager().deleteKit(kitName, player);
                    ctwServer.getLangManager().sendMessage("cmd-success", player);
                    return;
                }
                default:
                    ctwServer.getLangManager().sendText("commands.ctwsetup-kit", player);
                    ctwServer.getSoundManager().playTipSound(player);
                    return;
            }
        }
        ctwServer.getLangManager().sendText("commands.ctwsetup", player);
        ctwServer.getSoundManager().playTipSound(player);
    }

    public boolean isAllowedInGameCmd(String cmd) {
        return allowedInGameCmds.contains(cmd);
    }
}