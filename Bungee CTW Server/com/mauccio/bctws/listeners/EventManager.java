package com.mauccio.bctws.listeners;

import java.util.TreeMap;

import com.mauccio.bctws.CTWServer;
import com.mauccio.bctws.game.GameManager;
import com.mauccio.bctws.game.TeamManager;
import com.mauccio.bctws.map.MapManager;
import com.mauccio.bctws.map.PoolManager;
import com.mauccio.bctws.utils.Utils;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.material.Wool;

public class EventManager {

    private final CTWServer ctwServer;
    private final GameListeners gameEvents;
    private final TreeMap<Player, SetupListeners> playerSetup;

    public enum SetUpAction {

        RED_WIN_WOOL, BLUE_WIN_WOOL, WOOL_SPAWNER
    }

    private class SetupListeners implements Listener {

        private final SetUpAction action;

        public SetupListeners(SetUpAction action) {
            this.action = action;
        }

        @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
        public void onBlockBreakEvent(BlockBreakEvent e) {
            if (!playerSetup.containsKey(e.getPlayer())) {
                return;
            }
            Location currLoc;
            if (e.getBlock().getType() != Material.WOOL) {
                if (e.getBlock().getType() == Material.MOB_SPAWNER) {
                    @SuppressWarnings("deprecation")
                    Wool wool = new Wool(e.getBlock().getType(), e.getBlock().getData());
                    currLoc = ctwServer.getMapManager().getWoolSpawnerLocation(e.getBlock().getWorld(), wool.getColor());
                    if (currLoc != null) {
                        ctwServer.getLangManager().sendMessage("spawner-deleted", e.getPlayer());
                        ctwServer.getMapManager().delWoolSpawner(e.getBlock());
                        return;
                    }
                }
                ctwServer.getLangManager().sendMessage("not-a-wool", e.getPlayer());
                e.setCancelled(true);
                return;
            }
            @SuppressWarnings("deprecation")
            Wool wool = new Wool(e.getBlock().getType(), e.getBlock().getData());
            currLoc = ctwServer.getMapManager().getBlueWoolWinLocation(e.getBlock().getWorld(), wool.getColor());
            if (currLoc != null) {
                ctwServer.getLangManager().sendMessage("cappoint-deleted", e.getPlayer());
                ctwServer.getMapManager().delBlueWoolWinPoint(e.getBlock());
                return;
            }

            currLoc = ctwServer.getMapManager().getRedWoolWinLocation(e.getBlock().getWorld(), wool.getColor());
            if (currLoc != null) {
                ctwServer.getLangManager().sendMessage("cappoint-deleted", e.getPlayer());
                ctwServer.getMapManager().delRedWoolWinPoint(e.getBlock());
                return;
            }
        }

        @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
        public void onBlockPlaceEvent(BlockPlaceEvent e) {

            if (!playerSetup.containsKey(e.getPlayer())) {
                return;
            }
            if (e.getBlock().getType() != Material.WOOL) {
                ctwServer.getLangManager().sendMessage("not-a-wool", e.getPlayer());
                e.setCancelled(true);
                return;
            }
            @SuppressWarnings("deprecation")
            Wool wool = new Wool(e.getBlock().getType(), e.getBlock().getData());
            Location currLoc;
            if (action == SetUpAction.BLUE_WIN_WOOL || action == SetUpAction.RED_WIN_WOOL) {
                currLoc = ctwServer.getMapManager().getBlueWoolWinLocation(e.getBlock().getWorld(), wool.getColor());
                if (currLoc != null) {
                    e.getPlayer().sendMessage(ctwServer.getLangManager().getText("woolwin-already-blueteam")
                            .replace("%X%", currLoc.getBlockX() + "").replace("%Y%", currLoc.getBlockY() + "")
                            .replace("%Z%", currLoc.getBlockZ() + ""));
                    e.setCancelled(true);
                    return;
                }
                currLoc = ctwServer.getMapManager().getRedWoolWinLocation(e.getBlock().getWorld(), wool.getColor());
                if (currLoc != null) {
                    e.getPlayer().sendMessage(ctwServer.getLangManager().getText("woolwin-already-redteam")
                            .replace("%X%", currLoc.getBlockX() + "").replace("%Y%", currLoc.getBlockY() + "")
                            .replace("%Z%", currLoc.getBlockZ() + ""));
                    e.setCancelled(true);
                    return;
                }
            } else if(action == SetUpAction.WOOL_SPAWNER){
                currLoc = ctwServer.getMapManager().getWoolSpawnerLocation(e.getBlock().getWorld(), wool.getColor());
                if (currLoc != null) {
                    e.getPlayer().sendMessage(ctwServer.getLangManager().getText("spawner-already-exists")
                            .replace("%X%", currLoc.getBlockX() + "").replace("%Y%", currLoc.getBlockY() + "")
                            .replace("%Z%", currLoc.getBlockZ() + ""));
                    e.setCancelled(true);
                    return;
                }
            }

            switch (action) {
                case BLUE_WIN_WOOL:
                    if (ctwServer.getMapManager().addBlueWoolWinPoint(e.getBlock())) {
                        ctwServer.getLangManager().sendMessage("blue-wool-winpoint-placed", e.getPlayer());
                    }
                    break;
                case RED_WIN_WOOL:
                    if (ctwServer.getMapManager().addRedWoolWinPoint(e.getBlock())) {
                        ctwServer.getLangManager().sendMessage("red-wool-winpoint-placed", e.getPlayer());
                    }
                    break;
                case WOOL_SPAWNER:
                    if (ctwServer.getMapManager().addwoolSpawner(e.getBlock())) {
                        e.getPlayer().sendMessage(ctwServer.getLangManager().getText("spawner-placed")
                                .replace("%WOOL%", wool.getColor().toString()));
                    }
                    break;
            }
        }

    }

    private class GameListeners implements Listener {

        @EventHandler(priority = EventPriority.HIGHEST)
        public void PlayerBucketEmptyEvent(PlayerBucketEmptyEvent e) {
            if (ctwServer.getConfigManager().isEditMode()) {
                return;
            }
            ctwServer.getGameManager().events.cancelUseBukketOnProtectedAreas(e);

        }

        @EventHandler(priority = EventPriority.HIGHEST)
        public void onWeatherChange(WeatherChangeEvent e) {
            if (ctwServer.getConfigManager().isEditMode()) {
                return;
            }
            ctwServer.getGameManager().ajustWeather(e);
        }

        @EventHandler(priority = EventPriority.HIGHEST)
        public void onPlayerMove(PlayerMoveEvent e) {
            if (ctwServer.getConfigManager().isEditMode()) {
                return;
            }
            ctwServer.getGameManager().denyEnterToProhibitedZone(e);
            if (!e.isCancelled()) {
                if (ctwServer.getMapManager().isMap(e.getPlayer().getWorld())) {
                    ctwServer.getMapManager().announceAreaBoundering(e);
                }
            }
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onVoid(PlayerMoveEvent e) {
            if (ctwServer.getConfigManager().isEditMode()) {
                return;
            }
            Player p = e.getPlayer();
            if (ctwServer.getConfigManager().isVoidInstaKill()) {
                if(ctwServer.getPlayerManager().getTeamId(p) != null &&
                        ctwServer.getPlayerManager().getTeamId(p) != TeamManager.TeamId.SPECTATOR) {
                        if (p.getHealth() <= 0) {
                            return;
                        }
                        if (p.getLocation().getBlockY() <= 0) {
                            EntityDamageEvent damageEvent = new EntityDamageEvent(
                                    p,
                                    EntityDamageEvent.DamageCause.VOID,
                                    p.getHealth() + 1.0
                            );

                            Bukkit.getPluginManager().callEvent(damageEvent);
                            if (!damageEvent.isCancelled()) {
                                p.setLastDamageCause(damageEvent);
                             p.setHealth(0.0);
                            }
                    }
                }
            }
        }

        @EventHandler(priority = EventPriority.HIGHEST)
        public void onDeath(PlayerDeathEvent e) {
            if (ctwServer.getConfigManager().isEditMode()) {
                return;
            }
            ctwServer.getTeamManager().manageDeath(e);
        }



        /*
        @EventHandler(ignoreCancelled = true)
        public void onItemSpawnEvent(ItemSpawnEvent e) {
            ctwServer.getTeamManager().onArmourDrop(e);
            if (!e.isCancelled()) {
                if (ctwServer.getPoolManager().isInGame(e.getEntity().getWorld())) {
                    if (ctwServer.getPoolManager().isProhibited(e.getEntity())) {
                        e.setCancelled(true);
                    }
                }
            }
         }
         */

        @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
        public void onPlayerCommand(PlayerCommandPreprocessEvent e) {
            if (ctwServer.getConfigManager().isEditMode()) {
                return;
            }
            if (ctwServer.getPlayerManager().getTeamId(e.getPlayer()) != null) {
                if (!ctwServer.hasPermission(e.getPlayer(), "ingame-extra-cmds")) {
                    String cmd = e.getMessage().split(" ")[0].replaceFirst("/", "");
                    if (!ctwServer.getCommandManager().isAllowedInGameCmd(cmd)) {
                        e.setCancelled(true);
                        String errorMessage = ChatColor.RED + "/" + cmd + " " + ctwServer.getLangManager().getText("disabled") + ".";
                        ctwServer.getLangManager().sendMessage(errorMessage, e.getPlayer());
                    }
                }
            }
        }

        @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
        public void onPlayerChat(AsyncPlayerChatEvent e) {
            if (ctwServer.getConfigManager().isEditMode()) {
                return;
            }
            ctwServer.getTeamManager().playerChat(e);
        }

        @EventHandler(ignoreCancelled = false, priority = EventPriority.LOWEST)
        public void onPlayerInteract(PlayerInteractEvent e) {
            if (ctwServer.getConfigManager().isEditMode()) {
                return;
            }
            ctwServer.getTeamManager().cancelSpectator(e);

            if (e.isCancelled()) {
                return;
            }

            if (e.getAction().equals(Action.RIGHT_CLICK_BLOCK)
                    && e.getClickedBlock().getType() == Material.WORKBENCH
                    && !e.getPlayer().isSneaking()) {
                if (ctwServer.getPlayerManager().getTeamId(e.getPlayer()) != null) {
                    e.setCancelled(true);
                    e.getPlayer().openWorkbench(null, true);
                }

            }

            if (e.isCancelled()) {
                return;
            }
        }

        @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
        public void onPlayerDrop(PlayerDropItemEvent e
        ) {
            if (ctwServer.getConfigManager().isEditMode()) {
                return;
            }
            ctwServer.getTeamManager().cancelSpectator(e);
        }

        @EventHandler(ignoreCancelled = false, priority = EventPriority.HIGHEST)
        public void onEntityDamageByEntity(EntityDamageByEntityEvent e) {
            if (ctwServer.getConfigManager().isEditMode()) {
                return;
            }
            if (e.isCancelled() && e.getEntity() instanceof Player) {
                TeamManager.TeamId teamId = ctwServer.getPlayerManager().getTeamId((Player) e.getEntity());
                if (teamId != null && teamId != TeamManager.TeamId.SPECTATOR) {
                    e.setCancelled(false);
                }
            }
            if (!e.isCancelled()) {
                ctwServer.getTeamManager().cancelSpectatorOrSameTeam(e);
            }
        }

        @EventHandler
        public void onHit(EntityDamageByEntityEvent event) {
            if (ctwServer.getConfigManager().isEditMode()) {
                return;
            }
            if (!(event.getDamager() instanceof Player)) return;
            Player damager = (Player) event.getDamager();
            if(event.getEntity() instanceof Player) {
                if(ctwServer.getPlayerManager().getTeamId(damager) != null) {
                    if(ctwServer.getPlayerManager().getTeamId(damager) != TeamManager.TeamId.SPECTATOR) {
                        if (ctwServer.getPlayerManager().canSeeBloodEffect(damager)) {
                            Location loc = event.getEntity().getLocation().add(0, 1, 0);
                            damager.playEffect(loc, Effect.STEP_SOUND, Material.REDSTONE_BLOCK);
                        }
                    }
                }
            }
        }

        @EventHandler(ignoreCancelled = false, priority = EventPriority.HIGHEST)
        public void onPlayerFish(PlayerFishEvent e) {
            if (ctwServer.getConfigManager().isEditMode()) {
                return;
            }
            ctwServer.getTeamManager().cancelSameTeam(e);
        }

        @EventHandler(ignoreCancelled = false, priority = EventPriority.HIGHEST)
        public void onPlayerInteractEntity(PlayerInteractEntityEvent e) {
            if (ctwServer.getConfigManager().isEditMode()) {
                return;
            }
            ctwServer.getTeamManager().cancelSpectator(e);
        }

        @EventHandler
        public void onRespawn(PlayerRespawnEvent e) {
            if (ctwServer.getConfigManager().isEditMode()) {
                return;
            }
            ctwServer.getGameManager().onGameRespawn(e);
        }

        @EventHandler(ignoreCancelled = false, priority = EventPriority.HIGHEST)
        public void onInventoryOpenEvent(InventoryOpenEvent e) {
            if (ctwServer.getConfigManager().isEditMode()) {
                return;
            }
            ctwServer.getGameManager().cancelProtectedChest(e);
        }

        @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
        public void onInventoryClick(InventoryClickEvent e) {
            if (ctwServer.getConfigManager().isEditMode()) {
                return;
            }
            ctwServer.getTeamManager().cancelSpectator(e);
            if (!e.isCancelled()) {
                ctwServer.getGameManager().checkTarget(e);
            }
        }

        @EventHandler
        public void alertOnJoin(PlayerJoinEvent e) {
            if(e.getPlayer().hasPermission("ctw.admin") || e.getPlayer().isOp()) {
                if(ctwServer.getEconomy() == null) {
                    if(ctwServer.getConfigManager().isKitMenuEnabled()) {
                        ctwServer.getLangManager().sendMessage("no-vault-alert", e.getPlayer());
                    }
                }
            }
        }

        @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
        public void onPlayerTeleport(PlayerTeleportEvent e) {
            if (ctwServer.getConfigManager().isEditMode()) {
                return;
            }
            if (!e.getFrom().getWorld().getName().equals(e.getTo().getWorld().getName())) {
                if (ctwServer.getPoolManager().isInGame(e.getTo().getWorld())) {
                    Player player = e.getPlayer();
                    Bukkit.getScheduler().runTaskLater(ctwServer, new Runnable() {
                        @Override
                        public void run() {
                            ctwServer.getGameManager().movePlayerTo(player, TeamManager.TeamId.SPECTATOR);
                        }
                    }, 5);
                } else {
                    if (ctwServer.getPoolManager().isInGame(e.getFrom().getWorld())) {
                        ctwServer.getGameManager().playerLeftGame(e.getPlayer());
                    }
                }
            }
        }

        @EventHandler
        public void onTeleport(PlayerTeleportEvent e) {
            if (ctwServer.getConfigManager().isEditMode()) {
                return;
            }
            Bukkit.getScheduler().runTaskLater(ctwServer, () -> {
                ctwServer.getPlayerManager().updateTablistFor(e.getPlayer());
            }, 2L);
        }

        @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
        public void onPlayerQuit(PlayerQuitEvent e) {
            if (ctwServer.getConfigManager().isEditMode()) {
                return;
            }
            Player player = e.getPlayer();
            e.setQuitMessage(null);

            player.setDisplayName(player.getName());
            player.setPlayerListName(player.getName());

            if (ctwServer.getPoolManager().isInGame(player.getWorld())) {
                ctwServer.getGameManager().playerLeftGame(player);
            }
        }

        @EventHandler
        public void onJoinTablist(PlayerJoinEvent e) {
            if (ctwServer.getConfigManager().isEditMode()) {
                return;
            }
            ctwServer.getPlayerManager().updateTablistFor(e.getPlayer());
        }

        @EventHandler
        public void onWorldChange(PlayerChangedWorldEvent e) {
            if (ctwServer.getConfigManager().isEditMode()) {
                return;
            }
            ctwServer.getPlayerManager().updateTablistFor(e.getPlayer());
        }

        @EventHandler
        public void onWorldUnload(WorldUnloadEvent e) {
            GameManager.Game game = ctwServer.getGameManager().getGameByWorld(e.getWorld());
            if (game != null) {
                ctwServer.getGameManager().removeGame(game.getPoolName());
                ctwServer.getLogger().info("Game eliminado porque se descargó el mundo " + e.getWorld().getName());

                PoolManager.Pool pool = ctwServer.getPoolManager().getPool(game.getPoolName());
                if (pool != null) {
                    ctwServer.getGameManager().addGame(pool.getName());
                    ctwServer.getLogger().info("Game recreado para pool " + pool.getName());
                }
            }
        }

        @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
        public void onJoinEvent(PlayerJoinEvent e) {
            if (ctwServer.getConfigManager().isEditMode()) {
                return;
            }
            e.setJoinMessage(null);

            Player player = e.getPlayer();

            String joinMessage = ctwServer.getLangManager().getText("join-message")
                    .replace("%PLAYER%", player.getDisplayName());
            player.sendMessage(joinMessage);

            if (ctwServer.getPlayerManager().getTeamId(player) == null) {
                player.setDisplayName(player.getName());
                player.setPlayerListName(player.getName());
                ctwServer.getTeamManager().addToTeam(player, TeamManager.TeamId.SPECTATOR);

            }

            PoolManager.Pool pool = ctwServer.getPoolManager().getActivePool();
            if (pool == null || pool.getMaps().isEmpty()) {
                ctwServer.getLogger().severe("No hay mapas en la pool activa");
                return;
            }

            String mapName = pool.getCurrentMap();
            MapManager.MapData data = ctwServer.getMapManager().getMapData(mapName);
            World world = Bukkit.getWorld("maps/" + mapName);

            Location spawnLocation = new Location(world,
                    data.mapSpawn.getX(),
                    data.mapSpawn.getY(),
                    data.mapSpawn.getZ(),
                    data.mapSpawn.getYaw(),
                    data.mapSpawn.getPitch());

            player.teleport(spawnLocation);

            GameManager.Game game = ctwServer.getGameManager().getGameByWorld(world);
            if (game != null && game.getState() == GameManager.GameState.IN_GAME) {
                ctwServer.getNametagManager().updateNametag(player, TeamManager.TeamId.SPECTATOR, game.getBoard());
                ctwServer.getPlayerManager().addPlayerTo(player, TeamManager.TeamId.SPECTATOR);
                ctwServer.getGameManager().updateScoreBoard(game);
            }
        }


        @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
        public void onBlockPlaceEvent(BlockPlaceEvent e) {
            if (ctwServer.getConfigManager().isEditMode()) {
                return;
            }
            ctwServer.getTeamManager().cancelSpectator(e);
            if (!e.isCancelled()) {
                ctwServer.getGameManager().events.cancelEditProtectedAreas(e);
            }
            if (e.isCancelled()) {
                ctwServer.getGameManager().checkTarget(e);
            }
        }

        @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
        public void onCrafting(CraftItemEvent e) {
            if (ctwServer.getConfigManager().isEditMode()) {
                return;
            }
            ctwServer.getGameManager().events.cancelCrafting(e);
        }

        @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
        public void onBlockBreakEvent(BlockBreakEvent e) {
            if (ctwServer.getConfigManager().isEditMode()) {
                return;
            }
            ctwServer.getTeamManager().cancelSpectator(e);
            ctwServer.getGameManager().events.cancelEditProtectedAreas(e);
            if (!e.isCancelled()) {
                if (ctwServer.getPlayerManager().getTeamId(e.getPlayer()) != null) {
                    Location blockLoc = e.getBlock().getLocation();
                    for (Entity entity : e.getPlayer().getWorld().getNearbyEntities(blockLoc,1, 2, 1)) {
                        if(entity instanceof Player) {
                            Player other = (Player) entity;
                            if (other.getName().equals(e.getPlayer().getName())) {
                                continue;
                            }
                            if (other.getLocation().getBlockX() == e.getBlock().getLocation().getBlockX()
                                    && other.getLocation().getBlockY() >= e.getBlock().getLocation().getBlockY()
                                    && other.getLocation().getBlockY() < e.getBlock().getLocation().getBlockY() + 2
                                    && other.getLocation().getBlockZ() == e.getBlock().getLocation().getBlockZ()
                                    && e.getBlock().getType().isSolid()){

                                String spleafText = ctwServer.getLangManager().getText("block-spleaf");

                                ctwServer.getLangManager().sendVerbatimTextToWorld(
                                        spleafText.replace("%DAMAGER%",
                                                        ctwServer.getPlayerManager().getChatColor(e.getPlayer()) + e.getPlayer().getName())
                                                .replace("%VICTIM%",
                                                        ctwServer.getPlayerManager().getChatColor(other) + other.getName()), other.getWorld(), null);
                                break;
                            }
                        }
                    }
                }
            }
        }

        @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
        public void onPlayerPickupItem(PlayerPickupItemEvent e) {
            if (ctwServer.getConfigManager().isEditMode()) {
                return;
            }
            ctwServer.getTeamManager().cancelSpectator(e);
            if (!e.isCancelled()) {
                ctwServer.getGameManager().checkTarget(e);
            }
        }

        @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
        public void onEntityTarget(EntityTargetEvent e
        ) {
            if (ctwServer.getConfigManager().isEditMode()) {
                return;
            }
            ctwServer.getTeamManager().cancelSpectator(e);
        }

        @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
        public void onBlockDamage(BlockDamageEvent e
        ) {
            if (ctwServer.getConfigManager().isEditMode()) {
                return;
            }
            ctwServer.getTeamManager().cancelSpectator(e);
        }

        @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
        public void onEntityDamage(EntityDamageEvent e) {
            if (ctwServer.getConfigManager().isEditMode()) {
                return;
            }
            ctwServer.getTeamManager().cancelSpectator(e);
        }

        @EventHandler(priority = EventPriority.HIGHEST)
        public void onFallDamage(EntityDamageEvent e) {
            if (ctwServer.getConfigManager().isEditMode()) {
                return;
            }
            if(e.getCause() == DamageCause.FALL) {
                if(e.getEntity() instanceof Player) {
                    Player plr = (Player) e.getEntity();
                    if(ctwServer.getPlayerManager().getTeamId(plr) != null) {
                        e.setCancelled(ctwServer.getConfigManager().isFallDamage());
                    }
                }
            }
        }

        @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
        public void onFoodLevelChange(FoodLevelChangeEvent e) {
            if (ctwServer.getConfigManager().isEditMode()) {
                return;
            }
            ctwServer.getTeamManager().cancelSpectator(e);
            if (e.getEntity() instanceof Player && !e.isCancelled()) {
                Player player = (Player) e.getEntity();
                TeamManager.TeamId ti = ctwServer.getPlayerManager().getTeamId(player);
                if (ti != null && player.getFoodLevel() > e.getFoodLevel()) {
                    if ((Math.random() * ((10) + 1)) > 4) {
                        e.setCancelled(true);
                    }
                }
            }
        }
    }

    public EventManager(CTWServer ctwServer) {
        this.ctwServer = ctwServer;
        gameEvents = new GameListeners();
        playerSetup = new TreeMap<>(new Utils.PlayerComparator());
        registerGameEvents();
    }

    public void registerGameEvents() {
        ctwServer.getServer().getPluginManager().registerEvents(gameEvents, ctwServer);
    }

    public void registerSetupEvents(Player player, SetUpAction action) {
        unregisterSetUpEvents(player);
        SetupListeners sl = new SetupListeners(action);
        ctwServer.getServer().getPluginManager().registerEvents(sl, ctwServer);
        playerSetup.put(player, sl);
    }

    public void unregisterGameEvents() {
        HandlerList.unregisterAll(gameEvents);
    }

    public void unregisterSetUpEvents(Player player) {
        SetupListeners sl = playerSetup.remove(player);
        if (sl != null) {
            HandlerList.unregisterAll(sl);
        }
    }
}
