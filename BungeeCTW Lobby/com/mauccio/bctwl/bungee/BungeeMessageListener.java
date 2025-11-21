package com.mauccio.bctwl.bungee;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

public class BungeeMessageListener implements PluginMessageListener {

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if(!channel.equals("BungeeCord")) return;

        ByteArrayDataInput in = ByteStreams.newDataInput(message);
        String subChannel = in.readUTF();

        switch(subChannel) {
            case "RESULT":
                String resultData = in.readUTF();
                break;
            case "UPDATE_STATS":
                String statsData = in.readUTF();
                break;
            case "GetServers":
                String serverList = in.readUTF();
                String[] servers = serverList.split(", ");
                player.getServer().getPluginManager()
                        .callEvent(new ServersListReceivedEvent(player, servers));
                break;
        }
    }
}
