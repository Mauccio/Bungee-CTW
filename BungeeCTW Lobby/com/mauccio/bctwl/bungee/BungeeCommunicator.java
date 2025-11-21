package com.mauccio.bctwl.bungee;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.mauccio.bctwl.CTWLobby;
import org.bukkit.entity.Player;

public class BungeeCommunicator {

    private CTWLobby ctwLobby;

    public BungeeCommunicator(CTWLobby ctwLobby) {
        this.ctwLobby = ctwLobby;
        ctwLobby.getServer().getMessenger().registerOutgoingPluginChannel(ctwLobby,"BungeeCord");
        ctwLobby.getServer().getMessenger().registerIncomingPluginChannel(ctwLobby, "BungeeCord", new BungeeMessageListener());
    }

    public void sendPlayerToServer(Player player, String serverName) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Connect");
        out.writeUTF(serverName);
        player.sendPluginMessage(ctwLobby, "BungeeCord", out.toByteArray());
    }

    public void sendCustomMessage(String subChannel, String data) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF(subChannel);
        out.writeUTF(data);
        ctwLobby.getServer().sendPluginMessage(ctwLobby, "BungeeCord", out.toByteArray());
    }

    public void requestServerList(Player player) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("GetServers");
        player.sendPluginMessage(ctwLobby, "BungeeCord", out.toByteArray());
    }
}
