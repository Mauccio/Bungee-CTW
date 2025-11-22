package com.mauccio.bctws.game;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import com.mauccio.bctws.CTWServer;
import org.bukkit.*;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.material.Wool;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public class TeamManager {

    private final CTWServer plugin;
    private final Scoreboard scoreboard;
    private final TreeMap<TeamId, TeamInfo> teams;
    public final String armourBrandName;
    private final String bTeamText;
    private final Inventory joinMenuInventory;

    /**
     * Name of the team
     */
    public enum TeamId {
        RED, BLUE, SPECTATOR
    }

    private class TeamInfo {

        Team team;
        Color tshirtColor;
        ChatColor chatColor;
        String name;

        public TeamInfo(TeamId id, Color tshirtColor, DyeColor dye, ChatColor chatColor) {
            team = scoreboard.registerNewTeam(id.toString());
            team.setAllowFriendlyFire(false);
            team.setPrefix(chatColor + "");
            this.tshirtColor = tshirtColor;
            this.chatColor = chatColor;
            name = plugin.getLangManager().getText(id.toString() + "-TEAM");
            team.setDisplayName(chatColor + name);
        }
    }

    public TeamManager(CTWServer plugin) {
        this.plugin = plugin;
        scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        teams = new TreeMap<>();
        TeamInfo teamInfo;
        teamInfo = new TeamInfo(TeamId.RED, Color.RED, DyeColor.RED, ChatColor.RED);
        teams.put(TeamId.RED, teamInfo);
        teamInfo = new TeamInfo(TeamId.BLUE, Color.BLUE, DyeColor.BLUE, ChatColor.BLUE);
        teams.put(TeamId.BLUE, teamInfo);
        teamInfo = new TeamInfo(TeamId.SPECTATOR, Color.AQUA, null, ChatColor.AQUA);
        teams.put(TeamId.SPECTATOR, teamInfo);
        armourBrandName = plugin.getLangManager().getText("armour-brand");
        bTeamText = plugin.getLangManager().getText("brackets-team");
        joinMenuInventory = getTeamInventoryMenu();
    }

    public void addToTeam(Player player, TeamId teamId) {
        teams.get(teamId).team.addEntry(player.getName());
    }

    public void removeFromTeam(Player player, TeamId teamId) {
        if (teamId == null) return;
        Scoreboard sb = plugin.getGameManager().getBoardForWorld(player.getWorld());
        if (sb != null) {
            Team team = sb.getTeam(teamId.name());
            if (team != null) {
                team.removeEntry(player.getName());
            }
        }
    }

    public Color getTshirtColor(TeamId teamId) {
        return teams.get(teamId).tshirtColor;
    }

    public ChatColor getChatColor(TeamId teamId) {
        return teams.get(teamId).chatColor;
    }

    public String getName(TeamId teamId) {
        return teams.get(teamId).name;
    }

    public void onArmourDrop(ItemSpawnEvent e) {
        List<String> lore = e.getEntity().getItemStack().getItemMeta().getLore();
        if (lore == null) {
            return;
        }
        if (lore.contains(armourBrandName)) {
            e.setCancelled(true);
        }
    }

    public void playerChat(AsyncPlayerChatEvent e) {
        Player sender = e.getPlayer();
        TeamId senderTi = plugin.getPlayerManager().getTeamId(sender);
        e.setCancelled(true);

        if (senderTi == null) {
            String message = "<" + e.getPlayer().getDisplayName() + "> " + e.getMessage();
            for (Player receiver : e.getPlayer().getWorld().getPlayers()) {
                receiver.sendMessage(message);
            }
            plugin.getLogger().info(message);
        } else {
            String message = getChatColor(senderTi) + bTeamText + " "
                    + sender.getDisplayName().replace(sender.getName(),
                    getChatColor(senderTi) + sender.getName())
                    + ": " + ChatColor.RESET + e.getMessage();

            for (Player receiver : sender.getWorld().getPlayers()) {
                TeamId receiverTi = plugin.getPlayerManager().getTeamId(receiver);
                if (receiverTi == null || receiverTi != senderTi) {
                    continue;
                }
                receiver.sendMessage(message);
            }

        }
    }

    @SuppressWarnings("incomplete-switch")
    public void cancelSpectator(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) e.getWhoClicked();
        if (plugin.getPlayerManager().isSpectator(player)) {
            e.setCancelled(true);
            if (e.getCurrentItem() != null) {
                if (e.getCurrentItem().equals(plugin.getPlayerManager().getMenuItem())
                        && !joinMenuInventory.getViewers().contains(player)) {
                    player.openInventory(joinMenuInventory);
                } else {
                    switch (e.getCurrentItem().getType()) {
                        case NETHER_STAR:
                            player.closeInventory();
                            plugin.getGameManager().movePlayerTo(player, null);
                            break;
                        case EYE_OF_ENDER:
                            player.closeInventory();
                            break;
                        case WOOL:
                            player.closeInventory();
                            Wool wool = (Wool) e.getCurrentItem().getData();
                            switch (wool.getColor()) {
                                case RED:
                                    if(player.hasPermission("ctw.choseteam")) {
                                        plugin.getGameManager().joinInTeam(player, TeamId.RED);
                                        plugin.getTitleManager().sendJoinRed(player);
                                        plugin.getSoundManager().playTeamJoinSound(player);
                                    } else {
                                        plugin.getLangManager().sendMessage("not-teamselect-perm", player);
                                    }
                                    break;
                                case BLUE:
                                    if(player.hasPermission("ctw.choseteam")) {
                                        plugin.getGameManager().joinInTeam(player, TeamId.BLUE);
                                        plugin.getTitleManager().sendJoinBlue(player);
                                        plugin.getSoundManager().playTeamJoinSound(player);
                                    } else {
                                        plugin.getLangManager().sendMessage("not-teamselect-perm", player);
                                    }
                                    break;
                            }
                            break;
                    }
                }
            }
        }
    }

    public void cancelSpectator(PlayerInteractEvent e) {
        if (e.getItem() != null && e.getItem().equals(plugin.getPlayerManager().getMenuItem())) {
            e.getPlayer().openInventory(joinMenuInventory);
        }
        if (e.isCancelled()) {
            return;
        }
        if (plugin.getPlayerManager().isSpectator(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    public void cancelSpectator(PlayerDropItemEvent e) {
        if (plugin.getPlayerManager().isSpectator(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    public void cancelSpectator(BlockPlaceEvent e) {
        if (plugin.getPlayerManager().isSpectator(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    public void cancelSpectator(BlockBreakEvent e) {
        if (plugin.getPlayerManager().isSpectator(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    public void cancelSpectator(PlayerPickupItemEvent e) {
        if (plugin.getPlayerManager().isSpectator(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    public void cancelSpectator(EntityTargetEvent e) {
        if (!(e.getTarget() instanceof Player)) {
            return;
        }
        Player player = (Player) e.getTarget();
        if (plugin.getPlayerManager().isSpectator(player)) {
            e.setCancelled(true);
        }
    }

    public void cancelSpectator(BlockDamageEvent e) {
        if (plugin.getPlayerManager().isSpectator(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    public void cancelSpectator(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player)) {
            return;
        }
        Player player = (Player) e.getEntity();
        if (plugin.getPlayerManager().isSpectator(player)) {
            e.setCancelled(true);
        }
    }

    public void cancelSpectator(FoodLevelChangeEvent e) {
        if (!(e.getEntity() instanceof Player)) {
            return;
        }
        Player player = (Player) e.getEntity();
        if (plugin.getPlayerManager().isSpectator(player)) {
            e.setCancelled(true);
        }
    }

    public void cancelSpectator(PlayerInteractEntityEvent e) {
        if (plugin.getPlayerManager().isSpectator(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    private Inventory getTeamInventoryMenu() {
        Inventory teamMenu;
        teamMenu = Bukkit.createInventory(null, 27, plugin.getLangManager().getText("pick-your-team"));

        List<String> ayuda = new ArrayList<>();

        ItemStack option = new ItemStack(Material.EMERALD);
        ItemMeta im = option.getItemMeta();
        im.setDisplayName(plugin.getLangManager().getText("view-tutorial"));
        ayuda.add(plugin.getLangManager().getText("not-available-yet"));
        im.setLore(ayuda);
        option.setItemMeta(im);
        teamMenu.setItem(18, option);

        ayuda.clear();
        option = new ItemStack(Material.NETHER_STAR);
        im = option.getItemMeta();
        im.setDisplayName(plugin.getLangManager().getText("auto-join"));
        ayuda.add(plugin.getLangManager().getText("auto-join-help"));
        im.setLore(ayuda);
        option.setItemMeta(im);
        teamMenu.setItem(13, option);

        ayuda.clear();
        Wool wool = new Wool(DyeColor.BLUE);
        option = wool.toItemStack();
        im = option.getItemMeta();
        im.setDisplayName(plugin.getLangManager().getText("join-blue"));
        ayuda.add(plugin.getLangManager().getText("blue-join-help"));
        im.setLore(ayuda);
        option.setItemMeta(im);
        teamMenu.setItem(15, option);

        ayuda.clear();
        wool = new Wool(DyeColor.RED);
        option = wool.toItemStack();
        im = option.getItemMeta();
        im.setDisplayName(plugin.getLangManager().getText("join-red"));
        ayuda.add(plugin.getLangManager().getText("red-join-help"));
        im.setLore(ayuda);
        option.setItemMeta(im);
        teamMenu.setItem(11, option);

        ayuda.clear();
        option = new ItemStack(Material.EYE_OF_ENDER);
        im = option.getItemMeta();
        im.setDisplayName(plugin.getLangManager().getText("close"));
        ayuda.add(plugin.getLangManager().getText("close-menu"));
        im.setLore(ayuda);
        option.setItemMeta(im);
        teamMenu.setItem(26, option);

        return teamMenu;
    }

    public void cancelSameTeam(PlayerFishEvent e) {
        if (e.getCaught() instanceof Player) {
            Player damager = e.getPlayer();
            Player player = (Player) e.getCaught();
            TeamId playerTeam = plugin.getPlayerManager().getTeamId(player);
            TeamId damagerTeam = plugin.getPlayerManager().getTeamId(damager);
            if (playerTeam == damagerTeam) {
                e.setCancelled(true);
            }
        }
    }

    public void cancelSpectatorOrSameTeam(EntityDamageByEntityEvent e) {
        Arrow arrow;
        if (!(e.getEntity() instanceof Player)) {
            return;
        }
        final Player player = (Player) e.getEntity();

        if (plugin.getPlayerManager().isSpectator(player)) {
            e.setCancelled(true);
            return;
        }

        TeamId playerTeam = plugin.getPlayerManager().getTeamId(player);

        if (playerTeam == null) {
            return;
        }
        Player damager;
        if (!(e.getDamager() instanceof Player)) {
            if (!(e.getDamager() instanceof Arrow)) {
                return;
            } else {
                arrow = (Arrow) e.getDamager();
                if (arrow.getShooter() instanceof Player) {
                    damager = (Player) arrow.getShooter();
                } else {
                    return;
                }
            }
        } else {
            damager = (Player) e.getDamager();
        }

        if (plugin.getPlayerManager().isSpectator(damager)) {
            e.setCancelled(true);
            return;
        }

        TeamId damagerTeam = plugin.getPlayerManager().getTeamId(damager);

        if (damagerTeam == null) {
            return;
        }
        if (damagerTeam == playerTeam || playerTeam == TeamId.SPECTATOR) {
            e.setCancelled(true);
            return;
        }
        plugin.getPlayerManager().setLastDamager(player, damager);
    }

    public void manageDeath(PlayerDeathEvent e) {
        Player player = e.getEntity();
        GameManager.Game game = plugin.getGameManager().getGameByWorld(player.getWorld());

        /*
        if (game == null || game.state != GameManager.GameState.IN_GAME) {
            player.setHealth(20);
            return;
        }

         */

        e.setDeathMessage("");

        Player killer = null;
        int blockDistance = 0;
        boolean headshot = false;

        if (player.getLastDamageCause() instanceof EntityDamageByEntityEvent) {
            EntityDamageByEntityEvent dmg = (EntityDamageByEntityEvent) player.getLastDamageCause();
            if (dmg.getDamager() instanceof Player) {
                killer = (Player) dmg.getDamager();
            } else if (dmg.getDamager() instanceof Arrow) {
                Arrow arrow = (Arrow) dmg.getDamager();
                if (arrow.getShooter() instanceof Player) {
                    killer = (Player) arrow.getShooter();
                    blockDistance = (int) player.getLocation().distance(killer.getLocation());
                    double y = arrow.getLocation().getY();
                    double shotY = player.getLocation().getY();
                    headshot = y - shotY > 1.35d;
                }
            }
        }

        String murderText;
        if (killer != null) {
            if (blockDistance == 0) {
                ItemStack is = killer.getItemInHand();
                murderText = plugin.getLangManager().getMurderText(player, killer, is);
            } else {
                murderText = plugin.getLangManager().getRangeMurderText(player, killer, blockDistance, headshot);
                if (headshot) {
                    plugin.getSoundManager().playHeadshotSound(killer);
                    plugin.getTitleManager().sendHeadshot(player);
                    plugin.getLangManager().sendMessage("", killer);
                }
            }
        } else {
            EntityDamageEvent ede = player.getLastDamageCause();
            if (ede != null) {
                if (ede.getCause() == EntityDamageEvent.DamageCause.VOID) {
                    String killerName = plugin.getPlayerManager().getLastDamager(player);
                    if (killerName != null) {
                        killer = plugin.getServer().getPlayer(killerName);
                        if (killer != null) {
                            murderText = plugin.getLangManager().getMurderText(player, killer, null);
                        } else {
                            murderText = plugin.getLangManager().getNaturalDeathText(player, ede.getCause());
                        }
                    } else {
                        murderText = plugin.getLangManager().getNaturalDeathText(player, ede.getCause());
                    }
                } else {
                    murderText = plugin.getLangManager().getNaturalDeathText(player, ede.getCause());
                }
            } else {
                murderText = plugin.getLangManager().getNaturalDeathText(player, EntityDamageEvent.DamageCause.SUICIDE);
            }
        }

        for (Player receiver : player.getWorld().getPlayers()) {
            if (!plugin.getPlayerManager().canSeeOthersDeathMessages(receiver)) {
                if (!receiver.getName().equals(player.getName())
                        && (killer == null || !receiver.getName().equals(killer.getName()))) {
                    continue;
                }
            }
            receiver.sendMessage(murderText);
        }

        if (plugin.getDBManager() != null) {
            String playerName = player.getName();
            if (killer != null) {
                String killerName = killer.getName();
                Player finalKiller = killer;
                killer.sendMessage(plugin.getLangManager().getText("player-messages.add-points"));
                player.sendMessage(plugin.getLangManager().getText("player-messages.remove-points"));

                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    plugin.getDBManager().addEvent(killerName, playerName, "KILL|" + murderText);
                    plugin.getDBManager().incKill(killerName, 1);
                    plugin.getDBManager().incScore(killerName, plugin.getScores().kill);

                    if (plugin.getEconomy() != null) {
                        plugin.getEconomy().depositPlayer(finalKiller, plugin.getScores().coins_kill);
                        finalKiller.sendMessage(plugin.getLangManager().getText("player-messages.add-coins.kill"));
                    }
                });

                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    plugin.getDBManager().addEvent(playerName, killerName, "DEAD|" + murderText);
                    plugin.getDBManager().incDeath(playerName, 1);
                    plugin.getDBManager().incScore(playerName, plugin.getScores().death);
                });
            } else {
                player.sendMessage(plugin.getLangManager().getText("player-messages.remove-points"));
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    plugin.getDBManager().addEvent(playerName, "SUICIDE|" + murderText);
                    plugin.getDBManager().incDeath(playerName, 1);
                    plugin.getDBManager().incScore(playerName, plugin.getScores().death);
                });
            }
        }
    }

    public Inventory getMenuInv() {
        return joinMenuInventory;
    }
}