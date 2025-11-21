package com.mauccio.bctwl.listeners;

import com.mauccio.bctwl.CTWLobby;
import com.mauccio.bctwl.utils.LobbyItem;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class EventManager implements Listener {

    private final CTWLobby ctwLobby;

    public EventManager(CTWLobby ctwLobby) {
        this.ctwLobby = ctwLobby;
    }

    @EventHandler
    public void onRoomsGUIClick(InventoryClickEvent e) {
        if (!ctwLobby.getLobbyManager().isPluginGUI(e.getInventory())) return;
        e.setCancelled(true);

        Player player = (Player) e.getWhoClicked();
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        String title = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());

        String format = ChatColor.stripColor(ctwLobby.getLangManager().getText("rooms-gui-format"));
        String prefix = format.split("%ROOM%")[0];

        if (title.startsWith(prefix)) {
            String serverName = title.substring(prefix.length()).trim();
            ctwLobby.getBungeeCommunicator().sendPlayerToServer(player, serverName);
            player.closeInventory();
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        if(ctwLobby.getLobbyManager().getLobbyLoc() != null) {
            Location loc = ctwLobby.getLobbyManager().getLobbyLoc();
            e.getPlayer().teleport(loc);
            if(ctwLobby.getConfigManager().isLobbyBoardEnabled()) {
                ctwLobby.getLobbyManager().assignLobbyBoard(e.getPlayer());
                ctwLobby.getLobbyManager().refreshLobbyBoard();
            }
            if(ctwLobby.getLobbyManager().getLobbyItems() != null && ctwLobby.getConfigManager().isLobbyItemsEnabled()) {
                e.getPlayer().getInventory().setContents(ctwLobby.getLobbyManager().getLobbyItems());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSignChange(SignChangeEvent e) {
        ctwLobby.getSignManager().checkForGameInPost(e);
    }

    @EventHandler(ignoreCancelled = false, priority = EventPriority.LOWEST)
    public void onPlayerInteract(PlayerInteractEvent e) {
        if (e.isCancelled()) {
                return;
        }

        if (e.getAction().equals(Action.RIGHT_CLICK_BLOCK)
                    || e.getAction().equals(Action.LEFT_CLICK_BLOCK)) {
            if (e.getClickedBlock().getType() == Material.WALL_SIGN
                    || e.getClickedBlock().getType() == Material.SIGN_POST) {
                ctwLobby.getSignManager().checkForPlayerJoin(e);
            }
        }
    }

    @EventHandler
    public void onLobbyItemUse(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        Action a = e.getAction();
        if (!(a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK)) return;

        ItemStack item = e.getItem();
        if (item == null || item.getType() == Material.AIR) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return;

        for (LobbyItem lobbyItem : ctwLobby.getLobbyManager().getAllItems()) {
            if (meta.getDisplayName().equals(ChatColor.translateAlternateColorCodes('&', lobbyItem.getName()))) {
                String command = lobbyItem.getCommand();
                if (command != null && !command.isEmpty()) {
                    p.performCommand(command);
                }
                e.setCancelled(true);
                break;
            }
        }

    }

    @EventHandler
    public void onInvClick(InventoryClickEvent event) {
        if (event.getCurrentItem() == null || event.getCurrentItem().equals(new ItemStack(Material.AIR))) return;

        ItemStack clicked = event.getCurrentItem();
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return;

        for (LobbyItem item : ctwLobby.getLobbyManager().getAllItems()) {
            if (meta.getDisplayName().equals(ChatColor.translateAlternateColorCodes('&', item.getName()))) {
                event.setCancelled(true);
                break;
            }
        }
    }

    @EventHandler
    public void onDropItem(PlayerDropItemEvent event) {
        ItemStack dropped = event.getItemDrop().getItemStack();
        ItemMeta meta = dropped.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return;
        for (LobbyItem item : ctwLobby.getLobbyManager().getAllItems()) {
            if (meta.getDisplayName().equals(ChatColor.translateAlternateColorCodes('&', item.getName()))) {
                event.setCancelled(true);
                break;
            }
        }
    }

    @EventHandler
    public void cancelPluginGUI(InventoryClickEvent e) {
        if (!ctwLobby.getLobbyManager().isPluginGUI(e.getInventory())) return;
        e.setCancelled(true);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTitle().equals(ctwLobby.getLangManager().getText("rooms-gui"))) {
            event.setCancelled(true);
        }
    }
}
