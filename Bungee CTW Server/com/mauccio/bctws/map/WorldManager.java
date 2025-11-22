package com.mauccio.bctws.map;

import com.mauccio.bctws.CTWServer;
import com.sk89q.worldedit.bukkit.selections.CuboidSelection;
import com.sk89q.worldedit.bukkit.selections.Selection;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantLock;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.util.FileUtil;

public class WorldManager {

    private final CTWServer ctwServer;
    private final EmptyGenerator eg;
    private final TreeMap<String, CuboidSelection> restoreAreas;
    private final ReentrantLock _restoreAreas_mutex;

    public static class EmptyGenerator extends ChunkGenerator {

        private final ArrayList<BlockPopulator> populator;
        private final byte[][] blocks;

        public EmptyGenerator() {
            super();
            populator = new ArrayList<>();
            blocks = new byte[256 / 16][];
        }

        @Override
        public List<BlockPopulator> getDefaultPopulators(World world) {
            return populator;
        }

        @Override
        public boolean canSpawn(World world, int x, int z) {
            return true;
        }

        @Override
        public byte[][] generateBlockSections(World world, Random random, int x, int z, BiomeGrid biomes) {
            return blocks;
        }

    }

    public WorldManager(CTWServer ctwServer) {
        this.ctwServer = ctwServer;
        eg = new EmptyGenerator();
        _restoreAreas_mutex = new ReentrantLock(true);
        restoreAreas = new TreeMap<>();
    }

    public EmptyGenerator getEmptyWorldGenerator() {
        return eg;
    }

    private void setDefaults(World world) {
        world.setAmbientSpawnLimit(0);
        world.setAnimalSpawnLimit(0);
        world.setAutoSave(true);
        world.setDifficulty(Difficulty.EASY);
        world.setGameRuleValue("doMobSpawning", "false");
        world.setMonsterSpawnLimit(0);
        world.setPVP(true);
        world.setWaterAnimalSpawnLimit(0);
        world.setWeatherDuration(Integer.MAX_VALUE);
    }

    public World loadWorld(String worldName) {
        World world = ctwServer.getServer().getWorld(worldName);
        if (world == null) {
            File worldDir = new File(worldName);
            if (worldDir.exists() && worldDir.isDirectory()) {
                WorldCreator creator = WorldCreator.name(worldName).seed(0)
                        .environment(World.Environment.NORMAL);
                creator.generator(getEmptyWorldGenerator());
                world = Bukkit.createWorld(creator);
            }
        }
        if (world != null) setDefaults(world);
        return world;
    }

    private World createWorld(String worldName) {
        World world = loadWorld(worldName);
        if (world == null) {
            Random r = new Random();
            long seed = (long) (r.nextDouble() * Long.MAX_VALUE);
            World.Environment e = World.Environment.NORMAL;
            WorldCreator creator = WorldCreator.name(worldName).seed(seed).environment(e);
            creator.generator(getEmptyWorldGenerator());
            world = Bukkit.createWorld(creator);
            setDefaults(world);
        }
        return world;
    }

    public World createEmptyWorld(String worldName) {
        World world = loadWorld(worldName);
        if (world == null) {
            WorldCreator creator = WorldCreator.name(worldName)
                    .seed(new Random().nextLong())
                    .environment(World.Environment.NORMAL)
                    .generator(getEmptyWorldGenerator());
            world = Bukkit.createWorld(creator);
        }
        setDefaults(world);
        world.getBlockAt(0, 61, 0).setType(Material.GLASS);
        world.setSpawnLocation(0, 63, 0);
        return world;
    }

    public void clearEntities(World world) {
        for (Entity entity : world.getEntities()) {
            if (entity.getType() != EntityType.ITEM_FRAME &&
                    entity.getType() != EntityType.PLAYER) {
                entity.remove();
            }
        }
    }

    public void unloadWorld(World world) {
        clearEntities(world);
        world.setKeepSpawnInMemory(false);
        for (Chunk chunk : world.getLoadedChunks()) {
            world.unloadChunk(chunk);
        }
        Bukkit.unloadWorld(world, false);
    }

    public World cloneWorld(World world, String newWorld, boolean load) {
        File newWorldDir = new File(newWorld);
        if (newWorldDir.exists()) {
            return null;
        }
        try {
            copyFolder(world.getWorldFolder(), newWorldDir);
        } catch (IOException ex) {
            ctwServer.getLogger().severe(ex.toString());
            return null;
        }
        File uidFile = new File(newWorld, "uid.dat");
        uidFile.delete();
        World ret;
        if (load) {
            ret = loadWorld(newWorld);
        } else {
            ret = null;
        }
        return ret;
    }

    public World cloneWorld(World source, String newWorld) {
        File newWorldDir = new File(newWorld);
        if (newWorldDir.exists()) return null;
        try {
            copyFolder(source.getWorldFolder(), newWorldDir);
        } catch (IOException ex) {
            ctwServer.getLogger().severe(ex.toString());
            return null;
        }
        new File(newWorld, "uid.dat").delete();
        return loadWorld(newWorld);
    }

    private static void copyFolder(File src, File dest) throws IOException {
        if (src.isDirectory()) {
            if (!dest.exists()) {
                dest.mkdir();
            }
            String files[] = src.list();
            for (String file : files) {
                File srcFile = new File(src, file);
                File destFile = new File(dest, file);
                copyFolder(srcFile, destFile);
            }
        } else {
            if (!FileUtil.copy(src, dest)) {
                System.out.println("Error copying: " + src.getAbsolutePath() + " to " + dest.getAbsolutePath());
            }
        }
    }

    public void restoreMap(MapManager.MapData map, World clonedWorld) {
        if (map == null || clonedWorld == null || map.restaurationArea == null) {
            return;
        }

        Location min = map.restaurationArea.getMinimumPoint();
        Location max = map.restaurationArea.getMaximumPoint();
        int delay = 1;
        World source = map.world;
        if (source == null) return;

        for (int x = min.getBlockX(); x <= max.getBlockX(); x++) {
            Location areaMin = new Location(source, x, min.getBlockY(), min.getBlockZ());
            Location areaMax = new Location(source, x, max.getBlockY(), max.getBlockZ());
            Selection sel = new CuboidSelection(source, areaMin, areaMax);

            Bukkit.getScheduler().runTaskLater(ctwServer, () -> {
                cloneRegion(ctwServer, source, clonedWorld, sel);
            }, delay);

            delay += 0.5;
        }

        Bukkit.getScheduler().runTaskLater(ctwServer, () -> {
            ctwServer.getPoolManager().removeWools(map.world.getName(), clonedWorld);
        }, delay);
    }


    private void cloneRegion(CTWServer plugin, World source, World destination, Selection area) {
        Location min = area.getMinimumPoint();
        Location max = area.getMaximumPoint();

        for (int X = min.getBlockX(); X <= max.getBlockX(); X++) {
            for (int Y = min.getBlockY(); Y <= max.getBlockY(); Y++) {
                for (int Z = min.getBlockZ(); Z <= max.getBlockZ(); Z++) {
                    Block src = source.getBlockAt(X, Y, Z);
                    Block dst = destination.getBlockAt(X, Y, Z);
                    dst.setTypeIdAndData(src.getTypeId(), src.getData(), false);
                    if (src.getType() == Material.SIGN_POST
                            || src.getType() == Material.WALL_SIGN) {
                        Sign s = (Sign) src.getState();
                        Sign d = (Sign) dst.getState();
                        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
                            @Override
                            public void run() {
                                d.setLine(0, s.getLine(0));
                                d.setLine(1, s.getLine(1));
                                d.setLine(2, s.getLine(2));
                                d.setLine(3, s.getLine(3));
                                d.update();
                            }
                        }, 1);
                    } else if (src.getType() == Material.CHEST
                            || src.getType() == Material.TRAPPED_CHEST) {

                        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
                            @Override
                            public void run() {
                                Chest s = (Chest) src.getState();
                                Chest d = (Chest) dst.getState();
                                try {
                                    d.getInventory().setContents(s.getInventory().getContents());
                                } catch (IllegalArgumentException ex) {
                                    // Do nothing.
                                }
                            }
                        }, 1);

                    }
                }
            }
        }
        clearEntities(destination);
    }
}