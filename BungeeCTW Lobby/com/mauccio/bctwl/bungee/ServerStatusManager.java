package com.mauccio.bctwl.bungee;

import java.util.HashMap;
import java.util.Map;

public class ServerStatusManager {

    public static class ServerInfo {
        private ServerStatus status;
        private int currentPlayers;
        private int maxPlayers;

        public ServerInfo(ServerStatus status, int currentPlayers, int maxPlayers) {
            this.status = status;
            this.currentPlayers = currentPlayers;
            this.maxPlayers = maxPlayers;
        }

        public ServerStatus getStatus() { return status; }
        public int getCurrentPlayers() { return currentPlayers; }
        public int getMaxPlayers() { return maxPlayers; }

        public void setStatus(ServerStatus status) { this.status = status; }
        public void setCurrentPlayers(int currentPlayers) { this.currentPlayers = currentPlayers; }
        public void setMaxPlayers(int maxPlayers) { this.maxPlayers = maxPlayers; }
    }

    private final Map<String, ServerInfo> servers = new HashMap<>();

    public void updateServer(String name, ServerStatus status, int currentPlayers, int maxPlayers) {
        servers.put(name, new ServerInfo(status, currentPlayers, maxPlayers));
    }

    public ServerStatus getStatus(String name) {
        return servers.containsKey(name) ? servers.get(name).getStatus() : ServerStatus.INACTIVE;
    }

    public int getCurrentPlayers(String name) {
        return servers.containsKey(name) ? servers.get(name).getCurrentPlayers() : 0;
    }

    public int getMaxPlayers(String name) {
        return servers.containsKey(name) ? servers.get(name).getMaxPlayers() : 0;
    }

    public int getTotalServers() {
        return servers.size();
    }

    public int getActiveServers() {
        return (int) servers.values().stream()
                .filter(info -> info.getStatus() == ServerStatus.ACTIVE_GAME)
                .count();
    }
}
