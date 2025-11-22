package com.mauccio.bctws.game;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.mauccio.bctws.CTWServer;
import com.mauccio.bctws.map.MapManager;
import com.mauccio.bctws.map.PoolManager;
import com.mauccio.bctws.utils.Utils;
import com.sk89q.worldedit.bukkit.selections.CuboidSelection;
import com.sk89q.worldedit.bukkit.selections.Selection;

import java.io.File;
import java.util.*;
import java.util.logging.Level;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.Wool;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.*;

public class GameManager {

    int counter;

    public class Events {

        @SuppressWarnings("incomplete-switch")
        private boolean isProhibitedLocation(Location location, TeamManager.TeamId ti, Game game) {
            boolean ret = false;
            if (ti != null && ti != TeamManager.TeamId.SPECTATOR) {
                switch (ti) {
                    case BLUE:
                        for (Selection sel : game.blueProhibitedAreas) {
                            if (sel.contains(location)) {
                                ret = true;
                                break;
                            }
                        }
                        break;
                    case RED:
                        for (Selection sel : game.redProhibitedAreas) {
                            if (sel.contains(location)) {
                                ret = true;
                                break;
                            }
                        }
                        break;
                }
            }
            return ret;
        }

        public void cancelEditProtectedAreas(BlockPlaceEvent e) {
            Game game = worldGame.get(e.getBlock().getWorld());
            if (game != null) {
                if (isProtected(e.getBlock(), game)) {
                    e.setCancelled(true);
                } else {
                    TeamManager.TeamId ti = ctwServer.getPlayerManager().getTeamId(e.getPlayer());
                    if (isProhibitedLocation(e.getBlock().getLocation(), ti, game)) {
                        e.setCancelled(true);
                    }
                }
            }
        }

        public void cancelEditProtectedAreas(BlockBreakEvent e) {
            Game game = worldGame.get(e.getBlock().getWorld());
            if (game != null) {
                Material type = e.getBlock().getType();
                List<String> breakable = ctwServer.getConfigManager().getBreakableBlocks();
                if (breakable.contains(type.name())) {
                    return;
                }
                if (isProtected(e.getBlock(), game)) {
                    e.setCancelled(true);
                } else {
                    TeamManager.TeamId ti = ctwServer.getPlayerManager().getTeamId(e.getPlayer());
                    if (isProhibitedLocation(e.getBlock().getLocation(), ti, game)) {
                        e.setCancelled(true);
                    }
                }
            }
        }

        public void cancelCrafting(CraftItemEvent e) {
            if (!(e.getWhoClicked() instanceof Player)) return;
            Player plr = (Player) e.getWhoClicked();

            Game game = worldGame.get(plr.getWorld());
            if (game == null) { return;}
            ItemStack result = e.getCurrentItem();
            if(result == null) return;

            List<String> noCrafteable = ctwServer.getConfigManager().getNoCrafteableItems();

            if(noCrafteable.contains(result.getType().name())) {
                e.setCancelled(true);
            }
        }

        public void cancelUseBukketOnProtectedAreas(PlayerBucketEmptyEvent e) {
            Game game = worldGame.get(e.getBlockClicked().getWorld());
            if (game != null) {
                if (isProtected(e.getBlockClicked(), game)) {
                    e.setCancelled(true);
                } else {
                    TeamManager.TeamId ti = ctwServer.getPlayerManager().getTeamId(e.getPlayer());
                    if (isProhibitedLocation(e.getBlockClicked().getLocation(), ti, game)) {
                        e.setCancelled(true);
                    }
                }
            }
        }

        private boolean isProtected(Block block, Game game) {
            boolean ret = false;
            Location loc = block.getLocation();
            if (block.getType() == Material.MOB_SPAWNER) {
                ret = true;
            } else {
                if (game.restaurationArea != null && !game.restaurationArea.contains(loc)) {
                    ret = true;
                } else {
                    for (Selection sel : game.mapData.protectedAreas) {
                        loc.setWorld(sel.getWorld());
                        if (sel.contains(loc)) {
                            ret = true;
                            break;
                        }
                    }
                }

            }
            return ret;
        }
    }

    /**
     * Game information.
     */
    private class Target {

        TeamManager.TeamId team;
        DyeColor color;
        Location location;
        boolean completed;
    }

    public enum GameState {

        IN_GAME, FINISHED, NOT_IN_GAME
    }

    public class Game {

        String poolName;
        int redPlayers;
        int bluePlayers;
        public MapManager.MapData mapData;
        World world;
        TreeMap<Location, Target> targets;
        BukkitTask bt;
        int step;
        TreeSet<Selection> blueProhibitedAreas;
        TreeSet<Selection> redProhibitedAreas;
        private Selection restaurationArea;
        private final Scoreboard board;
        private GameState state;

        public Game() {
            blueProhibitedAreas = new TreeSet<>(new Utils.SelectionComparator());
            redProhibitedAreas = new TreeSet<>(new Utils.SelectionComparator());
            board = Bukkit.getScoreboardManager().getNewScoreboard();
            state = GameState.IN_GAME;
            ctwServer.getNametagManager().initializeTeams(board);
        }

        public String getPoolName() {
            return poolName;
        }

        public int getRedPlayers() {
            return redPlayers;
        }

        public int getBluePlayers() {
            return bluePlayers;
        }

        public MapManager.MapData getMapData() {
            return mapData;
        }

        public World getWorld() {
            return world;
        }

        public TreeMap<Location, Target> getTargets() {
            return targets;
        }

        public BukkitTask getBt() {
            return bt;
        }

        public int getStep() {
            return step;
        }

        public TreeSet<Selection> getBlueProhibitedAreas() {
            return blueProhibitedAreas;
        }

        public TreeSet<Selection> getRedProhibitedAreas() {
            return redProhibitedAreas;
        }

        public GameState getState() {
            return state;
        }

        public Scoreboard getBoard() {
            return board;
        }

        public Selection getRestaurationArea() {
            return restaurationArea;
        }
    }

    private final CTWServer ctwServer;
    private final TreeMap<String, Game> games;
    private final TreeMap<World, Game> worldGame;
    public final Events events;
    private final String decorator;

    public GameManager(CTWServer ctwServer) {
        this.ctwServer = ctwServer;
        games = new TreeMap<>();
        events = new Events();
        worldGame = new TreeMap<>(new Utils.WorldComparator());
        decorator = ctwServer.getConfig().getString("message-decorator");

        Bukkit.getScheduler().runTaskTimer(ctwServer, new Runnable() {
            @Override
            public void run() {
                spawnWool(games);
            }
        }, 300, 300);

        Bukkit.getScheduler().runTaskTimer(ctwServer, new Runnable() {
            @Override
            public void run() {
                controlPlayers();
            }
        }, 40, 40);
    }

    public Game getGameByPool(String poolName) {
        return games.get(poolName);
    }

    /**
     * Simple player control.
     */
    private void controlPlayers() {
        for (Player player : ctwServer.getServer().getOnlinePlayers()) {
            if (ctwServer.getPlayerManager().getTeamId(player) == null) {
                if (ctwServer.getMapManager().isMap(player.getWorld())
                        && !player.hasPermission("ctw.admin")) {
                    ctwServer.getLogger().log(Level.INFO,
                            "Unexpected event: Player {0} has no team and was on {1}",
                            new Object[]{player.getName(), player.getWorld().getName()});

                    if (ctwServer.getConfigManager().isEditMode()) {
                        World centralWorld = Bukkit.getWorlds().get(0);
                        player.teleport(centralWorld.getSpawnLocation());
                    } else {
                        PoolManager.Pool active = ctwServer.getPoolManager().getActivePool();
                        if (active != null) {
                            String poolName = active.getName();
                            Game game = games.get(poolName);
                            if (game == null) {
                                game = addGame(poolName);
                            }

                            ctwServer.getPlayerManager().addPlayerTo(player, TeamManager.TeamId.SPECTATOR);

                            Location spawn = game.mapData.mapSpawn;
                            spawn.setWorld(game.world);
                            player.teleport(spawn);
                            player.setScoreboard(game.board);

                            ctwServer.getLangManager().sendMessage("assigned-spectator", player);
                        }
                    }
                }
            } else {
                World world = player.getWorld();
                boolean inGame = worldGame.containsKey(world);

                if (!inGame) {
                    ctwServer.getLogger().log(Level.INFO,
                            "Unexpected event: Player {0} has team and was on {1}",
                            new Object[]{player.getName(), world.getName()});
                    ctwServer.getPlayerManager().clearTeam(player);
                }
            }
        }
    }

    /**
     * Moves a player into a pool.
     *
     * @param player   the player who must be moved into a Room.
     * @param poolName Pool's name.
     */
    public void movePlayerToRoom(Player player, String poolName) {
        World targetWorld = ctwServer.getPoolManager().getCurrentWorld(poolName);
        if (targetWorld != null) {
            ctwServer.getLangManager().sendVerbatimTextToWorld(
                    ctwServer.getLangManager().getText("player-join-map")
                            .replace("%PLAYER%", ctwServer.getPlayerManager().getChatColor(player) + player.getName()),
                    targetWorld, player);

            String mapName = ctwServer.getPoolManager().getPool(poolName).getCurrentMap();
            MapManager.MapData mapData = ctwServer.getMapManager().getMapData(mapName);

            Location spawn = new Location(targetWorld,
                    mapData.mapSpawn.getX(),
                    mapData.mapSpawn.getY(),
                    mapData.mapSpawn.getZ(),
                    mapData.mapSpawn.getYaw(),
                    mapData.mapSpawn.getPitch());

            player.teleport(spawn);

            ctwServer.getPlayerManager().addPlayerTo(player, TeamManager.TeamId.SPECTATOR);
            Game game = ctwServer.getGameManager().addGame(poolName);
            player.setScoreboard(game.board);

            ctwServer.getTitleManager().sendJoinRoom(player);
            ctwServer.getLangManager().sendMessage("use-join", player);
        } else {
            ctwServer.getLangManager().sendMessage("room-has-no-map", player);
        }
    }


    /**
     * Choose team logic.
     *
     * @param player the player who must be moved into the new team.
     * @param teamId Id of the team where player must be put or null for random.
     */
    public boolean joinInTeam(Player player, TeamManager.TeamId teamId) {

        if (teamId == TeamManager.TeamId.SPECTATOR || ctwServer.hasPermission(player, "choseteam")) {
            movePlayerTo(player, teamId);
        } else {
            ctwServer.getLangManager().sendMessage("not-teamselect-perm", player);
        }
        return true;
    }

    /**
     * Moves a player into a new team.
     *
     * @param player the player who must be moved into the new team.
     * @param teamId Id of the team where player must be put or null for random.
     */
    public void movePlayerTo(Player player, TeamManager.TeamId teamId) {
        Game game = worldGame.get(player.getWorld());
        if (game == null || game.mapData == null) {
            PoolManager.Pool active = ctwServer.getPoolManager().getActivePool();
            if (active == null) return;
            String poolName = active.getName();
            game = addGame(poolName);
        }
        String poolName = game.poolName;

        if (teamId != TeamManager.TeamId.SPECTATOR
                && !ctwServer.hasPermission(player, "override-limit")
                && getPlayersIn(poolName) >= game.mapData.maxPlayers) {
            ctwServer.getLangManager().sendMessage("no-free-slots", player);
            return;
        }

        TeamManager.TeamId prevTeam = ctwServer.getPlayerManager().getTeamId(player);
        if (prevTeam != null) {
            switch (prevTeam) {
                case BLUE: game.bluePlayers--; break;
                case RED:  game.redPlayers--;  break;
            }
        }

        if (teamId == null) {
            if (game.redPlayers <= game.bluePlayers) {
                teamId = TeamManager.TeamId.RED;
                ctwServer.getTitleManager().sendJoinRed(player);
                ctwServer.getSoundManager().playTeamJoinSound(player);
            } else {
                teamId = TeamManager.TeamId.BLUE;
                ctwServer.getTitleManager().sendJoinBlue(player);
                ctwServer.getSoundManager().playTeamJoinSound(player);
            }
        }

        String advert;
        switch (teamId) {
            case BLUE:
                game.bluePlayers++;
                advert = ctwServer.getLangManager().getText("player-join-blue");
                break;
            case RED:
                game.redPlayers++;
                advert = ctwServer.getLangManager().getText("player-join-red");
                break;
            default:
                advert = ctwServer.getLangManager().getText("player-join-spect");
        }

        ctwServer.getPlayerManager().addPlayerTo(player, teamId);

        if (ctwServer.getDBManager() != null) {
            String playerName = player.getName();
            String finalAdvert = advert;
            Bukkit.getScheduler().runTaskAsynchronously(ctwServer, () -> {
                ctwServer.getDBManager().addEvent(playerName, "JOIN|" + poolName + "|" + finalAdvert);
            });
        }

        player.sendMessage(advert.replace("%PLAYER%", player.getName()));
        takeToSpawn(player);
        player.setScoreboard(game.board);
        updateScoreBoard(game);
        ctwServer.getNametagManager().updateNametag(player, teamId, game.board);

        if (teamId != TeamManager.TeamId.SPECTATOR) {
            if (game.mapData.kitArmour) {
                ItemStack air = new ItemStack(Material.AIR);
                player.getInventory().setBoots(air);
                player.getInventory().setChestplate(air);
                player.getInventory().setHelmet(air);
                player.getInventory().setLeggings(air);
            }

            ItemStack[] kit = ctwServer.getKitManager().getKit(player);
            if (kit == null || kit.length == 0) {
                kit = ctwServer.getKitManager().getGlobalKitYAML();
            }

            if (kit != null && kit.length > 0) {
                player.getInventory().setContents(kit);
            } else {
                ctwServer.getLogger().info(ChatColor.translateAlternateColorCodes('&',
                        "&4Global Kit is not set, use &c/saveglobalkit &4to set!"));
            }
        }
    }

    /**
     * Moves a player outside of a Game.
     *
     * @param player the player who must be moved outside of a Game.
     */

    public void playerLeftGame(Player player) {
        String poolName;
        Game game = worldGame.get(player.getWorld());
        if (game != null) {
            poolName = game.poolName;
            TeamManager.TeamId teamId = ctwServer.getPlayerManager().getTeamId(player);

            if (teamId != null) {
                for (Team team : game.board.getTeams()) {
                    team.removeEntry(player.getName());
                }
                switch (teamId) {
                    case BLUE: if (game.bluePlayers > 0) game.bluePlayers--; break;
                    case RED:  if (game.redPlayers > 0) game.redPlayers--;  break;
                }
            }
        } else {
            poolName = null;
        }

        if (ctwServer.getDBManager() != null && poolName != null) {
            String playerName = player.getName();
            Bukkit.getScheduler().runTaskAsynchronously(ctwServer, () -> {
                ctwServer.getDBManager().addEvent(playerName, "LEFT|" + poolName);
            });
        }

        ctwServer.getLangManager().sendVerbatimTextToWorld(
                ctwServer.getLangManager().getText("player-left-map")
                        .replace("%PLAYER%", ctwServer.getPlayerManager().getChatColor(player) + player.getName()),
                player.getWorld(), player);

        ctwServer.getPlayerManager().clearTeam(player);
        player.setDisplayName(player.getName());
        player.setPlayerListName(player.getName());
        for (Player other : ctwServer.getServer().getOnlinePlayers()) {
            other.showPlayer(player);
        }

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Connect");
        out.writeUTF("lobby");
        player.sendPluginMessage(ctwServer, "BungeeCord", out.toByteArray());

        Scoreboard emptyBoard = Bukkit.getScoreboardManager().getNewScoreboard();
        player.setScoreboard(emptyBoard);
    }

    public int getPlayersIn(String poolName) {
        Game game = games.get(poolName);
        if (game == null || game.world == null) {
            return 0;
        }
        return game.world.getPlayers().size();
    }

    public void checkForSpectator(Player player) {

        for (Player spectator : player.getWorld().getPlayers()) {
            if (ctwServer.getPlayerManager().getTeamId(spectator) != TeamManager.TeamId.SPECTATOR) {
                continue;
            }
            if (player.getLocation().distance(spectator.getLocation()) < 4) {
                spectator.teleport(spectator.getLocation().add(0, 5, 0));
                spectator.setFlying(true);
            }
        }
    }

    public void denyEnterToProhibitedZone(PlayerMoveEvent e) {
        TeamManager.TeamId ti = ctwServer.getPlayerManager().getTeamId(e.getPlayer());
        if (ti == null || ti == TeamManager.TeamId.SPECTATOR || e.getPlayer().getGameMode().equals(GameMode.SPECTATOR)) {
            return;
        }

        Game game = worldGame.get(e.getPlayer().getWorld());
        if (game != null) {
            switch (ti) {
                case BLUE:
                    for (Selection sel : game.blueProhibitedAreas) {
                        if (!sel.contains(e.getTo())) {
                            continue;
                        }
                        if (sel.contains(e.getFrom())) {
                            e.getPlayer().teleport(getBlueSpawn(game.poolName));
                        } else {
                            e.setCancelled(true);
                            e.getPlayer().teleport(e.getFrom());
                            if (ctwServer.getConfigManager().isProtectedZoneMsg()) {
                                ctwServer.getLangManager().sendMessage("prohibited-area", e.getPlayer());
                            }
                        }
                    }
                    checkForSpectator(e.getPlayer());
                    break;

                case RED:
                    for (Selection sel : game.redProhibitedAreas) {
                        if (!sel.contains(e.getTo())) {
                            continue;
                        }
                        if (sel.contains(e.getFrom())) {
                            e.getPlayer().teleport(getRedSpawn(game.poolName));
                        } else {
                            e.setCancelled(true);
                            e.getPlayer().teleport(e.getFrom());
                            if (ctwServer.getConfigManager().isProtectedZoneMsg()) {
                                ctwServer.getLangManager().sendMessage("prohibited-area", e.getPlayer());
                            }
                        }
                    }
                    checkForSpectator(e.getPlayer());
                    break;
            }
        }
    }

    public Location getRedSpawn(String poolName) {
        Game game = games.get(poolName);
        if (game == null || game.mapData == null) return null;
        return new Location(game.world, game.mapData.redSpawn.getBlockX(),
                game.mapData.redSpawn.getBlockY(), game.mapData.redSpawn.getBlockZ(), game.mapData.redSpawn.getYaw(), game.mapData.redSpawn.getPitch());
    }

    public Location getBlueSpawn(String poolName) {
        Game game = games.get(poolName);
        if (game == null || game.mapData == null) return null;
        return new Location(game.world, game.mapData.blueSpawn.getBlockX(),
                game.mapData.blueSpawn.getBlockY(), game.mapData.blueSpawn.getBlockZ(), game.mapData.blueSpawn.getYaw(), game.mapData.blueSpawn.getPitch());
    }

    public GameState getState(String poolName) {
        Game game = games.get(poolName);
        if (game == null) {
            return GameState.NOT_IN_GAME;
        } else {
            return game.state;
        }
    }

    /**
     * Crea un nuevo Game para la Pool especificada.
     *
     * @param poolName nombre de la Pool
     * @return Game creado
     */
    public Game addGame(String poolName) {
        Game game = new Game();
        game.poolName = poolName;

        PoolManager.Pool pool = ctwServer.getPoolManager().getPool(poolName);
        if (pool == null) {
            ctwServer.getLogger().warning("No existe la Pool: " + poolName);
            return null;
        }

        String mapName = pool.getCurrentMap();
        game.mapData = ctwServer.getMapManager().getMapData(mapName);

        World world = Bukkit.getWorld("maps/" + mapName);
        if (world == null) {
            File uidFile = new File(Bukkit.getWorldContainer(), "maps/" + mapName + "/uid.dat");
            if (uidFile.exists()) uidFile.delete();
            world = Bukkit.createWorld(new WorldCreator("maps/" + mapName));
        }
        if (world == null) {
            ctwServer.getLogger().severe("No se pudo cargar el mundo para " + mapName);
            return null;
        }
        game.world = world;

        games.put(poolName, game);
        worldGame.put(game.world, game);
        game.targets = new TreeMap<>(new Utils.LocationBlockComparator());

        for (String color : game.mapData.redWoolWinPoints.keySet()) {
            Target t = new Target();
            t.color = DyeColor.valueOf(color);
            Location tempLoc = game.mapData.redWoolWinPoints.get(color);
            t.location = new Location(game.world, tempLoc.getBlockX(),
                    tempLoc.getBlockY(), tempLoc.getBlockZ());
            t.team = TeamManager.TeamId.RED;
            game.targets.put(t.location, t);
        }

        for (String color : game.mapData.blueWoolWinPoints.keySet()) {
            Target t = new Target();
            t.color = DyeColor.valueOf(color);
            Location tempLoc = game.mapData.blueWoolWinPoints.get(color);
            t.location = new Location(game.world, tempLoc.getBlockX(),
                    tempLoc.getBlockY(), tempLoc.getBlockZ());
            t.team = TeamManager.TeamId.BLUE;
            game.targets.put(t.location, t);
        }

        if (game.mapData.blueInaccessibleAreas != null) {
            for (Selection sel : game.mapData.blueInaccessibleAreas) {
                game.blueProhibitedAreas.add(new CuboidSelection(game.world,
                        sel.getNativeMinimumPoint(), sel.getNativeMaximumPoint()));
            }
        }
        if (game.mapData.redInaccessibleAreas != null) {
            for (Selection sel : game.mapData.redInaccessibleAreas) {
                game.redProhibitedAreas.add(new CuboidSelection(game.world,
                        sel.getNativeMinimumPoint(), sel.getNativeMaximumPoint()));
            }
        }

        if (game.mapData.restaurationArea != null) {
            game.restaurationArea = new CuboidSelection(game.world,
                    game.mapData.restaurationArea.getNativeMinimumPoint(),
                    game.mapData.restaurationArea.getNativeMaximumPoint());
        }

        updateScoreBoard(game);

        if (game.mapData.weather.fixed) {
            game.world.setStorm(game.mapData.weather.storm);
        }

        return game;
    }


    public Game createGame(PoolManager.Pool pool, World world, MapManager.MapData mapData) {
        if (pool == null || world == null || mapData == null) {
            ctwServer.getLogger().severe("createGame: parámetros nulos pool=" + (pool==null) + " world=" + (world==null) + " mapData=" + (mapData==null));
            return null;
        }

        Game game = new Game();
        game.poolName = pool.getName();
        game.mapData = mapData;
        game.world = world;
        game.targets = new TreeMap<>(new Utils.LocationBlockComparator());

        for (String color : mapData.redWoolWinPoints.keySet()) {
            Target t = new Target();
            t.color = DyeColor.valueOf(color);
            Location tempLoc = mapData.redWoolWinPoints.get(color);
            t.location = new Location(world, tempLoc.getBlockX(), tempLoc.getBlockY(), tempLoc.getBlockZ());
            t.team = TeamManager.TeamId.RED;
            game.targets.put(t.location, t);
        }
        for (String color : mapData.blueWoolWinPoints.keySet()) {
            Target t = new Target();
            t.color = DyeColor.valueOf(color);
            Location tempLoc = mapData.blueWoolWinPoints.get(color);
            t.location = new Location(world, tempLoc.getBlockX(), tempLoc.getBlockY(), tempLoc.getBlockZ());
            t.team = TeamManager.TeamId.BLUE;
            game.targets.put(t.location, t);
        }

        if (mapData.blueInaccessibleAreas != null) {
            for (Selection sel : mapData.blueInaccessibleAreas) {
                game.blueProhibitedAreas.add(new CuboidSelection(world,
                        sel.getNativeMinimumPoint(), sel.getNativeMaximumPoint()));
            }
        }
        if (mapData.redInaccessibleAreas != null) {
            for (Selection sel : mapData.redInaccessibleAreas) {
                game.redProhibitedAreas.add(new CuboidSelection(world,
                        sel.getNativeMinimumPoint(), sel.getNativeMaximumPoint()));
            }
        }
        if (mapData.restaurationArea != null) {
            game.restaurationArea = new CuboidSelection(world,
                    mapData.restaurationArea.getNativeMinimumPoint(),
                    mapData.restaurationArea.getNativeMaximumPoint());
        }

        games.put(pool.getName(), game);
        worldGame.put(world, game);

        updateScoreBoard(game);

        if (mapData.weather.fixed) {
            world.setStorm(mapData.weather.storm);
        }

        ctwServer.getLogger().info("Game creado en pool " + pool.getName());
        return game;
    }

    public Game getGameByWorld(World world) {
        return worldGame.get(world);
    }

    public void removeGame(String poolName) {
        Game game = games.remove(poolName);
        if (game != null) {
            worldGame.remove(game.world);
            Bukkit.unloadWorld(game.world, false);
        }
    }


    public void takeToSpawn(Player player) {
        Game game = worldGame.get(player.getWorld());
        if (game == null || game.mapData == null) return;
        TeamManager.TeamId teamId = ctwServer.getPlayerManager().getTeamId(player);
        Location spawn;
        if (teamId != null) {
            switch (teamId) {
                case BLUE:
                    spawn = game.mapData.blueSpawn;
                    break;
                case RED:
                    spawn = game.mapData.redSpawn;
                    break;
                default:
                    spawn = game.mapData.mapSpawn;
            }
            spawn.setWorld(game.world);
            player.teleport(spawn);
        }
    }

    public void checkTarget(InventoryClickEvent e) {
        checkTarget((Player) e.getWhoClicked(), e.getCurrentItem());
    }

    public void checkTarget(PlayerPickupItemEvent e) {
        checkTarget(e.getPlayer(), e.getItem().getItemStack());
    }

    @SuppressWarnings("incomplete-switch")
    public void cancelProtectedChest(InventoryOpenEvent e) {
        Player player = (Player) e.getPlayer();
        Game game = worldGame.get(player.getWorld());
        if (game != null && (e.getInventory().getHolder() instanceof Chest
                || e.getInventory().getHolder() instanceof DoubleChest)) {
            TeamManager.TeamId teamId = ctwServer.getPlayerManager().getTeamId(player);
            Location chestLocation;
            if (e.getInventory().getHolder() instanceof Chest) {
                Chest chest = (Chest) e.getInventory().getHolder();
                chestLocation = chest.getLocation();
            } else {
                DoubleChest chest = (DoubleChest) e.getInventory().getHolder();
                chestLocation = chest.getLocation();
            }
            switch (teamId) {
                case BLUE:
                    for (Selection sel : game.blueProhibitedAreas) {
                        if (sel.contains(chestLocation)) {
                            e.setCancelled(true);
                            break;
                        }
                    }
                    break;
                case RED:
                    for (Selection sel : game.redProhibitedAreas) {
                        if (sel.contains(chestLocation)) {
                            e.setCancelled(true);
                            break;
                        }
                    }
                    break;
            }
        }
    }

    public void onGameRespawn(PlayerRespawnEvent e) {
        PoolManager.Pool activePool = ctwServer.getPoolManager().getActivePool();
        if (activePool == null) return;

        String poolName = activePool.getName();
        TeamManager.TeamId ti = ctwServer.getPlayerManager().getTeamId(e.getPlayer());
        if (ti == null) return;

        switch (ti) {
            case RED:
                e.setRespawnLocation(ctwServer.getGameManager().getRedSpawn(poolName));
                ctwServer.getPlayerManager().disguise(e.getPlayer(), TeamManager.TeamId.RED);
                break;
            case BLUE:
                e.setRespawnLocation(ctwServer.getGameManager().getBlueSpawn(poolName));
                ctwServer.getPlayerManager().disguise(e.getPlayer(), TeamManager.TeamId.BLUE);
                break;
            case SPECTATOR:
                Game game = games.get(poolName);
                if (game != null && game.mapData != null) {
                    Location spawn = game.mapData.mapSpawn;
                    spawn.setWorld(game.world);
                    e.setRespawnLocation(spawn);
                }
                return;
            default:
                return;
        }

        String mapName = activePool.getCurrentMap();

        if (ctwServer.getMapManager().getKitarmour(mapName)) {
            ItemStack air = new ItemStack(Material.AIR);
            e.getPlayer().getInventory().setBoots(air);
            e.getPlayer().getInventory().setChestplate(air);
            e.getPlayer().getInventory().setHelmet(air);
            e.getPlayer().getInventory().setLeggings(air);
        }

        ItemStack[] kit = ctwServer.getKitManager().getKit(e.getPlayer());
        if (kit != null && kit.length > 0) {
            e.getPlayer().getInventory().setContents(kit);
        } else {
            ItemStack[] globalKit = ctwServer.getKitManager().getGlobalKitYAML();
            if (globalKit != null && globalKit.length > 0) {
                e.getPlayer().getInventory().setContents(globalKit);
            } else {
                ctwServer.getLangManager().sendMessage("global-kit-error", e.getPlayer());
            }
        }


    }

    @SuppressWarnings({ "incomplete-switch", "deprecation" })
    public void checkTarget(Player player, ItemStack is) {
        Game game = worldGame.get(player.getWorld());
        if (game != null) {
            if (player.getInventory().containsAtLeast(is, 1)) {
                return;
            }
            if (is == null) {
                return;
            }
            if (is.getType() == Material.WOOL) {
                Wool wool = new Wool(is.getTypeId(), is.getData().getData());
                String woolName = ctwServer.getLangManager().getWoolName(wool.getColor());
                String message = ctwServer.getLangManager().getText("wool-pickup-message")
                        .replace("%PLAYER%", ctwServer.getPlayerManager().getChatColor(player) + player.getName())
                        .replace("%WOOL%", Utils.toChatColor(wool.getColor()) + woolName);
                switch (ctwServer.getPlayerManager().getTeamId(player)) {
                    case BLUE:
                        for (String colorName : game.mapData.blueWoolWinPoints.keySet()) {
                            if (colorName.equals(wool.getColor().name())) {
                                ctwServer.getLangManager().sendVerbatimMessageToTeam(message, player);
                                for (Player players : game.world.getPlayers()) {
                                    String colored = ctwServer.getPlayerManager().getChatColor(player) + player.getName();
                                    String woolTitle = Utils.toChatColor(wool.getColor()) + woolName;
                                    ctwServer.getTitleManager().sendWoolPickup(players, colored, woolTitle);
                                    ctwServer.getSoundManager().playWoolPickupSound(players);
                                }
                                break;
                            }
                        }
                        break;
                    case RED:
                        for (String colorName : game.mapData.redWoolWinPoints.keySet()) {
                            if (colorName.equals(wool.getColor().name())) {
                                ctwServer.getLangManager().sendVerbatimMessageToTeam(message, player);
                                for (Player players : game.world.getPlayers()) {
                                    String colored = ctwServer.getPlayerManager().getChatColor(player) + player.getName();
                                    String woolTitle = Utils.toChatColor(wool.getColor()) + woolName;
                                    ctwServer.getTitleManager().sendWoolPickup(players, colored, woolTitle);
                                    ctwServer.getSoundManager().playWoolPickupSound(players);
                                }
                                break;
                            }
                        }
                        break;
                }

                if (ctwServer.getDBManager() != null) {
                    String playerName = player.getName();
                    Bukkit.getScheduler().runTaskAsynchronously(ctwServer, new Runnable() {
                        @Override
                        public void run() {
                            ctwServer.getDBManager().addEvent(playerName, "WOOL-PICKUP|" + message);
                        }
                    });
                }
            }
        }
    }

    public void advanceGame(World world) {
        Game game = worldGame.get(world);
        if (game != null) {
            game.step = 10;
            for (Player player : game.world.getPlayers()) {
                if (ctwServer.getConfig().getBoolean("keep-teams-on-win")) {
                    player.setGameMode(GameMode.SPECTATOR);
                    ctwServer.getPlayerManager().clearInventory(player);
                    player.setAllowFlight(true);
                    player.setFlying(true);
                    if (!player.isOnGround()) {
                        player.teleport(player.getLocation().add(0, 0.5, 0));
                    }
                } else {
                    ctwServer.getPlayerManager().addPlayerTo(player, TeamManager.TeamId.SPECTATOR);
                }
            }
            startNewRound(game);
        }
    }

    @SuppressWarnings("deprecation")
    public void checkTarget(BlockPlaceEvent e) {
        Game game = worldGame.get(e.getBlock().getWorld());
        if (game != null) {
            Target t = game.targets.get(e.getBlock().getLocation());

            if (t != null) {
                if (e.getBlock().getType() == Material.WOOL) {
                    Wool wool = new Wool(e.getBlock().getType(), e.getBlock().getData());

                    if (wool.getColor() == t.color && t.team == ctwServer.getPlayerManager().getTeamId(e.getPlayer())) {
                        e.setCancelled(false);
                        t.completed = true;

                        if (!decorator.isEmpty()) {
                            ctwServer.getLangManager().sendVerbatimTextToWorld(ChatColor.GOLD + "" + ChatColor.BOLD + decorator, e.getBlock().getWorld(), null);
                        }

                        String woolName = ctwServer.getLangManager().getWoolName(t.color);
                        String winText = ctwServer.getLangManager().getText("win-wool-placed")
                                .replace("%PLAYER%", e.getPlayer().getName())
                                .replace("%WOOL%", woolName);
                        ctwServer.getLangManager().sendVerbatimTextToWorld(winText, e.getBlock().getWorld(), null);

                        for (Player players : game.world.getPlayers()) {
                            String colored = ctwServer.getPlayerManager().getChatColor(e.getPlayer()) + e.getPlayer().getName();
                            String woolTitle = Utils.toChatColor(wool.getColor()) + woolName;
                            ctwServer.getTitleManager().sendWoolPlaced(players, colored, woolTitle);
                            ctwServer.getSoundManager().playWinWoolSound(players);
                        }
                        checkForWin(game);

                        if (!decorator.isEmpty()) {
                            ctwServer.getLangManager().sendVerbatimTextToWorld(ChatColor.GOLD + "" + ChatColor.BOLD + decorator, e.getBlock().getWorld(), null);
                        }

                        Utils.firework(ctwServer, e.getBlock().getLocation(),
                                wool.getColor().getColor(), wool.getColor().getColor(), wool.getColor().getColor(),
                                FireworkEffect.Type.BALL_LARGE);
                        updateScoreBoard(game);
                        if (ctwServer.getDBManager() != null) {
                            String playerName = e.getPlayer().getName();
                            String msg = ctwServer.getLangManager().getText("player-messages.add-points-capture");
                            e.getPlayer().sendMessage(msg);
                            Bukkit.getScheduler().runTaskAsynchronously(ctwServer, new Runnable() {
                                @Override
                                public void run() {
                                    ctwServer.getDBManager().addEvent(playerName, "WOOL-CAPTURE|" + t.color.toString() + "|" + game.poolName + "|" + winText);
                                    ctwServer.getDBManager().incScore(playerName, ctwServer.getScores().capture);
                                    if(ctwServer.getEconomy() != null) {
                                        ctwServer.getEconomy().depositPlayer(e.getPlayer(), ctwServer.getScores().coins_capture);
                                        String msgCoins = ctwServer.getLangManager().getText("player-messages.add-coins.capture");
                                        e.getPlayer().sendMessage(msgCoins);
                                    }
                                    ctwServer.getDBManager().incWoolCaptured(playerName, 1);
                                }
                            });
                        }
                    }
                }
            }
        }
    }

    public Scoreboard getBoardForWorld(World world) {
        Game game = worldGame.get(world);
        return (game != null) ? game.board : null;
    }

    public void updateScoreBoard(Game game) {
        Scoreboard board = game.board;

        Objective old = board.getObjective("wools");
        if (old != null) old.unregister();

        Objective obj = board.registerNewObjective("wools", "dummy");
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        obj.setDisplayName(ctwServer.getLangManager().getText("scoreboard.title"));
        List<String> lines = ctwServer.getLangManager().getStringList("scoreboard.lines");
        int score = lines.size() + game.targets.size();

        for (String raw : lines) {
            if (raw.equalsIgnoreCase("%BLUE_WOOLS%")) {
                for (String woolLine : buildWoolLines(game, TeamManager.TeamId.BLUE)) {
                    addScoreLine(obj, woolLine, score--);
                }
                continue;
            }
            if (raw.equalsIgnoreCase("%RED_WOOLS%")) {
                for (String woolLine : buildWoolLines(game, TeamManager.TeamId.RED)) {
                    addScoreLine(obj, woolLine, score--);
                }
                continue;
            }
            String spaceFormat = ctwServer.getPoolManager().getCurrentMap() != null ? ctwServer.getPoolManager().getCurrentMap().replace("_", " ") : "";
            String line = raw
                    .replace("%BLUE_TEAM_NAME%", ctwServer.getLangManager().getText("scoreboard.blue-team-name"))
                    .replace("%RED_TEAM_NAME%", ctwServer.getLangManager().getText("scoreboard.red-team-name"))

                    .replace("%MAP_NAME%", spaceFormat)
                    .replace("%SERVER_IP%", ctwServer.getLangManager().getText("server-ip"));

            addScoreLine(obj, line, score--);
        }
        for (Player player : game.world.getPlayers()) {
            player.setScoreboard(board);
        }
    }

    private List<String> buildWoolLines(Game game, TeamManager.TeamId team) {
        List<String> lines = new ArrayList<>();
        for (Target t : game.targets.values()) {
            if (t.team != team) continue;
            String state = Utils.toChatColor(t.color) +
                    (t.completed ? ctwServer.getLangManager().getChar("chars.wool.placed")
                            : ctwServer.getLangManager().getChar("chars.wool.not-placed"));
            String woolName = ctwServer.getLangManager().getWoolName(t.color);
            String line = state + " " + ChatColor.WHITE + woolName;
            lines.add(line);
        }
        return lines;
    }

    private void addScoreLine(Objective obj, String text, int score) {
        String line = ChatColor.translateAlternateColorCodes('&', text);
        if (line.length() > 32) line = line.substring(0, 32);
        obj.getScore(line).setScore(score);
    }

    private void checkForWin(Game game) {
        boolean redComplete = true;
        boolean blueComplete = true;
        for (Target target : game.targets.values()) {
            if (!target.completed) {
                if (target.team == TeamManager.TeamId.BLUE) {
                    blueComplete = false;
                } else {
                    redComplete = false;
                }
            }
        }
        if (redComplete) {
            if (!decorator.isEmpty()) {
                ctwServer.getLangManager().sendVerbatimTextToWorld(decorator, game.world, null);
            }
            ctwServer.getLangManager().sendVerbatimTextToWorld(ctwServer.getLangManager().getText("red-win-game"), game.world, null);
            for (Player players : game.world.getPlayers()) {
                if (ctwServer.getConfig().getBoolean("keep-teams-on-win")) {
                    players.setGameMode(GameMode.SPECTATOR);
                    ctwServer.getPlayerManager().clearInventory(players);
                    players.setAllowFlight(true);
                    players.setFlying(true);
                    if (ctwServer.getConfig().getBoolean("user-fireworks-on-win")) {

                        Utils.firework(ctwServer, players.getLocation(),
                                Color.GREEN, Color.RED, Color.BLUE,
                                FireworkEffect.Type.BALL_LARGE);
                    }
                    if (!players.isOnGround()) {
                        players.teleport(players.getLocation().add(0, 0.5, 0));
                    }
                } else {
                    ctwServer.getPlayerManager().addPlayerTo(players, TeamManager.TeamId.SPECTATOR);
                }
                Utils.firework(ctwServer, players.getLocation(),
                        Color.ORANGE, Color.RED, Color.FUCHSIA,
                        FireworkEffect.Type.BALL_LARGE);
                ctwServer.getSoundManager().playTeamWinSound(players);
                ctwServer.getTitleManager().sendWinRed(players);
            }
            if (!decorator.isEmpty()) {
                ctwServer.getLangManager().sendVerbatimTextToWorld(decorator, game.world, null);
            }
            game.step = 60;
            Bukkit.getScheduler().runTaskLater(ctwServer, () -> startNewRound(game), 60L);
        } else if (blueComplete) {
            if (!decorator.isEmpty()) {
                ctwServer.getLangManager().sendVerbatimTextToWorld(decorator, game.world, null);
            }
            ctwServer.getLangManager().sendVerbatimTextToWorld(ctwServer.getLangManager().getText("blue-win-game"), game.world, null);

            for (Player players : game.world.getPlayers()) {
                if (ctwServer.getConfig().getBoolean("keep-teams-on-win")) {
                    players.setGameMode(GameMode.SPECTATOR);
                    ctwServer.getPlayerManager().clearInventory(players);
                    players.setAllowFlight(true);
                    players.setFlying(true);
                    if (ctwServer.getConfig().getBoolean("user-fireworks-on-win")) {

                        Utils.firework(ctwServer, players.getLocation(),
                                Color.GREEN, Color.RED, Color.BLUE,
                                FireworkEffect.Type.BALL_LARGE);
                    }
                    if (!players.isOnGround()) {
                        players.teleport(players.getLocation().add(0, 0.5, 0));
                    }
                } else {
                    ctwServer.getPlayerManager().addPlayerTo(players, TeamManager.TeamId.SPECTATOR);
                }
                Utils.firework(ctwServer, players.getLocation(),
                        Color.PURPLE, Color.TEAL, Color.BLUE,
                        FireworkEffect.Type.BALL_LARGE);
                ctwServer.getSoundManager().playTeamWinSound(players);
                ctwServer.getTitleManager().sendWinBlue(players);
            }

            if (!decorator.isEmpty()) {
                ctwServer.getLangManager().sendVerbatimTextToWorld(decorator, game.world, null);
            }
            game.step = 60;
            Bukkit.getScheduler().runTaskLater(ctwServer, () -> startNewRound(game), 60L);
        }
    }

    public void ajustWeather(WeatherChangeEvent e) {
        Game game = this.worldGame.get(e.getWorld());
        if (game != null &&
                game.mapData.weather.fixed &&
                e.toWeatherState() != game.mapData.weather.storm)
            e.getWorld().setStorm(game.mapData.weather.storm);
    }

    private void startNewRound(Game game) {
        game.state = GameState.FINISHED;
        counter++;

        game.bt = Bukkit.getScheduler().runTaskTimer(ctwServer, new Runnable() {
            @Override
            public void run() {
                try {
                    switch (game.step) {
                        case 60:
                            ctwServer.getLangManager().sendMessageToWorld("thirty-seconds-to-start", game.world, null);
                            for (Player player : game.world.getPlayers()) {
                                ctwServer.getTitleManager().sendCountdown30(player);
                                ctwServer.getSoundManager().playReversingSound(player);
                            }
                            break;
                        case 40:
                            ctwServer.getLangManager().sendMessageToWorld("twenty-seconds-to-start", game.world, null);
                            for (Player player : game.world.getPlayers()) {
                                ctwServer.getTitleManager().sendCountdown20(player);
                                ctwServer.getSoundManager().playReversingSound(player);
                            }
                            break;
                        case 20:
                            ctwServer.getLangManager().sendMessageToWorld("ten-seconds-to-start", game.world, null);
                            for (Player player : game.world.getPlayers()) {
                                ctwServer.getTitleManager().sendCountdown10(player);
                                ctwServer.getSoundManager().playReversingSound(player);
                            }
                            break;
                        case 10:
                            ctwServer.getLangManager().sendVerbatimTextToWorld(ctwServer.getLangManager().getText("next-game-starts-in-five"), game.world, null);
                            for (Player player : game.world.getPlayers()) {
                                ctwServer.getTitleManager().sendCountdown5(player);
                                ctwServer.getSoundManager().playReversingSound(player);
                            }
                            break;
                        case 8:
                            ctwServer.getLangManager().sendVerbatimTextToWorld(ctwServer.getLangManager().getText("four-seconds-to-start"), game.world, null);
                            for (Player player : game.world.getPlayers()) {
                                ctwServer.getTitleManager().sendCountdown4(player);
                                ctwServer.getSoundManager().playReversingSound(player);
                            }
                            break;
                        case 6:
                            ctwServer.getLangManager().sendVerbatimTextToWorld(ctwServer.getLangManager().getText("three-seconds-to-start"), game.world, null);
                            for (Player player : game.world.getPlayers()) {
                                ctwServer.getTitleManager().sendCountdown3(player);
                                ctwServer.getSoundManager().playReversingSound(player);
                            }
                            break;
                        case 4:
                            ctwServer.getLangManager().sendVerbatimTextToWorld(ctwServer.getLangManager().getText("two-seconds-to-start"), game.world, null);
                            for (Player player : game.world.getPlayers()) {
                                ctwServer.getTitleManager().sendCountdown2(player);
                                ctwServer.getSoundManager().playReversingSound(player);
                            }
                            break;
                        case 2:
                            ctwServer.getLangManager().sendVerbatimTextToWorld(ctwServer.getLangManager().getText("one-second-to-start"), game.world, null);
                            for (Player player : game.world.getPlayers()) {
                                ctwServer.getTitleManager().sendCountdown1(player);
                                ctwServer.getSoundManager().playReversingSound(player);
                            }
                            break;
                        case 0:
                            TreeMap<Player, TeamManager.TeamId> currentTeams = new TreeMap<>(new Utils.PlayerComparator());

                            ctwServer.getPoolManager().swapMap();
                            String newMapName = ctwServer.getPoolManager().getCurrentMap();

                            MapManager.MapData newData = ctwServer.getMapManager().getMapData(newMapName);

                            World newClonedWorld = Bukkit.getWorld("maps/" + newMapName);

                            ctwServer.getWorldManager().restoreMap(newData, newClonedWorld);

                            Location redSpawn = null;
                            Location blueSpawn = null;

                            if (newData.redSpawn != null) {
                                redSpawn = new Location(newClonedWorld,
                                        newData.redSpawn.getX(),
                                        newData.redSpawn.getY(),
                                        newData.redSpawn.getZ(),
                                        newData.redSpawn.getYaw(),
                                        newData.redSpawn.getPitch());
                            }
                            if (newData.blueSpawn != null) {
                                blueSpawn = new Location(newClonedWorld,
                                        newData.blueSpawn.getX(),
                                        newData.blueSpawn.getY(),
                                        newData.blueSpawn.getZ(),
                                        newData.blueSpawn.getYaw(),
                                        newData.blueSpawn.getPitch());
                            }
                            for (Player player : game.world.getPlayers()) {
                                TeamManager.TeamId teamId = ctwServer.getPlayerManager().getTeamId(player);
                                currentTeams.put(player, teamId);

                                if (teamId == TeamManager.TeamId.RED && redSpawn != null) {
                                    player.teleport(redSpawn);
                                } else if (teamId == TeamManager.TeamId.BLUE && blueSpawn != null) {
                                    player.teleport(blueSpawn);
                                } else {
                                    player.teleport(newClonedWorld.getSpawnLocation());
                                }

                                ctwServer.getTitleManager().sendChangeMap(player);
                                ctwServer.getSoundManager().playMapChangeSound(player);
                            }

                            Bukkit.getScheduler().runTaskLater(ctwServer, () -> {
                                for (Player player : currentTeams.keySet()) {
                                    ctwServer.getGameManager().movePlayerTo(player, currentTeams.get(player));
                                }
                            }, 10);

                            ctwServer.getGameManager().removeGame(game.poolName);
                            Game newGame = ctwServer.getGameManager().addGame(game.poolName);
                            newGame.mapData = newData;
                            newGame.world = newClonedWorld;
                            newGame.state = GameState.IN_GAME;

                            for (Map.Entry<Player, TeamManager.TeamId> entry : currentTeams.entrySet()) {
                                ctwServer.getPlayerManager().addPlayerTo(entry.getKey(), entry.getValue());
                            }

                            ctwServer.getLangManager().sendMessageToWorld("starting-new-game", newGame.world, null);
                            break;
                        case -1:
                            game.bt.cancel();
                    }
                } finally {
                    game.step--;
                }
            }
        }, 10, 10);
    }

    private void spawnWool(TreeMap<String, Game> games) {
        for (Game game : games.values()) {
            if (game.mapData.woolSpawners != null) {
                for (String woolColor : game.mapData.woolSpawners.keySet()) {
                    DyeColor dyeColor = DyeColor.valueOf(woolColor);
                    Wool wool = new Wool(dyeColor);
                    ItemStack stack = wool.toItemStack(1);
                    Location loc = new Location(game.world,
                            game.mapData.woolSpawners.get(woolColor).getBlockX(),
                            game.mapData.woolSpawners.get(woolColor).getBlockY(),
                            game.mapData.woolSpawners.get(woolColor).getBlockZ());
                    for (Player player : game.world.getPlayers()) {
                        if (player.getLocation().distance(loc) <= 6
                                && !ctwServer.getPlayerManager().isSpectator(player)) {
                            game.world.dropItem(loc, stack);
                        }
                    }
                }
            }
        }
    }
}
