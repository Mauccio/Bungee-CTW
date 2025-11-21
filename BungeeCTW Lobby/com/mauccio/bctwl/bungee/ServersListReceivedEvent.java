package com.mauccio.bctwl.bungee;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class ServersListReceivedEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private final Player player;
    private final String[] servers;

    public ServersListReceivedEvent(Player player, String[] servers) {
        this.player = player;
        this.servers = servers;
    }

    public Player getPlayer() { return player; }
    public String[] getServers() { return servers; }

    @Override
    public HandlerList getHandlers() { return handlers; }
    public static HandlerList getHandlerList() { return handlers; }
}
