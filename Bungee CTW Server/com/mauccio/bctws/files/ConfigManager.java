package com.mauccio.bctws.files;

import com.mauccio.bctws.CTWServer;
import com.mauccio.bctws.map.MapManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.*;
import java.io.*;
import java.util.List;

public class ConfigManager {

    private final CTWServer ctwServer;
    private FileConfiguration config;

    public ConfigManager(CTWServer ctwServer) {
        this.ctwServer = ctwServer;
        ctwServer.saveDefaultConfig();
        this.config = ctwServer.getConfig();
        this.load(false);
    }

    public void load() {
        this.load(true);
    }

    public void persists() {
        this.ctwServer.saveConfig();
    }

    private void load(boolean reload) {
        if (reload) {
            this.ctwServer.reloadConfig();
        }
        File defaultMapFile = new File(this.ctwServer.getDataFolder(), "defaultmap.yml");
        if (!defaultMapFile.exists()) {
            this.ctwServer.saveResource("defaultmap.yml", false);
        }
    }

    public boolean isEditMode() {
        return this.config.getBoolean("editor-mode", true);
    }

    private void copyDirectory(File source, File target) throws IOException {
        if (source == null || !source.exists() || !source.isDirectory()) {
            ctwServer.getLogger().warning("No se encontró carpeta modelo: " + source);
            return;
        }
        if (!target.exists()) {
            target.mkdirs();
        }

        File[] files = source.listFiles();
        if (files == null) return;

        for (File file : files) {
            File dest = new File(target, file.getName());
            if (file.isDirectory()) {
                copyDirectory(file, dest);
            } else {
                java.nio.file.Files.copy(file.toPath(), dest.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }

        File uidFile = new File(target, "uid.dat");
        if (uidFile.exists()) {
            if (uidFile.delete()) {
                ctwServer.getLogger().info("uid.dat eliminado en clon: " + target.getName());
            } else {
                ctwServer.getLogger().warning("No se pudo eliminar uid.dat en clon: " + target.getName());
            }
        }
    }


    private void deleteDirectory(File dir) throws IOException {
        if (!dir.exists()) return;
        for (File file : dir.listFiles()) {
            if (file.isDirectory()) {
                deleteDirectory(file);
            }
            file.delete();
        }
        dir.delete();
    }


    public void verifyEditorMode() {
        if (config == null) {
            ctwServer.getLogger().severe("Config is not loaded yet!");
            return;
        }
        boolean editMode = isEditMode();
        boolean hasMaps = !ctwServer.getMapManager().getMaps().isEmpty();

        if (editMode) {
            if (!hasMaps) {
                ctwServer.getLogger().warning("Editor Mode: No hay mapas configurados.");
                ctwServer.getLogger().info("Usa /ctwsetup mapconfig para añadir al menos un mapa antes de desactivar el modo editor.");
            } else {
                ctwServer.getLogger().info("Editor Mode enabled.");
            }
        } else {
            if (!hasMaps) {
                ctwServer.getLogger().severe("No hay mapas configurados. Forzando editor-mode a true por seguridad.");
                ctwServer.getConfig().set("editor-mode", true);
                ctwServer.saveConfig();
                Bukkit.getPluginManager().disablePlugin(ctwServer);
            } else {

                ctwServer.getLogger().info("Editor Mode disabled: cargando clones en /maps/...");
                for (String mapName : ctwServer.getMapManager().getMaps()) {
                    MapManager.MapData data = ctwServer.getMapManager().getMapData(mapName);
                    File source = new File(Bukkit.getWorldContainer(), mapName);
                    File target = new File(Bukkit.getWorldContainer(), "maps/" + mapName);

                    try {
                        if (target.exists()) {
                            ctwServer.getLogger().info("Clon existente para " + mapName + ", borrando...");
                            deleteDirectory(target);
                        }
                        copyDirectory(source, target);
                        ctwServer.getLogger().info("Mapa clonado: " + mapName);
                    } catch (IOException ex) {
                        ctwServer.getLogger().severe("Error clonando mapa " + mapName + ": " + ex.getMessage());
                    }
                }
            }
        }
    }

    public String getSignFirstLine() {
        return this.config.getString("signs.first-line-text");
    }

    public boolean implementSpawnCmd() {
        return this.config.getBoolean("implement-spawn-cmd", false);
    }

    public boolean isVoidInstaKill() {
        return this.ctwServer.getConfig().getBoolean("instakill-on-void", false);
    }

    public boolean isFallDamage() {
        return this.ctwServer.getConfig().getBoolean("disable-fall-damage", false);
    }

    public boolean isKitSQL() {
        return this.ctwServer.getConfig().getBoolean("use-sql-for-kits", false);
    }

    public List<String> getBreakableBlocks() {
        return ctwServer.getConfig().getStringList("no-protected-blocks");
    }

    public List<String> getNoCrafteableItems() {
        return ctwServer.getConfig().getStringList("no-crafteable-items");
    }

    public boolean isSoundsEnabled() {
        return this.ctwServer.getConfig().getBoolean("sounds.enabled", true);
    }

    public boolean isGlobalTablistEnabled() {
        return this.ctwServer.getConfig().getBoolean("global-tablist", false);
    }

    public boolean isLobbyGuardEnabled() {
        return this.ctwServer.getConfig().getBoolean("lobby.guard", false);
    }

    public boolean isLobbyItemsEnabled() {
        return this.ctwServer.getConfig().getBoolean("lobby.items", true);
    }

    public boolean isLobbyBoardEnabled() {
        return this.ctwServer.getConfig().getBoolean("lobby.scoreboard", true);
    }

    public boolean isKitMenuEnabled() {
        return this.ctwServer.getConfig().getBoolean("kit-menu", false);
    }

    public boolean isProtectedZoneMsg() {
        return this.ctwServer.getConfig().getBoolean("prohibited-msg", true);
    }

    public boolean isNoRespawnScreen() {
        return this.ctwServer.getConfig().getBoolean("no-respawn-screen", false);
    }
}