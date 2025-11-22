package com.mauccio.bctws.map;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

import com.mauccio.bctws.CTWServer;
import com.mauccio.bctws.game.GameManager;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Item;

public class PoolManager {

    private final CTWServer ctwServer;
    private final FileConfiguration config;
    private TreeMap<String, Pool> pools;
    private Pool activePool;

    public static class Pool {

        private String name;
        private List<String> maps;
        int mapIndex;
        private World currentWorld;

        public Pool(String name, List<String> maps) {
            this.name = name;
            this.maps = new ArrayList<>(maps);
            this.mapIndex = 0;
        }

        public String getName() { return name; }

        public String getCurrentMap() {
            if (maps == null || maps.isEmpty()) return null;
            return maps.get(mapIndex);
        }

        public String getNextMap() {
            if (maps.isEmpty()) return null;
            return maps.get((mapIndex + 1) % maps.size());
        }

        public void swapMap() {
            if (maps.isEmpty()) return;
            mapIndex = (mapIndex + 1) % maps.size();
        }

        public World getCurrentWorld(WorldManager wm) {
            String mapName = getCurrentMap();
            if (mapName == null) return null;
            currentWorld = wm.loadWorld(mapName);
            return currentWorld;
        }

        public List<String> getMaps() { return maps; }
    }

    public PoolManager(CTWServer ctwServer) {
        this.ctwServer = ctwServer;
        this.config = ctwServer.getConfig();
        this.pools = new TreeMap<>();
        loadPools();
        setActivePool(ctwServer.getConfig().getString("active-pool", "default"));
    }

    public Pool getActivePool() { return activePool; }

    public void setActivePool(String poolName) {
        Pool pool = pools.get(poolName);
        if (pool == null) {
            ctwServer.alert("Pool no existe: " + poolName);
            return;
        }
        activePool = pool;
        config.set("active-pool", poolName);
        save();
    }

    private void loadPools() {
        pools.clear();
        if (config.getConfigurationSection("pools") == null) return;

        for (String poolName : config.getConfigurationSection("pools").getKeys(false)) {
            List<String> mapNames = config.getStringList("pools." + poolName);
            Pool pool = new Pool(poolName, mapNames);
            pools.put(poolName, pool);
        }

        String activePoolName = config.getString("active-pool", "default");
        activePool = pools.getOrDefault(activePoolName, null);
    }

    public String getCurrentMap() {
        return activePool != null ? activePool.getCurrentMap() : null;
    }

    public String getNextMap() {
        return activePool != null ? activePool.getNextMap() : null;
    }


    public World swapMap() {
        if (activePool == null) return null;

        activePool.swapMap();
        String mapName = activePool.getCurrentMap();

        MapManager.MapData mapData = ctwServer.getMapManager().getMapData(mapName);
        if (mapData == null) {
            ctwServer.getLogger().warning("No se encontró MapData para " + mapName);
            return null;
        }

        World clonedWorld = Bukkit.getWorld("maps/" + mapName);
        if (clonedWorld == null) {
            File uidFile = new File(Bukkit.getWorldContainer(), "maps/" + mapName + "/uid.dat");
            if (uidFile.exists()) uidFile.delete();
            clonedWorld = Bukkit.createWorld(new WorldCreator("maps/" + mapName));
        }

        ctwServer.getWorldManager().restoreMap(mapData, clonedWorld);
        removeWools(mapName, clonedWorld);

        return clonedWorld;
    }


    public boolean addPool(String poolName) {
        if(getPool(poolName) == null) {
            config.set("pools." + poolName, new ArrayList<String>());
            save();
            return true;
        }
        return false;
    }

    public World getCurrentWorld(String poolName) {
        Pool pool = getPool(poolName);
        if (pool == null) return null;
        return pool.getCurrentWorld(ctwServer.getWorldManager());
    }

    public boolean removePool(String poolName) {
        if(getPool(poolName) != null) {
            config.set("pools." + poolName, null);
            save();
            return true;
        }
        return false;
    }

    public boolean addMapToPool(String poolName, String mapName) {
        Pool pool = getPool(poolName);
        MapManager.MapData map = ctwServer.getMapManager().getMapData(mapName);
        if(pool == null) {
            return false;
        }
        if(map == null) {
            return false;
        }
        List<String> maps = config.getStringList("pools." + poolName);
        maps.add(mapName);
        config.set("pools." + poolName, maps);
        save();
        return true;
    }

    public boolean removeMapFromPool(String poolName, String mapName) {
        Pool pool = getPool(poolName);
        MapManager.MapData map = ctwServer.getMapManager().getMapData(mapName);
        if(pool == null) {
            return false;
        }
        if(map == null) {
            return false;
        }
        List<String> maps = config.getStringList("pools." + poolName);
        maps.remove(mapName);
        config.set("pools." + poolName, maps);
        save();
        return true;
    }

    public List<String> listPools() {
        List<String> list = new ArrayList<>();
        for(Pool pool : pools.values()) {
            String entry = ChatColor.AQUA + pool.name;
            String mapList;
            if (pool.maps != null) {
                mapList = ChatColor.GREEN + "[ ";
                for (String map : pool.maps) {
                    mapList = mapList.concat(map.concat(" "));
                }
                mapList = mapList.concat("]");
            } else {
                mapList = ChatColor.RED + "[" + ctwServer.getLangManager().getText("none") + "]";
            }

            entry = entry.concat(ChatColor.AQUA + ctwServer.getLangManager().getText("maps") + ": " + mapList);

            list.add(entry);
        }
        return list;
    }

    public boolean isInGame(World world) {
        if (world == null) return false;
        GameManager.Game game = ctwServer.getGameManager().getGameByWorld(world);
        return game != null && game.getState() == GameManager.GameState.IN_GAME;
    }


    public Pool getPool(String poolName) {
        if (config.getConfigurationSection("pools") == null) return null;
        return pools.get(poolName);
    }

    public void init() {
        String active = ctwServer.getConfig().getString("active-pool", "default");
        for (String poolName : ctwServer.getConfig().getConfigurationSection("pools").getKeys(false)) {
            List<String> mapNames = ctwServer.getConfig().getStringList("pools." + poolName);
            Pool pool = new Pool(poolName, mapNames);
            pools.put(poolName, pool);
            if (poolName.equalsIgnoreCase(active)) activePool = pool;
        }
        if (activePool == null) activePool = pools.get("default");
        ctwServer.getLogger().info("Pools inicializadas. Activa: " + activePool.getName());
    }

    public void load() {
        pools.clear();

        if (config.getConfigurationSection("pools") == null) return;

        for (String poolName : config.getConfigurationSection("pools").getKeys(false)) {
            List<String> mapNames = config.getStringList("pools." + poolName);

            if (mapNames == null || mapNames.isEmpty()) {
                ctwServer.getLogger().warning("Pool \"" + poolName + "\" has no maps defined, disabling.");
                continue;
            }

            List<String> validMaps = new ArrayList<>();
            for (String mapName : mapNames) {
                if (ctwServer.getMapManager().getRestaurationArea(mapName) == null) {
                    ctwServer.alert("Ignoring map \"" + mapName + "\" in pool \"" + poolName + "\": restauration area is not set.");
                    continue;
                }
                validMaps.add(mapName);
            }

            if (validMaps.isEmpty()) {
                ctwServer.getLogger().warning("Pool \"" + poolName + "\" has no valid maps, disabling.");
                continue;
            }

            Pool pool = new Pool(poolName, validMaps);
            pools.put(poolName, pool);
        }

        String activePoolName = config.getString("active-pool", "default");
        activePool = pools.getOrDefault(activePoolName, null);
    }

    public void persist() {
        for (Pool pool : pools.values()) {
            if (pool.maps != null) {
                List<String> mapList = new ArrayList<>(pool.maps);
                config.set("pools." + pool.getName(), mapList);
            }
        }

        if (activePool != null) {
            config.set("active-pool", activePool.getName());
        }
        save();
    }

    public boolean isProhibited(Item item) {
        World world = item.getWorld();
        GameManager.Game game = ctwServer.getGameManager().getGameByWorld(world);
        if (game == null || game.mapData == null) {
            return false;
        }

        MapManager.MapData data = game.mapData;
        if (data.noDropOnBreak != null) {
            return data.noDropOnBreak.contains(item.getItemStack().getType());
        }
        return false;
    }

    public Set<String> getPools() {
        return pools.keySet();
    }

    public boolean exists(String poolName) {
        return getPool(poolName) != null;
    }

    /**
     *
     * @param mapName : Model World
     * @param newWorld : Cloned World
     */
    public void removeWools(String mapName, World newWorld) {
        for (Location loc : ctwServer.getMapManager().getWoolWinLocations(mapName)) {
            Location capturePoint = new Location(newWorld, loc.getBlockX(),
                    loc.getBlockY(), loc.getBlockZ());
            capturePoint.getBlock().setType(Material.AIR);
        }
        ctwServer.getLogger().info("Wools removed from " + newWorld.getName());
    }

    public boolean hasMap(String poolName, String mapName) {
        Pool pool = getPool(poolName);
        if (pool == null || pool.maps == null) {
            return false;
        }
        return pool.maps.contains(mapName);
    }

    private void save() {
        try {
            ctwServer.saveConfig();
        } catch (Exception e) {
            ctwServer.getLogger().severe("Error saving pools: " + e.getMessage());
        }
    }
}